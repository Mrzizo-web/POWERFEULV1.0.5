package com.example.data.engine

import com.example.data.local.AppDatabase
import com.example.data.local.entity.GatewayQueueEntity
import com.example.data.network.GatewayLocalNetworkHostPolicy
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class GatewaySyncEngine(
    private val db: AppDatabase,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    private val autoStartRetry: Boolean = true
) {
    private val queueDao = db.gatewayQueueDao()
    private var startupJob: Job? = null
    private var retryJob: Job? = null

    val pendingQueue: Flow<List<GatewayQueueEntity>> = queueDao.getPendingQueueItems()
    val allQueue: Flow<List<GatewayQueueEntity>> = queueDao.getAllQueueItems()
    val pendingCount: Flow<Int> = queueDao.getPendingCount()

    init {
        if (autoStartRetry) {
            startupJob = coroutineScope.launch {
                queueDao.recoverInProgressItems()
                if (isActive) startPeriodicRetryLoop()
            }
        }
    }

    fun stopPeriodicRetryLoop() {
        startupJob?.cancel()
        startupJob = null
        retryJob?.cancel()
        retryJob = null
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
        coroutineScope.launch { processQueueItem(item) }
        return item
    }

    fun startPeriodicRetryLoop() {
        if (retryJob?.isActive == true) return
        retryJob = coroutineScope.launch {
            while (isActive) {
                try {
                    retryPendingItems()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(15_000L)
            }
        }
    }

    suspend fun retryPendingItems() = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val pending = queueDao.getPendingQueueItemsSync()
        for (item in pending) {
            if (now >= item.nextAttemptAt) processQueueItem(item)
        }
    }

    suspend fun retryNow(itemId: String) = withContext(Dispatchers.IO) {
        // The status transition is an atomic SQL update. Never write an old row
        // snapshot back over a worker that may already have claimed the item.
        if (queueDao.prepareManualRetry(itemId) != 1) return@withContext
        val prepared = queueDao.getQueueItemById(itemId) ?: return@withContext
        processQueueItem(prepared)
    }

    suspend fun retryAllNow() = withContext(Dispatchers.IO) {
        // Snapshot IDs only; atomically claim each row immediately before retry.
        val retryableIds = queueDao.getManuallyRetryableItemsSync().map { it.id }
        for (id in retryableIds) {
            if (queueDao.prepareManualRetry(id) != 1) continue
            val prepared = queueDao.getQueueItemById(id) ?: continue
            processQueueItem(prepared)
        }
    }

    suspend fun clearCompleted() = withContext(Dispatchers.IO) {
        queueDao.clearCompleted()
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        queueDao.clearAll()
    }

    private suspend fun processQueueItem(item: GatewayQueueEntity) {
        val startedAt = System.currentTimeMillis()
        if (queueDao.claimItem(item.id, startedAt) != 1) return

        // A worker may have loaded this row before a manual retry reset it. Once the
        // atomic claim succeeds, reload the current row so stale retryCount/payload
        // values cannot overwrite the manual retry state.
        val freshClaimedRow = queueDao.getQueueItemById(item.id) ?: return
        val claimedItem = freshClaimedRow.copy(status = "IN_PROGRESS", lastAttemptAt = startedAt)

        if (claimedItem.targetEndpoint.isBlank()) {
            // A missing destination is not a successful delivery. Keep the record
            // in the retry/error queue so the UI cannot report silent success.
            handleFailure(claimedItem, "Endpoint URL is not configured; item was not transmitted")
            return
        }

        try {
            val success = sendHttpRequest(claimedItem.targetEndpoint, claimedItem.payload)
            if (success) {
                queueDao.updateItem(
                    claimedItem.copy(
                        status = "COMPLETED",
                        lastAttemptAt = System.currentTimeMillis(),
                        errorMessage = ""
                    )
                )
            } else {
                handleFailure(claimedItem, "فشل الاتصال بنقطة النهاية: رمز خطأ غير متوقع")
            }
        } catch (cancelled: CancellationException) {
            // Return a claimed item to RETRYING before propagating cancellation.
            queueDao.updateItem(
                claimedItem.copy(status = "RETRYING", nextAttemptAt = System.currentTimeMillis())
            )
            throw cancelled
        } catch (e: Exception) {
            handleFailure(claimedItem, e.localizedMessage ?: "فشل الاتصال بالشبكة المحلية")
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
        val now = System.currentTimeMillis()
        queueDao.updateItem(
            item.copy(
                status = if (isFinal) "FAILED" else "RETRYING",
                retryCount = newRetry,
                lastAttemptAt = now,
                nextAttemptAt = now + (backoffSeconds * 1000L),
                errorMessage = error
            )
        )
    }

    private fun isLocalNetworkHost(host: String): Boolean =
        GatewayLocalNetworkHostPolicy.isAllowedCleartextHost(host)

    private fun sendHttpRequest(targetUrl: String, body: String): Boolean {
        val url = URL(targetUrl)
        require(url.protocol.equals("https", ignoreCase = true) ||
            url.protocol.equals("http", ignoreCase = true)
        ) { "Unsupported Gateway endpoint protocol" }
        // The Gateway is strictly local-first: HTTPS must not be used to exfiltrate
        // queue payloads to an arbitrary public endpoint either.
        require(isLocalNetworkHost(url.host)) {
            "Gateway requests are restricted to a local/private network host"
        }
        val conn = (url.openConnection() as HttpURLConnection).apply {
            // Never forward SMS/payment payloads to a redirect target.
            instanceFollowRedirects = false
            requestMethod = "POST"
            connectTimeout = 4000
            readTimeout = 4000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        }
        try {
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }
            val code = conn.responseCode
            return code in 200..299
        } finally {
            conn.disconnect()
        }
    }
}
