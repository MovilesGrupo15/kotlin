package edu.uniandes.ecosnap.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import edu.uniandes.ecosnap.Analytics
import edu.uniandes.ecosnap.data.repository.AuthRepository
import edu.uniandes.ecosnap.ui.screens.camera.CameraScanScreen
import edu.uniandes.ecosnap.ui.screens.home.HomeScreen
import edu.uniandes.ecosnap.ui.screens.login.LoginScreen
import edu.uniandes.ecosnap.ui.screens.redeem.RedeemScreen
import edu.uniandes.ecosnap.ui.screens.scan.RecyclingGuideScreen
import edu.uniandes.ecosnap.ui.screens.scan.ScanScreen
import edu.uniandes.ecosnap.ui.screens.history.ScanHistoryScreen
import edu.uniandes.ecosnap.ui.screens.history.VisitedPointsScreen
import edu.uniandes.ecosnap.ui.screens.dashboard.DashboardScreen

object AppDestinations {
    const val LOGIN_ROUTE = "login"
    const val HOME_ROUTE = "home"
    const val SCAN_ROUTE = "scan"
    const val REDEEM_ROUTE = "redeem"
    const val CAMERA_SCAN = "camera_scan"
    const val RECYCLING_GUIDE_ROUTE = "recycling_guide"
    const val SCAN_HISTORY_ROUTE = "scan_history"
    const val VISITED_POINTS_ROUTE = "visited_points"
    const val DASHBOARD_ROUTE = "dashboard"
}

@Composable
fun AppNavHost(navController: NavHostController) {
    val startDestination = remember {
        if (AuthRepository.getCurrentUser() != null) {
            AppDestinations.HOME_ROUTE
        } else {
            AppDestinations.LOGIN_ROUTE
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(AppDestinations.LOGIN_ROUTE) {
            Analytics.screenEvent("login_screen")
            LoginScreen(navController)
        }

        composable(AppDestinations.HOME_ROUTE) {
            Analytics.screenEvent("home_screen")
            HomeScreen(
                onScanClick = { navController.navigate(AppDestinations.SCAN_ROUTE) },
                onRedeemClick = { navController.navigate(AppDestinations.REDEEM_ROUTE) },
                navController = navController,
            )
        }

        composable(AppDestinations.SCAN_ROUTE) {
            Analytics.screenEvent("scan_screen")
            ScanScreen(
                onNavigateBack = { navController.popBackStack() },
                onCameraScanClick = { navController.navigate(AppDestinations.CAMERA_SCAN) },
                onRecyclingGuideClick = { navController.navigate(AppDestinations.RECYCLING_GUIDE_ROUTE) }
            )
        }

        composable(AppDestinations.RECYCLING_GUIDE_ROUTE) {
            Analytics.screenEvent("guide_screen")
            RecyclingGuideScreen(
                onNavigateBack = { navController.popBackStack() },
                onCameraScanClick = { navController.navigate(AppDestinations.CAMERA_SCAN) }
            )
        }

        composable(AppDestinations.SCAN_HISTORY_ROUTE) {
            Analytics.screenEvent("scan_history_screen")
            ScanHistoryScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(AppDestinations.VISITED_POINTS_ROUTE) {
            Analytics.screenEvent("visited_points_screen")
            VisitedPointsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(AppDestinations.DASHBOARD_ROUTE) {
            Analytics.screenEvent("dashboard_screen")
            DashboardScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(AppDestinations.REDEEM_ROUTE) {
            Analytics.screenEvent("redeem_screen")
            RedeemScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(AppDestinations.CAMERA_SCAN) {
            Analytics.screenEvent("camera_scan_screen")
            CameraScanScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}