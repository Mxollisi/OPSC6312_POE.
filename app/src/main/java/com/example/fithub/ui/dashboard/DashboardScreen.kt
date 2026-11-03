package com.example.fithub.ui.dashboard

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fithub.R
import com.example.fithub.ServiceLocator
import com.example.fithub.data.LocationManager
import com.example.fithub.data.StepCounterManager
import com.example.fithub.data.WeatherCondition
import com.example.fithub.data.WeatherInfo
import com.example.fithub.data.WeatherRepository
import com.example.fithub.data.repository.WorkoutStats
import com.example.fithub.notifications.NotificationHelper
import com.example.fithub.ui.components.MetricCard
import com.example.fithub.ui.theme.FitAmber
import com.example.fithub.ui.theme.FitBlue
import com.example.fithub.ui.theme.FitOrange
import com.example.fithub.ui.theme.FitRed
import com.example.fithub.util.FitnessCalculator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {
    private val _stats = MutableStateFlow(WorkoutStats())
    val stats: StateFlow<WorkoutStats> = _stats

    init {
        viewModelScope.launch {
            ServiceLocator.workouts.version.collect {
                val uid = ServiceLocator.session.current?.uid ?: return@collect
                _stats.value = try {
                    ServiceLocator.workouts.getStats(uid)
                } catch (e: Exception) {
                    WorkoutStats()
                }
            }
        }
    }
}

@Composable
fun weatherLabel(info: WeatherInfo): String {
    val condition = when (info.condition) {
        WeatherCondition.CLEAR -> stringResource(R.string.weather_clear)
        WeatherCondition.CLOUDY -> stringResource(R.string.weather_cloudy)
        WeatherCondition.FOG -> stringResource(R.string.weather_fog)
        WeatherCondition.RAIN -> stringResource(R.string.weather_rain)
        WeatherCondition.SNOW -> stringResource(R.string.weather_snow)
        WeatherCondition.STORM -> stringResource(R.string.weather_storm)
    }
    return "${info.temperatureC}°C • $condition"
}

@Composable
fun DashboardScreen(
    onStartActivity: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val context = LocalContext.current
    val stats by viewModel.stats.collectAsState()
    val session by ServiceLocator.session.session.collectAsState()
    val settings by ServiceLocator.settings.settings.collectAsState()

    val stepCounterManager = remember { StepCounterManager(context) }
    var liveSteps by remember { mutableIntStateOf(0) }
    var weather by remember { mutableStateOf<WeatherInfo?>(null) }
    var weatherLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val loc = LocationManager.lastKnownLocation(context)
        weather = WeatherRepository.fetchCurrentWeather(loc?.latitude ?: -26.2041, loc?.longitude ?: 28.0473)
        weatherLoaded = true
    }

    DisposableEffect(Unit) {
        stepCounterManager.start()
        onDispose { stepCounterManager.stop() }
    }

    LaunchedEffect(Unit) {
        while (true) {
            liveSteps = stepCounterManager.steps
            delay(1000L)
        }
    }

    val totalSteps = liveSteps + stats.todaySteps
    val goal = settings.dailyStepGoal
    val distanceMeters = stats.todayDistanceMeters + FitnessCalculator.stepsToKm(liveSteps) * 1000.0
    val activeMinutes = (stats.todayDurationSec / 60) + (liveSteps / 100)
    val totalPoints = stats.totalPoints + FitnessCalculator.stepPoints(liveSteps)

    // Real-time alert when the daily goal is reached (once per day).
    LaunchedEffect(totalSteps >= goal) {
        if (totalSteps >= goal && goal > 0 && ServiceLocator.settings.shouldNotifyGoalToday()) {
            NotificationHelper.showGoalReached(context, goal)
        }
    }

    val permissionsToRequest = remember {
        buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(Manifest.permission.ACTIVITY_RECOGNITION)
        }.toTypedArray()
    }
    val permissionDeniedMsg = stringResource(R.string.error_location_permission)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) onStartActivity()
        else Toast.makeText(context, permissionDeniedMsg, Toast.LENGTH_LONG).show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.welcome_name, session?.displayName?.substringBefore(' ').orEmpty()),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Text(
            text = stringResource(R.string.todays_progress),
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(
                title = stringResource(R.string.metric_steps),
                value = "%,d".format(totalSteps),
                subtitle = stringResource(R.string.metric_steps_goal, goal),
                icon = Icons.Default.DirectionsWalk,
                iconTint = MaterialTheme.colorScheme.primary,
                isPrimary = true
            )
            LinearProgressIndicator(
                progress = { (totalSteps.toFloat() / goal.coerceAtLeast(1)).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )

            if (!stepCounterManager.isAvailable) {
                Text(
                    text = stringResource(R.string.step_sensor_missing),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                MetricCard(
                    title = stringResource(R.string.metric_distance),
                    value = FitnessCalculator.formatKm(distanceMeters),
                    icon = Icons.Default.Place,
                    iconTint = FitBlue,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = stringResource(R.string.metric_active_time),
                    value = stringResource(R.string.minutes_short, activeMinutes.toInt()),
                    icon = Icons.Default.Schedule,
                    iconTint = FitOrange,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                MetricCard(
                    title = stringResource(R.string.metric_points),
                    value = stringResource(R.string.points_short, totalPoints),
                    icon = Icons.Default.Star,
                    iconTint = FitRed,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = stringResource(R.string.metric_streak),
                    value = pluralStringResourceSafe(R.plurals.streak_days, stats.streakDays),
                    icon = Icons.Default.LocalFireDepartment,
                    iconTint = FitOrange,
                    modifier = Modifier.weight(1f)
                )
            }

            MetricCard(
                title = stringResource(R.string.metric_weather),
                value = when (val w = weather) {
                    null -> stringResource(
                        if (weatherLoaded) R.string.weather_unavailable else R.string.weather_loading
                    )
                    else -> weatherLabel(w)
                },
                icon = Icons.Default.WbSunny,
                iconTint = FitAmber
            )

            if (stats.pendingSync > 0) {
                MetricCard(
                    title = stringResource(R.string.metric_pending_sync),
                    value = stats.pendingSync.toString(),
                    subtitle = stringResource(R.string.pending_sync_hint),
                    icon = Icons.Default.CloudUpload,
                    iconTint = FitBlue
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (LocationManager.hasPermission(context)) onStartActivity()
                else permissionLauncher.launch(permissionsToRequest)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
            Text(stringResource(R.string.btn_start_activity), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
fun pluralStringResourceSafe(id: Int, count: Int): String {
    val resources = LocalContext.current.resources
    return resources.getQuantityString(id, count, count)
}
