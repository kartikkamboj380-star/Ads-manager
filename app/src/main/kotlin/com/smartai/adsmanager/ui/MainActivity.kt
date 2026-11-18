package com.smartai.adsmanager.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.smartai.adsmanager.service.ScreenCapturePermissionManager
import com.smartai.adsmanager.ui.screens.AdAssistanceScreen
import com.smartai.adsmanager.ui.screens.AppListScreen
import com.smartai.adsmanager.ui.screens.DashboardScreen
import com.smartai.adsmanager.ui.screens.LiveMonitorScreen
import com.smartai.adsmanager.ui.screens.RewardHistoryScreen
import com.smartai.adsmanager.ui.theme.SmartAIAdsManagerTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val permissionManager = ScreenCapturePermissionManager()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request notification permission on Android 13+ (needed for the foreground service).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermissionIfNeeded()
        }

        setContent {
            SmartAIAdsManagerTheme {
                AppNav()
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                1001,
            )
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun AppNav() {
        val ui by viewModel.ui.collectAsState()
        val navController = rememberNavController()

        // MediaProjection consent launcher.
        val projectionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            // Interpret the consent result through the permission manager. A denied or
            // malformed result is reported as a clear error — the app never crashes or
            // disappears.
            val granted = permissionManager.interpretResult(result.resultCode, result.data)
            if (granted != null) {
                val app = ui.selectedApp
                if (app != null) {
                    viewModel.onCaptureResult(
                        result.resultCode,
                        granted,
                        app.packageName,
                        app.appName,
                    )
                } else {
                    viewModel.clearError()
                }
            } else {
                viewModel.onCaptureDenied()
            }
        }

        // When the ViewModel asks for screen capture, launch the consent intent.
        // LocalContext must be read in the composable scope (not inside the LaunchedEffect
        // suspend lambda), so capture it here and pass it in.
        val context = LocalContext.current
        LaunchedEffect(ui.awaitingCapture) {
            if (ui.awaitingCapture) {
                val intent = permissionManager.createCaptureIntent(context)
                projectionLauncher.launch(intent)
            }
        }

        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route

        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavItem(
                        icon = Icons.Default.Home,
                        label = "Dashboard",
                        route = Routes.DASHBOARD,
                        currentRoute = currentRoute,
                        onClick = {
                            navController.navigate(Routes.DASHBOARD) {
                                popUpTo(Routes.DASHBOARD) { inclusive = true }
                            }
                        },
                    )
                    NavItem(
                        icon = Icons.Default.Apps,
                        label = "Apps",
                        route = Routes.APPS,
                        currentRoute = currentRoute,
                        onClick = { navController.navigate(Routes.APPS) },
                    )
                    NavItem(
                        icon = Icons.Default.Bolt,
                        label = "Assistance",
                        route = Routes.ASSISTANCE,
                        currentRoute = currentRoute,
                        onClick = { navController.navigate(Routes.ASSISTANCE) },
                    )
                    NavItem(
                        icon = Icons.Default.History,
                        label = "Rewards",
                        route = Routes.REWARDS,
                        currentRoute = currentRoute,
                        onClick = { navController.navigate(Routes.REWARDS) },
                    )
                }
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Routes.DASHBOARD,
                modifier = Modifier.padding(innerPadding),
            ) {
                composable(Routes.DASHBOARD) {
                    DashboardScreen(viewModel = viewModel, onNavigate = { navController.navigate(it) })
                }
                composable(Routes.APPS) {
                    AppListScreen(viewModel = viewModel)
                }
                composable(Routes.ASSISTANCE) {
                    AdAssistanceScreen(
                        viewModel = viewModel,
                        onOpenLiveMonitor = { navController.navigate(Routes.LIVE_MONITOR) },
                    )
                }
                composable(Routes.REWARDS) {
                    RewardHistoryScreen(viewModel = viewModel)
                }
                composable(Routes.LIVE_MONITOR) {
                    LiveMonitorScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun RowScope.NavItem(
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        label: String,
        route: String,
        currentRoute: String?,
        onClick: () -> Unit,
    ) {
        NavigationBarItem(
            selected = currentRoute == route,
            onClick = onClick,
            icon = { androidx.compose.material3.Icon(icon, contentDescription = label) },
            label = { Text(label) },
        )
    }
}
