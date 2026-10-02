package com.roadwatch.fl.fragment

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
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
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.roadwatch.fl.MainActivity
import com.roadwatch.fl.R
import com.roadwatch.fl.bluetooth.BluetoothSyncManager
import com.roadwatch.fl.model.SensorData
import com.roadwatch.fl.service.SensorService

class HomeFragment : Fragment() {

    private var isRecording = false
    private var isPaused = false
    private var selectedSensor = "Accelerometer"
    private var currentLabel = "normal"
    private var currentMotionState = "moving"
    private var isRemoteUpdate = false

    private val sensors = listOf(
        "Accelerometer",
        "Gyroscope",
        "Magnetometer",
        "Orientation",
        "Gravity",
        "Rotation Vector",
        "Barometer",
        "LightSensor",
        "DeviceMotion",
        "Pedometer",
        "Location"
    )
    private val enabledSensors = mutableMapOf<String, Boolean>().apply {
        sensors.forEach { put(it, true) }
    }

    private lateinit var sensorGrid: RecyclerView
    private lateinit var tvData: TextView
    private lateinit var tvTimestamp: TextView
    private lateinit var btnRecord: MaterialButton
    private lateinit var btnPause: MaterialButton
    private lateinit var chipGroup: ChipGroup
    private lateinit var btnAddLabel: MaterialButton
    private lateinit var toggleMotionState: MaterialButtonToggleGroup
    private lateinit var btnStateMoving: MaterialButton
    private lateinit var btnStateStopped: MaterialButton

    // Recording Frequency Card Views
    private lateinit var tvStatusBadge: TextView
    private lateinit var tvBtSyncBadge: TextView
    private lateinit var tvLiveFreq: TextView
    private lateinit var tvTargetFreq: TextView
    private lateinit var tvSampleCount: TextView
    private lateinit var tvActiveLabel: TextView

    private val PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACTIVITY_RECOGNITION
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sensorGrid = view.findViewById(R.id.sensor_grid)
        tvData = view.findViewById(R.id.tv_data)
        tvTimestamp = view.findViewById(R.id.tv_timestamp)
        btnRecord = view.findViewById(R.id.btn_record)
        btnPause = view.findViewById(R.id.btn_pause)
        chipGroup = view.findViewById(R.id.chip_group)
        btnAddLabel = view.findViewById(R.id.btn_add_label)
        toggleMotionState = view.findViewById(R.id.toggle_motion_state)
        btnStateMoving = view.findViewById(R.id.btn_state_moving)
        btnStateStopped = view.findViewById(R.id.btn_state_stopped)

        tvStatusBadge = view.findViewById(R.id.tv_status_badge)
        tvBtSyncBadge = view.findViewById(R.id.tv_bt_sync_badge)
        tvLiveFreq = view.findViewById(R.id.tv_live_freq)
        tvTargetFreq = view.findViewById(R.id.tv_target_freq)
        tvSampleCount = view.findViewById(R.id.tv_sample_count)
        tvActiveLabel = view.findViewById(R.id.tv_active_label)

        checkPermissions()
        setupSensorGrid()
        setupLabels()
        setupButtons()
        setupMotionStateToggle()
        setupAddLabelButton()
        setupBluetoothSyncBadge()
        syncServiceState()
        updateActiveLabelDisplay()
    }

    override fun onResume() {
        super.onResume()
        (activity as? MainActivity)?.getSensorService()?.refreshLocationUpdates()
        syncServiceState()
        updateDataDisplay()
        updateActiveLabelDisplay()
        updateMotionStateButtonsUI()
    }

    fun onRecordingStateChanged(recording: Boolean, paused: Boolean) {
        isRecording = recording
        isPaused = paused
        view?.post {
            if (isAdded) {
                updateButtonStates()
                updateFrequencyCard()
            }
        }
    }

    fun onRemoteMotionStateChanged(state: String) {
        currentMotionState = state
        view?.post {
            if (!isAdded) return@post
            if (::toggleMotionState.isInitialized) {
                val targetId = if (state == "stopped") R.id.btn_state_stopped else R.id.btn_state_moving
                if (toggleMotionState.checkedButtonId != targetId) {
                    isRemoteUpdate = true
                    try {
                        toggleMotionState.check(targetId)
                    } finally {
                        isRemoteUpdate = false
                    }
                }
                updateMotionStateButtonsUI()
            }
        }
    }

    fun onRemoteLabelChanged(label: String) {
        currentLabel = label
        view?.post {
            if (!isAdded) return@post
            updateActiveLabelDisplay()
            if (::chipGroup.isInitialized) {
                isRemoteUpdate = true
                try {
                    var found = false
                    for (i in 0 until chipGroup.childCount) {
                        val chip = chipGroup.getChildAt(i) as? Chip
                        val match = chip?.text.toString().equals(label, ignoreCase = true)
                        if (chip?.isChecked != match) {
                            chip?.isChecked = match
                        }
                        if (match) found = true
                    }
                    if (!found && label != "normal") {
                        val newChip = createLabelChip(label, isCustom = true)
                        chipGroup.addView(newChip)
                        newChip.isChecked = true
                    }
                } finally {
                    isRemoteUpdate = false
                }
            }
        }
    }

    private fun setupBluetoothSyncBadge() {
        val btManager = BluetoothSyncManager.getInstance(requireContext())
        btManager.registerStateListener(btStateListener)
        updateBtSyncBadgeUI(btManager.getConnectionState(), btManager.getConnectedDeviceName())
        tvBtSyncBadge.setOnClickListener {
            val state = btManager.getConnectionState()
            val device = btManager.getConnectedDeviceName()
            val count = btManager.getConnectedPeerCount()
            val info = when (state) {
                BluetoothSyncManager.ConnectionState.CONNECTED -> {
                    if (count > 1) "Synced with $count peers: $device" else "Synced with $device"
                }
                BluetoothSyncManager.ConnectionState.CONNECTING -> "Connecting to $device..."
                BluetoothSyncManager.ConnectionState.LISTENING -> "Waiting for peer connection..."
                BluetoothSyncManager.ConnectionState.DISCONNECTED -> "Sync enabled but disconnected"
                BluetoothSyncManager.ConnectionState.DISABLED -> "Bluetooth Sync is OFF (Configure in Settings)"
            }
            Toast.makeText(context, info, Toast.LENGTH_SHORT).show()
        }
    }

    private val btStateListener: (BluetoothSyncManager.ConnectionState, String?) -> Unit = { state, deviceName ->
        view?.post {
            if (isAdded) {
                updateBtSyncBadgeUI(state, deviceName)
            }
        }
    }

    private fun updateBtSyncBadgeUI(state: BluetoothSyncManager.ConnectionState, deviceName: String?) {
        if (!::tvBtSyncBadge.isInitialized) return
        val btManager = BluetoothSyncManager.getInstance(requireContext())
        when (state) {
            BluetoothSyncManager.ConnectionState.CONNECTED -> {
                tvBtSyncBadge.visibility = View.VISIBLE
                val count = btManager.getConnectedPeerCount()
                if (count > 1) {
                    tvBtSyncBadge.text = "SYNC: $count PEERS"
                } else {
                    val name = deviceName ?: "PEER"
                    val truncated = if (name.length > 10) name.take(9) + "…" else name
                    tvBtSyncBadge.text = "SYNC: $truncated"
                }
                tvBtSyncBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.primary))
            }
            BluetoothSyncManager.ConnectionState.CONNECTING -> {
                tvBtSyncBadge.visibility = View.VISIBLE
                tvBtSyncBadge.text = "SYNC: CONNECTING"
                tvBtSyncBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.tertiary))
            }
            BluetoothSyncManager.ConnectionState.LISTENING -> {
                tvBtSyncBadge.visibility = View.VISIBLE
                tvBtSyncBadge.text = "SYNC: LISTENING"
                tvBtSyncBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.tertiary))
            }
            BluetoothSyncManager.ConnectionState.DISCONNECTED -> {
                tvBtSyncBadge.visibility = View.VISIBLE
                tvBtSyncBadge.text = "SYNC: NO PEER"
                tvBtSyncBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.on_surface_muted))
            }
            BluetoothSyncManager.ConnectionState.DISABLED -> {
                tvBtSyncBadge.visibility = View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        context?.let { ctx ->
            BluetoothSyncManager.getInstance(ctx).unregisterStateListener(btStateListener)
        }
    }

    private fun syncServiceState() {
        val service = (activity as? MainActivity)?.getSensorService()
        if (service != null) {
            isRecording = service.isRecording()
            isPaused = service.isPaused()
            currentMotionState = service.getMotionState()
            currentLabel = service.getLabel()
        }
        if (::toggleMotionState.isInitialized) {
            val targetId = if (currentMotionState == "stopped") R.id.btn_state_stopped else R.id.btn_state_moving
            if (toggleMotionState.checkedButtonId != targetId) {
                toggleMotionState.check(targetId)
            }
            updateMotionStateButtonsUI()
        }
        if (::chipGroup.isInitialized) {
            for (i in 0 until chipGroup.childCount) {
                val chip = chipGroup.getChildAt(i) as? Chip
                val shouldBeChecked = chip?.text.toString().equals(currentLabel, ignoreCase = true)
                if (chip?.isChecked != shouldBeChecked) {
                    chip?.isChecked = shouldBeChecked
                }
            }
        }
        updateButtonStates()
        updateFrequencyCard()
        updateActiveLabelDisplay()
    }

    private fun updateButtonStates() {
        if (!isAdded) return
        if (isRecording) {
            btnRecord.text = "STOP"
            btnRecord.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.error))
            btnRecord.setTextColor(ContextCompat.getColor(requireContext(), R.color.on_error))
            btnPause.visibility = View.VISIBLE
            btnPause.text = if (isPaused) "RESUME" else "PAUSE"
            val pauseBgColor = if (isPaused) R.color.success else R.color.secondary
            btnPause.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), pauseBgColor))
            btnPause.setTextColor(ContextCompat.getColor(requireContext(), R.color.on_primary))
        } else {
            btnRecord.text = "RECORD"
            btnRecord.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.primary))
            btnRecord.setTextColor(ContextCompat.getColor(requireContext(), R.color.on_primary))
            btnPause.visibility = View.GONE
            btnPause.text = "PAUSE"
            btnPause.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.secondary))
            btnPause.setTextColor(ContextCompat.getColor(requireContext(), R.color.on_primary))
        }
    }

    private fun updateFrequencyCard() {
        if (!isAdded) return
        val service = (activity as? MainActivity)?.getSensorService()
        val targetFreq = service?.getSamplingFrequency() ?: 50.0
        val samplingInterval = service?.getSamplingInterval() ?: 20L
        val liveFreq = service?.getLiveFrequency() ?: 0.0
        val sampleCount = service?.getBufferSize() ?: 0

        tvTargetFreq.text = "${"%.1f".format(targetFreq)} Hz (${samplingInterval}ms)"
        tvSampleCount.text = "$sampleCount"

        if (isRecording) {
            if (isPaused) {
                tvStatusBadge.text = "PAUSED"
                tvStatusBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.tertiary))
                tvLiveFreq.text = "0.0 Hz"
                tvLiveFreq.setTextColor(ContextCompat.getColor(requireContext(), R.color.tertiary))
            } else {
                tvStatusBadge.text = "RECORDING"
                tvStatusBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.primary))
                tvLiveFreq.text = "${"%.1f".format(liveFreq)} Hz"
                tvLiveFreq.setTextColor(ContextCompat.getColor(requireContext(), R.color.primary))
            }
        } else {
            tvStatusBadge.text = "STANDBY"
            tvStatusBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.on_surface_variant))
            tvLiveFreq.text = "0.0 Hz"
            tvLiveFreq.setTextColor(ContextCompat.getColor(requireContext(), R.color.on_surface_variant))
        }
    }

    private fun checkPermissions() {
        val missingPermissions = PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            requestPermissions(missingPermissions.toTypedArray(), 100)
        } else {
            (activity as? MainActivity)?.getSensorService()?.refreshLocationUpdates()
        }

        val lm = requireContext().getSystemService(android.location.LocationManager::class.java)
        val isGpsEnabled = lm?.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) == true
        val isNetEnabled = lm?.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER) == true
        if (!isGpsEnabled && !isNetEnabled) {
            Toast.makeText(context, "Location/GPS is disabled on device. Please enable Location in quick settings.", Toast.LENGTH_LONG).show()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            val locationGranted = grantResults.indices.any { i ->
                (permissions[i] == Manifest.permission.ACCESS_FINE_LOCATION ||
                 permissions[i] == Manifest.permission.ACCESS_COARSE_LOCATION) &&
                grantResults[i] == PackageManager.PERMISSION_GRANTED
            }
            if (locationGranted) {
                (activity as? MainActivity)?.getSensorService()?.refreshLocationUpdates()
                updateDataDisplay()
            }
            if (grantResults.any { it != PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(context, "Some permissions denied. App may not work correctly.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupSensorGrid() {
        sensorGrid.layoutManager = GridLayoutManager(context, 4)
        sensorGrid.adapter = SensorAdapter(sensors, enabledSensors,
            onSensorSelected = { sensor ->
                selectedSensor = sensor
                updateDataDisplay()
            },
            onSensorToggled = { sensor, enabled ->
                enabledSensors[sensor] = enabled
                (activity as? MainActivity)?.getSensorService()?.setSensorEnabled(sensor, enabled)
            }
        )
    }

    private fun setupLabels() {
        chipGroup.removeAllViews()
        val defaultLabels = listOf(
            "Pothole", "Speed Breaker", "Uneven Manhole", "Utility Cut",
            "Alligator Cracking", "Raveling", "Edge Drop-off", "Washout",
            "Rutting", "Improvised Bump", "HBB Displacement", "Obstacle"
        )

        defaultLabels.forEach { label ->
            chipGroup.addView(createLabelChip(label, isCustom = false))
        }

        val prefs = requireContext().getSharedPreferences("roadwatch_prefs", Context.MODE_PRIVATE)
        val customLabels = prefs.getStringSet("custom_anomaly_labels", emptySet()) ?: emptySet()
        customLabels.sorted().forEach { label ->
            if (!defaultLabels.any { it.equals(label, ignoreCase = true) }) {
                chipGroup.addView(createLabelChip(label, isCustom = true))
            }
        }
    }

    private fun createLabelChip(label: String, isCustom: Boolean): Chip {
        return Chip(requireContext()).apply {
            text = label
            isCheckable = true
            isChecked = label.equals(currentLabel, ignoreCase = true)
            setChipBackgroundColorResource(R.color.chip_bg_selector)
            setChipStrokeColorResource(R.color.chip_stroke_selector)
            chipStrokeWidth = resources.displayMetrics.density * 1f
            setTextColor(ContextCompat.getColorStateList(requireContext(), R.color.chip_text_selector))

            if (isCustom) {
                isCloseIconVisible = true
                setCloseIconTintResource(R.color.on_surface_muted)
                setOnCloseIconClickListener {
                    val chipLabel = text.toString()
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Remove Custom Label")
                        .setMessage("Do you want to remove the custom label \"$chipLabel\"?")
                        .setPositiveButton("REMOVE") { _, _ ->
                            chipGroup.removeView(this)
                            val p = requireContext().getSharedPreferences("roadwatch_prefs", Context.MODE_PRIVATE)
                            val customSet = p.getStringSet("custom_anomaly_labels", emptySet())?.toMutableSet() ?: mutableSetOf()
                            customSet.remove(chipLabel)
                            p.edit().putStringSet("custom_anomaly_labels", customSet).apply()

                            if (currentLabel.equals(chipLabel, ignoreCase = true)) {
                                currentLabel = "normal"
                                updateActiveLabelDisplay()
                                (activity as? MainActivity)?.getSensorService()?.setLabel("normal")
                            }
                            Toast.makeText(context, "Removed label: $chipLabel", Toast.LENGTH_SHORT).show()
                        }
                        .setNegativeButton("CANCEL", null)
                        .show()
                }
            }

            setOnCheckedChangeListener { _, isChecked ->
                if (isRemoteUpdate) return@setOnCheckedChangeListener
                if (isChecked) {
                    currentLabel = label
                    updateActiveLabelDisplay()
                    (activity as? MainActivity)?.getSensorService()?.setLabel(label)
                    // Uncheck other chips
                    for (i in 0 until chipGroup.childCount) {
                        val otherChip = chipGroup.getChildAt(i) as? Chip
                        if (otherChip != this && otherChip?.isChecked == true) {
                            otherChip.isChecked = false
                        }
                    }
                } else {
                    if (currentLabel.equals(label, ignoreCase = true)) {
                        currentLabel = "normal"
                        updateActiveLabelDisplay()
                        (activity as? MainActivity)?.getSensorService()?.setLabel("normal")
                    }
                }
            }
        }
    }

    private fun setupMotionStateToggle() {
        val initialMotionState = (activity as? MainActivity)?.getSensorService()?.getMotionState() ?: currentMotionState
        val targetId = if (initialMotionState == "stopped") R.id.btn_state_stopped else R.id.btn_state_moving
        toggleMotionState.check(targetId)
        updateMotionStateButtonsUI()

        toggleMotionState.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val state = if (checkedId == R.id.btn_state_stopped) "stopped" else "moving"
                currentMotionState = state
                if (!isRemoteUpdate) {
                    (activity as? MainActivity)?.getSensorService()?.setMotionState(state)
                }
                updateMotionStateButtonsUI()
            }
        }
    }

    private fun updateMotionStateButtonsUI() {
        if (!isAdded || !::btnStateMoving.isInitialized || !::btnStateStopped.isInitialized) return
        val isMoving = currentMotionState == "moving"

        val context = requireContext()
        val successColor = ContextCompat.getColor(context, R.color.success)
        val errorColor = ContextCompat.getColor(context, R.color.error)
        val surfaceColor = ContextCompat.getColor(context, R.color.surface)
        val surfaceBorderColor = ContextCompat.getColor(context, R.color.surface_border)
        val textMutedColor = ContextCompat.getColor(context, R.color.on_surface_muted)
        val whiteColor = ContextCompat.getColor(context, R.color.white)

        if (isMoving) {
            btnStateMoving.backgroundTintList = ColorStateList.valueOf(successColor)
            btnStateMoving.setTextColor(whiteColor)
            btnStateMoving.strokeColor = ColorStateList.valueOf(successColor)
            btnStateMoving.strokeWidth = (resources.displayMetrics.density * 1.5f).toInt()

            btnStateStopped.backgroundTintList = ColorStateList.valueOf(surfaceColor)
            btnStateStopped.setTextColor(textMutedColor)
            btnStateStopped.strokeColor = ColorStateList.valueOf(surfaceBorderColor)
            btnStateStopped.strokeWidth = (resources.displayMetrics.density * 1f).toInt()
        } else {
            btnStateMoving.backgroundTintList = ColorStateList.valueOf(surfaceColor)
            btnStateMoving.setTextColor(textMutedColor)
            btnStateMoving.strokeColor = ColorStateList.valueOf(surfaceBorderColor)
            btnStateMoving.strokeWidth = (resources.displayMetrics.density * 1f).toInt()

            btnStateStopped.backgroundTintList = ColorStateList.valueOf(errorColor)
            btnStateStopped.setTextColor(whiteColor)
            btnStateStopped.strokeColor = ColorStateList.valueOf(errorColor)
            btnStateStopped.strokeWidth = (resources.displayMetrics.density * 1.5f).toInt()
        }
    }

    private fun setupAddLabelButton() {
        btnAddLabel.setOnClickListener {
            showAddLabelDialog()
        }
    }

    private fun showAddLabelDialog() {
        val input = EditText(requireContext()).apply {
            hint = "e.g. Rumble Strip, Speed Bump"
            setSingleLine()
            setTextColor(ContextCompat.getColor(requireContext(), R.color.on_surface))
            setHintTextColor(ContextCompat.getColor(requireContext(), R.color.on_surface_muted))
            textSize = 14f
        }
        val container = FrameLayout(requireContext()).apply {
            val padHorizontal = (24 * resources.displayMetrics.density).toInt()
            val padVertical = (12 * resources.displayMetrics.density).toInt()
            setPadding(padHorizontal, padVertical, padHorizontal, 0)
            addView(input)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Add Anomaly Label")
            .setMessage("Enter the anomaly condition to annotate in datasets:")
            .setView(container)
            .setPositiveButton("ADD") { _, _ ->
                val labelText = input.text.toString().trim()
                if (labelText.isNotEmpty()) {
                    addCustomLabel(labelText)
                }
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun addCustomLabel(label: String) {
        // If already in chipGroup, select it
        for (i in 0 until chipGroup.childCount) {
            val chip = chipGroup.getChildAt(i) as? Chip
            if (chip?.text.toString().equals(label, ignoreCase = true)) {
                chip?.isChecked = true
                Toast.makeText(context, "Label '$label' already exists and is selected.", Toast.LENGTH_SHORT).show()
                return
            }
        }

        // Persist to preferences
        val prefs = requireContext().getSharedPreferences("roadwatch_prefs", Context.MODE_PRIVATE)
        val customSet = prefs.getStringSet("custom_anomaly_labels", emptySet())?.toMutableSet() ?: mutableSetOf()
        customSet.add(label)
        prefs.edit().putStringSet("custom_anomaly_labels", customSet).apply()

        // Add chip & select it
        val newChip = createLabelChip(label, isCustom = true)
        chipGroup.addView(newChip)
        newChip.isChecked = true
        Toast.makeText(context, "Added and selected label: $label", Toast.LENGTH_SHORT).show()
    }

    private fun updateActiveLabelDisplay() {
        if (!isAdded) return
        val displayLabel = if (currentLabel == "normal") "NORMAL" else currentLabel.uppercase()
        tvActiveLabel.text = displayLabel
        val color = if (currentLabel == "normal") {
            ContextCompat.getColor(requireContext(), R.color.on_surface_variant)
        } else {
            ContextCompat.getColor(requireContext(), R.color.primary)
        }
        tvActiveLabel.setTextColor(color)
    }

    private fun setupButtons() {
        btnRecord.setOnClickListener {
            val service = (activity as? MainActivity)?.getSensorService() ?: return@setOnClickListener
            if (!isRecording) {
                val intent = Intent(requireContext(), SensorService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    requireContext().startForegroundService(intent)
                } else {
                    requireContext().startService(intent)
                }
                service.startRecording()
                isRecording = true
                isPaused = false
            } else {
                service.stopRecording()
                isRecording = false
                isPaused = false
            }
            updateButtonStates()
            updateFrequencyCard()
        }

        btnPause.setOnClickListener {
            val service = (activity as? MainActivity)?.getSensorService() ?: return@setOnClickListener
            if (!isPaused) {
                service.pauseRecording()
                isPaused = true
            } else {
                service.resumeRecording()
                isPaused = false
            }
            updateButtonStates()
            updateFrequencyCard()
        }
    }

    private fun updateDataDisplay() {
        if (!isAdded) return
        val service = (activity as? MainActivity)?.getSensorService()
        if (selectedSensor == "Location") {
            val loc = service?.getLatestLocation()
            if (loc != null) {
                val lat = loc.latitude.format(6)
                val lng = loc.longitude.format(6)
                val alt = if (loc.hasAltitude()) "${loc.altitude.format(2)} m" else "N/A"
                val spd = if (loc.hasSpeed()) "${loc.speed.format(2)} m/s" else "0.00 m/s"
                tvData.text = "Lat: $lat\nLng: $lng\nAlt: $alt\nSpeed: $spd"
                tvTimestamp.text = "TS: ${loc.time}"
            } else {
                tvData.text = "Lat: Searching GPS/Network...\nLng: Searching GPS/Network...\nAlt: N/A\nSpeed: 0.00 m/s"
            }
        } else if (service != null) {
            val data = service.getLatestSensorData()
            updateSensorData(data)
        }
    }

    fun updateSensorData(data: SensorData) {
        view?.post {
            if (!isAdded) return@post
            val text = when (selectedSensor) {
                "Accelerometer" -> "X: ${data.accX.format(4)} g\nY: ${data.accY.format(4)} g\nZ: ${data.accZ.format(4)} g"
                "Gyroscope" -> "X: ${data.gyroX.format(4)} rad/s\nY: ${data.gyroY.format(4)} rad/s\nZ: ${data.gyroZ.format(4)} rad/s"
                "Magnetometer" -> "X: ${data.magX.format(4)} uT\nY: ${data.magY.format(4)} uT\nZ: ${data.magZ.format(4)} uT"
                "Orientation" -> "Azimuth (Yaw): ${data.orientAzimuth.format(2)}°\nPitch: ${data.orientPitch.format(2)}°\nRoll: ${data.orientRoll.format(2)}°"
                "Gravity" -> "X: ${data.gravX.format(4)} m/s²\nY: ${data.gravY.format(4)} m/s²\nZ: ${data.gravZ.format(4)} m/s²"
                "Rotation Vector", "RotationVector" -> "X: ${data.rotX.format(4)}\nY: ${data.rotY.format(4)}\nZ: ${data.rotZ.format(4)}\nScalar (W): ${data.rotScalar.format(4)}"
                "Barometer" -> "Pressure: ${data.pressure.format(2)} hPa"
                "LightSensor" -> "Illuminance: ${data.illuminance.format(2)} lx"
                "DeviceMotion" -> "Alpha: ${data.dmAlpha.format(4)} rad\nBeta: ${data.dmBeta.format(4)} rad\nGamma: ${data.dmGamma.format(4)} rad"
                "Pedometer" -> "Steps: ${data.stepCount}"
                "Location" -> {
                    val service = (activity as? MainActivity)?.getSensorService()
                    val loc = service?.getLatestLocation()
                    val lat = data.latitude?.format(6) ?: (loc?.latitude?.format(6) ?: "Searching...")
                    val lng = data.longitude?.format(6) ?: (loc?.longitude?.format(6) ?: "Searching...")
                    val alt = (data.altitude ?: (if (loc?.hasAltitude() == true) loc.altitude else null))?.let { "${it.format(2)} m" } ?: "N/A"
                    val spd = (data.speed ?: (if (loc?.hasSpeed() == true) loc.speed else (if (loc != null) 0f else null)))?.let { "${it.format(2)} m/s" } ?: "0.00 m/s"
                    "Lat: $lat\nLng: $lng\nAlt: $alt\nSpeed: $spd"
                }
                else -> "Select a sensor"
            }
            tvData.text = text
            tvTimestamp.text = "TS: ${data.timestamp}"

            val service = (activity as? MainActivity)?.getSensorService()
            if (isRecording && !isPaused && service != null) {
                val liveFreq = service.getLiveFrequency()
                tvLiveFreq.text = "${"%.1f".format(liveFreq)} Hz"
            }
            val count = service?.getBufferSize() ?: 0
            tvSampleCount.text = "$count"
        }
    }

    private fun Float.format(digits: Int) = "%.${digits}f".format(this)
    private fun Double.format(digits: Int) = "%.${digits}f".format(this)
}
