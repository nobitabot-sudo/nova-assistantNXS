package com.blurr.voice

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.blurr.voice.data.TaskHistoryItem
import com.blurr.voice.utilities.LocalStore
import com.blurr.voice.utilities.Logger

class MomentsFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: LinearLayout
    private lateinit var adapter: MomentsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_moments, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize views
        recyclerView = view.findViewById(R.id.task_history_recycler_view)
        emptyState = view.findViewById(R.id.empty_state)

        // Setup RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = MomentsAdapter(emptyList())
        recyclerView.adapter = adapter

        // Load task history
        loadTaskHistory()
    }

    private fun loadTaskHistory() {
        try {
            val taskHistory = LocalStore.getTaskHistory()
            if (taskHistory.isNotEmpty()) {
                showTaskHistory(taskHistory)
            } else {
                showEmptyState()
            }
        } catch (e: Exception) {
            Logger.e("MomentsFragment", "Error loading task history", e)
            showEmptyState()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::adapter.isInitialized) {
            loadTaskHistory()
        }
    }

    private fun showTaskHistory(taskHistory: List<TaskHistoryItem>) {
        adapter = MomentsAdapter(taskHistory)
        recyclerView.adapter = adapter
        recyclerView.visibility = View.VISIBLE
        emptyState.visibility = View.GONE
    }

    private fun showEmptyState() {
        recyclerView.visibility = View.GONE
        emptyState.visibility = View.VISIBLE
    }
}
