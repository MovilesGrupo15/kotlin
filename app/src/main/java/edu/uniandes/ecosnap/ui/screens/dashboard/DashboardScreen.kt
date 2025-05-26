package edu.uniandes.ecosnap.ui.screens.dashboard

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.uniandes.ecosnap.ui.theme.Green

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateBack: () -> Unit,
    viewModel: DashboardViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Initialize ViewModel
    LaunchedEffect(Unit) {
        viewModel.initialize(context)
    }

    // Auto-refresh every 30 seconds when screen is active
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30000) // 30 seconds
            if (!uiState.isLoading) {
                viewModel.backgroundRefresh()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Bar
        TopAppBar(
            title = {
                Text(
                    "Dashboard de Estadísticas",
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
                IconButton(
                    onClick = { viewModel.refreshData() },
                    enabled = !uiState.isLoading
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Actualizar",
                        tint = if (uiState.isLoading) Color.Gray else Color(0xFF00C853)
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Green
            )
        )

        // Content
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                uiState.isLoading -> {
                    LoadingContent(progress = uiState.loadingProgress)
                }

                uiState.error != null -> {
                    ErrorContent(
                        error = uiState.error!!,
                        onRetry = { viewModel.loadDashboardData() }
                    )
                }

                uiState.dashboardStats != null -> {
                    DashboardContent(
                        stats = uiState.dashboardStats!!,
                        serverStats = uiState.serverStats,
                        processorMetrics = uiState.processorMetrics,
                        lastUpdateTime = uiState.lastUpdateTime
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingContent(progress: Int) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            // Animated loading indicator
            val infiniteTransition = rememberInfiniteTransition(label = "loading")
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000),
                    repeatMode = RepeatMode.Reverse
                ), label = "alpha"
            )

            Text(
                text = "📊",
                fontSize = 64.sp,
                modifier = Modifier.alpha(alpha)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Cargando Estadísticas",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Procesando datos en múltiples hilos...",
                fontSize = 14.sp,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = progress / 100f,
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = Green
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "$progress%",
                fontSize = 12.sp,
                color = Green,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Threading info
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Gray.copy(alpha = 0.1f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "🧵 Multithreading Activo",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "I/O: Dispatchers.IO | CPU: Dispatchers.Default",
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorContent(
    error: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "❌",
                fontSize = 48.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Error al cargar dashboard",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = error,
                fontSize = 14.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = Green)
            ) {
                Text("Reintentar")
            }
        }
    }
}

@Composable
private fun DashboardContent(
    stats: DashboardStats,
    serverStats: Map<String, Any>,
    processorMetrics: Map<String, Any>,
    lastUpdateTime: Long
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header with update time
        item {
            UpdateTimeHeader(lastUpdateTime)
        }

        // Main statistics cards
        item {
            MainStatsRow(stats)
        }

        // Waste type breakdown
        item {
            WasteTypeSection(stats.wasteTypeStats)
        }

        // Weekly trends
        item {
            TrendsSection(stats.weeklyTrends, stats.thisWeekScans, stats.lastWeekScans, stats.improvementPercentage)
        }

        // Server statistics
        item {
            ServerStatsSection(serverStats)
        }

        // Performance metrics
        item {
            PerformanceSection(processorMetrics)
        }
    }
}

@Composable
private fun UpdateTimeHeader(lastUpdateTime: Long) {
    val timeFormat = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
    val updateTimeStr = timeFormat.format(java.util.Date(lastUpdateTime))

    Card(
        colors = CardDefaults.cardColors(containerColor = Green.copy(alpha = 0.1f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "🔄",
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Última actualización: $updateTimeStr",
                fontSize = 12.sp,
                color = Green,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun MainStatsRow(stats: DashboardStats) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(
            title = "Escaneos",
            value = "${stats.totalScans}",
            subtitle = "Total realizados",
            color = Green,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            title = "Puntos",
            value = "${stats.totalVisits}",
            subtitle = "Visitados",
            color = Color.Blue,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            title = "Precisión",
            value = "${(stats.avgConfidence * 100).toInt()}%",
            subtitle = "Promedio",
            color = Color(0xFFFF9800),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    subtitle: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                fontSize = 12.sp,
                color = Color.Gray
            )
            Text(
                text = value,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = Color.Gray
            )
        }
    }
}

@Composable
private fun WasteTypeSection(wasteTypes: List<WasteTypeStats>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Tipos de Residuos",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (wasteTypes.isEmpty()) {
                Text(
                    text = "No hay datos de tipos de residuos",
                    color = Color.Gray,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            } else {
                wasteTypes.take(5).forEach { wasteType ->
                    WasteTypeRow(wasteType)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun WasteTypeRow(wasteType: WasteTypeStats) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = wasteType.type,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "${wasteType.count}",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Green
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "${wasteType.percentage.toInt()}%",
            fontSize = 12.sp,
            color = Color.Gray
        )
    }
}

@Composable
private fun TrendsSection(
    trends: List<ActivityTrend>,
    thisWeekScans: Int,
    lastWeekScans: Int,
    improvementPercentage: Float
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tendencias Semanales",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Icon(
                    imageVector = Icons.Default.List,
                    contentDescription = null,
                    tint = if (improvementPercentage >= 0) Color(0xFF4CAF50) else Color(0xFFF44336),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Improvement indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Esta semana: $thisWeekScans",
                    fontSize = 14.sp
                )
                Text(
                    text = "Semana pasada: $lastWeekScans",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            val improvementText = if (improvementPercentage >= 0) {
                "+${improvementPercentage.toInt()}% de mejora"
            } else {
                "${improvementPercentage.toInt()}% de cambio"
            }

            Text(
                text = improvementText,
                fontSize = 12.sp,
                color = if (improvementPercentage >= 0) Color(0xFF4CAF50) else Color(0xFFF44336),
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Daily trends
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(trends) { trend ->
                    TrendDayCard(trend)
                }
            }
        }
    }
}

@Composable
private fun TrendDayCard(trend: ActivityTrend) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (trend.scanCount > 0) Green.copy(alpha = 0.1f) else Color.Gray.copy(alpha = 0.1f)
        ),
        modifier = Modifier.width(60.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = trend.date,
                fontSize = 10.sp,
                color = Color.Gray
            )
            Text(
                text = "${trend.scanCount}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (trend.scanCount > 0) Green else Color.Gray
            )
            Text(
                text = "escaneos",
                fontSize = 8.sp,
                color = Color.Gray
            )
        }
    }
}

@Composable
private fun ServerStatsSection(serverStats: Map<String, Any>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🌐",
                    fontSize = 20.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Estadísticas Globales",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (serverStats.isEmpty()) {
                Text(
                    text = "Cargando datos del servidor...",
                    color = Color.Gray,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ServerStatItem(
                        title = "Escaneos Globales",
                        value = "${serverStats["globalScans"] ?: "N/A"}"
                    )
                    ServerStatItem(
                        title = "Usuarios Activos",
                        value = "${serverStats["activeUsers"] ?: "N/A"}"
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Residuo más común: ${serverStats["topWasteType"]}",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Text(
                    text = "Promedio por usuario: ${serverStats["avgUserScans"]} escaneos",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
private fun ServerStatItem(title: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Blue
        )
        Text(
            text = title,
            fontSize = 10.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PerformanceSection(processorMetrics: Map<String, Any>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Gray.copy(alpha = 0.05f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "⚡",
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Performance & Threading",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (processorMetrics.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PerformanceRow(
                        "CPUs Disponibles:",
                        "${processorMetrics["availableProcessors"] ?: "N/A"}"
                    )
                    PerformanceRow(
                        "Memoria Máxima:",
                        "${processorMetrics["maxMemory"] ?: "N/A"} MB"
                    )
                    PerformanceRow(
                        "Memoria Libre:",
                        "${processorMetrics["freeMemory"] ?: "N/A"} MB"
                    )
                    PerformanceRow(
                        "Memoria Usada:",
                        "${processorMetrics["usedMemory"] ?: "N/A"} MB"
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "🧵 Multithreading: Dispatchers.IO (I/O) + Dispatchers.Default (CPU)",
                    fontSize = 10.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun PerformanceRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color.Gray
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}