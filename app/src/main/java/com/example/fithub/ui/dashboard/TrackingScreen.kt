package com.example.fithub.ui.dashboard

import android.location.Location
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fithub.R
import com.example.fithub.ServiceLocator
import com.example.fithub.data.LocationManager
import com.example.fithub.data.StepCounterManager
import com.example.fithub.data.model.Workout
import com.example.fithub.notifications.NotificationHelper
import com.example.fithub.sync.SyncScheduler
import com.example.fithub.ui.theme.FitBlue
import com.example.fithub.ui.theme.FitRed
import com.example.fithub.util.FitnessCalculator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun workoutTypeLabel(type: String): String = when (type) {
    Workout.TYPE_WALK -> stringResource(R.string.type_walk)
    Workout.TYPE_CYCLE -> stringResource(R.string.type_cycle)
    else -> stringResource(R.string.type_run)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackingScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    // Guarantees we only leave the screen once, even if Stop and Discard race.
    var finished by remember { mutableStateOf(false) }
    val finishOnce: () -> Unit = {
        if (!finished) {
            finished = true
            onFinished()
        }
    }
    val scope = rememberCoroutineScope()

    val startTime = rememberSaveable { System.currentTimeMillis() }
    var secondsElapsed by rememberSaveable { mutableLongStateOf(0L) }
    var distanceMeters by rememberSaveable { mutableDoubleStateOf(0.0) }
    var type by rememberSaveable { mutableStateOf(Workout.TYPE_RUN) }
    var currentLocation by remember { mutableStateOf<Location?>(null) }
    var lastLocation by remember { mutableStateOf<Location?>(null) }
    var gpsActive by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    val stepCounter = remember { StepCounterManager(context) }

    DisposableEffect(Unit) {
        val locationManager = LocationManager(context) { newLoc ->
            currentLocation = newLoc
            // Only count accurate fixes, and ignore GPS "jumps" that are physically impossible.
            if (newLoc.accuracy <= 50f) {
                val prev = lastLocation
                if (prev != null) {
                    val delta = prev.distanceTo(newLoc)
                    if (delta in 1f..200f) distanceMeters += delta
                }
                lastLocation = newLoc
            }
        }
        gpsActive = locationManager.startLocationUpdates()
        stepCounter.start()
        onDispose {
            locationManager.stopLocationUpdates()
            stepCounter.stop()
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            secondsElapsed++
        }
    }

    BackHandler { showDiscardDialog = true }

    val tooShortMsg = stringResource(R.string.workout_too_short)
    val saveFailedMsg = stringResource(R.string.error_save_failed)

    fun saveAndFinish() {
        if (saving) return
        val session = ServiceLocator.session.current
        if (secondsElapsed < FitnessCalculator.MIN_WORKOUT_SECONDS || session == null) {
            Toast.makeText(context, tooShortMsg, Toast.LENGTH_SHORT).show()
            finishOnce()
            return
        }
        saving = true
        val steps = if (stepCounter.steps > 0) stepCounter.steps else FitnessCalculator.estimateSteps(distanceMeters)
        val points = FitnessCalculator.points(distanceMeters, secondsElapsed)
        val workout = Workout(
            userId = session.uid,
            type = type,
            startTime = startTime,
            durationSec = secondsElapsed,
            distanceMeters = distanceMeters,
            steps = steps,
            points = points
        )
        scope.launch {
            try {
                ServiceLocator.workouts.save(workout)   // saved offline first
                NotificationHelper.showWorkoutSaved(context, points)
                SyncScheduler.syncNow(context)           // uploads now, or when back online
            } catch (e: Exception) {
                Toast.makeText(context, saveFailedMsg, Toast.LENGTH_LONG).show()
            }
            saving = false
            finishOnce()
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.dialog_discard_title)) },
            text = { Text(stringResource(R.string.dialog_discard_body)) },
            confirmButton = {
                TextButton(onClick = { showDiscardDialog = false; finishOnce() }) {
                    Text(stringResource(R.string.btn_discard), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(stringResource(R.string.btn_keep_going))
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Activity type
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Workout.TYPES.forEach { t ->
                    FilterChip(
                        selected = type == t,
                        onClick = { type = t },
                        label = { Text(workoutTypeLabel(t)) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.duration).uppercase(),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = FitnessCalculator.formatDuration(secondsElapsed),
                fontSize = 46.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                StatTile(stringResource(R.string.metric_distance), FitnessCalculator.formatKm(distanceMeters), Modifier.weight(1f))
                StatTile(
                    stringResource(R.string.metric_points),
                    FitnessCalculator.points(distanceMeters, secondsElapsed).toString(),
                    Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // GPS status card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.gps_status),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    val loc = currentLocation
                    val waiting = stringResource(R.string.gps_waiting)
                    Text(
                        text = stringResource(
                            R.string.latitude,
                            loc?.let { String.format(Locale.US, "%.5f", it.latitude) } ?: waiting
                        ),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = FitBlue
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(
                            R.string.longitude,
                            loc?.let { String.format(Locale.US, "%.5f", it.longitude) } ?: waiting
                        ),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = FitBlue
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    val statusColor = if (gpsActive) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(statusColor, shape = CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(if (gpsActive) R.string.gps_active else R.string.gps_inactive),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = statusColor
                        )
                    }
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            OutlinedIconButton(
                onClick = { showDiscardDialog = true },
                modifier = Modifier.size(56.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.btn_discard))
            }
            IconButton(
                onClick = { saveAndFinish() },
                enabled = !saving,
                modifier = Modifier
                    .size(80.dp)
                    .background(FitRed, shape = CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = stringResource(R.string.btn_stop_save),
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}
