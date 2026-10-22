package com.blurr.voice.utilities

import android.content.Context
import com.blurr.voice.MyApplication
import com.blurr.voice.data.TaskHistoryItem
import com.blurr.voice.data.UserMemory
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Everything Nova remembers lives here, on the phone only (SharedPreferences, JSON).
 * This replaces the old Firebase / Firestore storage, so no login or cloud account is needed.
 */
object LocalStore {
    private const val PREFS = "nova_local_store"
    private const val KEY_MEMORIES = "memories"
    private const val KEY_TASKS = "task_history"
    private const val MAX_TASKS = 200

    private val lock = Any()
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    private fun prefs(context: Context = MyApplication.appContext) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun addChangeListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeChangeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyChanged() {
        listeners.forEach {
            try {
                it()
            } catch (_: Exception) {
            }
        }
    }

    // ---------------------------------------------------------------- memories

    fun getMemories(): List<UserMemory> = synchronized(lock) {
        val raw = prefs().getString(KEY_MEMORIES, null) ?: return@synchronized emptyList()
        val out = mutableListOf<UserMemory>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    UserMemory(
                        id = o.optString("id", UUID.randomUUID().toString()),
                        text = o.optString("text", ""),
                        source = o.optString("source", "User"),
                        createdAt = Date(o.optLong("createdAt", System.currentTimeMillis()))
                    )
                )
            }
        } catch (_: Exception) {
        }
        out.sortedByDescending { it.createdAt }
    }

    private fun saveMemories(list: List<UserMemory>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("text", it.text)
                    .put("source", it.source)
                    .put("createdAt", it.createdAt.time)
            )
        }
        prefs().edit().putString(KEY_MEMORIES, arr.toString()).apply()
    }

    fun addMemory(text: String, source: String = "User"): UserMemory {
        val memory = UserMemory(
            id = UUID.randomUUID().toString(),
            text = text,
            source = source,
            createdAt = Date()
        )
        synchronized(lock) {
            saveMemories(getMemories() + memory)
        }
        notifyChanged()
        return memory
    }

    fun updateMemory(id: String, newText: String) {
        synchronized(lock) {
            saveMemories(getMemories().map { if (it.id == id) it.copy(text = newText) else it })
        }
        notifyChanged()
    }

    fun deleteMemory(id: String) {
        synchronized(lock) {
            saveMemories(getMemories().filter { it.id != id })
        }
        notifyChanged()
    }

    // ------------------------------------------------------------ task history

    fun getTaskHistory(): List<TaskHistoryItem> = synchronized(lock) {
        val raw = prefs().getString(KEY_TASKS, null) ?: return@synchronized emptyList()
        val out = mutableListOf<TaskHistoryItem>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    TaskHistoryItem(
                        task = o.optString("task", ""),
                        status = o.optString("status", ""),
                        startedAt = if (o.has("startedAt")) Date(o.getLong("startedAt")) else null,
                        completedAt = if (o.has("completedAt")) Date(o.getLong("completedAt")) else null,
                        success = if (o.has("success")) o.getBoolean("success") else null,
                        errorMessage = if (o.has("errorMessage")) o.optString("errorMessage") else null
                    )
                )
            }
        } catch (_: Exception) {
        }
        out.sortedByDescending { it.startedAt ?: Date(0) }
    }

    private fun saveTasks(list: List<TaskHistoryItem>) {
        val arr = JSONArray()
        list.take(MAX_TASKS).forEach {
            val o = JSONObject().put("task", it.task).put("status", it.status)
            it.startedAt?.let { d -> o.put("startedAt", d.time) }
            it.completedAt?.let { d -> o.put("completedAt", d.time) }
            it.success?.let { s -> o.put("success", s) }
            it.errorMessage?.let { m -> o.put("errorMessage", m) }
            arr.put(o)
        }
        prefs().edit().putString(KEY_TASKS, arr.toString()).apply()
    }

    fun trackTaskStart(task: String) {
        synchronized(lock) {
            val item = TaskHistoryItem(
                task = task,
                status = "started",
                startedAt = Date(),
                completedAt = null,
                success = null,
                errorMessage = null
            )
            saveTasks(listOf(item) + getTaskHistory())
        }
        notifyChanged()
    }

    /** Marks the most recent still-running entry for this task as finished. */
    fun trackTaskCompletion(task: String, success: Boolean, errorMessage: String? = null) {
        synchronized(lock) {
            val list = getTaskHistory().toMutableList()
            val index = list.indexOfFirst { it.task == task && it.status == "started" }
            val finished = Date()
            if (index >= 0) {
                val old = list[index]
                list[index] = old.copy(
                    status = if (success) "completed" else "failed",
                    completedAt = finished,
                    success = success,
                    errorMessage = errorMessage
                )
            } else {
                list.add(
                    0,
                    TaskHistoryItem(
                        task = task,
                        status = if (success) "completed" else "failed",
                        startedAt = finished,
                        completedAt = finished,
                        success = success,
                        errorMessage = errorMessage
                    )
                )
            }
            saveTasks(list)
        }
        notifyChanged()
    }

    fun clearAll() {
        synchronized(lock) {
            prefs().edit().clear().apply()
        }
        notifyChanged()
    }
}
