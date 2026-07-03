package com.vfxsal.filemanager.feature.files.util

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Plain java.util.zip based compress/extract - no third-party dependency needed (and none
 * reachable in this build environment anyway, since Google's Maven repo is unavailable here).
 * Only handles the standard .zip format; .rar/.7z etc. would need an external library.
 */
object ZipOps {

    fun zip(sources: List<File>, destZip: File): Boolean = try {
        ZipOutputStream(FileOutputStream(destZip).buffered()).use { zos ->
            sources.forEach { source -> addToZip(zos, source, source.name) }
        }
        true
    } catch (e: Exception) {
        destZip.delete()
        false
    }

    private fun addToZip(zos: ZipOutputStream, file: File, entryName: String) {
        if (file.isDirectory) {
            val children = file.listFiles().orEmpty()
            if (children.isEmpty()) {
                zos.putNextEntry(ZipEntry("$entryName/"))
                zos.closeEntry()
            } else {
                children.forEach { child -> addToZip(zos, child, "$entryName/${child.name}") }
            }
        } else {
            zos.putNextEntry(ZipEntry(entryName))
            FileInputStream(file).use { it.copyTo(zos) }
            zos.closeEntry()
        }
    }

    /** Extracts [zipFile] into [destDir], rejecting entries that would escape it (zip-slip). */
    fun unzip(zipFile: File, destDir: File): Boolean = try {
        destDir.mkdirs()
        val destCanonicalPath = destDir.canonicalPath
        ZipInputStream(FileInputStream(zipFile).buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val outFile = File(destDir, entry.name)
                val outCanonicalPath = outFile.canonicalPath
                if (outCanonicalPath != destCanonicalPath && !outCanonicalPath.startsWith(destCanonicalPath + File.separator)) {
                    throw SecurityException("Zip entry outside target directory: ${entry.name}")
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { output -> zis.copyTo(output) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        true
    } catch (e: Exception) {
        false
    }
}
