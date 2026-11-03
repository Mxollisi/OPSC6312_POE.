package com.example.fithub.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
import com.example.fithub.data.model.Workout
import com.example.fithub.sync.SyncScheduler
import com.example.fithub.ui.dashboard.workoutTypeLabel
import com.example.fithub.ui.exercises.EmptyMessage
import com.example.fithub.ui.theme.FitOrange
import com.example.fithub.util.FitnessCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class HistoryViewModel : ViewModel() {
    private val _workouts = MutableStateFlow<List<Workout>?>(null)
    val workouts: StateFlow<List<Workout>?> = _workouts

    init {
        viewModelScope.launch {
            ServiceLocator.workouts.version.collect {
                val uid = ServiceLocator.session.current?.uid ?: return@collect
                _workouts.value = try {
                    ServiceLocator.workouts.getWorkouts(uid)
                } catch (e: Exception) {
                    emptyList()
                }
            }
        }
    }

    fun delete(w: Workout) {
        viewModelScope.launch { ServiceLocator.workouts.delete(w) }
    }
}

@Composable
fun HistoryScreen(viewModel: HistoryViewModel = viewModel()) {
    val context = LocalContext.current
    val workouts by viewModel.workouts.collectAsState()
    val isOnline by ServiceLocator.network.isOnline.collectAsState()
    val settings by ServiceLocator.settings.settings.collectAsState()
    val session by ServiceLocator.session.session.collectAsState()
    var toDelete by remember { mutableStateOf<Workout?>(null) }

    toDelete?.let { w ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(stringResource(R.string.dialog_delete_title)) },
            text = { Text(stringResource(R.string.dialog_delete_body)) },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(w); toDelete = null }) {
                    Text(stringResource(R.string.btn_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(stringResource(R.string.btn_cancel)) } }
        )
    }

    val list = workouts
    Column(Modifier.fillMaxSize()) {
        // Sync status card
        val pending = list?.count { !it.synced } ?: 0
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (pending == 0) stringResource(R.string.sync_all_done)
                        else stringResource(R.string.sync_pending_count, pending),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    val last = settings.lastSyncTime
                    Text(
                        text = when {
                            session?.offline == true -> stringResource(R.string.sync_offline_login)
                            !isOnline -> stringResource(R.string.sync_waiting_connection)
                            last > 0 -> stringResource(
                                R.string.sync_last,
                                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(last))
                            )
                            else -> stringResource(R.string.sync_never)
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                FilledTonalButton(
                    onClick = { SyncScheduler.syncNow(context) },
                    enabled = isOnline && pending > 0
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.btn_sync_now))
                }
            }
        }

        when {
            list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            list.isEmpty() -> EmptyMessage(stringResource(R.string.history_empty))
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(list, key = { it.localId }) { w ->
                    WorkoutRow(w, onDelete = { toDelete = w })
                }
            }
        }
    }
}

@Composable
private fun WorkoutRow(w: Workout, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(workoutTypeLabel(w.type), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(w.startTime)),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${FitnessCalculator.formatDuration(w.durationSec)} • ${FitnessCalculator.formatKm(w.distanceMeters)} • " +
                        stringResource(R.string.points_short, w.points),
                    fontSize = 13.sp
                )
            }
            Icon(
                imageVector = if (w.synced) Icons.Default.CloudDone else Icons.Default.CloudUpload,
                contentDescription = stringResource(if (w.synced) R.string.status_synced else R.string.status_pending),
                tint = if (w.synced) MaterialTheme.colorScheme.primary else FitOrange
            )
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.DeleteOutline, contentDescription = stringResource(R.string.btn_delete))
            }
        }
    }
}
