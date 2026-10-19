package com.example.data.engine

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentProofRetentionTest {
    @Test
    fun deletesOnlyExpiredImagesAndKeepsRecentImagesAndOtherFiles() {
        val directory = createTempDir(prefix = "payment-proofs-test-")
        try {
            val now = 1_800_000_000_000L
            val expiredImage = File(directory, "expired.jpg").apply {
                writeBytes(byteArrayOf(1, 2, 3))
                setLastModified(now - PaymentProofRetention.RETENTION_MILLIS - 1L)
            }
            val recentImage = File(directory, "recent.jpg").apply {
                writeBytes(byteArrayOf(4, 5, 6))
                setLastModified(now - PaymentProofRetention.RETENTION_MILLIS + 1L)
            }
            val expiredText = File(directory, "notes.txt").apply {
                writeText("not a payment-proof image")
                setLastModified(now - PaymentProofRetention.RETENTION_MILLIS - 1L)
            }
            val childDirectory = File(directory, "old-folder").apply {
                mkdir()
                setLastModified(now - PaymentProofRetention.RETENTION_MILLIS - 1L)
            }

            assertEquals(1, PaymentProofRetention.cleanupExpired(directory, now))
            assertTrue(!expiredImage.exists())
            assertTrue(recentImage.exists())
            assertTrue(expiredText.exists())
            assertTrue(childDirectory.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun missingProofDirectoryIsHarmless() {
        val root = createTempDir(prefix = "missing-proofs-")
        try {
            assertEquals(0, PaymentProofRetention.cleanupExpired(File(root, "not-created"), 1_800_000_000_000L))
        } finally {
            root.deleteRecursively()
        }
    }
}
