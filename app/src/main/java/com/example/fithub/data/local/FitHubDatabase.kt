package com.example.fithub.data.local

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.fithub.data.model.Exercise
import com.example.fithub.data.model.Workout

/**
 * Local SQLite database that powers Offline Mode.
 *
 *  workouts     – every activity is written here first; `synced` = 0 until uploaded to Firestore
 *  exercises    – cached copy of the wger API so the exercise library works offline
 *  local_users  – salted PBKDF2 password hashes so a returning user can log in offline
 */
class FitHubDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $T_WORKOUTS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id TEXT NOT NULL,
                type TEXT NOT NULL,
                start_time INTEGER NOT NULL,
                duration_sec INTEGER NOT NULL,
                distance_m REAL NOT NULL,
                steps INTEGER NOT NULL,
                points INTEGER NOT NULL,
                synced INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_workouts_user ON $T_WORKOUTS(user_id, synced)")

        db.execSQL(
            """
            CREATE TABLE $T_EXERCISES (
                id INTEGER PRIMARY KEY,
                name TEXT NOT NULL,
                category TEXT NOT NULL,
                description TEXT NOT NULL,
                muscles TEXT NOT NULL,
                equipment TEXT NOT NULL,
                image_url TEXT
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $T_LOCAL_USERS (
                email TEXT PRIMARY KEY,
                uid TEXT NOT NULL,
                display_name TEXT NOT NULL,
                password_hash TEXT NOT NULL,
                salt TEXT NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $T_WORKOUTS")
        db.execSQL("DROP TABLE IF EXISTS $T_EXERCISES")
        db.execSQL("DROP TABLE IF EXISTS $T_LOCAL_USERS")
        onCreate(db)
    }

    // ---------------------------------------------------------------- workouts

    fun insertWorkout(w: Workout): Long {
        val values = ContentValues().apply {
            put("user_id", w.userId)
            put("type", w.type)
            put("start_time", w.startTime)
            put("duration_sec", w.durationSec)
            put("distance_m", w.distanceMeters)
            put("steps", w.steps)
            put("points", w.points)
            put("synced", if (w.synced) 1 else 0)
        }
        return writableDatabase.insertOrThrow(T_WORKOUTS, null, values)
    }

    fun getWorkouts(userId: String): List<Workout> =
        queryWorkouts("user_id = ?", arrayOf(userId))

    fun getUnsyncedWorkouts(userId: String): List<Workout> =
        queryWorkouts("user_id = ? AND synced = 0", arrayOf(userId))

    fun markWorkoutSynced(localId: Long) {
        val values = ContentValues().apply { put("synced", 1) }
        writableDatabase.update(T_WORKOUTS, values, "id = ?", arrayOf(localId.toString()))
    }

    fun deleteWorkout(localId: Long) {
        writableDatabase.delete(T_WORKOUTS, "id = ?", arrayOf(localId.toString()))
    }

    private fun queryWorkouts(selection: String, args: Array<String>): List<Workout> {
        readableDatabase.query(
            T_WORKOUTS, null, selection, args, null, null, "start_time DESC"
        ).use { c ->
            val result = ArrayList<Workout>(c.count)
            while (c.moveToNext()) result.add(c.toWorkout())
            return result
        }
    }

    private fun Cursor.toWorkout() = Workout(
        localId = getLong(getColumnIndexOrThrow("id")),
        userId = getString(getColumnIndexOrThrow("user_id")),
        type = getString(getColumnIndexOrThrow("type")),
        startTime = getLong(getColumnIndexOrThrow("start_time")),
        durationSec = getLong(getColumnIndexOrThrow("duration_sec")),
        distanceMeters = getDouble(getColumnIndexOrThrow("distance_m")),
        steps = getInt(getColumnIndexOrThrow("steps")),
        points = getInt(getColumnIndexOrThrow("points")),
        synced = getInt(getColumnIndexOrThrow("synced")) == 1
    )

    // --------------------------------------------------------------- exercises

    fun replaceExercises(list: List<Exercise>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(T_EXERCISES, null, null)
            for (e in list) {
                val values = ContentValues().apply {
                    put("id", e.id)
                    put("name", e.name)
                    put("category", e.category)
                    put("description", e.description)
                    put("muscles", e.muscles)
                    put("equipment", e.equipment)
                    put("image_url", e.imageUrl)
                }
                db.insertWithOnConflict(T_EXERCISES, null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getExercises(): List<Exercise> {
        readableDatabase.query(T_EXERCISES, null, null, null, null, null, "name COLLATE NOCASE ASC")
            .use { c ->
                val result = ArrayList<Exercise>(c.count)
                while (c.moveToNext()) {
                    val imgIdx = c.getColumnIndexOrThrow("image_url")
                    result.add(
                        Exercise(
                            id = c.getInt(c.getColumnIndexOrThrow("id")),
                            name = c.getString(c.getColumnIndexOrThrow("name")),
                            category = c.getString(c.getColumnIndexOrThrow("category")),
                            description = c.getString(c.getColumnIndexOrThrow("description")),
                            muscles = c.getString(c.getColumnIndexOrThrow("muscles")),
                            equipment = c.getString(c.getColumnIndexOrThrow("equipment")),
                            imageUrl = if (c.isNull(imgIdx)) null else c.getString(imgIdx)
                        )
                    )
                }
                return result
            }
    }

    // ------------------------------------------------------------- local users

    data class LocalUser(
        val email: String,
        val uid: String,
        val displayName: String,
        val passwordHash: String,
        val salt: String
    )

    fun upsertLocalUser(user: LocalUser) {
        val values = ContentValues().apply {
            put("email", user.email.lowercase())
            put("uid", user.uid)
            put("display_name", user.displayName)
            put("password_hash", user.passwordHash)
            put("salt", user.salt)
        }
        writableDatabase.insertWithOnConflict(T_LOCAL_USERS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getLocalUser(email: String): LocalUser? {
        readableDatabase.query(
            T_LOCAL_USERS, null, "email = ?", arrayOf(email.lowercase()), null, null, null
        ).use { c ->
            if (!c.moveToFirst()) return null
            return LocalUser(
                email = c.getString(c.getColumnIndexOrThrow("email")),
                uid = c.getString(c.getColumnIndexOrThrow("uid")),
                displayName = c.getString(c.getColumnIndexOrThrow("display_name")),
                passwordHash = c.getString(c.getColumnIndexOrThrow("password_hash")),
                salt = c.getString(c.getColumnIndexOrThrow("salt"))
            )
        }
    }

    fun updateLocalUserName(uid: String, displayName: String) {
        val values = ContentValues().apply { put("display_name", displayName) }
        writableDatabase.update(T_LOCAL_USERS, values, "uid = ?", arrayOf(uid))
    }

    companion object {
        private const val DB_NAME = "fithub.db"
        private const val DB_VERSION = 1
        private const val T_WORKOUTS = "workouts"
        private const val T_EXERCISES = "exercises"
        private const val T_LOCAL_USERS = "local_users"

        @Volatile
        private var INSTANCE: FitHubDatabase? = null

        fun get(context: Context): FitHubDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: FitHubDatabase(context.applicationContext).also { INSTANCE = it }
            }
    }
}
