package edu.uniandes.ecosnap.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import edu.uniandes.ecosnap.ui.screens.camera.CameraScanScreen
import edu.uniandes.ecosnap.ui.screens.home.HomeScreen
import edu.uniandes.ecosnap.ui.screens.redeem.RedeemScreen
import edu.uniandes.ecosnap.ui.screens.scan.ScanScreen

object AppDestinations {
    const val HOME_ROUTE = "home"
    const val SCAN_ROUTE = "scan"
    const val REDEEM_ROUTE = "redeem"
    const val CAMERA_SCAN = "camera_scan"
}

@Composable
fun AppNavHost(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = AppDestinations.HOME_ROUTE
    ) {
        composable(AppDestinations.HOME_ROUTE) {
            HomeScreen(
                onScanClick = { navController.navigate(AppDestinations.SCAN_ROUTE) },
                onRedeemClick = { navController.navigate(AppDestinations.REDEEM_ROUTE) }
            )
        }
        composable(AppDestinations.SCAN_ROUTE) {
            ScanScreen(onNavigateBack = { navController.popBackStack() }, onCameraScanClick = {navController.navigate(AppDestinations.CAMERA_SCAN)})
        }
        composable(AppDestinations.REDEEM_ROUTE) {
            RedeemScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(AppDestinations.CAMERA_SCAN) {
            CameraScanScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}