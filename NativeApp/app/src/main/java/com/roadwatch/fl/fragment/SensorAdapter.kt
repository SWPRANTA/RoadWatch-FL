package com.roadwatch.fl.fragment

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch
import com.roadwatch.fl.R

class SensorAdapter(
    private val sensors: List<String>,
    private val enabledSensors: MutableMap<String, Boolean>,
    private val onSensorSelected: (String) -> Unit,
    private val onSensorToggled: (String, Boolean) -> Unit
) : RecyclerView.Adapter<SensorAdapter.ViewHolder>() {

    private var selectedSensor: String? = null

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view as MaterialCardView
        val icon: ImageView = view.findViewById(R.id.sensor_icon)
        val name: TextView = view.findViewById(R.id.sensor_name)
        val toggle: MaterialSwitch = view.findViewById(R.id.sensor_toggle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_sensor, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val sensor = sensors[position]
        holder.name.text = when (sensor) {
            "Rotation Vector", "RotationVector" -> "Rot Vector"
            else -> sensor
        }
        holder.icon.setImageResource(getSensorIcon(sensor))

        val isEnabled = enabledSensors[sensor] ?: true
        holder.itemView.alpha = if (isEnabled) 1.0f else 0.45f

        // Set toggle state without triggering listener
        holder.toggle.setOnCheckedChangeListener(null)
        holder.toggle.isChecked = isEnabled

        holder.toggle.setOnCheckedChangeListener { _, isChecked ->
            enabledSensors[sensor] = isChecked
            holder.itemView.alpha = if (isChecked) 1.0f else 0.45f
            onSensorToggled(sensor, isChecked)
        }

        holder.itemView.setOnClickListener {
            selectedSensor = sensor
            onSensorSelected(sensor)
            notifyDataSetChanged()
        }

        val context = holder.card.context
        val density = context.resources.displayMetrics.density
        if (sensor == selectedSensor) {
            holder.card.setCardBackgroundColor(ContextCompat.getColor(context, R.color.primary_container))
            holder.card.strokeColor = ContextCompat.getColor(context, R.color.primary)
            holder.card.strokeWidth = (1.5f * density).toInt()
            holder.name.setTextColor(ContextCompat.getColor(context, R.color.primary))
        } else {
            holder.card.setCardBackgroundColor(ContextCompat.getColor(context, R.color.surface))
            holder.card.strokeColor = ContextCompat.getColor(context, R.color.surface_border)
            holder.card.strokeWidth = (1f * density).toInt()
            holder.name.setTextColor(ContextCompat.getColor(context, R.color.on_surface))
        }
    }

    override fun getItemCount() = sensors.size

    private fun getSensorIcon(sensor: String): Int {
        return when (sensor) {
            "Accelerometer" -> R.drawable.ic_sensor_accelerometer
            "Gyroscope" -> R.drawable.ic_sensor_gyroscope
            "Magnetometer" -> R.drawable.ic_sensor_magnetometer
            "Orientation" -> R.drawable.ic_sensor_orientation
            "Gravity" -> R.drawable.ic_sensor_gravity
            "Rotation Vector", "RotationVector" -> R.drawable.ic_sensor_rotation_vector
            "Barometer" -> R.drawable.ic_sensor_barometer
            "LightSensor" -> R.drawable.ic_sensor_light
            "DeviceMotion" -> R.drawable.ic_sensor_device_motion
            "Pedometer" -> R.drawable.ic_sensor_pedometer
            "Location" -> R.drawable.ic_sensor_location
            else -> R.drawable.ic_sensor_accelerometer
        }
    }
}
