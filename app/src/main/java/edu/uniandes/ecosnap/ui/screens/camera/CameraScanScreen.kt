package edu.uniandes.ecosnap.ui.screens.camera

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.Canvas
import androidx.compose.ui.text.drawText
import androidx.core.content.ContextCompat
import edu.uniandes.ecosnap.Analytics
import edu.uniandes.ecosnap.BuildConfig
import edu.uniandes.ecosnap.data.pub.Publisher
import edu.uniandes.ecosnap.data.pub.Subscriber
import edu.uniandes.ecosnap.data.pub.SubscriptionToken
import edu.uniandes.ecosnap.domain.model.DetectionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.*
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicLong

enum class ConnectionState { CONNECTING, CONNECTED, FAILED, DISCONNECTED }

class SimplePublisher<T> : Publisher<T> {
    private val subs = ConcurrentHashMap<SubscriptionToken, Subscriber<T>>()
    private val idGen = AtomicLong()
    override fun subscribe(s: Subscriber<T>) = SubscriptionToken(idGen.incrementAndGet()).also { subs[it] = s }
    override fun unsubscribe(t: SubscriptionToken) { subs.remove(t) }
    fun publishNext(v: T) { subs.values.forEach { it.onNext(v) } }
    fun publishError(e: Throwable) { subs.values.forEach { it.onError(e) } }
    fun hasSubscribers() = subs.isNotEmpty()
    override fun publish(data: T) = publishNext(data)
}

class WebSocketManager(private val url: String) {
    private val client = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()
    private val reconnectScheduler = Executors.newSingleThreadScheduledExecutor()
    private var webSocket: WebSocket? = null
    private var retryCount = 0
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
                    detectionPublisher.publishError(e)
                }
            }
            override fun onFailure(ws: WebSocket, t: Throwable, resp: Response?) {
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
        if (detectionPublisher.hasSubscribers()) webSocket?.send(bytes.toByteString())
    }

    fun disconnect() {
        webSocket?.close(1000, "bye")
        reconnectScheduler.shutdown()
    }
}

class ImageCaptureManager(
    private val imageCapture: ImageCapture,
    private val executor: Executor,
    private val wsMgr: WebSocketManager
) {
    fun captureAndSend() {
        imageCapture.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(img: ImageProxy) {
                val buf = img.planes[0].buffer
                val bytes = ByteArray(buf.remaining()).apply { buf.get(this) }
                wsMgr.sendImage(bytes)
                img.close()
            }
            override fun onError(exc: ImageCaptureException) {
                Log.e("CameraScan", "capture failed", exc)
            }
        })
    }
}

@Composable
fun CameraScanScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val textMeasurer = rememberTextMeasurer()
    val hasCamPerm = remember { mutableStateOf(false) }
    val detections = remember { mutableStateOf<List<DetectionResult>>(emptyList()) }
    val connectionState = remember { mutableStateOf(ConnectionState.DISCONNECTED) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val wsMgr = remember { WebSocketManager("wss://${BuildConfig.SERVER_URL}/detect") }
    val imageCapture = remember { mutableStateOf<ImageCapture?>(null) }
    val detectToken = remember { mutableStateOf<SubscriptionToken?>(null) }
    val connToken = remember { mutableStateOf<SubscriptionToken?>(null) }
    val captureScheduler = remember { Executors.newScheduledThreadPool(1) }
    val captureFuture = remember { mutableStateOf<ScheduledFuture<*>?>(null) }
    val showFeedback = remember { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { hasCamPerm.value = it }

    LaunchedEffect(Unit) {
        hasCamPerm.value = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasCamPerm.value) permLauncher.launch(Manifest.permission.CAMERA)
        Analytics.screenEvent("camera_scan")
        Analytics.actionEvent("camera_scan_start")
        wsMgr.connect()
        detectToken.value = wsMgr.detectionPublisher.subscribe(object : Subscriber<List<DetectionResult>> {
            override fun onNext(data: List<DetectionResult>) {
                Analytics.firebaseAnalytics.logEvent("camera_scan_success", Bundle().apply {
                    putString("count", data.size.toString())
                    data.groupBy { it.type }.forEach { (k,v) -> putString(k, v.size.toString()) }
                })
                detections.value = data
            }
            override fun onError(e: Throwable) {
                Analytics.actionEvent("camera_scan_error")
                Log.e("CameraScan", "error", e)
            }
        })
        connToken.value = wsMgr.connectionPublisher.subscribe(object : Subscriber<ConnectionState> {
            override fun onNext(v: ConnectionState) { connectionState.value = v }
            override fun onError(e: Throwable) {}
        })
    }

    DisposableEffect(Unit) {
        onDispose {
            detectToken.value?.let { wsMgr.detectionPublisher.unsubscribe(it) }
            connToken.value?.let { wsMgr.connectionPublisher.unsubscribe(it) }
            wsMgr.disconnect()
            cameraExecutor.shutdown()
            captureScheduler.shutdown()
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar()
        TitleBar(onNavigateBack)
        val color = when (connectionState.value) {
            ConnectionState.CONNECTING -> Color.Yellow
            ConnectionState.FAILED, ConnectionState.DISCONNECTED -> Color.Red
            else -> Color.Gray
        }
        Box(Modifier.fillMaxWidth().background(color).padding(4.dp),
            contentAlignment = Alignment.Center) {
            Text(connectionState.value.name, color = Color.Black)
        }
        Box(Modifier.fillMaxSize().weight(1f)) {
            if (hasCamPerm.value) {
                CameraPreview { ic ->
                    imageCapture.value = ic
                }
                LaunchedEffect(imageCapture.value, connectionState.value) {
                    captureFuture.value?.cancel(false)
                    captureFuture.value = null
                    val ic = imageCapture.value
                    if (ic != null && connectionState.value == ConnectionState.CONNECTED) {
                        val mgr = ImageCaptureManager(ic, cameraExecutor, wsMgr)
                        captureFuture.value = captureScheduler.scheduleWithFixedDelay({
                            mgr.captureAndSend()
                        }, 0, 500, TimeUnit.MILLISECONDS)
                    }
                }
                Canvas(Modifier.fillMaxSize()) {
                    val w = size.width; val h = size.height
                    val colors = mapOf(
                        "plastic" to Color.Blue, "paper" to Color.Yellow,
                        "glass" to Color.Cyan, "metal" to Color.Red,
                        "organic" to Color.Green
                    )
                    detections.value.forEach { d ->
                        val x = d.bbox[0] * w; val y = d.bbox[1] * h
                        val ww = d.bbox[2] * w; val hh = d.bbox[3] * h
                        val col = colors[d.type] ?: Color.White
                        drawRect(col, topLeft = androidx.compose.ui.geometry.Offset(x, y),
                            size = androidx.compose.ui.geometry.Size(ww, hh),
                            style = Stroke(3f))
                        drawText(textMeasurer, "${d.type} ${(d.confidence*100).toInt()}%",
                            androidx.compose.ui.geometry.Offset(x, y - 15f),
                            style = TextStyle(col, 14.sp, FontWeight.Bold,
                                background = Color.Black.copy(alpha=0.7f)))
                    }
                }
            } else {
                Box(Modifier.fillMaxSize().background(Color.Black),
                    contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Camera permission required", color = Color.White)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { permLauncher.launch(Manifest.permission.CAMERA) },
                            colors = ButtonDefaults.buttonColors(Color(0xFF00C853))) {
                            Text("Grant Camera Permission")
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            Button(onClick = {
                wsMgr.detectionPublisher.unsubscribe(detectToken.value!!)
                wsMgr.disconnect()
                showFeedback.value = true
            }, Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(Color(0xFF00C853))) {
                Text("Ready", fontSize=18.sp, fontWeight=FontWeight.Bold)
            }
        }
    }

    if (showFeedback.value) {
        FeedbackDialog(
            onDismiss = { showFeedback.value = false },
            onSend = { r, msg ->
                Analytics.firebaseAnalytics.logEvent("detection_feedback", Bundle().apply {
                    putInt("rating", r)
                    if (msg.isNotBlank()) putString("message", msg)
                })
                showFeedback.value = false
                onNavigateBack()
            },
            onNotNow = {
                showFeedback.value = false
                onNavigateBack()
            }
        )
    }
}

@Composable
fun FeedbackDialog(
    onDismiss: () -> Unit,
    onSend: (Int, String) -> Unit,
    onNotNow: () -> Unit
) {
    var rating by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Detection Feedback") },
        text = {
            Column {
                Text("Please rate the detection accuracy (optional):")
                RatingBar(rating = rating, onRatingChanged = { rating = it })
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text("Optional message") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSend(rating, message) }, enabled = rating > 0) {
                Text("Send")
            }
        },
        dismissButton = {
            TextButton(onClick = onNotNow) { Text("Not Now") }
        },
        properties = DialogProperties(dismissOnClickOutside = false)
    )
}

@Composable
fun RatingBar(
    modifier: Modifier = Modifier,
    rating: Int,
    onRatingChanged: (Int) -> Unit,
    stars: Int = 5,
    starColor: Color = Color(0xFFFFC107)
) {
    Row(modifier.padding(vertical=8.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 1..stars) {
            IconButton(onClick = { onRatingChanged(i) }) {
                Icon(
                    imageVector = if (i <= rating) Icons.Filled.Star else Icons.Outlined.Star,
                    contentDescription = null,
                    tint = if (i <= rating) starColor else Color.Gray.copy(alpha=0.5f),
                    modifier = Modifier.size(36.dp)
                )
            }
            if (i<stars) Spacer(Modifier.width(4.dp))
        }
    }
}

@Composable
fun CameraPreview(onPreviewReady: (ImageCapture) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    AndroidView(factory = { ctx ->
        PreviewView(ctx).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
            ProcessCameraProvider.getInstance(ctx).also { fut ->
                fut.addListener({
                    val provider = fut.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(surfaceProvider)
                    }
                    val ic = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA, preview, ic)
                    onPreviewReady(ic)
                }, ContextCompat.getMainExecutor(ctx))
            }
        }
    }, modifier=Modifier.fillMaxSize())
}

@Composable
private fun TopBar() {
    Box(Modifier.fillMaxWidth().background(Color(0xFF00C853)).padding(16.dp)) {
        IconButton(onClick = {}, Modifier.align(Alignment.CenterStart)) {
            Icon(Icons.Filled.Menu, contentDescription = null, tint = Color.Black)
        }
    }
}

@Composable
private fun TitleBar(onNavigateBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(Color.White).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onNavigateBack) {
            Icon(Icons.Filled.ArrowBack, contentDescription = null, tint = Color.Black)
        }
        Text("Camera Scan", fontSize=28.sp, fontWeight=FontWeight.Bold, color=Color.Black)
    }
}
