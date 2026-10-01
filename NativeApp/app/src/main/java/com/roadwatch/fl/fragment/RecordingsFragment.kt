package com.roadwatch.fl.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.roadwatch.fl.R
import com.roadwatch.fl.util.StorageManager

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
                    val dialog = com.roadwatch.fl.dialog.TableViewerDialog(
                        requireContext(),
                        file.uri,
                        file.name
                    )
                    dialog.show()
                },
                onShare = { file ->
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/csv"
                        putExtra(Intent.EXTRA_STREAM, android.net.Uri.parse(file.uri))
                    }
                    startActivity(Intent.createChooser(intent, "Share Recording"))
                },
                onDelete = { file ->
                    storageManager.deleteRecording(file.uri)
                    loadRecordings()
                }
            )
        }
    }
}
