package edu.uniandes.ecosnap.ui.screens.camera

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import edu.uniandes.ecosnap.BuildConfig
import edu.uniandes.ecosnap.data.pub.Publisher
import edu.uniandes.ecosnap.data.pub.Subscriber
import edu.uniandes.ecosnap.data.pub.SubscriptionToken
import edu.uniandes.ecosnap.domain.model.DetectionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong


class DetectionPublisher : Publisher<List<DetectionResult>> {
    private val subscribers = ConcurrentHashMap<SubscriptionToken, Subscriber<List<DetectionResult>>>()
    private val idGenerator = AtomicLong(0)

    override fun subscribe(subscriber: Subscriber<List<DetectionResult>>): SubscriptionToken {
        val token = SubscriptionToken(idGenerator.incrementAndGet())
        subscribers[token] = subscriber
        return token
    }

    override fun unsubscribe(token: SubscriptionToken) {
        subscribers.remove(token)
    }

    override fun publish(data: List<DetectionResult>) {
        subscribers.values.forEach { subscriber ->
            subscriber.onNext(data)
        }
    }

    fun publishError(error: Throwable) {
        subscribers.values.forEach { subscriber ->
            subscriber.onError(error)
        }
    }

    fun hasSubscribers(): Boolean = subscribers.isNotEmpty()
}

class WebSocketManager(private val url: String) {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private var webSocket: WebSocket? = null

    val detectionPublisher = DetectionPublisher()

    fun connect() {
        val request = Request.Builder()
            .url(url)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("WebSocket", "Connection opened")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val detections = Json.decodeFromString<List<DetectionResult>>(text)
                    detectionPublisher.publish(detections)
                } catch (e: Exception) {
                    detectionPublisher.publishError(e)
                    Log.e("WebSocket", "Error parsing message: $text", e)
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                Log.d("WebSocket", "Received binary message of size ${bytes.size}")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                detectionPublisher.publishError(t)
                Log.e("WebSocket", "Connection failure", t)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("WebSocket", "Connection closing: $code, $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("WebSocket", "Connection closed: $code, $reason")
            }
        })
    }

    fun sendImage(imageBytes: ByteArray) {
        if (detectionPublisher.hasSubscribers()) {
            webSocket?.send(imageBytes.toByteString(0, imageBytes.size))
        }
    }

    fun disconnect() {
        webSocket?.close(1000, "User finished")
        webSocket = null
    }
}

class ImageCaptureManager(
    private val imageCapture: ImageCapture,
    private val executor: Executor,
    private val webSocketManager: WebSocketManager
) {
    fun captureAndSend() {
        imageCapture.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)

                webSocketManager.sendImage(bytes)

                image.close()
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("CameraScan", "Image capture failed", exception)
            }
        })
    }
}

@Composable
fun CameraScanScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val textMeasurer = rememberTextMeasurer()
    var hasCameraPermission by remember { mutableStateOf(false) }
    var detections by remember { mutableStateOf<List<DetectionResult>>(emptyList()) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val webSocketManager = remember { WebSocketManager("ws://${BuildConfig.SERVER_URL}/detect") }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var subscriptionToken by remember { mutableStateOf<SubscriptionToken?>(null) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        hasCameraPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        webSocketManager.connect()

        val subscriber = object : Subscriber<List<DetectionResult>> {
            override fun onNext(data: List<DetectionResult>) {
                detections = data
            }

            override fun onError(error: Throwable) {
                Log.e("CameraScan", "Detection error", error)
            }
        }

        subscriptionToken = webSocketManager.detectionPublisher.subscribe(subscriber)
    }

    DisposableEffect(Unit) {
        onDispose {
            subscriptionToken?.let { token ->
                webSocketManager.detectionPublisher.unsubscribe(token)
            }
            webSocketManager.disconnect()
            cameraExecutor.shutdown()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar()
        TitleBar(onNavigateBack)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.6f)
        ) {
            if (hasCameraPermission) {
                CameraPreview { captureUseCase ->
                    imageCapture = captureUseCase
                }

                LaunchedEffect(imageCapture) {
                    val captureUseCase = imageCapture ?: return@LaunchedEffect
                    val imageCaptureManager = ImageCaptureManager(
                        captureUseCase,
                        cameraExecutor,
                        webSocketManager
                    )

                    while (true) {
                        imageCaptureManager.captureAndSend()
                        withContext(Dispatchers.IO) {
                            Thread.sleep(500)
                        }
                    }
                }

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height

                    val typeColors = mapOf(
                        "plastic" to Color.Blue,
                        "paper" to Color.Yellow,
                        "glass" to Color.Cyan,
                        "metal" to Color.Red,
                        "organic" to Color.Green
                    )

                    detections.forEach { detection ->
                        val x = detection.bbox[0] * canvasWidth
                        val y = detection.bbox[1] * canvasHeight
                        val width = detection.bbox[2] * canvasWidth
                        val height = detection.bbox[3] * canvasHeight

                        val color = typeColors[detection.type] ?: Color.White

                        drawRect(
                            color = color,
                            topLeft = androidx.compose.ui.geometry.Offset(x, y),
                            size = androidx.compose.ui.geometry.Size(width, height),
                            style = Stroke(width = 3f)
                        )

                        val label = "${detection.type} (${(detection.confidence * 100).toInt()}%)"
                        drawText(
                            textMeasurer = textMeasurer,
                            text = label,
                            topLeft = androidx.compose.ui.geometry.Offset(x, y - 15f),
                            style = TextStyle(
                                color = color,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                background = Color.Black.copy(alpha = 0.7f)
                            )
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Camera permission required",
                            color = Color.White,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        Button(
                            onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF00C853)
                            )
                        ) {
                            Text("Grant Camera Permission")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Button(
                onClick = {
                    subscriptionToken?.let { token ->
                        webSocketManager.detectionPublisher.unsubscribe(token)
                    }
                    webSocketManager.disconnect()
                    onNavigateBack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00C853)
                )
            ) {
                Text(
                    text = "Ready",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun CameraPreview(onPreviewReady: (ImageCapture) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageCapture
                    )

                    onPreviewReady(imageCapture)
                } catch (e: Exception) {
                    Log.e("CameraPreview", "Use case binding failed", e)
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun TopBar() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF00C853))
            .padding(16.dp)
    ) {
        IconButton(
            onClick = { },
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Menu",
                tint = Color.Black
            )
        }
    }
}

@Composable
private fun TitleBar(onNavigateBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onNavigateBack) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Back",
                tint = Color.Black
            )
        }

        Text(
            text = "Camera Scan",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )
    }
}