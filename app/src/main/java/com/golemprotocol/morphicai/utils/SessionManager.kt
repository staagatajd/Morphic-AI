package com.golemprotocol.morphicai.utils

import android.content.Context
import android.content.SharedPreferences
import com.golemprotocol.morphicai.models.User
import org.json.JSONObject

class SessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "golem_session"
        private const val KEY_USER = "user_json"
        private const val KEY_TOKEN = "auth_token"
    }

    fun saveSession(user: User, token: String) {
        prefs.edit().apply {
            putString(KEY_USER, user.toJson())
            putString(KEY_TOKEN, token)
            apply()
        }
    }

    fun getUser(): User? {
        val userJson = prefs.getString(KEY_USER, null) ?: return null
        return try {
            User.fromJson(userJson)
        } catch (e: Exception) {
            null
        }
    }

    fun getToken(): String? {
        return prefs.getString(KEY_TOKEN, null)
    }

    fun clearSession() {
        prefs.edit().clear().apply()
    }

    fun isLoggedIn(): Boolean {
        return getToken() != null && getUser() != null
    }
}
