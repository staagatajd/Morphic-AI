package com.golemprotocol.morphicai.models

import org.json.JSONObject

data class Golem(
    val id: String,
    val ownerId: String,
    var name: String,
    var description: String? = null,
    var avatarUrl: String? = null,
    var isPublic: Boolean = false,
    var isSynchronized: Boolean = true,
    var createdAt: String
) {
    fun toMap() = mapOf(
        "id" to id,
        "owner_id" to ownerId,
        "name" to name,
        "description" to description,
        "avatar_url" to avatarUrl,
        "is_public" to isPublic,
        "is_synchronized" to isSynchronized,
        "created_at" to createdAt
    )

    fun toJson() = JSONObject(toMap()).toString()

    companion object {
        fun fromMap(map: Map<String, Any?>) = Golem(
            id = map["id"]?.toString().orEmpty(),
            ownerId = map["owner_id"]?.toString().orEmpty(),
            name = map["name"]?.toString().orEmpty(),
            description = map["description"]?.toString(),
            avatarUrl = map["avatar_url"]?.toString(),
            isPublic = map["is_public"].toString().toBoolean(),
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