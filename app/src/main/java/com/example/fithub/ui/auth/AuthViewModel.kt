package com.example.fithub.ui.auth

import android.app.Application
import android.content.Context
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.fithub.R
import com.example.fithub.ServiceLocator
import com.example.fithub.data.model.Session
import com.example.fithub.notifications.PushManager
import com.example.fithub.sync.SyncScheduler
import com.example.fithub.util.Validators
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

data class AuthUiState(
    val isLoading: Boolean = false,
    @StringRes val errorRes: Int? = null,
    @StringRes val infoRes: Int? = null,
    val isSuccess: Boolean = false
)

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val session = ServiceLocator.session
    private val settings = ServiceLocator.settings
    private val credentials = ServiceLocator.credentials

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState

    private fun setError(@StringRes res: Int) {
        _uiState.value = _uiState.value.copy(isLoading = false, errorRes = res, infoRes = null)
    }

    // ------------------------------------------------------------------ login

    fun login(email: String, password: String) {
        val cleanEmail = email.trim()
        when {
            cleanEmail.isEmpty() || password.isEmpty() -> return setError(R.string.error_fill_all)
            !Validators.isValidEmail(cleanEmail) -> return setError(R.string.error_invalid_email)
        }

        viewModelScope.launch {
            _uiState.value = AuthUiState(isLoading = true)
            try {
                val user = auth.signInWithEmailAndPassword(cleanEmail, password).await().user
                    ?: return@launch setError(R.string.error_generic)
                val name = user.displayName?.takeIf { it.isNotBlank() } ?: cleanEmail.substringBefore('@')
                // Keep a salted hash (not the password) so the next login can work offline.
                credentials.save(cleanEmail, user.uid, name, password)
                onAuthenticated(user.uid, cleanEmail, name, Session.PROVIDER_PASSWORD, offline = false)
            } catch (e: FirebaseNetworkException) {
                // No internet: fall back to the locally stored hash.
                val local = credentials.verify(cleanEmail, password)
                if (local != null) {
                    onAuthenticated(local.uid, cleanEmail, local.displayName, Session.PROVIDER_PASSWORD, offline = true)
                } else {
                    setError(R.string.error_offline_no_account)
                }
            } catch (e: Exception) {
                setError(mapAuthError(e))
            }
        }
    }

    // --------------------------------------------------------------- register

    fun register(
        fullName: String,
        email: String,
        password: String,
        confirmPassword: String,
        age: String,
        fitnessLevel: String
    ) {
        val cleanEmail = email.trim()
        val cleanName = fullName.trim()
        val parsedAge = Validators.parseAge(age)
        when {
            cleanName.isEmpty() || cleanEmail.isEmpty() || password.isEmpty() || age.isBlank() ->
                return setError(R.string.error_fill_all)
            !Validators.isValidName(cleanName) -> return setError(R.string.error_invalid_name)
            !Validators.isValidEmail(cleanEmail) -> return setError(R.string.error_invalid_email)
            !Validators.isStrongPassword(password) -> return setError(R.string.error_weak_password)
            password != confirmPassword -> return setError(R.string.error_password_mismatch)
            parsedAge == null -> return setError(R.string.error_invalid_age)
        }

        viewModelScope.launch {
            _uiState.value = AuthUiState(isLoading = true)
            try {
                val user = auth.createUserWithEmailAndPassword(cleanEmail, password).await().user
                    ?: return@launch setError(R.string.error_generic)

                user.updateProfile(
                    UserProfileChangeRequest.Builder().setDisplayName(cleanName).build()
                ).await()

                settings.update { it.copy(age = parsedAge, fitnessLevel = fitnessLevel) }

                // NOTE: the password is NOT stored in Firestore. Firebase Auth keeps a hash.
                val userData = hashMapOf<String, Any?>(
                    "uid" to user.uid,
                    "fullName" to cleanName,
                    "email" to cleanEmail,
                    "age" to parsedAge,
                    "fitnessLevel" to fitnessLevel,
                    "authProvider" to Session.PROVIDER_PASSWORD,
                    "preferredLanguage" to currentLanguageTag(),
                    "dailyStepGoal" to settings.current.dailyStepGoal,
                    "notificationsEnabled" to true,
                    "points" to 0,
                    "currentStreak" to 0,
                    "totalWorkouts" to 0,
                    "createdAt" to Timestamp.now()
                )
                withTimeoutOrNull(10_000L) {
                    firestore.collection("users").document(user.uid).set(userData).await()
                }

                credentials.save(cleanEmail, user.uid, cleanName, password)
                onAuthenticated(user.uid, cleanEmail, cleanName, Session.PROVIDER_PASSWORD, offline = false)
            } catch (e: Exception) {
                setError(mapAuthError(e))
            }
        }
    }

    // ------------------------------------------------- single sign-on (Google)

    /** [activityContext] must be an Activity context so the Google account picker can open. */
    fun signInWithGoogle(activityContext: Context) {
        val webClientId = googleWebClientId(activityContext)
        if (webClientId.isBlank()) return setError(R.string.error_sso_not_configured)

        viewModelScope.launch {
            _uiState.value = AuthUiState(isLoading = true)
            try {
                val option = GetSignInWithGoogleOption.Builder(webClientId).build()
                val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
                val result = CredentialManager.create(activityContext).getCredential(activityContext, request)
                val credential = result.credential

                if (credential !is CustomCredential ||
                    credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) return@launch setError(R.string.error_sso_failed)

                val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authResult = auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
                val user = authResult.user ?: return@launch setError(R.string.error_sso_failed)
                val isNew = authResult.additionalUserInfo?.isNewUser == true

                saveGoogleUserDoc(user, isNew)
                val email = user.email.orEmpty()
                val name = user.displayName?.takeIf { it.isNotBlank() } ?: email.substringBefore('@')
                onAuthenticated(user.uid, email, name, Session.PROVIDER_GOOGLE, offline = false)
            } catch (e: GetCredentialCancellationException) {
                _uiState.value = AuthUiState() // user closed the picker — not an error
            } catch (e: NoCredentialException) {
                setError(R.string.error_sso_no_account)
            } catch (e: GetCredentialException) {
                setError(R.string.error_sso_failed)
            } catch (e: Exception) {
                setError(mapAuthError(e))
            }
        }
    }

    private suspend fun saveGoogleUserDoc(user: FirebaseUser, isNew: Boolean) {
        val data = hashMapOf<String, Any?>(
            "uid" to user.uid,
            "fullName" to (user.displayName ?: ""),
            "email" to (user.email ?: ""),
            "authProvider" to Session.PROVIDER_GOOGLE,
            "lastLoginAt" to Timestamp.now()
        )
        if (isNew) {
            data["createdAt"] = Timestamp.now()
            data["points"] = 0
            data["currentStreak"] = 0
            data["totalWorkouts"] = 0
            data["fitnessLevel"] = settings.current.fitnessLevel
            data["dailyStepGoal"] = settings.current.dailyStepGoal
            data["notificationsEnabled"] = true
            data["preferredLanguage"] = currentLanguageTag()
        }
        withTimeoutOrNull(10_000L) {
            firestore.collection("users").document(user.uid).set(data, SetOptions.merge()).await()
        }
    }

    // --------------------------------------------------------- password reset

    fun sendPasswordReset(email: String) {
        val cleanEmail = email.trim()
        if (!Validators.isValidEmail(cleanEmail)) return setError(R.string.error_reset_need_email)
        viewModelScope.launch {
            _uiState.value = AuthUiState(isLoading = true)
            try {
                auth.sendPasswordResetEmail(cleanEmail).await()
                _uiState.value = AuthUiState(infoRes = R.string.info_reset_sent)
            } catch (e: Exception) {
                setError(mapAuthError(e))
            }
        }
    }

    // ----------------------------------------------------------------- shared

    private fun onAuthenticated(uid: String, email: String, name: String, provider: String, offline: Boolean) {
        session.save(Session(uid, email, name, provider, offline))
        val app = getApplication<Application>()
        SyncScheduler.schedulePeriodic(app)
        if (!offline) {
            PushManager.register(settings.current.notificationsEnabled)
            SyncScheduler.syncNow(app)
            settings.syncToCloud(uid, name, currentLanguageTag())
        }
        _uiState.value = AuthUiState(isSuccess = true)
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(errorRes = null, infoRes = null)
    }

    fun resetSuccess() {
        _uiState.value = _uiState.value.copy(isSuccess = false)
    }

    companion object {
        @StringRes
        fun mapAuthError(e: Exception): Int = when (e) {
            is FirebaseAuthWeakPasswordException -> R.string.error_weak_password
            is FirebaseAuthUserCollisionException -> R.string.error_email_in_use
            is FirebaseAuthInvalidUserException -> R.string.error_invalid_credentials
            is FirebaseAuthInvalidCredentialsException -> R.string.error_invalid_credentials
            is FirebaseTooManyRequestsException -> R.string.error_too_many_requests
            is FirebaseNetworkException -> R.string.error_network
            else -> R.string.error_generic
        }

        fun currentLanguageTag(): String =
            AppCompatDelegate.getApplicationLocales().toLanguageTags().ifBlank { "en" }

        /**
         * Uses the web client ID generated by google-services.json once Google sign-in is
         * enabled in Firebase. Falls back to the value in strings.xml.
         */
        fun googleWebClientId(context: Context): String {
            val generatedId = context.resources.getIdentifier(
                "default_web_client_id", "string", context.packageName
            )
            return if (generatedId != 0) context.getString(generatedId)
            else context.getString(R.string.google_web_client_id)
        }
    }
}
