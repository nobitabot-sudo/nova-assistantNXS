package com.blurr.voice

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.blurr.voice.data.UserMemory
import com.blurr.voice.utilities.LocalStore
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.util.Date
import java.util.UUID

class MemoriesActivity : AppCompatActivity() {

    private lateinit var memoriesRecyclerView: RecyclerView
    private lateinit var emptyStateText: TextView
    private lateinit var addMemoryFab: FloatingActionButton
    private lateinit var memoriesAdapter: MemoriesAdapter
    
    
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_memories)
        
        setupViews()
        setupRecyclerView()
        loadMemories()
    }

    private fun setupViews() {
        // Setup toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "My Memories"
        
        // Setup views
        memoriesRecyclerView = findViewById(R.id.memoriesRecyclerView)
        emptyStateText = findViewById(R.id.emptyStateText)
        addMemoryFab = findViewById(R.id.addMemoryFab)
        
        // Setup privacy card click listener
        val privacyCard = findViewById<com.google.android.material.card.MaterialCardView>(R.id.privacyCard)
        privacyCard.setOnClickListener {
            val intent = Intent(this, PrivacyActivity::class.java)
            startActivity(intent)
        }
        
        // Setup FAB click listener
        addMemoryFab.setOnClickListener {
            showAddEditMemoryDialog(null)
        }
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_memories, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_privacy -> {
                val intent = Intent(this, PrivacyActivity::class.java)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun setupRecyclerView() {
        memoriesAdapter = MemoriesAdapter(
            memories = emptyList(),
            onEditClick = { memory ->
                showAddEditMemoryDialog(memory)
            },
            onDeleteClick = { memory ->
                showDeleteConfirmationDialog(memory)
            }
        )
        
        memoriesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MemoriesActivity)
            adapter = memoriesAdapter
        }
        
        // Setup swipe to delete
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false
            
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val memory = memoriesAdapter.getMemoryAt(position)
                if (memory != null) {
                    showDeleteConfirmationDialog(memory)
                }
            }
        }
        
        ItemTouchHelper(swipeHandler).attachToRecyclerView(memoriesRecyclerView)
    }
    
    private fun loadMemories() {
        updateUI(LocalStore.getMemories())
    }
    
    private fun updateUI(memories: List<UserMemory>) {
        if (memories.isEmpty()) {
            memoriesRecyclerView.visibility = View.GONE
            emptyStateText.visibility = View.VISIBLE
            emptyStateText.text = "No memories yet.\nTap the + button to add your first memory!"
        } else {
            memoriesRecyclerView.visibility = View.VISIBLE
            emptyStateText.visibility = View.GONE
            memoriesAdapter.updateMemories(memories)
        }
    }
    
    private fun showAddEditMemoryDialog(existingMemory: UserMemory?) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_memory, null)
        val memoryEditText = dialogView.findViewById<EditText>(R.id.memoryEditText)
        val saveButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.saveButton)
        val cancelButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.cancelButton)
//        val titleTextView = dialogView.findViewById<TextView>(R.id.dialogTitle) // Assuming there is a title view, or I'll just skip setting title if ID not found

        if (existingMemory != null) {
            memoryEditText.setText(existingMemory.text)
            saveButton.text = "Update"
        }
        
        // Enable/disable save button based on text input
        memoryEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                saveButton.isEnabled = !s.isNullOrBlank()
            }
        })
        
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        
        saveButton.setOnClickListener {
            val memoryText = memoryEditText.text.toString().trim()
            if (memoryText.isNotEmpty()) {
                if (existingMemory != null) {
                    updateMemory(existingMemory, memoryText)
                } else {
                    addMemory(memoryText)
                }
                dialog.dismiss()
            }
        }
        
        cancelButton.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun addMemory(memoryText: String) {
        LocalStore.addMemory(memoryText)
        Toast.makeText(this, "Memory added", Toast.LENGTH_SHORT).show()
        loadMemories()
    }

    private fun updateMemory(oldMemory: UserMemory, newText: String) {
        LocalStore.updateMemory(oldMemory.id, newText)
        Toast.makeText(this, "Memory updated", Toast.LENGTH_SHORT).show()
        loadMemories()
    }
    
    private fun showDeleteConfirmationDialog(memory: UserMemory) {
        AlertDialog.Builder(this)
            .setTitle("Delete Memory")
            .setMessage("Are you sure you want to delete this memory?\n\n\"${memory.text}\"")
            .setPositiveButton("Delete") { _, _ ->
                deleteMemory(memory)
            }
            .setNegativeButton("Cancel") { _, _ ->
                // Restore the swiped item
                memoriesAdapter.notifyDataSetChanged()
            }
            .setCancelable(false)
            .show()
    }
    
    private fun deleteMemory(memory: UserMemory) {
        LocalStore.deleteMemory(memory.id)
        showSnackbar("Memory deleted", "Undo") { }
        loadMemories()
    }
    
    private fun showSnackbar(message: String, actionText: String, action: () -> Unit) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG)
            //.setAction(actionText) { action() } // Undo disabled for simplicity as discussed
            .show()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}