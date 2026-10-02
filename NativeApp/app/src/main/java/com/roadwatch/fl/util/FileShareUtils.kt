package com.roadwatch.fl.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object FileShareUtils {

    /**
     * Shares a CSV recording file via standard Android ACTION_SEND intent using FileProvider.
     * Compatible with Google Drive, Telegram, Gmail, WhatsApp, and other file-handling targets.
     */
    fun shareRecordingFile(context: Context, file: File, displayName: String = file.name) {
        if (!file.exists()) {
            Toast.makeText(context, "File does not exist: $displayName", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, displayName)
                putExtra(Intent.EXTRA_TEXT, "RoadWatch FL Sensor Recording: $displayName")
                putExtra(
                    Intent.EXTRA_MIME_TYPES,
                    arrayOf("text/csv", "text/comma-separated-values", "text/plain")
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                // ClipData automatically propagates read URI permission to the chosen target app on modern Android
                clipData = ClipData.newRawUri(displayName, contentUri)
            }

            val chooser = Intent.createChooser(shareIntent, "Share Recording").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            // Explicitly grant read permission to all candidate applications
            val resInfoList = context.packageManager.queryIntentActivities(
                chooser,
                PackageManager.MATCH_DEFAULT_ONLY
            )
            for (resolveInfo in resInfoList) {
                val packageName = resolveInfo.activityInfo.packageName
                context.grantUriPermission(
                    packageName,
                    contentUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }

            context.startActivity(chooser)
        } catch (e: Exception) {
            android.util.Log.e("FileShareUtils", "Error sharing file: ${file.absolutePath}", e)
            Toast.makeText(context, "Failed to share: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
