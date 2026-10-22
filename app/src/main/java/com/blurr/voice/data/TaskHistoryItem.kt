package com.blurr.voice.data

import java.util.Date

data class TaskHistoryItem(
    val task: String,
    val status: String,
    val startedAt: Date?,
    val completedAt: Date?,
    val success: Boolean?,
    val errorMessage: String?
) {
    fun getStatusEmoji(): String {
        return when (status.lowercase()) {
            "started" -> "🔄"
            "completed" -> if (success == true) "✅" else "❌"
            "failed" -> "❌"
            else -> "⏳"
        }
    }
    
    fun getFormattedStartTime(): String {
        return startedAt?.let { date ->
            val formatter = java.text.SimpleDateFormat("MMM dd, yyyy 'at' h:mm a", java.util.Locale.getDefault())
            formatter.format(date)
        } ?: "Unknown"
    }
    
    fun getFormattedCompletionTime(): String {
        return completedAt?.let { date: java.util.Date ->
            val formatter = java.text.SimpleDateFormat("MMM dd, yyyy 'at' h:mm a", java.util.Locale.getDefault())
            formatter.format(date)
        } ?: "Not completed"
    }
}
