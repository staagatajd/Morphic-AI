package com.golemprotocol.morphicai.models

data class AppSettings(
    val id: Int = 1, // Single row for app settings
    val largeTexts: Boolean = false,
    val alwaysOn: Boolean = false
)
