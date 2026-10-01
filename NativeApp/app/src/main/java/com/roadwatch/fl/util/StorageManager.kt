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

    fun saveSession(data: List<SensorData>): File {
        val timestamp = System.currentTimeMillis()
        val filename = "recording_all_sensors_$timestamp.csv"
        val file = File(getRecordingsDir(), filename)

        FileWriter(file).use { writer ->
            writer.write("timestamp,acc_x,acc_y,acc_z,gyro_x,gyro_y,gyro_z,mag_x,mag_y,mag_z,pressure,illuminance,dm_alpha,dm_beta,dm_gamma,step_count,latitude,longitude,speed,altitude,orient_azimuth,orient_pitch,orient_roll,grav_x,grav_y,grav_z,rot_x,rot_y,rot_z,rot_scalar,label,motion_state\n")
            for (d in data) {
                writer.write("${d.timestamp},${d.accX},${d.accY},${d.accZ},${d.gyroX},${d.gyroY},${d.gyroZ},${d.magX},${d.magY},${d.magZ},${d.pressure},${d.illuminance},${d.dmAlpha},${d.dmBeta},${d.dmGamma},${d.stepCount},${d.latitude ?: ""},${d.longitude ?: ""},${d.speed ?: ""},${d.altitude ?: ""},${d.orientAzimuth},${d.orientPitch},${d.orientRoll},${d.gravX},${d.gravY},${d.gravZ},${d.rotX},${d.rotY},${d.rotZ},${d.rotScalar},${d.label},${d.motionState}\n")
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
