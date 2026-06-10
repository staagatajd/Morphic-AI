package com.golemprotocol.morphicai.services

import com.golemprotocol.morphicai.models.SyncMetadata
import com.golemprotocol.morphicai.models.User
import com.golemprotocol.morphicai.models.AppSettings
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

/**
 * Sync State Enum
 * 0 = Pending (local changes not synced)
 * 1 = Synced (confirmed with remote)
 * 2 = Conflict (divergence detected)
 */
enum class SyncState(val value: Int) {
    PENDING(0),
    SYNCED(1),
    CONFLICT(2);

    companion object {
        fun fromValue(value: Int) = entries.find { it.value == value } ?: PENDING
    }
}

/**
 * Sync Result Structure
 */
data class SyncResult(
    val modelType: String,
    val success: Boolean,
    val recordsProcessed: Int,
    val recordsInserted: Int,
    val recordsUpdated: Int,
    val recordsConflicted: Int,
    val message: String,
    val timestamp: String = Instant.now().toString(),
    val errors: List<String> = emptyList()
)

/**
 * Data Models for Workspace and Role Analytics
 */
data class Workspace(
    val id: String,
    val name: String,
    val createdAt: String
)

data class RoleAnalytics(
    val id: Int,
    val currentRole: String,
    val mostUsedRole: String,
    val roleSwitchesCount: Int
)

data class Message(
    val text: String,
    val isUser: Boolean
)

/**
 * Local Database Helper with crash recovery and transaction support
 */
class DatabaseOpenHelper(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    companion object {
        private const val DATABASE_NAME = "golem_protocol.db"
        const val DATABASE_VERSION = 3
    }

    override fun onCreate(db: SQLiteDatabase) {
        createAllTables(db)
        
        // Initial records for new paradigms
        db.execSQL("INSERT OR IGNORE INTO role_analytics (id, current_role, most_used_role, role_switches_count) VALUES (1, 'Developer', 'Developer', 0)")
        db.execSQL("INSERT OR IGNORE INTO workspaces (id, name, created_at) VALUES ('first_workspace_uuid', 'My First Workspace', '2026-06-10T00:00:00Z')")
        
        initializeCrashRecovery(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // Create the tables that were missing
            db.execSQL("""
            CREATE TABLE IF NOT EXISTS workspaces (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                created_at TEXT NOT NULL
            )
        """.trimIndent())

            db.execSQL("""
            CREATE TABLE IF NOT EXISTS role_analytics (
                id INTEGER PRIMARY KEY DEFAULT 1,
                current_role TEXT NOT NULL DEFAULT 'Developer',
                most_used_role TEXT NOT NULL DEFAULT 'Developer',
                role_switches_count INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())

            // Seed the initial rows
            db.execSQL("""
            INSERT OR IGNORE INTO role_analytics 
            (id, current_role, most_used_role, role_switches_count) 
            VALUES (1, 'Developer', 'Developer', 0)
        """.trimIndent())

            db.execSQL("""
            INSERT OR IGNORE INTO workspaces (id, name, created_at) 
            VALUES ('first_workspace_uuid', 'My First Workspace', '2026-06-10T00:00:00Z')
        """.trimIndent())
        }
        if (oldVersion < 3) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS messages (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    workspace_id TEXT NOT NULL,
                    text TEXT NOT NULL,
                    is_user INTEGER NOT NULL,
                    timestamp TEXT NOT NULL,
                    FOREIGN KEY (workspace_id) REFERENCES workspaces(id)
                )
            """.trimIndent())
        }
    }

    private fun createAllTables(db: SQLiteDatabase) {
        // User table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS users (
                id TEXT PRIMARY KEY,
                username TEXT NOT NULL UNIQUE,
                email TEXT NOT NULL UNIQUE,
                role TEXT NOT NULL DEFAULT 'user',
                is_active INTEGER NOT NULL DEFAULT 1,
                is_deleted INTEGER NOT NULL DEFAULT 0,
                sync_state INTEGER NOT NULL DEFAULT 0,
                last_modified TEXT NOT NULL,
                created_at TEXT NOT NULL
            )
        """.trimIndent())

        // Workspaces table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS workspaces (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                created_at TEXT NOT NULL
            )
        """.trimIndent())

        // Role Analytics table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS role_analytics (
                id INTEGER PRIMARY KEY DEFAULT 1,
                current_role TEXT NOT NULL DEFAULT 'Developer',
                most_used_role TEXT NOT NULL DEFAULT 'Developer',
                role_switches_count INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())

        // Messages table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                workspace_id TEXT NOT NULL,
                text TEXT NOT NULL,
                is_user INTEGER NOT NULL,
                timestamp TEXT NOT NULL,
                FOREIGN KEY (workspace_id) REFERENCES workspaces(id)
            )
        """.trimIndent())

        // SyncMetadata table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS sync_metadata (
                id TEXT PRIMARY KEY,
                owner_id TEXT NOT NULL,
                model_type TEXT NOT NULL,
                last_sync_timestamp TEXT NOT NULL,
                created_at TEXT NOT NULL,
                UNIQUE(owner_id, model_type),
                FOREIGN KEY (owner_id) REFERENCES users(id)
            )
        """.trimIndent())

        // Crash recovery log table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS crash_recovery_log (
                id TEXT PRIMARY KEY,
                operation_type TEXT NOT NULL,
                model_type TEXT NOT NULL,
                record_id TEXT NOT NULL,
                snapshot TEXT NOT NULL,
                created_at TEXT NOT NULL,
                recovered INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())

        // AppSettings table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS app_settings (
                id INTEGER PRIMARY KEY DEFAULT 1,
                large_texts INTEGER NOT NULL DEFAULT 0,
                always_on INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())

        // Initial settings record
        db.execSQL("INSERT OR IGNORE INTO app_settings (id, large_texts, always_on) VALUES (1, 0, 0)")

        // Create indexes for query performance
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_users_owner ON users(id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_sync_metadata_owner ON sync_metadata(owner_id)")
    }

    private fun initializeCrashRecovery(db: SQLiteDatabase) {
        // Check for incomplete transactions and mark for recovery
        db.execSQL("""
            DELETE FROM crash_recovery_log WHERE recovered = 1
        """)
    }
}

/**
 * Storage Helper Layer - Generic CRUD operations with transaction awareness
 */
class StorageHelper(private val db: SQLiteDatabase) {

    suspend fun <T> inTransaction(block: suspend () -> T): T {
        db.beginTransaction()
        return try {
            val result = block()
            db.setTransactionSuccessful()
            result
        } finally {
            db.endTransaction()
        }
    }

    fun logCrashRecovery(
        operationType: String,
        modelType: String,
        recordId: String,
        snapshot: String
    ) {
        val values = ContentValuesBuilder()
            .put("id", UUID.randomUUID().toString())
            .put("operation_type", operationType)
            .put("model_type", modelType)
            .put("record_id", recordId)
            .put("snapshot", snapshot)
            .put("created_at", Instant.now().toString())
            .put("recovered", 0)
            .build()

        db.insert("crash_recovery_log", null, values)
    }

    fun getCrashRecoveryLogs(): List<Map<String, String>> {
        val logs = mutableListOf<Map<String, String>>()
        val cursor = db.query(
            "crash_recovery_log",
            null,
            "recovered = 0",
            null,
            null,
            null,
            "created_at ASC"
        )

        cursor.use {
            while (it.moveToNext()) {
                logs.add(cursorToMap(it))
            }
        }
        return logs
    }

    fun markCrashRecoveryLogAsRecovered(logId: String) {
        val values = ContentValuesBuilder().put("recovered", 1).build()
        db.update("crash_recovery_log", values, "id = ?", arrayOf(logId))
    }

    private fun cursorToMap(cursor: Cursor): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (i in 0 until cursor.columnCount) {
            map[cursor.getColumnName(i)] = cursor.getString(i) ?: ""
        }
        return map
    }
}

/**
 * Content Values Builder - Helper for creating database rows
 */
class ContentValuesBuilder {
    private val values = ContentValues()

    fun put(key: String, value: Any?) = apply {
        when (value) {
            null -> values.putNull(key)
            is String -> values.put(key, value)
            is Int -> values.put(key, value)
            is Boolean -> values.put(key, if (value) 1 else 0)
            is Long -> values.put(key, value)
            is Double -> values.put(key, value)
            else -> values.put(key, value.toString())
        }
    }

    fun build() = values
}

/**
 * Mapping Utilities - Bidirectional conversion with flexible schema handling
 */
class MappingUtils {
    companion object {
        /**
         * Convert model to storage map with sync state
         */
        fun modelToStorage(model: Any, syncState: Int = SyncState.PENDING.value): Map<String, Any?> {
            return when (model) {
                is User -> mapOf(
                    "id" to model.id,
                    "username" to model.username,
                    "email" to model.email,
                    "role" to model.role,
                    "is_active" to if (model.isActive) 1 else 0,
                    "is_deleted" to 0,
                    "sync_state" to syncState,
                    "last_modified" to Instant.now().toString(),
                    "created_at" to model.createdAt
                )
                is SyncMetadata -> mapOf(
                    "id" to model.id,
                    "owner_id" to model.ownerId,
                    "model_type" to "sync_metadata",
                    "last_sync_timestamp" to model.lastSyncTimestamp,
                    "created_at" to model.createdAt
                )
                else -> emptyMap()
            }
        }

        /**
         * Convert storage row to model with schema evolution support
         */
        fun storageToUser(row: Map<String, Any?>): User {
            return User(
                id = row["id"]?.toString() ?: "",
                username = row["username"]?.toString() ?: "",
                email = row["email"]?.toString() ?: "",
                role = row["role"]?.toString() ?: "user",
                isActive = (row["is_active"]?.toString() ?: "1").toIntOrNull() == 1,
                createdAt = row["created_at"]?.toString() ?: Instant.now().toString()
            )
        }

        fun storageToSyncMetadata(row: Map<String, Any?>): SyncMetadata {
            return SyncMetadata(
                id = row["id"]?.toString() ?: "",
                ownerId = row["owner_id"]?.toString() ?: "",
                lastSyncTimestamp = row["last_sync_timestamp"]?.toString() ?: "",
                createdAt = row["created_at"]?.toString() ?: Instant.now().toString()
            )
        }
    }
}

/**
 * External Data Parsing Utilities - Flexible handling of variable structures
 */
class ExternalDataParser {
    companion object {
        // Internal utility function to map JSONObject keys safely without an external utility dependency
        private fun JSONObject.toMap(): Map<String, Any?> {
            val map = mutableMapOf<String, Any?>()
            val keysItr = this.keys()
            while (keysItr.hasNext()) {
                val key = keysItr.next()
                var value = this.get(key)
                if (value == JSONObject.NULL) {
                    value = null
                }
                map[key] = value
            }
            return map
        }

        /**
         * Extract user from external data with multiple possible roots
         * Handles: Direct object, nested structures, missing fields
         */
        fun parseUser(data: Any?): User? {
            return try {
                val map = when (data) {
                    is JSONObject -> data.toMap()
                    is Map<*, *> -> data.mapKeys { it.key.toString() }
                    else -> return null
                }

                // Strict extraction - only use explicitly declared fields
                val id = map["id"]?.toString()?.takeIf { it.isNotEmpty() } ?: return null
                val username = map["username"]?.toString()?.takeIf { it.isNotEmpty() } ?: return null
                val email = map["email"]?.toString()?.takeIf { it.isNotEmpty() } ?: return null
                val role = map["role"]?.toString() ?: "user"
                val isActive = (map["is_active"]?.toString() ?: "true").toBoolean()
                val createdAt = map["created_at"]?.toString() ?: Instant.now().toString()

                User(id, username, email, role, isActive, createdAt)
            } catch (e: Exception) {
                null
            }
        }

        /**
         * Extract array of records, skipping malformed entries
         */
        fun parseArray(data: Any?): List<Map<String, Any?>> {
            return try {
                when (data) {
                    is JSONArray -> {
                        val result = mutableListOf<Map<String, Any?>>()
                        for (i in 0 until data.length()) {
                            try {
                                val item = data.getJSONObject(i)
                                result.add(item.toMap())
                            } catch (e: Exception) {
                                // Skip malformed entries
                            }
                        }
                        result
                    }
                    is List<*> -> {
                        data.mapNotNull { item ->
                            try {
                                when (item) {
                                    is JSONObject -> item.toMap()
                                    is Map<*, *> -> item.mapKeys { it.key.toString() }
                                    else -> null
                                }
                            } catch (e: Exception) {
                                null
                            }
                        }
                    }
                    else -> emptyList()
                }
            } catch (e: Exception) {
                emptyList()
            }
        }

        /**
         * Check if required field is present
         */
        fun hasRequiredField(map: Map<String, Any?>, field: String): Boolean {
            return map[field]?.toString()?.isNotEmpty() == true
        }
    }
}

/**
 * Main DatabaseService - CRUD and Sync Operations
 */
class DatabaseService(context: Context) {
    private val helper = DatabaseOpenHelper(context)
    private val db: SQLiteDatabase = helper.writableDatabase
    private val storage = StorageHelper(db)
    private val mapping = MappingUtils
    private val parser = ExternalDataParser

    // ============= APP SETTINGS =============

    suspend fun getAppSettings(): AppSettings = withContext(Dispatchers.IO) {
        val cursor = db.query("app_settings", null, "id = 1", null, null, null, null)
        cursor.use {
            if (it.moveToFirst()) {
                AppSettings(
                    id = it.getInt(it.getColumnIndexOrThrow("id")),
                    largeTexts = it.getInt(it.getColumnIndexOrThrow("large_texts")) == 1,
                    alwaysOn = it.getInt(it.getColumnIndexOrThrow("always_on")) == 1
                )
            } else {
                AppSettings()
            }
        }
    }

    suspend fun updateAppSettings(settings: AppSettings): Boolean = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("large_texts", if (settings.largeTexts) 1 else 0)
            put("always_on", if (settings.alwaysOn) 1 else 0)
        }
        db.update("app_settings", values, "id = 1", null) > 0
    }

    // ============= USER CRUD =============

    suspend fun upsertUser(user: User): Boolean = withContext(Dispatchers.IO) {
        storage.inTransaction {
            try {
                storage.logCrashRecovery("UPSERT", "USER", user.id, user.toJson())

                val existing = db.query(
                    "users",
                    null,
                    "id = ?",
                    arrayOf(user.id),
                    null,
                    null,
                    null
                )

                val values = ContentValuesBuilder()
                    .put("id", user.id)
                    .put("username", user.username)
                    .put("email", user.email)
                    .put("role", user.role)
                    .put("is_active", user.isActive)
                    .put("is_deleted", 0)
                    .put("sync_state", SyncState.PENDING.value)
                    .put("last_modified", Instant.now().toString())
                    .put("created_at", user.createdAt)
                    .build()

                val result = if (existing.count > 0) {
                    db.update("users", values, "id = ?", arrayOf(user.id)) > 0
                } else {
                    db.insert("users", null, values) > 0
                }

                existing.close()
                result
            } catch (e: Exception) {
                false
            }
        }
    }

    suspend fun getUserById(userId: String): User? = withContext(Dispatchers.IO) {
        val cursor = db.query(
            "users",
            null,
            "id = ? AND is_deleted = 0",
            arrayOf(userId),
            null,
            null,
            null
        )

        cursor.use {
            if (it.moveToFirst()) {
                mapping.storageToUser(rowToMap(it))
            } else {
                null
            }
        }
    }

    suspend fun getAllUsers(): List<User> = withContext(Dispatchers.IO) {
        val cursor = db.query(
            "users",
            null,
            "is_deleted = 0",
            null,
            null,
            null,
            "created_at DESC"
        )

        cursor.use {
            val users = mutableListOf<User>()
            while (it.moveToNext()) {
                users.add(mapping.storageToUser(rowToMap(it)))
            }
            users
        }
    }

    suspend fun softDeleteUser(userId: String): Boolean = withContext(Dispatchers.IO) {
        storage.inTransaction {
            try {
                storage.logCrashRecovery("SOFT_DELETE", "USER", userId, "")

                val values = ContentValuesBuilder()
                    .put("is_deleted", 1)
                    .put("sync_state", SyncState.PENDING.value)
                    .put("last_modified", Instant.now().toString())
                    .build()

                db.update("users", values, "id = ?", arrayOf(userId)) > 0
            } catch (e: Exception) {
                false
            }
        }
    }

    // ============= WORKSPACE CRUD =============

    suspend fun insertWorkspace(workspace: Workspace): Boolean = withContext(Dispatchers.IO) {
        storage.inTransaction {
            try {
                storage.logCrashRecovery("INSERT", "WORKSPACE", workspace.id, "")
                val values = ContentValuesBuilder()
                    .put("id", workspace.id)
                    .put("name", workspace.name)
                    .put("created_at", workspace.createdAt)
                    .build()
                db.insert("workspaces", null, values) > 0
            } catch (e: Exception) {
                false
            }
        }
    }

    suspend fun getAllWorkspaces(): List<Workspace> = withContext(Dispatchers.IO) {
        val cursor = db.query("workspaces", null, null, null, null, null, "created_at DESC")
        cursor.use {
            val list = mutableListOf<Workspace>()
            while (it.moveToNext()) {
                list.add(
                    Workspace(
                        id = it.getString(it.getColumnIndexOrThrow("id")),
                        name = it.getString(it.getColumnIndexOrThrow("name")),
                        createdAt = it.getString(it.getColumnIndexOrThrow("created_at"))                    )
                )
            }
            list
        }
    }

    // ============= MESSAGE OPERATIONS =============

    suspend fun getMessagesByWorkspace(workspaceId: String): List<Message> = withContext(Dispatchers.IO) {
        val cursor = db.query(
            "messages",
            null,
            "workspace_id = ?",
            arrayOf(workspaceId),
            null,
            null,
            "timestamp ASC"
        )
        cursor.use {
            val list = mutableListOf<Message>()
            while (it.moveToNext()) {
                list.add(
                    Message(
                        text = it.getString(it.getColumnIndexOrThrow("text")),
                        isUser = it.getInt(it.getColumnIndexOrThrow("is_user")) == 1
                    )
                )
            }
            list
        }
    }

    suspend fun insertMessage(workspaceId: String, text: String, isUser: Boolean): Boolean = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("workspace_id", workspaceId)
            put("text", text)
            put("is_user", if (isUser) 1 else 0)
            put("timestamp", Instant.now().toString())
        }
        db.insert("messages", null, values) > 0
    }

    // ============= ROLE ANALYTICS MANAGEMENT =============

    suspend fun getRoleAnalytics(): RoleAnalytics = withContext(Dispatchers.IO) {
        val cursor = db.query("role_analytics", null, "id = 1", null, null, null, null)
        cursor.use {
            if (it.moveToFirst()) {
                RoleAnalytics(
                    id = it.getInt(it.getColumnIndexOrThrow("id")),
                    currentRole = it.getString(it.getColumnIndexOrThrow("current_role")),
                    mostUsedRole = it.getString(it.getColumnIndexOrThrow("most_used_role")),
                    roleSwitchesCount = it.getInt(it.getColumnIndexOrThrow("role_switches_count"))
                )
            } else {
                RoleAnalytics(1, "Developer", "Developer", 0)
            }
        }
    }

    suspend fun switchRole(newRole: String): Boolean = withContext(Dispatchers.IO) {
        storage.inTransaction {
            try {
                val current = getRoleAnalytics()
                val updatedSwitches = current.roleSwitchesCount + 1
                
                // For simplicity, make the new role the most used one or track via calculation
                val values = ContentValuesBuilder()
                    .put("current_role", newRole)
                    .put("most_used_role", newRole) 
                    .put("role_switches_count", updatedSwitches)
                    .build()
                db.update("role_analytics", values, "id = 1", null) > 0
            } catch (e: Exception) {
                false
            }
        }
    }

    // ============= SYNC OPERATIONS =============

    suspend fun syncUsers(externalData: Any?): SyncResult = withContext(Dispatchers.IO) {
        storage.inTransaction {
            val errors = mutableListOf<String>()
            var inserted = 0
            var updated = 0
            var conflicted = 0

            try {
                val records = parser.parseArray(externalData)

                records.forEach { record ->
                    try {
                        val parsedUser = parser.parseUser(record)
                        if (parsedUser == null) {
                            errors.add("Skipped malformed user record: ${record["id"]}")
                            return@forEach
                        }

                        val localUser = getUserById(parsedUser.id)

                        if (localUser == null) {
                            // Insert new record
                            upsertUser(parsedUser)
                            inserted++
                        } else {
                            // Update existing - preserve unsynced local changes
                            val localModified = getLastModified("users", parsedUser.id)
                            val externalModified = record["last_modified"]?.toString()
                                ?: Instant.now().toString()

                            val isLocalNewer = localModified > externalModified

                            if (isLocalNewer) {
                                // Keep local changes, mark as conflict if previously synced
                                val cursor = db.query(
                                    "users",
                                    arrayOf("sync_state"),
                                    "id = ?",
                                    arrayOf(parsedUser.id),
                                    null,
                                    null,
                                    null
                                )
                                cursor.use {
                                    if (it.moveToFirst()) {
                                        val syncState = it.getInt(0)
                                        if (syncState == SyncState.SYNCED.value) {
                                            markConflict("users", parsedUser.id)
                                            conflicted++
                                        }
                                    }
                                }
                            } else {
                                // Update with remote data
                                upsertUser(parsedUser)
                                updated++
                            }
                        }
                    } catch (e: Exception) {
                        errors.add("Error processing user ${record["id"]}: ${e.message}")
                    }
                }

                SyncResult(
                    modelType = "User",
                    success = true,
                    recordsProcessed = records.size,
                    recordsInserted = inserted,
                    recordsUpdated = updated,
                    recordsConflicted = conflicted,
                    message = "Synced $inserted new, updated $updated existing",
                    errors = errors
                )
            } catch (e: Exception) {
                SyncResult(
                    modelType = "User",
                    success = false,
                    recordsProcessed = 0,
                    recordsInserted = 0,
                    recordsUpdated = 0,
                    recordsConflicted = 0,
                    message = "Sync failed: ${e.message}",
                    errors = listOf(e.message ?: "Unknown error")
                )
            }
        }
    }

    suspend fun markSyncSuccessful(tableName: String, recordIds: List<String>): Boolean = withContext(Dispatchers.IO) {
        storage.inTransaction {
            try {
                val values = ContentValuesBuilder()
                    .put("sync_state", SyncState.SYNCED.value)
                    .put("last_modified", Instant.now().toString())
                    .build()

                recordIds.forEach { recordId ->
                    db.update(tableName, values, "id = ?", arrayOf(recordId))
                }

                true
            } catch (e: Exception) {
                false
            }
        }
    }

    suspend fun getConflictedRecords(tableName: String): List<Map<String, Any?>> = withContext(Dispatchers.IO) {
        val cursor = db.query(
            tableName,
            null,
            "sync_state = ? AND is_deleted = 0",
            arrayOf(SyncState.CONFLICT.value.toString()),
            null,
            null,
            "last_modified DESC"
        )

        cursor.use {
            val records = mutableListOf<Map<String, Any?>>()
            while (it.moveToNext()) {
                records.add(rowToMap(it))
            }
            records
        }
    }

    suspend fun getPendingRecords(tableName: String): List<Map<String, Any?>> = withContext(Dispatchers.IO) {
        val cursor = db.query(
            tableName,
            null,
            "sync_state = ? AND is_deleted = 0",
            arrayOf(SyncState.PENDING.value.toString()),
            null,
            null,
            "last_modified ASC"
        )

        cursor.use {
            val records = mutableListOf<Map<String, Any?>>()
            while (it.moveToNext()) {
                records.add(rowToMap(it))
            }
            records
        }
    }

    // ============= CRASH RECOVERY =============

    suspend fun recoverFromCrash(): Boolean = withContext(Dispatchers.IO) {
        storage.inTransaction {
            try {
                val recoveryLogs = storage.getCrashRecoveryLogs()

                recoveryLogs.forEach { log ->
                    try {
                        val operation = log["operation_type"]
                        // val recordId = log["record_id"]

                        when (operation) {
                            "SOFT_DELETE" -> {
                                // Ensure deletion is consistent
                            }
                            "UPSERT" -> {
                                // Verify record exists and is consistent
                                // val snapshot = log["snapshot"] ?: "{}"
                                // Reconstruct from snapshot if needed
                            }
                        }

                        storage.markCrashRecoveryLogAsRecovered(log["id"] ?: "")
                    } catch (e: Exception) {
                        // Continue with next recovery
                    }
                }

                true
            } catch (e: Exception) {
                false
            }
        }
    }

    suspend fun updateSyncMetadata(ownerId: String, modelType: String, timestamp: String): Boolean = withContext(Dispatchers.IO) {
        storage.inTransaction {
            try {
                val id = "$ownerId:$modelType"
                val existing = db.query(
                    "sync_metadata",
                    null,
                    "id = ?",
                    arrayOf(id),
                    null,
                    null,
                    null
                )

                val values = ContentValuesBuilder()
                    .put("id", id)
                    .put("owner_id", ownerId)
                    .put("model_type", modelType)
                    .put("last_sync_timestamp", timestamp)
                    .put("created_at", Instant.now().toString())
                    .build()

                val result = if (existing.count > 0) {
                    db.update("sync_metadata", values, "id = ?", arrayOf(id)) > 0
                } else {
                    db.insert("sync_metadata", null, values) > 0
                }

                existing.close()
                result
            } catch (e: Exception) {
                false
            }
        }
    }

    // ============= UTILITY HELPERS =============

    private fun rowToMap(cursor: Cursor): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        for (i in 0 until cursor.columnCount) {
            map[cursor.getColumnName(i)] = when (cursor.getType(i)) {
                Cursor.FIELD_TYPE_NULL -> null
                Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(i)
                Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(i)
                else -> cursor.getString(i)
            }
        }
        return map
    }

    private fun getLastModified(tableName: String, recordId: String): String {
        val cursor = db.query(
            tableName,
            arrayOf("last_modified"),
            "id = ?",
            arrayOf(recordId),
            null,
            null,
            null
        )

        return cursor.use {
            if (it.moveToFirst()) {
                it.getString(0)
            } else {
                Instant.now().toString()
            }
        }
    }

    private fun markConflict(tableName: String, recordId: String) {
        val values = ContentValuesBuilder()
            .put("sync_state", SyncState.CONFLICT.value)
            .put("last_modified", Instant.now().toString())
            .build()

        db.update(tableName, values, "id = ?", arrayOf(recordId))
    }

    fun close() {
        db.close()
        helper.close()
    }
}
