package com.example.data.engine

import com.example.data.local.AppDatabase
import com.example.data.local.entity.GatewayQueueEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class GatewaySyncEngine(
    private val db: AppDatabase,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    private val queueDao = db.gatewayQueueDao()
    private var retryJob: Job? = null

    val pendingQueue: Flow<List<GatewayQueueEntity>> = queueDao.getPendingQueueItems()
    val allQueue: Flow<List<GatewayQueueEntity>> = queueDao.getAllQueueItems()
    val pendingCount: Flow<Int> = queueDao.getPendingCount()

    init {
        startPeriodicRetryLoop()
    }

    suspend fun enqueueItem(
        type: String,
        payload: String,
        targetEndpoint: String = ""
    ): GatewayQueueEntity {
        val item = GatewayQueueEntity(
            type = type,
            payload = payload,
            targetEndpoint = targetEndpoint,
            status = "PENDING",
            nextAttemptAt = System.currentTimeMillis()
        )
        queueDao.insertItem(item)
        // Attempt immediate dispatch asynchronously
        coroutineScope.launch {
            processQueueItem(item)
        }
        return item
    }

    fun startPeriodicRetryLoop() {
        if (retryJob?.isActive == true) return
        retryJob = coroutineScope.launch {
            while (isActive) {
                try {
                    retryPendingItems()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(15_000L) // Check every 15 seconds
            }
        }
    }

    suspend fun retryPendingItems() = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val pending = queueDao.getPendingQueueItemsSync()
        for (item in pending) {
            if (now >= item.nextAttemptAt) {
                processQueueItem(item)
            }
        }
    }

    suspend fun retryNow(itemId: String) = withContext(Dispatchers.IO) {
        val all = queueDao.getPendingQueueItemsSync()
        val item = all.find { it.id == itemId }
        if (item != null) {
            processQueueItem(item.copy(retryCount = 0, nextAttemptAt = 0L))
        }
    }

    suspend fun retryAllNow() = withContext(Dispatchers.IO) {
        val pending = queueDao.getPendingQueueItemsSync()
        for (item in pending) {
            processQueueItem(item.copy(retryCount = 0, nextAttemptAt = 0L))
        }
    }

    suspend fun clearCompleted() = withContext(Dispatchers.IO) {
        queueDao.clearCompleted()
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        queueDao.clearAll()
    }

    private suspend fun processQueueItem(item: GatewayQueueEntity) {
        if (item.targetEndpoint.isBlank()) {
            // Internal offline queue event (e.g. processed locally and acknowledged)
            queueDao.updateItem(item.copy(status = "COMPLETED", lastAttemptAt = System.currentTimeMillis()))
            return
        }

        try {
            val success = sendHttpRequest(item.targetEndpoint, item.payload)
            if (success) {
                queueDao.updateItem(
                    item.copy(
                        status = "COMPLETED",
                        lastAttemptAt = System.currentTimeMillis(),
                        errorMessage = ""
                    )
                )
            } else {
                handleFailure(item, "فشل الاتصال بنقطة النهاية: رمز خطأ غير متوقع")
            }
        } catch (e: Exception) {
            handleFailure(item, e.localizedMessage ?: "فشل الاتصال بالشبكة المحلية")
        }
    }

    private suspend fun handleFailure(item: GatewayQueueEntity, error: String) {
        val newRetry = item.retryCount + 1
        val isFinal = newRetry >= item.maxRetries
        val backoffSeconds = when (newRetry) {
            1 -> 5L
            2 -> 15L
            3 -> 30L
            4 -> 60L
            else -> 120L
        }
        val nextTime = System.currentTimeMillis() + (backoffSeconds * 1000L)

        queueDao.updateItem(
            item.copy(
                status = if (isFinal) "FAILED" else "RETRYING",
                retryCount = newRetry,
                lastAttemptAt = System.currentTimeMillis(),
                nextAttemptAt = nextTime,
                errorMessage = error
            )
        )
    }

    private fun sendHttpRequest(targetUrl: String, body: String): Boolean {
        val url = URL(targetUrl)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 4000
            readTimeout = 4000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        }
        OutputStreamWriter(conn.outputStream, "UTF-8").use {
            it.write(body)
            it.flush()
        }
        val code = conn.responseCode
        conn.disconnect()
        return code in 200..299
    }
}
