package com.roadwatch.fl.fragment

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.roadwatch.fl.R
import com.roadwatch.fl.util.StorageManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecordingAdapter(
    private val recordings: List<StorageManager.RecordingFile>,
    private val onView: (StorageManager.RecordingFile) -> Unit,
    private val onRename: (StorageManager.RecordingFile) -> Unit,
    private val onShare: (StorageManager.RecordingFile) -> Unit,
    private val onDelete: (StorageManager.RecordingFile) -> Unit
) : RecyclerView.Adapter<RecordingAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.file_name)
        val details: TextView = view.findViewById(R.id.file_details)
        val btnView: MaterialButton = view.findViewById(R.id.btn_view)
        val btnRename: MaterialButton = view.findViewById(R.id.btn_rename)
        val btnShare: MaterialButton = view.findViewById(R.id.btn_share)
        val btnDelete: MaterialButton = view.findViewById(R.id.btn_delete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recording, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val file = recordings[position]
        holder.name.text = file.name

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val date = dateFormat.format(Date(file.modificationTime))
        val size = formatFileSize(file.size)
        holder.details.text = "$size - $date"

        holder.btnView.setOnClickListener { onView(file) }
        holder.btnRename.setOnClickListener { onRename(file) }
        holder.btnShare.setOnClickListener { onShare(file) }
        holder.btnDelete.setOnClickListener { onDelete(file) }
    }

    override fun getItemCount() = recordings.size

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "${bytes / (1024 * 1024)} MB"
        }
    }
}
