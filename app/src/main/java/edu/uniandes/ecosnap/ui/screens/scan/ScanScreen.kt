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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import edu.uniandes.ecosnap.data.observer.Observer
import edu.uniandes.ecosnap.data.repository.PointOfInterestRepository
import edu.uniandes.ecosnap.domain.model.PointOfInterest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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

data class NearbyPointsUiState(
    val pointsOfInterest: List<PointOfInterest> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class NearbyPointsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(NearbyPointsUiState())
    val uiState: StateFlow<NearbyPointsUiState> = _uiState.asStateFlow()

    private val pointsOfInterestObserver = object : Observer<PointOfInterest> {
        override fun onSuccess(data: PointOfInterest) {
            _uiState.update { currentState ->
                val updatedPoints = currentState.pointsOfInterest.toMutableList()
                updatedPoints.add(data)
                currentState.copy(
                    pointsOfInterest = updatedPoints,
                    isLoading = false
                )
            }
        }

        override fun onError(error: Throwable) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = "Error loading points of interest: ${error.message}"
                )
            }
        }
    }

    init {
        PointOfInterestRepository.addObserver(pointsOfInterestObserver)
        loadPointsOfInterest()
    }

    override fun onCleared() {
        PointOfInterestRepository.removeObserver(pointsOfInterestObserver)
        super.onCleared()
    }

    private fun loadPointsOfInterest() {
        _uiState.update {
            it.copy(
                pointsOfInterest = emptyList(),
                isLoading = true
            )
        }
        PointOfInterestRepository.fetch()
    }
}

fun getUserLocation(context: Context, callback: (Location?) -> Unit) {
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            callback(location)
        }
    } else {
        callback(null)
    }
}

@Composable
fun ScanScreen(onNavigateBack: () -> Unit) {
    val viewModel: NearbyPointsViewModel = viewModel()
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
            getUserLocation(context) { location ->
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
            getUserLocation(context) { location ->
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
        TopBar()
        TitleBar(onNavigateBack)

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
                            getUserLocation(context) { location ->
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
        }

        Spacer(modifier = Modifier.weight(0.05f))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.5f)
        ) {
            ScrollablePointsOfInterestList(uiState.pointsOfInterest)
        }
    }
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
            text = "Puntos Cercanos",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )
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
            Text(
                text = "${point.address}: ${point.name}",
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}