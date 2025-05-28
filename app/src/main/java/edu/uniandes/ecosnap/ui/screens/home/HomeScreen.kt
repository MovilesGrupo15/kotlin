package edu.uniandes.ecosnap.ui.screens.home

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import edu.uniandes.ecosnap.Analytics
import edu.uniandes.ecosnap.ui.components.OfferCard
import edu.uniandes.ecosnap.ui.theme.Green
import kotlinx.coroutines.launch


@Composable
private fun SkeletonText(modifier: Modifier = Modifier, width: Dp) {
    Box(
        modifier = modifier
            .width(width)
            .height(24.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Gray.copy(alpha = 0.3f))
    )
}

@Composable
private fun SkeletonOfferCard(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Gray.copy(alpha = 0.3f))
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onScanClick: () -> Unit,
    onRedeemClick: () -> Unit,
    navController: NavController,
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DismissibleDrawerSheet {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(12.dp))
                    Text("Menu", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(16.dp))
                    Divider()
                    Spacer(Modifier.height(16.dp))

                    // Historial de Escaneos (por ahora solo placeholder)
                    NavigationDrawerItem(
                        label = { Text("Historial de Escaneos") },
                        selected = false,
                        // En el onClick del NavigationDrawerItem del historial:
                        onClick = {
                            Analytics.buttonEvent("scan_history", "home_screen")
                            scope.launch { drawerState.close() }
                            navController.navigate("scan_history")
                        },
                        icon = {
                            Icon(Icons.Default.List, contentDescription = null)
                        }
                    )

                    Spacer(Modifier.height(8.dp))

                    NavigationDrawerItem(
                        label = { Text("Puntos Visitados") },
                        selected = false,
                        onClick = {
                            Analytics.buttonEvent("visited_points", "home_screen")
                            scope.launch { drawerState.close() }
                            navController.navigate("visited_points")
                        },
                        icon = {
                            Icon(Icons.Default.List, contentDescription = null)
                        }
                    )

                    Spacer(Modifier.height(8.dp))

                    NavigationDrawerItem(
                        label = { Text("Dashboard") },
                        selected = false,
                        onClick = {
                            Analytics.buttonEvent("dashboard", "home_screen")
                            scope.launch { drawerState.close() }
                            navController.navigate("dashboard")
                        },
                        icon = {
                            Icon(Icons.Default.List, contentDescription = null)
                        }
                    )

                    Spacer(Modifier.height(8.dp))

                    // Cerrar Sesión
                    NavigationDrawerItem(
                        label = {
                            Text(if (uiState.isAnonymous) "Ingresar" else "Cerrar sesión")
                        },
                        selected = false,
                        onClick = {
                            Analytics.buttonEvent("logout", "home_screen")
                            scope.launch { drawerState.close() }
                            viewModel.signOut()
                            navController.navigate("login") {
                                popUpTo("home") { inclusive = true }
                            }
                        }
                    )
                }
            }
        },
        gesturesEnabled = !isLoading
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                Analytics.buttonEvent("menu_open", "home_screen")
                                scope.launch { drawerState.open() }
                                      },
                            enabled = !isLoading
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Menu"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Green,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            },
            bottomBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = onScanClick,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Green
                        ),
                        enabled = !isLoading
                    ) {
                        Text("Escanear")
                    }
                    Button(
                        onClick = onRedeemClick,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Green
                        ),
                        enabled = !isLoading
                    ) {
                        Text("Redimir")
                    }
                }
            }
        ) { innerPadding ->
            if (isLoading) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp)
                ) {
                    Spacer(Modifier.height(16.dp))
                    SkeletonText(width = 200.dp)
                    Spacer(Modifier.height(8.dp))
                    SkeletonText(width = 150.dp)
                    Spacer(Modifier.height(16.dp))
                    repeat(5) {
                        SkeletonOfferCard(modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
            } else if (uiState.error != null && uiState.error!!.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Error: ${uiState.error}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Button(onClick = { viewModel.loadUserProfile() }) {
                        Analytics.buttonEvent("retry", "home_screen")
                        Text("Reintentar")
                    }
                }
            }
            else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp)
                ) {
                    item {
                        val greetingName = uiState.userName.ifBlank {
                            if (uiState.isAnonymous) "Usuario Anónimo" else ""
                        }

                        Text(
                            text = "Hola, $greetingName",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }

                    item {
                        Text(
                            text = "Hoy para tí",
                            fontSize = 24.sp,
                            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
                        )
                    }

                    if (uiState.offers.isEmpty() && !isLoading) {
                        item {
                            Text("No hay ofertas disponibles en este momento.")
                        }
                    } else {
                        items(uiState.offers) { offer ->
                            OfferCard(
                                offer = offer,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}