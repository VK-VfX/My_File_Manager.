package com.vfxsal.filemanager.feature.files.util

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile

/**
 * Unified compress/extract for the archive formats this app handles itself:
 * standard ZIP (via [ZipOps]/java.util.zip) and 7z (via Apache Commons Compress).
 *
 * ZIP work is delegated to [ZipOps] so its zip-slip protection and streaming stay the
 * single source of truth; 7z is implemented here on top of Commons Compress with the same
 * "no entry may escape the target directory" guard.
 */
object ArchiveOps {

    enum class Format(val extension: String, val label: String) {
        ZIP("zip", "ZIP"),
        SEVEN_Z("7z", "7z"),
    }

    private const val BUFFER_SIZE = 64 * 1024

    /** Extensions we can extract in-app. */
    private val EXTRACTABLE = setOf("zip", "7z", "jar", "apks", "apk")

    fun isExtractable(file: File): Boolean =
        file.isFile && file.extension.lowercase() in EXTRACTABLE

    /**
     * Compresses [sources] into a new archive named "[baseName].[ext]" inside [destDir], in the
     * requested [format]. Returns the created archive file, or null on failure.
     */
    fun compress(sources: List<File>, destDir: File, baseName: String, format: Format): File? {
        if (sources.isEmpty()) return null
        val safeBase = baseName.ifBlank { "archive" }
        val dest = FileOps.uniqueDestination(destDir, "$safeBase.${format.extension}")
        val ok = when (format) {
            Format.ZIP -> ZipOps.zip(sources, dest)
            Format.SEVEN_Z -> compress7z(sources, dest)
        }
        return if (ok) dest else { dest.delete(); null }
    }

    /**
     * Extracts [archive] into a fresh sibling folder named after the archive (de-duplicated),
     * returning that folder, or null on failure / unsupported format.
     */
    fun extract(archive: File): File? {
        if (!isExtractable(archive)) return null
        val destDir = FileOps.uniqueDestination(archive.parentFile ?: archive, archive.nameWithoutExtension)
        val ok = when (archive.extension.lowercase()) {
            "7z" -> extract7z(archive, destDir)
            else -> ZipOps.unzip(archive, destDir) // zip/jar/apk share the zip container
        }
        return if (ok) destDir else { destDir.deleteRecursively(); null }
    }

    // --- 7z ------------------------------------------------------------------------------------

    private fun compress7z(sources: List<File>, dest: File): Boolean = try {
        SevenZOutputFile(dest).use { out ->
            sources.forEach { addTo7z(out, it, it.name) }
        }
        true
    } catch (e: Exception) {
        dest.delete()
        false
    }

    private fun addTo7z(out: SevenZOutputFile, file: File, entryName: String) {
        if (file.isDirectory) {
            val children = file.listFiles().orEmpty()
            if (children.isEmpty()) {
                val entry = out.createArchiveEntry(file, "$entryName/")
                out.putArchiveEntry(entry)
                out.closeArchiveEntry()
            } else {
                children.forEach { child -> addTo7z(out, child, "$entryName/${child.name}") }
            }
        } else {
            val entry = out.createArchiveEntry(file, entryName)
            out.putArchiveEntry(entry)
            FileInputStream(file).use { input ->
                val buffer = ByteArray(BUFFER_SIZE)
                var read = input.read(buffer)
                while (read >= 0) {
                    out.write(buffer, 0, read)
                    read = input.read(buffer)
                }
            }
            out.closeArchiveEntry()
        }
    }

    private fun extract7z(archive: File, destDir: File): Boolean = try {
        destDir.mkdirs()
        val destCanonical = destDir.canonicalPath
        @Suppress("DEPRECATION")
        SevenZFile(archive).use { sevenZ ->
            var entry = sevenZ.nextEntry
            while (entry != null) {
                val outFile = File(destDir, entry.name)
                val outCanonical = outFile.canonicalPath
                if (outCanonical != destCanonical && !outCanonical.startsWith(destCanonical + File.separator)) {
                    throw SecurityException("7z entry outside target directory: ${entry.name}")
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var read = sevenZ.read(buffer)
                        while (read >= 0) {
                            output.write(buffer, 0, read)
                            read = sevenZ.read(buffer)
                        }
                    }
                }
                entry = sevenZ.nextEntry
            }
        }
        true
    } catch (e: Exception) {
        false
    }
}
