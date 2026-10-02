package com.roadwatch.fl.util

import android.content.Context
import android.os.Environment
import com.roadwatch.fl.model.SensorData
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StorageManager(private val context: Context) {

    private fun getRecordingsDir(): File {
        val dir = File(context.getExternalFilesDir(null), "recordings")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getDeviceName(): String {
        val prefs = context.getSharedPreferences("roadwatch_prefs", Context.MODE_PRIVATE)
        return prefs.getString("device_name", "")?.trim() ?: ""
    }

    fun setDeviceName(name: String) {
        val prefs = context.getSharedPreferences("roadwatch_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("device_name", name.trim()).apply()
    }

    fun saveSession(data: List<SensorData>): File {
        val timestamp = System.currentTimeMillis()
        val rawDevice = getDeviceName()
        val sanitized = rawDevice.replace(Regex("[^a-zA-Z0-9._-]"), "_").trim('_')
        val prefix = if (sanitized.isNotEmpty()) "${sanitized}_" else ""
        val filename = "${prefix}recording_all_sensors_$timestamp.csv"
        val file = File(getRecordingsDir(), filename)

        FileWriter(file).use { writer ->
            writer.write("timestamp,elapsed_time_ms,acc_x,acc_y,acc_z,gyro_x,gyro_y,gyro_z,mag_x,mag_y,mag_z,pressure,illuminance,dm_alpha,dm_beta,dm_gamma,step_count,latitude,longitude,speed,altitude,orient_azimuth,orient_pitch,orient_roll,grav_x,grav_y,grav_z,rot_x,rot_y,rot_z,rot_scalar,label,motion_state\n")
            for (d in data) {
                writer.write("${d.timestamp},${d.elapsedMs},${d.accX},${d.accY},${d.accZ},${d.gyroX},${d.gyroY},${d.gyroZ},${d.magX},${d.magY},${d.magZ},${d.pressure},${d.illuminance},${d.dmAlpha},${d.dmBeta},${d.dmGamma},${d.stepCount},${d.latitude ?: ""},${d.longitude ?: ""},${d.speed ?: ""},${d.altitude ?: ""},${d.orientAzimuth},${d.orientPitch},${d.orientRoll},${d.gravX},${d.gravY},${d.gravZ},${d.rotX},${d.rotY},${d.rotZ},${d.rotScalar},${d.label},${d.motionState}\n")
            }
        }

        return file
    }

    fun getRecordings(): List<RecordingFile> {
        val dir = getRecordingsDir()
        return dir.listFiles()
            ?.filter { it.extension == "csv" }
            ?.map { RecordingFile(it.absolutePath, it.name, it.length(), it.lastModified()) }
            ?.sortedByDescending { it.modificationTime }
            ?: emptyList()
    }

    fun deleteRecording(path: String): Boolean {
        return File(path).delete()
    }

    fun renameRecording(oldPath: String, newName: String): Pair<Boolean, String> {
        val oldFile = File(oldPath)
        if (!oldFile.exists()) {
            return Pair(false, "Original file does not exist")
        }

        var sanitizedName = newName.trim()
        if (!sanitizedName.endsWith(".csv", ignoreCase = true)) {
            sanitizedName += ".csv"
        }

        val illegalChars = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
        if (sanitizedName.any { it in illegalChars }) {
            return Pair(false, "Filename contains invalid characters")
        }

        if (sanitizedName.equals(".csv", ignoreCase = true)) {
            return Pair(false, "Filename cannot be empty")
        }

        val targetFile = File(oldFile.parentFile, sanitizedName)
        if (targetFile.exists() && !targetFile.canonicalPath.equals(oldFile.canonicalPath, ignoreCase = true)) {
            return Pair(false, "A file named '$sanitizedName' already exists")
        }

        return try {
            val renamed = oldFile.renameTo(targetFile)
            if (renamed) {
                Pair(true, targetFile.name)
            } else {
                Pair(false, "Storage permission or filesystem error")
            }
        } catch (e: Exception) {
            Pair(false, e.message ?: "Unknown error")
        }
    }

    fun readRecordingContent(path: String, maxLines: Int = 10): String {
        val file = File(path)
        if (!file.exists()) return ""
        return file.readLines().take(maxLines + 1).joinToString("\n")
    }

    data class RecordingFile(
        val uri: String,
        val name: String,
        val size: Long,
        val modificationTime: Long
    )
}
