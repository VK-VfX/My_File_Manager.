package com.vfxsal.filemanager.feature.files.vault

import android.content.Context
import com.vfxsal.filemanager.data.FileIndex
import com.vfxsal.filemanager.feature.clean.scan.FileTreeWalker
import com.vfxsal.filemanager.feature.files.tags.FileTagsStore
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * A private, PIN-gated space for files: moved-in items live in an app-private directory
 * (invisible to other apps, the gallery, and the rest of this app's own file listings)
 * until the user restores them. This is access-control, not encryption - anyone who roots
 * the device or gets a backup of app-private storage can still read the files, same as any
 * other "hide from casual snooping" vault feature on Android without a dedicated keystore.
 * The PIN itself is never stored: only a salted SHA-256 hash of it.
 */
object VaultOps {

    private const val VAULT_DIR_NAME = "vault"
    private const val MANIFEST_NAME = "manifest.txt"
    private const val PREFS_NAME = "vault_security"
    private const val KEY_PIN_HASH = "pin_hash"
    private const val KEY_PIN_SALT = "pin_salt"
    private const val KEY_PIN_ITERATIONS = "pin_iterations"
    private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
    private const val KEY_LOCKOUT_UNTIL = "lockout_until"

    private const val PBKDF2_ITERATIONS = 50_000
    private const val MAX_ATTEMPTS_BEFORE_LOCKOUT = 5
    private const val LOCKOUT_MILLIS = 30_000L

    data class VaultEntry(
        val id: String,
        val originalPath: String,
        val addedAtMillis: Long,
        val isDirectory: Boolean,
        val sizeBytes: Long,
    ) {
        fun vaultedFile(context: Context): File =
            File(vaultDir(context), "$id-${File(originalPath).name}")
    }

    fun vaultDir(context: Context): File = File(context.filesDir, VAULT_DIR_NAME).apply { mkdirs() }

    private fun manifestFile(context: Context): File = File(vaultDir(context), MANIFEST_NAME)

    // --- PIN management -----------------------------------------------------------------

    fun hasPin(context: Context): Boolean =
        prefs(context).contains(KEY_PIN_HASH)

    fun setPin(context: Context, pin: String) {
        val salt = randomSaltHex()
        val hash = pbkdf2Hash(pin, salt)
        prefs(context).edit()
            .putString(KEY_PIN_SALT, salt)
            .putString(KEY_PIN_HASH, hash)
            .putInt(KEY_PIN_ITERATIONS, PBKDF2_ITERATIONS)
            .apply()
    }

    /**
     * PINs set before v4.2 were stored as a single-round salted SHA-256; new ones use
     * PBKDF2 with [PBKDF2_ITERATIONS] rounds. A successful legacy verification silently
     * re-stores the PIN in the new format, so old vaults migrate on their next unlock.
     */
    fun verifyPin(context: Context, pin: String): Boolean {
        val salt = prefs(context).getString(KEY_PIN_SALT, null) ?: return false
        val expectedHash = prefs(context).getString(KEY_PIN_HASH, null) ?: return false
        val iterations = prefs(context).getInt(KEY_PIN_ITERATIONS, 0)
        return if (iterations > 0) {
            pbkdf2Hash(pin, salt) == expectedHash
        } else {
            val matched = legacySha256Hash(pin, salt) == expectedHash
            if (matched) setPin(context, pin)
            matched
        }
    }

    // --- Brute-force lockout -------------------------------------------------------------

    fun lockoutRemainingMs(context: Context): Long =
        (prefs(context).getLong(KEY_LOCKOUT_UNTIL, 0L) - System.currentTimeMillis()).coerceAtLeast(0L)

    fun recordFailedAttempt(context: Context) {
        val failed = prefs(context).getInt(KEY_FAILED_ATTEMPTS, 0) + 1
        if (failed >= MAX_ATTEMPTS_BEFORE_LOCKOUT) {
            prefs(context).edit()
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .putLong(KEY_LOCKOUT_UNTIL, System.currentTimeMillis() + LOCKOUT_MILLIS)
                .apply()
        } else {
            prefs(context).edit().putInt(KEY_FAILED_ATTEMPTS, failed).apply()
        }
    }

    fun clearFailedAttempts(context: Context) {
        prefs(context).edit()
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL, 0L)
            .apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun pbkdf2Hash(pin: String, saltHex: String): String {
        val spec = PBEKeySpec(pin.toCharArray(), hexToBytes(saltHex), PBKDF2_ITERATIONS, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded.joinToString(separator = "") { "%02x".format(it) }
    }

    private fun legacySha256Hash(pin: String, saltHex: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(hexToBytes(saltHex))
        return digest.digest(pin.toByteArray(Charsets.UTF_8)).joinToString(separator = "") { "%02x".format(it) }
    }

    private fun randomSaltHex(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString(separator = "") { "%02x".format(it) }
    }

    private fun hexToBytes(hex: String): ByteArray =
        ByteArray(hex.length / 2) { i -> ((Character.digit(hex[i * 2], 16) shl 4) + Character.digit(hex[i * 2 + 1], 16)).toByte() }

    // --- Vault contents ------------------------------------------------------------------

    fun moveIn(context: Context, file: File): Boolean {
        if (!file.exists()) return false
        return try {
            val id = UUID.randomUUID().toString()
            val originalPath = file.absolutePath
            val isDirectory = file.isDirectory
            val sizeBytes = if (isDirectory) FileTreeWalker.recursiveSize(file) else file.length()
            val dest = File(vaultDir(context), "$id-${file.name}")
            val moved = file.renameTo(dest) || run {
                file.copyRecursively(dest, overwrite = false)
                file.deleteRecursively()
            }
            if (moved) {
                appendManifestEntry(context, VaultEntry(id, originalPath, System.currentTimeMillis(), isDirectory, sizeBytes))
                FileTagsStore.onPathsRemoved(context, listOf(originalPath))
                FileIndex.invalidate()
            }
            moved
        } catch (e: Exception) {
            false
        }
    }

    fun listEntries(context: Context): List<VaultEntry> =
        readManifest(context).filter { it.vaultedFile(context).exists() }

    fun restore(context: Context, entry: VaultEntry): Boolean {
        val source = entry.vaultedFile(context)
        if (!source.exists()) {
            removeManifestEntry(context, entry.id)
            return false
        }
        val originalFile = File(entry.originalPath)
        val destParent = originalFile.parentFile ?: return false
        return try {
            destParent.mkdirs()
            val dest = if (originalFile.exists()) uniqueName(destParent, originalFile.name) else originalFile
            val restored = source.renameTo(dest) || run {
                source.copyRecursively(dest, overwrite = false)
                source.deleteRecursively()
            }
            if (restored) {
                removeManifestEntry(context, entry.id)
                FileIndex.invalidate()
            }
            restored
        } catch (e: Exception) {
            false
        }
    }

    fun deleteForever(context: Context, entry: VaultEntry): Boolean {
        val file = entry.vaultedFile(context)
        val deleted = !file.exists() || file.deleteRecursively()
        if (deleted) removeManifestEntry(context, entry.id)
        return deleted
    }

    private fun uniqueName(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var index = 1
        do {
            candidate = File(dir, "$base ($index)$ext")
            index++
        } while (candidate.exists())
        return candidate
    }

    private fun readManifest(context: Context): List<VaultEntry> {
        val file = manifestFile(context)
        if (!file.exists()) return emptyList()
        val lines = file.readLines()
        val entries = mutableListOf<VaultEntry>()
        var i = 0
        while (i + 4 < lines.size) {
            entries.add(
                VaultEntry(
                    id = lines[i],
                    originalPath = lines[i + 1],
                    addedAtMillis = lines[i + 2].toLongOrNull() ?: 0L,
                    isDirectory = lines[i + 3].toBoolean(),
                    sizeBytes = lines[i + 4].toLongOrNull() ?: 0L,
                ),
            )
            i += 5
        }
        return entries
    }

    private fun writeManifest(context: Context, entries: List<VaultEntry>) {
        val sb = StringBuilder()
        entries.forEach { entry ->
            sb.append(entry.id).append('\n')
            sb.append(entry.originalPath).append('\n')
            sb.append(entry.addedAtMillis).append('\n')
            sb.append(entry.isDirectory).append('\n')
            sb.append(entry.sizeBytes).append('\n')
        }
        manifestFile(context).writeText(sb.toString())
    }

    @Synchronized
    private fun appendManifestEntry(context: Context, entry: VaultEntry) {
        writeManifest(context, readManifest(context) + entry)
    }

    @Synchronized
    private fun removeManifestEntry(context: Context, id: String) {
        writeManifest(context, readManifest(context).filterNot { it.id == id })
    }
}
