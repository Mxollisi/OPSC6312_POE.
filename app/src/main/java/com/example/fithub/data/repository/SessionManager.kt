package com.example.fithub.data.repository

import android.content.Context
import com.example.fithub.data.model.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Remembers who is signed in on this device (online or offline login). */
class SessionManager(context: Context) {

    private val prefs = context.getSharedPreferences("fithub_session", Context.MODE_PRIVATE)

    private val _session = MutableStateFlow(load())
    val session: StateFlow<Session?> = _session

    val current: Session? get() = _session.value

    fun save(session: Session) {
        prefs.edit()
            .putString(KEY_UID, session.uid)
            .putString(KEY_EMAIL, session.email)
            .putString(KEY_NAME, session.displayName)
            .putString(KEY_PROVIDER, session.provider)
            .putBoolean(KEY_OFFLINE, session.offline)
            .apply()
        _session.value = session
    }

    fun updateDisplayName(name: String) {
        val s = _session.value ?: return
        save(s.copy(displayName = name))
    }

    fun clear() {
        prefs.edit().clear().apply()
        _session.value = null
    }

    private fun load(): Session? {
        val uid = prefs.getString(KEY_UID, null) ?: return null
        return Session(
            uid = uid,
            email = prefs.getString(KEY_EMAIL, "").orEmpty(),
            displayName = prefs.getString(KEY_NAME, "").orEmpty(),
            provider = prefs.getString(KEY_PROVIDER, Session.PROVIDER_PASSWORD).orEmpty(),
            offline = prefs.getBoolean(KEY_OFFLINE, false)
        )
    }

    private companion object {
        const val KEY_UID = "uid"
        const val KEY_EMAIL = "email"
        const val KEY_NAME = "name"
        const val KEY_PROVIDER = "provider"
        const val KEY_OFFLINE = "offline"
    }
}
