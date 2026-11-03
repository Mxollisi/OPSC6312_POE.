@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.fithub.ui.exercises

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.fithub.R
import com.example.fithub.ServiceLocator
import com.example.fithub.data.model.Exercise
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ExercisesUiState(
    val isLoading: Boolean = true,
    val all: List<Exercise> = emptyList(),
    val fromCache: Boolean = false,
    val failed: Boolean = false,
    val query: String = "",
    val category: String? = null
) {
    val categories: List<String> get() = all.map { it.category }.distinct().sorted()
    val filtered: List<Exercise>
        get() = all.filter { e ->
            (category == null || e.category == category) &&
                (query.isBlank() ||
                    e.name.contains(query.trim(), ignoreCase = true) ||
                    e.muscles.contains(query.trim(), ignoreCase = true))
        }
}

class ExercisesViewModel : ViewModel() {
    private val _state = MutableStateFlow(ExercisesUiState())
    val state: StateFlow<ExercisesUiState> = _state

    init { load(forceRefresh = false) }

    fun load(forceRefresh: Boolean) {
        _state.value = _state.value.copy(isLoading = true)
        viewModelScope.launch {
            val result = ServiceLocator.exercises.load(forceRefresh)
            _state.value = _state.value.copy(
                isLoading = false,
                all = result.exercises,
                fromCache = result.fromCache,
                failed = result.failed
            )
        }
    }

    fun setQuery(q: String) { _state.value = _state.value.copy(query = q.take(50)) }

    fun setCategory(c: String?) { _state.value = _state.value.copy(category = c) }
}

@Composable
fun ExercisesScreen(viewModel: ExercisesViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    var selected by remember { mutableStateOf<Exercise?>(null) }

    selected?.let { ExerciseDetailDialog(it) { selected = null } }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                placeholder = { Text(stringResource(R.string.search_exercises)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.cd_clear))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { viewModel.load(forceRefresh = true) }, enabled = !state.isLoading) {
                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.cd_refresh))
            }
        }

        if (state.categories.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = state.category == null,
                        onClick = { viewModel.setCategory(null) },
                        label = { Text(stringResource(R.string.filter_all)) }
                    )
                }
                items(state.categories) { c ->
                    FilterChip(
                        selected = state.category == c,
                        onClick = { viewModel.setCategory(if (state.category == c) null else c) },
                        label = { Text(c) }
                    )
                }
            }
        }

        if (state.fromCache && state.all.isNotEmpty()) {
            Text(
                text = stringResource(if (state.failed) R.string.exercises_offline_cache else R.string.exercises_cached),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        when {
            state.isLoading && state.all.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.all.isEmpty() -> EmptyMessage(
                stringResource(R.string.exercises_load_failed),
                actionLabel = stringResource(R.string.btn_retry),
                onAction = { viewModel.load(forceRefresh = true) }
            )
            state.filtered.isEmpty() -> EmptyMessage(stringResource(R.string.exercises_no_match))
            else -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        stringResource(R.string.exercises_count, state.filtered.size),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(state.filtered, key = { it.id }) { exercise ->
                    ExerciseRow(exercise) { selected = exercise }
                }
                item {
                    Text(
                        stringResource(R.string.wger_credit),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ExerciseRow(exercise: Exercise, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ExerciseThumb(exercise, 56)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(exercise.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(exercise.category, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                if (exercise.muscles.isNotBlank()) {
                    Text(
                        exercise.muscles,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ExerciseThumb(exercise: Exercise, sizeDp: Int) {
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxSize()) {}
        if (exercise.imageUrl != null) {
            AsyncImage(
                model = exercise.imageUrl,
                contentDescription = exercise.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(Icons.Default.FitnessCenter, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ExerciseDetailDialog(exercise: Exercise, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_close)) } },
        title = { Text(exercise.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (exercise.imageUrl != null) {
                    AsyncImage(
                        model = exercise.imageUrl,
                        contentDescription = exercise.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                }
                DetailLine(stringResource(R.string.label_category), exercise.category)
                if (exercise.muscles.isNotBlank()) DetailLine(stringResource(R.string.label_muscles), exercise.muscles)
                if (exercise.equipment.isNotBlank()) DetailLine(stringResource(R.string.label_equipment), exercise.equipment)
                Spacer(Modifier.height(8.dp))
                Text(exercise.description.ifBlank { stringResource(R.string.no_description) })
            }
        }
    )
}

@Composable
private fun DetailLine(label: String, value: String) {
    Text("$label: $value", fontSize = 14.sp, modifier = Modifier.padding(vertical = 2.dp))
}

@Composable
fun EmptyMessage(text: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(12.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}
