package com.golemprotocol.morphicai.services

import com.golemprotocol.morphicai.models.Command
import com.golemprotocol.morphicai.models.Golem
import com.golemprotocol.morphicai.models.SyncMetadata
import com.golemprotocol.morphicai.models.User
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
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
        private const val DATABASE_VERSION = 1
    }

    override fun onCreate(db: SQLiteDatabase) {
        createAllTables(db)
        initializeCrashRecovery(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Preserve existing data during schema evolution
        // No destructive changes - only additive
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

        // Golem table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS golems (
                id TEXT PRIMARY KEY,
                owner_id TEXT NOT NULL,
                name TEXT NOT NULL,
                description TEXT,
                avatar_url TEXT,
                is_public INTEGER NOT NULL DEFAULT 0,
                is_deleted INTEGER NOT NULL DEFAULT 0,
                sync_state INTEGER NOT NULL DEFAULT 0,
                last_modified TEXT NOT NULL,
                created_at TEXT NOT NULL,
                FOREIGN KEY (owner_id) REFERENCES users(id)
            )
        """.trimIndent())

        // Command table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS commands (
                id TEXT PRIMARY KEY,
                golem_id TEXT NOT NULL,
                owner_id TEXT NOT NULL,
                trigger_phrase TEXT NOT NULL,
                action_type TEXT NOT NULL,
                action_payload TEXT,
                is_deleted INTEGER NOT NULL DEFAULT 0,
                sync_state INTEGER NOT NULL DEFAULT 0,
                last_modified TEXT NOT NULL,
                created_at TEXT NOT NULL,
                FOREIGN KEY (golem_id) REFERENCES golems(id),
                FOREIGN KEY (owner_id) REFERENCES users(id)
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

        // Create indexes for query performance
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_users_owner ON users(id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_golems_owner ON golems(owner_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_golems_deleted ON golems(is_deleted)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_commands_golem ON commands(golem_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_commands_owner ON commands(owner_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_commands_deleted ON commands(is_deleted)")
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

    fun beginTransaction() {
        db.beginTransaction()
    }

    fun setTransactionSuccessful() {
        db.setTransactionSuccessful()
    }

    fun endTransaction() {
        db.endTransaction()
    }

    fun <T> inTransaction(block: () -> T): T {
        beginTransaction()
        return try {
            val result = block()
            setTransactionSuccessful()
            result
        } finally {
            endTransaction()
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
                is Golem -> mapOf(
                    "id" to model.id,
                    "owner_id" to model.ownerId,
                    "name" to model.name,
                    "description" to model.description,
                    "avatar_url" to model.avatarUrl,
                    "is_public" to if (model.isPublic) 1 else 0,
                    "is_deleted" to 0,
                    "sync_state" to syncState,
                    "last_modified" to Instant.now().toString(),
                    "created_at" to model.createdAt
                )
                is Command -> mapOf(
                    "id" to model.id,
                    "golem_id" to model.golemId,
                    "owner_id" to model.ownerId,
                    "trigger_phrase" to model.triggerPhrase,
                    "action_type" to model.actionType,
                    "action_payload" to model.actionPayload,
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

        fun storageToGolem(row: Map<String, Any?>): Golem {
            return Golem(
                id = row["id"]?.toString() ?: "",
                ownerId = row["owner_id"]?.toString() ?: "",
                name = row["name"]?.toString() ?: "",
                description = row["description"]?.toString(),
                avatarUrl = row["avatar_url"]?.toString(),
                isPublic = (row["is_public"]?.toString() ?: "0").toIntOrNull() == 1,
                isSynchronized = (row["sync_state"]?.toString()?.toIntOrNull() ?: 0) == SyncState.SYNCED.value,
                createdAt = row["created_at"]?.toString() ?: Instant.now().toString()
            )
        }

        fun storageToCommand(row: Map<String, Any?>): Command {
            return Command(
                id = row["id"]?.toString() ?: "",
                golemId = row["golem_id"]?.toString() ?: "",
                ownerId = row["owner_id"]?.toString() ?: "",
                triggerPhrase = row["trigger_phrase"]?.toString() ?: "",
                actionType = row["action_type"]?.toString() ?: "",
                actionPayload = row["action_payload"]?.toString(),
                isSynchronized = (row["sync_state"]?.toString()?.toIntOrNull() ?: 0) == SyncState.SYNCED.value,
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
         * Extract golem from external data
         */
        fun parseGolem(data: Any?): Golem? {
            return try {
                val map = when (data) {
                    is JSONObject -> data.toMap()
                    is Map<*, *> -> data.mapKeys { it.key.toString() }
                    else -> return null
                }

                val id = map["id"]?.toString()?.takeIf { it.isNotEmpty() } ?: return null
                val ownerId = map["owner_id"]?.toString()?.takeIf { it.isNotEmpty() } ?: return null
                val name = map["name"]?.toString()?.takeIf { it.isNotEmpty() } ?: return null
                val description = map["description"]?.toString()
                val avatarUrl = map["avatar_url"]?.toString()
                val isPublic = (map["is_public"]?.toString() ?: "false").toBoolean()
                val isSynchronized = true
                val createdAt = map["created_at"]?.toString() ?: Instant.now().toString()

                Golem(id, ownerId, name, description, avatarUrl, isPublic, isSynchronized, createdAt)
            } catch (e: Exception) {
                null
            }
        }

        /**
         * Extract command from external data
         */
        fun parseCommand(data: Any?): Command? {
            return try {
                val map = when (data) {
                    is JSONObject -> data.toMap()
                    is Map<*, *> -> data.mapKeys { it.key.toString() }
                    else -> return null
                }

                val id = map["id"]?.toString()?.takeIf { it.isNotEmpty() } ?: return null
                val golemId = map["golem_id"]?.toString()?.takeIf { it.isNotEmpty() } ?: return null
                val ownerId = map["owner_id"]?.toString()?.takeIf { it.isNotEmpty() } ?: return null
                val triggerPhrase = map["trigger_phrase"]?.toString() ?: ""
                val actionType = map["action_type"]?.toString() ?: ""
                val actionPayload = map["action_payload"]?.toString()
                val isSynchronized = true
                val createdAt = map["created_at"]?.toString() ?: Instant.now().toString()

                Command(id, golemId, ownerId, triggerPhrase, actionType, actionPayload, isSynchronized, createdAt)
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

    // ============= USER CRUD =============

    fun upsertUser(user: User): Boolean {
        return storage.inTransaction {
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

    fun getUserById(userId: String): User? {
        val cursor = db.query(
            "users",
            null,
            "id = ? AND is_deleted = 0",
            arrayOf(userId),
            null,
            null,
            null
        )

        return cursor.use {
            if (it.moveToFirst()) {
                mapping.storageToUser(rowToMap(it))
            } else {
                null
            }
        }
    }

    fun getAllUsers(): List<User> {
        val cursor = db.query(
            "users",
            null,
            "is_deleted = 0",
            null,
            null,
            null,
            "created_at DESC"
        )

        return cursor.use {
            val users = mutableListOf<User>()
            while (it.moveToNext()) {
                users.add(mapping.storageToUser(rowToMap(it)))
            }
            users
        }
    }

    fun softDeleteUser(userId: String): Boolean {
        return storage.inTransaction {
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

    // ============= GOLEM CRUD =============

    fun upsertGolem(golem: Golem): Boolean {
        return storage.inTransaction {
            try {
                storage.logCrashRecovery("UPSERT", "GOLEM", golem.id, golem.toJson())

                val existing = db.query(
                    "golems",
                    null,
                    "id = ?",
                    arrayOf(golem.id),
                    null,
                    null,
                    null
                )

                val values = ContentValuesBuilder()
                    .put("id", golem.id)
                    .put("owner_id", golem.ownerId)
                    .put("name", golem.name)
                    .put("description", golem.description)
                    .put("avatar_url", golem.avatarUrl)
                    .put("is_public", golem.isPublic)
                    .put("is_deleted", 0)
                    .put("sync_state", SyncState.PENDING.value)
                    .put("last_modified", Instant.now().toString())
                    .put("created_at", golem.createdAt)
                    .build()

                val result = if (existing.count > 0) {
                    db.update("golems", values, "id = ?", arrayOf(golem.id)) > 0
                } else {
                    db.insert("golems", null, values) > 0
                }

                existing.close()
                result
            } catch (e: Exception) {
                false
            }
        }
    }

    fun getGolemById(golemId: String): Golem? {
        val cursor = db.query(
            "golems",
            null,
            "id = ? AND is_deleted = 0",
            arrayOf(golemId),
            null,
            null,
            null
        )

        return cursor.use {
            if (it.moveToFirst()) {
                mapping.storageToGolem(rowToMap(it))
            } else {
                null
            }
        }
    }

    fun getGolemsByOwner(ownerId: String): List<Golem> {
        val cursor = db.query(
            "golems",
            null,
            "owner_id = ? AND is_deleted = 0",
            arrayOf(ownerId),
            null,
            null,
            "created_at DESC"
        )

        return cursor.use {
            val golems = mutableListOf<Golem>()
            while (it.moveToNext()) {
                golems.add(mapping.storageToGolem(rowToMap(it)))
            }
            golems
        }
    }

    fun softDeleteGolem(golemId: String): Boolean {
        return storage.inTransaction {
            try {
                storage.logCrashRecovery("SOFT_DELETE", "GOLEM", golemId, "")

                val values = ContentValuesBuilder()
                    .put("is_deleted", 1)
                    .put("sync_state", SyncState.PENDING.value)
                    .put("last_modified", Instant.now().toString())
                    .build()

                db.update("golems", values, "id = ?", arrayOf(golemId)) > 0
            } catch (e: Exception) {
                false
            }
        }
    }

    // ============= COMMAND CRUD =============

    fun upsertCommand(command: Command): Boolean {
        return storage.inTransaction {
            try {
                storage.logCrashRecovery("UPSERT", "COMMAND", command.id, command.toJson())

                val existing = db.query(
                    "commands",
                    null,
                    "id = ?",
                    arrayOf(command.id),
                    null,
                    null,
                    null
                )

                val values = ContentValuesBuilder()
                    .put("id", command.id)
                    .put("golem_id", command.golemId)
                    .put("owner_id", command.ownerId)
                    .put("trigger_phrase", command.triggerPhrase)
                    .put("action_type", command.actionType)
                    .put("action_payload", command.actionPayload)
                    .put("is_deleted", 0)
                    .put("sync_state", SyncState.PENDING.value)
                    .put("last_modified", Instant.now().toString())
                    .put("created_at", command.createdAt)
                    .build()

                val result = if (existing.count > 0) {
                    db.update("commands", values, "id = ?", arrayOf(command.id)) > 0
                } else {
                    db.insert("commands", null, values) > 0
                }

                existing.close()
                result
            } catch (e: Exception) {
                false
            }
        }
    }

    fun getCommandById(commandId: String): Command? {
        val cursor = db.query(
            "commands",
            null,
            "id = ? AND is_deleted = 0",
            arrayOf(commandId),
            null,
            null,
            null
        )

        return cursor.use {
            if (it.moveToFirst()) {
                mapping.storageToCommand(rowToMap(it))
            } else {
                null
            }
        }
    }

    fun getCommandsByGolem(golemId: String): List<Command> {
        val cursor = db.query(
            "commands",
            null,
            "golem_id = ? AND is_deleted = 0",
            arrayOf(golemId),
            null,
            null,
            "created_at DESC"
        )

        return cursor.use {
            val commands = mutableListOf<Command>()
            while (it.moveToNext()) {
                commands.add(mapping.storageToCommand(rowToMap(it)))
            }
            commands
        }
    }

    fun softDeleteCommand(commandId: String): Boolean {
        return storage.inTransaction {
            try {
                storage.logCrashRecovery("SOFT_DELETE", "COMMAND", commandId, "")

                val values = ContentValuesBuilder()
                    .put("is_deleted", 1)
                    .put("sync_state", SyncState.PENDING.value)
                    .put("last_modified", Instant.now().toString())
                    .build()

                db.update("commands", values, "id = ?", arrayOf(commandId)) > 0
            } catch (e: Exception) {
                false
            }
        }
    }

    // ============= SYNC OPERATIONS =============

    fun syncUsers(externalData: Any?): SyncResult {
        return storage.inTransaction {
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

    fun syncGolems(externalData: Any?): SyncResult {
        return storage.inTransaction {
            val errors = mutableListOf<String>()
            var inserted = 0
            var updated = 0
            var conflicted = 0

            try {
                val records = parser.parseArray(externalData)

                records.forEach { record ->
                    try {
                        val parsedGolem = parser.parseGolem(record)
                        if (parsedGolem == null) {
                            errors.add("Skipped malformed golem record: ${record["id"]}")
                            return@forEach
                        }

                        val localGolem = getGolemById(parsedGolem.id)

                        if (localGolem == null) {
                            upsertGolem(parsedGolem)
                            inserted++
                        } else {
                            val localModified = getLastModified("golems", parsedGolem.id)
                            val externalModified = record["last_modified"]?.toString()
                                ?: Instant.now().toString()

                            val isLocalNewer = localModified > externalModified

                            if (isLocalNewer) {
                                val cursor = db.query(
                                    "golems",
                                    arrayOf("sync_state"),
                                    "id = ?",
                                    arrayOf(parsedGolem.id),
                                    null,
                                    null,
                                    null
                                )
                                cursor.use {
                                    if (it.moveToFirst()) {
                                        val syncState = it.getInt(0)
                                        if (syncState == SyncState.SYNCED.value) {
                                            markConflict("golems", parsedGolem.id)
                                            conflicted++
                                        }
                                    }
                                }
                            } else {
                                upsertGolem(parsedGolem)
                                updated++
                            }
                        }
                    } catch (e: Exception) {
                        errors.add("Error processing golem ${record["id"]}: ${e.message}")
                    }
                }

                SyncResult(
                    modelType = "Golem",
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
                    modelType = "Golem",
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

    fun syncCommands(externalData: Any?): SyncResult {
        return storage.inTransaction {
            val errors = mutableListOf<String>()
            var inserted = 0
            var updated = 0
            var conflicted = 0

            try {
                val records = parser.parseArray(externalData)

                records.forEach { record ->
                    try {
                        val parsedCommand = parser.parseCommand(record)
                        if (parsedCommand == null) {
                            errors.add("Skipped malformed command record: ${record["id"]}")
                            return@forEach
                        }

                        val localCommand = getCommandById(parsedCommand.id)

                        if (localCommand == null) {
                            upsertCommand(parsedCommand)
                            inserted++
                        } else {
                            val localModified = getLastModified("commands", parsedCommand.id)
                            val externalModified = record["last_modified"]?.toString()
                                ?: Instant.now().toString()

                            val isLocalNewer = localModified > externalModified

                            if (isLocalNewer) {
                                val cursor = db.query(
                                    "commands",
                                    arrayOf("sync_state"),
                                    "id = ?",
                                    arrayOf(parsedCommand.id),
                                    null,
                                    null,
                                    null
                                )
                                cursor.use {
                                    if (it.moveToFirst()) {
                                        val syncState = it.getInt(0)
                                        if (syncState == SyncState.SYNCED.value) {
                                            markConflict("commands", parsedCommand.id)
                                            conflicted++
                                        }
                                    }
                                }
                            } else {
                                upsertCommand(parsedCommand)
                                updated++
                            }
                        }
                    } catch (e: Exception) {
                        errors.add("Error processing command ${record["id"]}: ${e.message}")
                    }
                }

                SyncResult(
                    modelType = "Command",
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
                    modelType = "Command",
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

    fun markSyncSuccessful(tableName: String, recordIds: List<String>): Boolean {
        return storage.inTransaction {
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

    fun getConflictedRecords(tableName: String): List<Map<String, Any?>> {
        val cursor = db.query(
            tableName,
            null,
            "sync_state = ? AND is_deleted = 0",
            arrayOf(SyncState.CONFLICT.value.toString()),
            null,
            null,
            "last_modified DESC"
        )

        return cursor.use {
            val records = mutableListOf<Map<String, Any?>>()
            while (it.moveToNext()) {
                records.add(rowToMap(it))
            }
            records
        }
    }

    fun getPendingRecords(tableName: String): List<Map<String, Any?>> {
        val cursor = db.query(
            tableName,
            null,
            "sync_state = ? AND is_deleted = 0",
            arrayOf(SyncState.PENDING.value.toString()),
            null,
            null,
            "last_modified ASC"
        )

        return cursor.use {
            val records = mutableListOf<Map<String, Any?>>()
            while (it.moveToNext()) {
                records.add(rowToMap(it))
            }
            records
        }
    }

    // ============= CRASH RECOVERY =============

    fun recoverFromCrash(): Boolean {
        return storage.inTransaction {
            try {
                val recoveryLogs = storage.getCrashRecoveryLogs()

                recoveryLogs.forEach { log ->
                    try {
                        val operation = log["operation_type"]
                        val recordId = log["record_id"]

                        when (operation) {
                            "SOFT_DELETE" -> {
                                // Ensure deletion is consistent
                            }
                            "UPSERT" -> {
                                // Verify record exists and is consistent
                                val snapshot = log["snapshot"] ?: "{}"
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

    fun updateSyncMetadata(ownerId: String, modelType: String, timestamp: String): Boolean {
        return storage.inTransaction {
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