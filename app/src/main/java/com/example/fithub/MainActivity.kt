package com.example.fithub

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.fithub.ui.auth.LoginScreen
import com.example.fithub.ui.auth.RegisterScreen
import com.example.fithub.ui.components.OfflineBanner
import com.example.fithub.ui.dashboard.DashboardScreen
import com.example.fithub.ui.dashboard.TrackingScreen
import com.example.fithub.ui.exercises.ExercisesScreen
import com.example.fithub.ui.history.HistoryScreen
import com.example.fithub.ui.settings.SettingsScreen
import com.example.fithub.ui.theme.FitHubTheme

object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val HOME = "home"
    const val EXERCISES = "exercises"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val TRACKING = "tracking"
}

private data class Tab(val route: String, @StringRes val label: Int, val icon: ImageVector)

private val TABS = listOf(
    Tab(Routes.HOME, R.string.tab_home, Icons.Default.Home),
    Tab(Routes.EXERCISES, R.string.tab_exercises, Icons.Default.FitnessCenter),
    Tab(Routes.HISTORY, R.string.tab_history, Icons.Default.History),
    Tab(Routes.SETTINGS, R.string.tab_settings, Icons.Default.Settings)
)

class MainActivity : AppCompatActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* result handled by NotificationHelper checks */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            val settings by ServiceLocator.settings.settings.collectAsState()
            FitHubTheme(darkTheme = settings.darkMode) {
                FitHubRoot()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FitHubRoot() {
    val navController = rememberNavController()
    val startDestination = remember {
        if (ServiceLocator.session.current != null) Routes.HOME else Routes.LOGIN
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val isOnline by ServiceLocator.network.isOnline.collectAsState()

    val titleRes: Int? = when (route) {
        Routes.HOME -> R.string.app_name
        Routes.EXERCISES -> R.string.title_exercises
        Routes.HISTORY -> R.string.title_history
        Routes.SETTINGS -> R.string.title_settings
        Routes.TRACKING -> R.string.title_tracking
        else -> null
    }
    val showTabs = TABS.any { it.route == route }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (titleRes != null) {
                TopAppBar(
                    title = { Text(stringResource(titleRes), fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        },
        bottomBar = {
            if (showTabs) {
                NavigationBar {
                    TABS.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = { navController.navigateToTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.label)) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (!isOnline && route != null) OfflineBanner()

            NavHost(navController = navController, startDestination = startDestination) {
                composable(Routes.LOGIN) {
                    LoginScreen(
                        onLoginSuccess = { navController.goHomeClearing() },
                        onNavigateToRegister = { navController.navigate(Routes.REGISTER) }
                    )
                }
                composable(Routes.REGISTER) {
                    RegisterScreen(
                        onRegisterSuccess = { navController.goHomeClearing() },
                        onNavigateToLogin = { navController.popBackStack() }
                    )
                }
                composable(Routes.HOME) {
                    DashboardScreen(onStartActivity = { navController.navigate(Routes.TRACKING) })
                }
                composable(Routes.EXERCISES) { ExercisesScreen() }
                composable(Routes.HISTORY) { HistoryScreen() }
                composable(Routes.SETTINGS) {
                    SettingsScreen(onLoggedOut = {
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(0) { inclusive = true }
                        }
                    })
                }
                composable(Routes.TRACKING) {
                    TrackingScreen(onFinished = { navController.popBackStack() })
                }
            }
        }
    }
}

private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavHostController.goHomeClearing() {
    navigate(Routes.HOME) {
        popUpTo(0) { inclusive = true }
    }
}
