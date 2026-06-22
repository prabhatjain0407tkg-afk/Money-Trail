package com.expensetracker.sms.model

data class SubCategory(
    val id: String,
    val displayName: String,
    val icon: String = "•",
    val colorValue: Long = 0xFF636E72L
)
