package edu.uniandes.ecosnap.ui.screens.history

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.uniandes.ecosnap.ui.theme.Green
import edu.uniandes.ecosnap.data.repository.VisitedPointsRepository
import edu.uniandes.ecosnap.domain.model.VisitedPointItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisitedPointsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var visitedPoints by remember { mutableStateOf<List<VisitedPointItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var cacheMetrics by remember { mutableStateOf<Map<String, Any>>(emptyMap()) }

    // Cargar datos
    LaunchedEffect(Unit) {
        VisitedPointsRepository.initialize(context)
        visitedPoints = VisitedPointsRepository.getAllVisitedPoints()
        cacheMetrics = VisitedPointsRepository.getCacheMetrics()
        isLoading = false
    }

    fun refreshData() {
        visitedPoints = VisitedPointsRepository.getAllVisitedPoints()
        cacheMetrics = VisitedPointsRepository.getCacheMetrics()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Bar
        TopAppBar(
            title = {
                Text(
                    "Puntos Visitados",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Volver",
                        tint = Color.Black
                    )
                }
            },
            actions = {
                // Contador de puntos visitados
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = 16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Color(0xFF00C853),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${visitedPoints.size}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00C853)
                    )
                }

                // Botón de refresh
                IconButton(onClick = { refreshData() }) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Actualizar",
                        tint = Color(0xFF00C853)
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Green
            )
        )

        // Content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            when {
                isLoading -> {
                    // Loading state
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Green)
                    }
                }

                visitedPoints.isEmpty() -> {
                    // Empty state
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Text(
                                text = "🗺️",
                                fontSize = 64.sp
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = "No has visitado ningún punto",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Marca puntos como visitados en el mapa para verlos aquí",
                                fontSize = 14.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(32.dp))

                            // Indicador de cache activo
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Green.copy(alpha = 0.1f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "⚡ Cache Activo",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "Tus puntos visitados se guardan con cache LRU",
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }

                else -> {
                    // Content with visited points
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Header stats
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                StatsCard(
                                    title = "Visitados",
                                    value = "${visitedPoints.size}",
                                    color = Green
                                )
                                StatsCard(
                                    title = "Cache Hits",
                                    value = "${cacheMetrics["cacheHits"] ?: 0}",
                                    color = Color.Blue
                                )
                                StatsCard(
                                    title = "Hit Rate",
                                    value = "${cacheMetrics["hitRate"] ?: 0}%",
                                    color = Color(0xFFFF9800)
                                )
                            }
                        }

                        // Visited points list
                        items(visitedPoints) { point ->
                            VisitedPointCard(point = point)
                        }

                        // Footer info
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.Gray.copy(alpha = 0.1f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Text(
                                        text = "⚡ Cache Strategy Implementada:",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "• LRU Memory Cache con máximo ${cacheMetrics["maxCacheSize"]} elementos",
                                        fontSize = 10.sp,
                                        color = Color.Gray
                                    )
                                    Text(
                                        text = "• Cache hits: ${cacheMetrics["cacheHits"]}, misses: ${cacheMetrics["cacheMisses"]}",
                                        fontSize = 10.sp,
                                        color = Color.Gray
                                    )
                                    Text(
                                        text = "• Persistencia: SharedPreferences + JSON serialization",
                                        fontSize = 10.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsCard(
    title: String,
    value: String,
    color: Color
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        modifier = Modifier.width(100.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = title,
                fontSize = 10.sp,
                color = Color.Gray
            )
        }
    }
}

@Composable
private fun VisitedPointCard(
    point: VisitedPointItem
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = point.pointName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Green
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = Color.Gray
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = point.address,
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = java.text.SimpleDateFormat("dd/MM", java.util.Locale.getDefault())
                            .format(java.util.Date(point.timestamp)),
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    Text(
                        text = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                            .format(java.util.Date(point.timestamp)),
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Badge de visitado
            Surface(
                color = Green.copy(alpha = 0.1f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "✅",
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Visitado el ${java.text.SimpleDateFormat("dd/MM/yyyy 'a las' HH:mm", java.util.Locale.getDefault())
                            .format(java.util.Date(point.timestamp))}",
                        fontSize = 10.sp,
                        color = Green,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}