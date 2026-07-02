package com.vfxsal.filemanager.util

import android.os.Environment
import android.os.StatFs

data class StorageStats(
    val totalBytes: Long,
    val freeBytes: Long,
) {
    val usedBytes: Long get() = totalBytes - freeBytes
    val usedFraction: Float get() = if (totalBytes == 0L) 0f else usedBytes.toFloat() / totalBytes.toFloat()
}

object StorageStatsUtils {
    fun primaryStorageStats(): StorageStats {
        val path = Environment.getExternalStorageDirectory()
        val statFs = StatFs(path.absolutePath)
        val total = statFs.blockCountLong * statFs.blockSizeLong
        val free = statFs.availableBlocksLong * statFs.blockSizeLong
        return StorageStats(totalBytes = total, freeBytes = free)
    }
}
