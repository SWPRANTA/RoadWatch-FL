package com.roadwatch.fl.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.roadwatch.fl.R
import com.roadwatch.fl.dialog.TableViewerDialog
import com.roadwatch.fl.util.FileShareUtils
import com.roadwatch.fl.util.StorageManager
import java.io.File

class RecordingsFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var storageManager: StorageManager
    private var recordings: List<StorageManager.RecordingFile> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_recordings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.recycler_view)
        tvEmpty = view.findViewById(R.id.tv_empty)
        storageManager = StorageManager(requireContext())

        recyclerView.layoutManager = LinearLayoutManager(context)
    }

    override fun onResume() {
        super.onResume()
        loadRecordings()
    }

    private fun loadRecordings() {
        recordings = storageManager.getRecordings()
        if (recordings.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            tvEmpty.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            recyclerView.adapter = RecordingAdapter(recordings,
                onView = { file ->
                    val dialog = TableViewerDialog(
                        requireContext(),
                        file.uri,
                        file.name
                    )
                    dialog.show()
                },
                onRename = { file ->
                    showRenameDialog(file)
                },
                onShare = { file ->
                    FileShareUtils.shareRecordingFile(
                        requireContext(),
                        File(file.uri),
                        file.name
                    )
                },
                onDelete = { file ->
                    showDeleteDialog(file)
                }
            )
        }
    }

    private fun showRenameDialog(file: StorageManager.RecordingFile) {
        val currentBaseName = if (file.name.endsWith(".csv", ignoreCase = true)) {
            file.name.substringBeforeLast(".csv")
        } else {
            file.name
        }

        val input = EditText(requireContext()).apply {
            setText(currentBaseName)
            hint = "New file name"
            setSingleLine()
            setTextColor(ContextCompat.getColor(requireContext(), R.color.on_surface))
            setHintTextColor(ContextCompat.getColor(requireContext(), R.color.on_surface_muted))
            textSize = 14f
            selectAll()
        }

        val container = FrameLayout(requireContext()).apply {
            val padHorizontal = (24 * resources.displayMetrics.density).toInt()
            val padVertical = (12 * resources.displayMetrics.density).toInt()
            setPadding(padHorizontal, padVertical, padHorizontal, 0)
            addView(input)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Rename Recording")
            .setMessage("Enter a new name for this recording (.csv extension is preserved):")
            .setView(container)
            .setPositiveButton("RENAME") { _, _ ->
                val enteredName = input.text.toString().trim()
                if (enteredName.isEmpty()) {
                    Toast.makeText(context, "Filename cannot be empty", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val (success, message) = storageManager.renameRecording(file.uri, enteredName)
                if (success) {
                    Toast.makeText(context, "Renamed to: $message", Toast.LENGTH_SHORT).show()
                    loadRecordings()
                } else {
                    Toast.makeText(context, "Rename failed: $message", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun showDeleteDialog(file: StorageManager.RecordingFile) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Recording")
            .setMessage("Are you sure you want to permanently delete \"${file.name}\"?")
            .setPositiveButton("DELETE") { _, _ ->
                val deleted = storageManager.deleteRecording(file.uri)
                if (deleted) {
                    Toast.makeText(context, "Deleted: ${file.name}", Toast.LENGTH_SHORT).show()
                    loadRecordings()
                } else {
                    Toast.makeText(context, "Failed to delete file", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }
}
