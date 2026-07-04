package com.vfxsal.filemanager.feature.files.vault

import android.content.Context
import com.vfxsal.filemanager.feature.clean.scan.FileTreeWalker
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

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
        val hash = hashPin(pin, salt)
        prefs(context).edit()
            .putString(KEY_PIN_SALT, salt)
            .putString(KEY_PIN_HASH, hash)
            .apply()
    }

    fun verifyPin(context: Context, pin: String): Boolean {
        val salt = prefs(context).getString(KEY_PIN_SALT, null) ?: return false
        val expectedHash = prefs(context).getString(KEY_PIN_HASH, null) ?: return false
        return hashPin(pin, salt) == expectedHash
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun hashPin(pin: String, saltHex: String): String {
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
            if (restored) removeManifestEntry(context, entry.id)
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
