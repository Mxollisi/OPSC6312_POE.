package com.example.fithub.data.repository

import com.example.fithub.data.local.FitHubDatabase
import com.example.fithub.security.PasswordHasher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Stores salted password hashes (never the password) so users can log in offline. */
class LocalCredentialStore(private val db: FitHubDatabase) {

    suspend fun save(email: String, uid: String, displayName: String, password: String) =
        withContext(Dispatchers.Default) {
            val salt = PasswordHasher.generateSalt()
            val hash = PasswordHasher.hash(password, salt)
            withContext(Dispatchers.IO) {
                db.upsertLocalUser(FitHubDatabase.LocalUser(email, uid, displayName, hash, salt))
            }
        }

    /** Returns the stored user if the password matches the saved hash, otherwise null. */
    suspend fun verify(email: String, password: String): FitHubDatabase.LocalUser? =
        withContext(Dispatchers.Default) {
            val user = withContext(Dispatchers.IO) { db.getLocalUser(email) } ?: return@withContext null
            if (PasswordHasher.verify(password, user.salt, user.passwordHash)) user else null
        }

    suspend fun updateName(uid: String, name: String) = withContext(Dispatchers.IO) {
        db.updateLocalUserName(uid, name)
    }
}
