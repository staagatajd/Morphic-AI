package com.golemprotocol.morphicai.models

import org.json.JSONObject

data class SyncMetadata(
    val id: String,
    val ownerId: String,
    var lastSyncTimestamp: String,
    var createdAt: String
) {
    fun toMap() = mapOf(
        "id" to id,
        "owner_id" to ownerId,
        "last_sync_timestamp" to lastSyncTimestamp,
        "created_at" to createdAt
    )

    fun toJson() = JSONObject(toMap()).toString()

    companion object {
        fun fromMap(map: Map<String, Any?>) = SyncMetadata(
            id = map["id"]?.toString().orEmpty(),
            ownerId = map["owner_id"]?.toString().orEmpty(),
            lastSyncTimestamp = map["last_sync_timestamp"]?.toString().orEmpty(),
            createdAt = map["created_at"]?.toString().orEmpty()
        )

        fun fromJson(source: String) =
            fromMap(jsonObjectToMap(JSONObject(source)))

        private fun jsonObjectToMap(jsonObject: JSONObject): Map<String, Any?> {
            val map = mutableMapOf<String, Any?>()
            val keysItr = jsonObject.keys()
            while (keysItr.hasNext()) {
                val key = keysItr.next()
                var value = jsonObject.get(key)
                if (value == JSONObject.NULL) {
                    value = null
                }
                map[key] = value
            }
            return map
        }
    }
}