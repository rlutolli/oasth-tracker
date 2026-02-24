package com.oasth.widget.widget

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.oasth.widget.R
import com.oasth.widget.data.StopConfigItem
import java.util.Collections

class SelectedStopsAdapter(
    private val items: MutableList<StopConfigItem>,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit,
    private val onDelete: (Int) -> Unit,
    private val onEdit: (StopConfigItem, Int) -> Unit
) : RecyclerView.Adapter<SelectedStopsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.tv_stop_name)
        val codeText: TextView = view.findViewById(R.id.tv_stop_code)
        val linesText: TextView = view.findViewById(R.id.tv_lines)
        val dragHandle: ImageView = view.findViewById(R.id.drag_handle)
        val deleteButton: ImageButton = view.findViewById(R.id.btn_delete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_selected_stop, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        
        holder.nameText.text = item.stopName
        holder.codeText.text = "Code: ${item.streetId}"
        
        val linesSummary = if (item.selectedLines.isEmpty()) {
            "Lines: All"
        } else {
            "Lines: ${item.selectedLines.joinToString(", ")}"
        }
        holder.linesText.text = linesSummary

        // Drag Handler
        holder.dragHandle.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                onStartDrag(holder)
            }
            false
        }

        // Delete Handler
        holder.deleteButton.setOnClickListener {
            onDelete(position)
        }
        
        // Edit Handler (Click anywhere else)
        holder.itemView.setOnClickListener {
            onEdit(item, position)
        }
    }

    override fun getItemCount() = items.size

    // Helper for DnD
    fun onItemMove(fromPosition: Int, toPosition: Int) {
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(items, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(items, i, i - 1)
            }
        }
        notifyItemMoved(fromPosition, toPosition)
    }
}
