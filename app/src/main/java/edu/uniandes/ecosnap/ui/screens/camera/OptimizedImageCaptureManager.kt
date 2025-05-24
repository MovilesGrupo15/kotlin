package edu.uniandes.ecosnap.ui.screens.camera

import android.util.Log
import androidx.camera.core.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import okhttp3.*
import okio.ByteString.Companion.toByteString
import edu.uniandes.ecosnap.domain.model.DetectionResult
import edu.uniandes.ecosnap.data.pub.Publisher
import edu.uniandes.ecosnap.data.pub.Subscriber
import edu.uniandes.ecosnap.data.pub.SubscriptionToken
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicLong

/**
 * Optimized ImageCaptureManager with memory management and rate limiting
 */
class OptimizedImageCaptureManager(
    private val imageCapture: ImageCapture,
    private val executor: Executor,
    private val wsMgr: OptimizedWebSocketManager
) {
    // Memory management
    private val maxPendingCaptures = 3
    private val pendingCaptures = AtomicLong(0)
    private val captureMutex = Mutex()

    // Rate limiting
    private var lastCaptureTime = 0L
    private val minCaptureInterval = 500L // 500ms between captures

    // Memory pool for ByteArrays to reduce allocations
    private val byteArrayPool = ConcurrentLinkedQueue<ByteArray>()
    private val pooledArraySize = 1024 * 1024 // 1MB typical image size
    private val maxPoolSize = 5

    fun captureAndSend() {
        val currentTime = System.currentTimeMillis()

        // Rate limiting check
        if (currentTime - lastCaptureTime < minCaptureInterval) {
            return
        }

        // Memory pressure check
        if (pendingCaptures.get() >= maxPendingCaptures) {
            Log.w("OptimizedCapture", "Skipping capture - too many pending captures")
            return
        }

        lastCaptureTime = currentTime
        pendingCaptures.incrementAndGet()

        imageCapture.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(img: ImageProxy) {
                try {
                    processImageOptimized(img)
                } finally {
                    // CRITICAL: Always close ImageProxy in finally block
                    img.close()
                    pendingCaptures.decrementAndGet()
                }
            }

            override fun onError(exc: ImageCaptureException) {
                Log.e("OptimizedCapture", "Capture failed", exc)
                pendingCaptures.decrementAndGet()
            }
        })
    }

    private fun processImageOptimized(img: ImageProxy) {
        try {
            val buffer = img.planes[0].buffer
            val size = buffer.remaining()

            // Use pooled ByteArray if available, otherwise create new one
            val bytes = getPooledByteArray(size) ?: ByteArray(size)
            buffer.get(bytes, 0, size)

            // Send to WebSocket
            wsMgr.sendImage(bytes)

            // Return ByteArray to pool if it's the right size
            returnToPool(bytes)

        } catch (e: Exception) {
            Log.e("OptimizedCapture", "Error processing image", e)
        }
    }

    private fun getPooledByteArray(requiredSize: Int): ByteArray? {
        if (requiredSize > pooledArraySize * 1.5) return null // Don't use pool for very large images

        val pooled = byteArrayPool.poll()
        return if (pooled != null && pooled.size >= requiredSize) {
            pooled
        } else {
            null
        }
    }

    private fun returnToPool(bytes: ByteArray) {
        if (bytes.size == pooledArraySize && byteArrayPool.size < maxPoolSize) {
            byteArrayPool.offer(bytes)
        }
        // If array is wrong size or pool is full, let GC handle it
    }

    fun cleanup() {
        byteArrayPool.clear()
        Log.d("OptimizedCapture", "Cleanup completed")
    }
}

/**
 * Enhanced WebSocketManager with memory-conscious operations
 */
class OptimizedWebSocketManager(private val url: String) {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(10, TimeUnit.SECONDS) // Add write timeout
        .build()

    private val reconnectScheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "WebSocket-Reconnect").apply { isDaemon = true }
    }

    private var webSocket: WebSocket? = null
    private var retryCount = 0

    // Rate limiting for sends
    private var lastSendTime = 0L
    private val minSendInterval = 100L // 100ms between sends

    val detectionPublisher = SimplePublisher<List<DetectionResult>>()
    val connectionPublisher = SimplePublisher<ConnectionState>()

    fun connect() {
        connectionPublisher.publishNext(ConnectionState.CONNECTING)
        val req = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, resp: Response) {
                retryCount = 0
                connectionPublisher.publishNext(ConnectionState.CONNECTED)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                try {
                    val list = Json.decodeFromString<List<DetectionResult>>(text)
                    detectionPublisher.publish(list)
                } catch (e: Exception) {
                    Log.e("OptimizedWebSocket", "JSON parsing error", e)
                    detectionPublisher.publishError(e)
                }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, resp: Response?) {
                Log.w("OptimizedWebSocket", "Connection failed", t)
                connectionPublisher.publishNext(ConnectionState.FAILED)
                scheduleReconnect()
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                connectionPublisher.publishNext(ConnectionState.DISCONNECTED)
            }
        })
    }

    private fun scheduleReconnect() {
        if (retryCount >= 5) return
        val delay = (1 shl retryCount).coerceAtMost(30).toLong()
        retryCount++
        reconnectScheduler.schedule({ connect() }, delay, TimeUnit.SECONDS)
    }

    fun sendImage(bytes: ByteArray) {
        val currentTime = System.currentTimeMillis()

        // Rate limiting
        if (currentTime - lastSendTime < minSendInterval) {
            return
        }

        if (detectionPublisher.hasSubscribers()) {
            webSocket?.send(bytes.toByteString())
            lastSendTime = currentTime
        }
    }

    fun disconnect() {
        webSocket?.close(1000, "bye")
        if (!reconnectScheduler.isShutdown) {
            reconnectScheduler.shutdown()
            try {
                if (!reconnectScheduler.awaitTermination(1, TimeUnit.SECONDS)) {
                    reconnectScheduler.shutdownNow()
                }
            } catch (e: InterruptedException) {
                reconnectScheduler.shutdownNow()
            }
        }
    }
}