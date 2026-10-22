package com.blurr.voice.utilities

/**
 * Nova has no accounts, plans or task limits. This class is kept only so the rest of the
 * code that used to call it keeps compiling; everything is simply "unlimited".
 */
class FreemiumManager {

    companion object {
        const val DAILY_TASK_LIMIT = Int.MAX_VALUE
    }

    suspend fun getDeveloperMessage(): String = ""

    suspend fun isUserSubscribed(): Boolean = true

    suspend fun provisionUserIfNeeded() {}

    suspend fun getTasksRemaining(): Long? = Long.MAX_VALUE

    suspend fun canPerformTask(): Boolean = true

    suspend fun decrementTaskCount() {}
}
