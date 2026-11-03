package com.example.fithub.ui.settings

import android.app.Application
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fithub.R
import com.example.fithub.ServiceLocator
import com.example.fithub.data.model.Session
import com.example.fithub.notifications.PushManager
import com.example.fithub.ui.auth.AuthViewModel
import com.example.fithub.ui.auth.FitnessLevelDropdown
import com.example.fithub.ui.components.MessageText
import com.example.fithub.util.Validators
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** Languages offered in the app: English plus two South African languages. */
val SUPPORTED_LANGUAGES = listOf("en" to R.string.lang_english, "zu" to R.string.lang_zulu, "af" to R.string.lang_afrikaans)

data class SettingsMessage(@StringRes val res: Int, val isError: Boolean)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val _message = MutableStateFlow<SettingsMessage?>(null)
    val message: StateFlow<SettingsMessage?> = _message

    fun clearMessage() { _message.value = null }

    fun saveProfile(name: String, ageText: String, fitnessLevel: String, goalText: String) {
        val cleanName = name.trim()
        val age = Validators.parseAge(ageText)
        val goal = Validators.parseStepGoal(goalText)
        when {
            !Validators.isValidName(cleanName) -> { _message.value = SettingsMessage(R.string.error_invalid_name, true); return }
            age == null -> { _message.value = SettingsMessage(R.string.error_invalid_age, true); return }
            goal == null -> { _message.value = SettingsMessage(R.string.error_invalid_goal, true); return }
        }

        ServiceLocator.settings.update { it.copy(age = age, fitnessLevel = fitnessLevel, dailyStepGoal = goal ?: it.dailyStepGoal) }
        ServiceLocator.session.updateDisplayName(cleanName)
        val session = ServiceLocator.session.current ?: return
        viewModelScope.launch {
            try { ServiceLocator.credentials.updateName(session.uid, cleanName) } catch (e: Exception) { }
        }
        // Saved locally right away; Firestore + Firebase Auth update when online.
        ServiceLocator.settings.syncToCloud(session.uid, cleanName, AuthViewModel.currentLanguageTag())
        FirebaseAuth.getInstance().currentUser?.updateProfile(
            UserProfileChangeRequest.Builder().setDisplayName(cleanName).build()
        )
        _message.value = SettingsMessage(R.string.settings_saved, false)
    }

    fun setNotifications(enabled: Boolean) {
        ServiceLocator.settings.update { it.copy(notificationsEnabled = enabled) }
        PushManager.register(enabled)
        pushSettings()
    }

    fun setDarkMode(enabled: Boolean) {
        ServiceLocator.settings.update { it.copy(darkMode = enabled) }
        pushSettings()
    }

    fun setLanguage(tag: String) {
        // AppCompat saves the choice and recreates the screen in the new language.
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
        val s = ServiceLocator.session.current ?: return
        ServiceLocator.settings.syncToCloud(s.uid, s.displayName, tag)
    }

    fun sendPasswordReset() {
        val email = ServiceLocator.session.current?.email ?: return
        viewModelScope.launch {
            _message.value = try {
                FirebaseAuth.getInstance().sendPasswordResetEmail(email).await()
                SettingsMessage(R.string.info_reset_sent, false)
            } catch (e: Exception) {
                SettingsMessage(AuthViewModel.mapAuthError(e), true)
            }
        }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                CredentialManager.create(getApplication()).clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) { }
            PushManager.register(false)
            FirebaseAuth.getInstance().signOut()
            ServiceLocator.session.clear()
            onDone()
        }
    }

    private fun pushSettings() {
        val s = ServiceLocator.session.current ?: return
        ServiceLocator.settings.syncToCloud(s.uid, s.displayName, AuthViewModel.currentLanguageTag())
    }
}

@Composable
fun SettingsScreen(onLoggedOut: () -> Unit, viewModel: SettingsViewModel = viewModel()) {
    val settings by ServiceLocator.settings.settings.collectAsState()
    val session by ServiceLocator.session.session.collectAsState()
    val message by viewModel.message.collectAsState()
    val currentLanguage = remember { AuthViewModel.currentLanguageTag().substringBefore('-') }

    var name by rememberSaveable { mutableStateOf(session?.displayName.orEmpty()) }
    var age by rememberSaveable { mutableStateOf(settings.age?.toString().orEmpty()) }
    var level by rememberSaveable { mutableStateOf(settings.fitnessLevel) }
    var goal by rememberSaveable { mutableStateOf(settings.dailyStepGoal.toString()) }
    var showLogout by remember { mutableStateOf(false) }

    if (showLogout) {
        AlertDialog(
            onDismissRequest = { showLogout = false },
            title = { Text(stringResource(R.string.dialog_logout_title)) },
            text = { Text(stringResource(R.string.dialog_logout_body)) },
            confirmButton = {
                TextButton(onClick = { showLogout = false; viewModel.logout(onLoggedOut) }) {
                    Text(stringResource(R.string.btn_logout), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showLogout = false }) { Text(stringResource(R.string.btn_cancel)) } }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ---------------- Profile
        SettingsSection(stringResource(R.string.section_profile), Icons.Default.Person) {
            Text(session?.email.orEmpty(), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                stringResource(
                    if (session?.provider == Session.PROVIDER_GOOGLE) R.string.signed_in_google else R.string.signed_in_email
                ),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(50); viewModel.clearMessage() },
                label = { Text(stringResource(R.string.label_full_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            OutlinedTextField(
                value = age,
                onValueChange = { age = Validators.digitsOnly(it, 3); viewModel.clearMessage() },
                label = { Text(stringResource(R.string.label_age)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            FitnessLevelDropdown(selected = level, onSelected = { level = it })
            OutlinedTextField(
                value = goal,
                onValueChange = { goal = Validators.digitsOnly(it, 6); viewModel.clearMessage() },
                label = { Text(stringResource(R.string.label_step_goal)) },
                supportingText = { Text(stringResource(R.string.hint_step_goal)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            message?.let { MessageText(stringResource(it.res), it.isError) }
            Button(
                onClick = { viewModel.saveProfile(name, age, level, goal) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) { Text(stringResource(R.string.btn_save)) }
        }

        // ---------------- Preferences
        SettingsSection(stringResource(R.string.section_preferences), Icons.Default.Tune) {
            SwitchRow(
                title = stringResource(R.string.pref_notifications),
                subtitle = stringResource(R.string.pref_notifications_desc),
                checked = settings.notificationsEnabled,
                onChange = viewModel::setNotifications
            )
            SwitchRow(
                title = stringResource(R.string.pref_dark_mode),
                subtitle = null,
                checked = settings.darkMode,
                onChange = viewModel::setDarkMode
            )
        }

        // ---------------- Language
        SettingsSection(stringResource(R.string.section_language), Icons.Default.Language) {
            SUPPORTED_LANGUAGES.forEach { (tag, labelRes) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = currentLanguage == tag,
                            onClick = { if (currentLanguage != tag) viewModel.setLanguage(tag) },
                            role = Role.RadioButton
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = currentLanguage == tag, onClick = null)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(labelRes))
                }
            }
        }

        // ---------------- Account
        SettingsSection(stringResource(R.string.section_account), Icons.Default.ManageAccounts) {
            if (session?.provider == Session.PROVIDER_PASSWORD) {
                OutlinedButton(onClick = viewModel::sendPasswordReset, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.LockReset, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.btn_change_password))
                }
            }
            OutlinedButton(
                onClick = { showLogout = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.btn_logout))
            }
        }

        Text(
            stringResource(R.string.app_version, "2.0"),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            content()
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
