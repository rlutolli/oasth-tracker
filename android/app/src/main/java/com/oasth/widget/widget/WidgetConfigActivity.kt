package com.oasth.widget.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.oasth.widget.R
import com.oasth.widget.data.StopConfigItem
import com.oasth.widget.data.StopRepository
import com.oasth.widget.data.WidgetConfig
import com.oasth.widget.data.WidgetConfigRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Collections
class WidgetConfigActivity : AppCompatActivity() {
    
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var configRepo: WidgetConfigRepository
    private lateinit var stopRepo: StopRepository
    
    // UI Elements
    private lateinit var searchInput: AutoCompleteTextView
    private lateinit var selectedList: RecyclerView
    private lateinit var saveButton: Button
    
    // State
    private val selectedItems = mutableListOf<StopConfigItem>()
    private lateinit var adapter: SelectedStopsAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_config)
        
        setResult(RESULT_CANCELED)
        
        // Init Repos
        configRepo = WidgetConfigRepository(this)
        stopRepo = StopRepository(this)
        
        // Get Widget ID
        widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        
        // Bind UI
        searchInput = findViewById(R.id.search_input)
        selectedList = findViewById(R.id.selected_stops_list)
        saveButton = findViewById(R.id.save_button)
        
        setupSearch()
        setupRecyclerView()
        loadConfiguration()
        
        saveButton.setOnClickListener { saveConfiguration() }
    }

    private fun setupSearch() {
        val allStops = stopRepo.getAllStops()
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            allStops.map { "${it.name} (${it.streetId})" }
        )
        searchInput.setAdapter(adapter)
        searchInput.threshold = 1 // Start searching after 1 character
        
        searchInput.setOnItemClickListener { parent, _, position, _ ->
            val selection = parent.getItemAtPosition(position) as String
            // Extract Code (last part inside parens)
            val streetId = selection.substringAfterLast("(").substringBefore(")")
            val name = selection.substringBeforeLast(" (")
            
            addStopToCart(streetId, name)
            searchInput.setText("") // Clear for next search
        }
    }
    private fun setupRecyclerView() {
        adapter = SelectedStopsAdapter(
            items = selectedItems,
            onStartDrag = { holder -> itemTouchHelper.startDrag(holder) },
            onDelete = { position ->
                selectedItems.removeAt(position)
                adapter.notifyItemRemoved(position)
                updateEmptyState()
            },
            onEdit = { item, position -> showLineFilterDialog(item, position) }
        )
        
        selectedList.layoutManager = LinearLayoutManager(this)
        selectedList.adapter = adapter
        itemTouchHelper.attachToRecyclerView(selectedList)
    }
    
    private val itemTouchHelper by lazy {
        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.adapterPosition
                val to = target.adapterPosition
                adapter.onItemMove(from, to)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // Not implementing swipe to delete to prevent accidental deletes
            }
            
            override fun isLongPressDragEnabled(): Boolean {
                return false // Using handle instead
            }
        })
    }

    private fun addStopToCart(streetId: String, name: String) {
        // Prevent duplicates? Optional. Let's allow for now.
        val newItem = StopConfigItem(
            streetId = streetId,
            stopName = name,
            selectedLines = emptyList() // Default: All lines
        )
        selectedItems.add(newItem)
        adapter.notifyItemInserted(selectedItems.size - 1)
        updateEmptyState()
    }
    
    private fun updateEmptyState() {
        val emptyText = findViewById<android.widget.TextView>(R.id.empty_state_text)
        emptyText.visibility = if (selectedItems.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun showLineFilterDialog(item: StopConfigItem, position: Int) {
        // 1. Get All Lines from Static DB
        val staticLines = stopRepo.getLinesForStop(item.streetId).toSortedSet()
        
        if (staticLines.isEmpty()) {
             Toast.makeText(this, "No lines found for this stop.", Toast.LENGTH_SHORT).show()
             return
        }
        
        val linesArray = staticLines.toTypedArray()
        val checkedItems = BooleanArray(linesArray.size)
        
        // 2. Pre-check selected lines
        // If selectedLines is empty, it means ALL are selected conceptually, 
        // BUT for the dialog loop, we might want to check all or check none.
        // Let's say: Empty list in storage = "All Allowed".
        // In UI: We usually want to show explicit checks.
        // Strategy: If empty list, check ALL. User can then uncheck some.
        // If not empty, check only those.
        
        val isAllSelected = item.selectedLines.isEmpty()
        
        for (i in linesArray.indices) {
            if (isAllSelected || item.selectedLines.contains(linesArray[i])) {
                checkedItems[i] = true
            }
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Select Lines for ${item.stopName}")
            .setMultiChoiceItems(linesArray, checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }
            .setPositiveButton("Apply") { _, _ ->
                val newSelection = mutableListOf<String>()
                var allChecked = true
                for (i in linesArray.indices) {
                    if (checkedItems[i]) {
                        newSelection.add(linesArray[i])
                    } else {
                        allChecked = false
                    }
                }
                
                // Optimization: If ALL are checked, save as empty list
                val finalSelection = if (allChecked) emptyList() else newSelection
                
                // Update Item
                val updatedItem = item.copy(selectedLines = finalSelection)
                selectedItems[position] = updatedItem
                adapter.notifyItemChanged(position)
            }
            .setNeutralButton("Deselect All") { dialog, _ ->
                 // Manually clear check UI
                 val listView = (dialog as androidx.appcompat.app.AlertDialog).listView
                 for (i in linesArray.indices) {
                     listView.setItemChecked(i, false)
                     checkedItems[i] = false
                 }
                 
                 // Logic: "Deselect All" means user wants to clear filters or start over.
                 // In our current logic, empty list = "All Lines".
                 // So we reset to empty list.
                 
                 Toast.makeText(this, "Filters Cleared (Showing All)", Toast.LENGTH_SHORT).show()
                 
                 val clearedItem = item.copy(selectedLines = emptyList())
                 selectedItems[position] = clearedItem
                 adapter.notifyItemChanged(position)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadConfiguration() {
        val items = configRepo.getSmartConfig(widgetId)
        selectedItems.clear()
        selectedItems.addAll(items)
        adapter.notifyDataSetChanged()
        updateEmptyState()
    }

    private fun saveConfiguration() {
        if (selectedItems.isEmpty()) {
            Toast.makeText(this, "Please add at least one stop", Toast.LENGTH_SHORT).show()
            return
        }

        saveButton.isEnabled = false
        saveButton.text = getString(R.string.save)

        CoroutineScope(Dispatchers.IO).launch {
            // 1. Serialize to JSON
            val gson = Gson()
            val configJson = gson.toJson(selectedItems)

            // 2. Legacy Fallback Fields (First stop only, for compatibility)
            val legacyCode = selectedItems.joinToString(",") { it.streetId }
            val legacyName = selectedItems.joinToString(",") { it.stopName }

            val config = WidgetConfig(
                widgetId = widgetId,
                stopCode = legacyCode,
                stopName = legacyName,
                lineFilters = "",
                configJson = configJson
            )

            configRepo.saveConfig(config)

            // 3. Update Widget
            val intent = Intent(this@WidgetConfigActivity, BusWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(widgetId))
            }
            sendBroadcast(intent)

            // 4. Finish
            CoroutineScope(Dispatchers.Main).launch {
                val resultIntent = Intent().apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                }
                setResult(RESULT_OK, resultIntent)
                finish()
            }
        }
    }

    private fun saveConfiguration() {
        if (selectedItems.isEmpty()) {
            Toast.makeText(this, "Please add at least one stop", Toast.LENGTH_SHORT).show()
            return
        }
        
        saveButton.isEnabled = false
        saveButton.text = getString(R.string.save)
        
        CoroutineScope(Dispatchers.IO).launch {
            // 1. Serialize to JSON
            val gson = Gson()
            val configJson = gson.toJson(selectedItems)
            
            // 2. Legacy Fallback Fields (First stop only, for compatibility)
            val first = selectedItems.first()
            val legacyCode = selectedItems.joinToString(",") { it.streetId }
            val legacyName = selectedItems.joinToString(",") { it.stopName }
            
            val config = WidgetConfig(
                widgetId = widgetId,
                stopCode = legacyCode,
                stopName = legacyName,
                lineFilters = "", // Legacy field ignored in favor of JSON
                configJson = configJson
            )
            
            configRepo.saveConfig(config)
            
            // 3. Update Widget
            val intent = Intent(this@WidgetConfigActivity, BusWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(widgetId))
            }
            sendBroadcast(intent)
            
            // 4. Finish
            CoroutineScope(Dispatchers.Main).launch {
                val resultIntent = Intent().apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                }
                setResult(RESULT_OK, resultIntent)
                finish()
            }
        }
    }
}
