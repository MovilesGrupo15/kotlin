package edu.uniandes.ecosnap.ui.screens.scan

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.preference.PreferenceManager
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import edu.uniandes.ecosnap.domain.model.PointOfInterest
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class MarkerFactory {
    fun createMarker(mapView: MapView, poi: PointOfInterest): Marker {
        val marker = Marker(mapView)
        marker.position = poi.toGeoPoint()
        marker.title = poi.name
        marker.snippet = poi.description
        return marker
    }

    fun createMarkers(mapView: MapView, pois: List<PointOfInterest>): List<Marker> {
        return pois.map { createMarker(mapView, it) }
    }
}

class MapController(private val mapView: MapView, private val markerFactory: MarkerFactory) {
    fun updateMarkers(pois: List<PointOfInterest>) {
        mapView.overlays.clear()
        val markers = markerFactory.createMarkers(mapView, pois)
        mapView.overlays.addAll(markers)
        mapView.invalidate()
    }

    fun centerMap(geoPoint: GeoPoint) {
        mapView.controller.animateTo(geoPoint)
    }
}

fun getUserLocation(context: Context, viewModel: NearbyPointsViewModel? = null, callback: (Location?) -> Unit) {
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            // Actualizar la ubicación en el ViewModel cuando está disponible
            location?.let {
                viewModel?.updateUserLocation(it.latitude, it.longitude)
            }

            callback(location)
        }
    } else {
        callback(null)
    }
}

@Composable
fun ScanScreen(
    onNavigateBack: () -> Unit,
    onCameraScanClick: () -> Unit,
    onRecyclingGuideClick: () -> Unit, // New parameter
    viewModel: NearbyPointsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var mapInitialized by remember { mutableStateOf(false) }
    var mapController by remember { mutableStateOf<MapController?>(null) }
    val bogotaCenter = remember { GeoPoint(4.6097, -74.0817) }
    var userLocation by remember { mutableStateOf<GeoPoint?>(null) }
    val markerFactory = remember { MarkerFactory() }

    val locationPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val locationPermissionsGranted = permissions.entries.any { it.value }

        if (locationPermissionsGranted) {
            getUserLocation(context, viewModel) { location ->
                location?.let {
                    userLocation = GeoPoint(it.latitude, it.longitude)
                    mapController?.centerMap(userLocation!!)
                }
            }
        }
    }

    LaunchedEffect(key1 = true) {
        Configuration.getInstance().load(context, PreferenceManager.getDefaultSharedPreferences(context))
        mapInitialized = true

        val hasPermissions = locationPermissions.any {
            ActivityCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

        if (hasPermissions) {
            getUserLocation(context, viewModel) { location ->
                location?.let {
                    userLocation = GeoPoint(it.latitude, it.longitude)
                }
            }
        } else {
            permissionLauncher.launch(locationPermissions)
        }
    }

    LaunchedEffect(uiState.pointsOfInterest) {
        mapController?.updateMarkers(uiState.pointsOfInterest)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(
            onCameraScanClick = onCameraScanClick,
            onRecyclingGuideClick = onRecyclingGuideClick // Pass the recycling guide navigation function
        )
        TitleBar(
            onNavigateBack = onNavigateBack,
            isOfflineMode = uiState.isOfflineMode,
            onRefresh = { viewModel.refreshFromNetwork() },
            pointCount = uiState.pointsOfInterest.size
        )

        Spacer(modifier = Modifier.weight(0.05f))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.4f)
        ) {
            if (mapInitialized) {
                AndroidView(
                    factory = { ctx ->
                        MapView(ctx).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            controller.setZoom(10.0)
                            controller.setCenter(bogotaCenter)
                            setMultiTouchControls(true)
                            mapController = MapController(this, markerFactory)
                            mapController?.updateMarkers(uiState.pointsOfInterest)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                FloatingActionButton(
                    onClick = {
                        val hasPermissions = locationPermissions.any {
                            ActivityCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                        }

                        if (hasPermissions) {
                            getUserLocation(context, viewModel) { location ->
                                location?.let {
                                    val userGeoPoint = GeoPoint(it.latitude, it.longitude)
                                    mapController?.centerMap(userGeoPoint)
                                } ?: run {
                                    mapController?.centerMap(bogotaCenter)
                                }
                            }
                        } else {
                            permissionLauncher.launch(locationPermissions)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                        .size(48.dp),
                    containerColor = Color(0xFF00C853)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "My Location",
                        tint = Color.White
                    )
                }
            }

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
        }

        Spacer(modifier = Modifier.weight(0.05f))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.5f)
        ) {
            when {
                uiState.error != null -> {
                    Text(
                        text = uiState.error!!,
                        color = Color.Red,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }
                uiState.pointsOfInterest.isEmpty() -> {
                    Text(
                        text = "No hay puntos de reciclaje disponibles",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }
                else -> {
                    ScrollablePointsOfInterestList(uiState.pointsOfInterest)
                }
            }
        }
    }
}

@Composable
private fun TopBar(
    onCameraScanClick: () -> Unit,
    onRecyclingGuideClick: () -> Unit // New parameter
) {
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

        // New Recycling Guide button in center
        Button(
            onClick = onRecyclingGuideClick,
            modifier = Modifier.align(Alignment.Center),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Color(0xFF00C853)
            )
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Recycling Guide",
                tint = Color(0xFF00C853)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Guía de Reciclaje",
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        }

        Button(
            onClick = onCameraScanClick,
            modifier = Modifier.align(Alignment.CenterEnd),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Color.Black
            )
        ) {
            Text(
                text = "Cámara",
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun TitleBar(
    onNavigateBack: () -> Unit,
    isOfflineMode: Boolean,
    onRefresh: () -> Unit,
    pointCount: Int = 0
) {
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

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Puntos Cercanos",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )

            // Mostrar el número de puntos visualizados
            if (pointCount > 0) {
                Text(
                    text = "Mostrando los $pointCount puntos más cercanos",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }

        if (isOfflineMode) {
            Text(
                text = "Modo sin conexión",
                fontSize = 12.sp,
                color = Color(0xFFFF9800),
                modifier = Modifier.padding(end = 8.dp)
            )

            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = Color(0xFF00C853)
                )
            }
        }
    }
}

@Composable
private fun ScrollablePointsOfInterestList(points: List<PointOfInterest>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        items(points) { point ->
            RecyclingPointCard(point)
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun RecyclingPointCard(point: PointOfInterest) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = point.name,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = point.address,
                fontSize = 14.sp
            )

            if (point.description.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = point.description,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }
    }
}