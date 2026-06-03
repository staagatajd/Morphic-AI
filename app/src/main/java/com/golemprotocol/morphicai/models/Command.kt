package com.golemprotocol.morphicai.models

import org.json.JSONObject

data class Command(
    val id: String,
    val golemId: String,
    val ownerId: String,
    var triggerPhrase: String,
    var actionType: String,
    var actionPayload: String? = null,
    var isSynchronized: Boolean = true,
    var createdAt: String
) {
    fun toMap() = mapOf(
        "id" to id,
        "golem_id" to golemId,
        "owner_id" to ownerId,
        "trigger_phrase" to triggerPhrase,
        "action_type" to actionType,
        "action_payload" to actionPayload,
        "is_synchronized" to isSynchronized,
        "created_at" to createdAt
    )

    fun toJson() = JSONObject(toMap()).toString()

    companion object {
        fun fromMap(map: Map<String, Any?>) = Command(
            id = map["id"]?.toString().orEmpty(),
            golemId = map["golem_id"]?.toString().orEmpty(),
            ownerId = map["owner_id"]?.toString().orEmpty(),
            triggerPhrase = map["trigger_phrase"]?.toString().orEmpty(),
            actionType = map["action_type"]?.toString().orEmpty(),
            actionPayload = map["action_payload"]?.toString(),
            isSynchronized = (map["is_synchronized"]?.toString() ?: "true").toBoolean(),
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