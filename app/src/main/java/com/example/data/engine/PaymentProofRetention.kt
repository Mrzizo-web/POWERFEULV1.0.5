package com.example.data.engine

import java.io.File

/**
 * Deletes only expired image files from the dedicated proof directory.
 * Sale rows and all other financial records are deliberately untouched.
 */
object PaymentProofRetention {
    const val RETENTION_MILLIS: Long = 40L * 24L * 60L * 60L * 1000L

    fun cleanupExpired(directory: File, nowMillis: Long = System.currentTimeMillis()): Int {
        if (!directory.isDirectory) return 0
        val cutoff = nowMillis - RETENTION_MILLIS
        val expiredImages = directory.listFiles()
            ?.filter { file ->
                file.isFile &&
                    file.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp") &&
                    file.lastModified() < cutoff
            }
            .orEmpty()
        return expiredImages.count { file -> runCatching { file.delete() }.getOrDefault(false) }
    }
}
