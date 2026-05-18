package com.example.detectcha.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "phishing_history")
data class PhishingHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val timestamp: Long,
    val label: String,
    val probability: Float
)
