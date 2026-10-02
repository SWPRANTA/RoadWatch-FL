package com.roadwatch.fl.fragment

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.roadwatch.fl.MainActivity
import com.roadwatch.fl.R
import com.roadwatch.fl.bluetooth.BluetoothSyncManager

class SettingsFragment : Fragment(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var hardwareInterval: Int = 20
    private var samplingInterval: Int = 20

    private lateinit var etHwInterval: TextInputEditText
    private lateinit var etSwInterval: TextInputEditText
    private lateinit var etDeviceName: TextInputEditText
    private lateinit var tvHwFreq: TextView
    private lateinit var tvSwFreq: TextView
    private lateinit var tvPreviewFilename: TextView
    private lateinit var chipGroup: ChipGroup
    private lateinit var tvTestResult: TextView

    // Bluetooth Sync Views
    private lateinit var switchBtSync: MaterialSwitch
    private lateinit var layoutBtSyncDetails: View
    private lateinit var tvBtStatus: TextView
    private lateinit var btnBtConnect: MaterialButton
    private lateinit var btnBtDisconnect: MaterialButton
    private lateinit var dividerBtLatency: View
    private lateinit var layoutBtLatency: View
    private lateinit var btnTestBtLatency: MaterialButton
    private lateinit var tvBtLatencyResult: TextView
    private lateinit var bluetoothSyncManager: BluetoothSyncManager

    private val handler = Handler(Looper.getMainLooper())
    private var testCount = 0
    private var testResults = mutableListOf<Int>()

    companion object {
        private const val REQUEST_BT_PERMS = 201
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sensorManager = requireContext().getSystemService(SensorManager::class.java)

        etHwInterval = view.findViewById(R.id.et_hw_interval)
        etSwInterval = view.findViewById(R.id.et_sw_interval)
        etDeviceName = view.findViewById(R.id.et_device_name)
        tvHwFreq = view.findViewById(R.id.tv_hw_freq)
        tvSwFreq = view.findViewById(R.id.tv_sw_freq)
        tvPreviewFilename = view.findViewById(R.id.tv_preview_filename)
        chipGroup = view.findViewById(R.id.chip_group)
        tvTestResult = view.findViewById(R.id.tv_test_result)

        bluetoothSyncManager = BluetoothSyncManager.getInstance(requireContext())
        switchBtSync = view.findViewById(R.id.switch_bt_sync)
        layoutBtSyncDetails = view.findViewById(R.id.layout_bt_sync_details)
        tvBtStatus = view.findViewById(R.id.tv_bt_status)
        btnBtConnect = view.findViewById(R.id.btn_bt_connect)
        btnBtDisconnect = view.findViewById(R.id.btn_bt_disconnect)
        dividerBtLatency = view.findViewById(R.id.divider_bt_latency)
        layoutBtLatency = view.findViewById(R.id.layout_bt_latency)
        btnTestBtLatency = view.findViewById(R.id.btn_test_bt_latency)
        tvBtLatencyResult = view.findViewById(R.id.tv_bt_latency_result)

        setupBluetoothSyncUI()

        val storageManager = com.roadwatch.fl.util.StorageManager(requireContext())
        val savedDeviceName = storageManager.getDeviceName()
        etDeviceName.setText(savedDeviceName)
        updatePreviewFilename(savedDeviceName)

        etDeviceName.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updatePreviewFilename(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        val service = (activity as? MainActivity)?.getSensorService()
        if (service != null) {
            val hw = service.getHardwareInterval()
            if (hw > 0) hardwareInterval = hw
            val sw = service.getSamplingInterval().toInt()
            if (sw > 0) samplingInterval = sw
        }

        etHwInterval.setText(hardwareInterval.toString())
        etSwInterval.setText(samplingInterval.toString())

        updateFrequencyDisplay()
        setupQuickSelect()
        setupButtons()
    }

    private fun setupQuickSelect() {
        val frequencies = listOf(
            "50 Hz" to 20,
            "100 Hz" to 10,
            "200 Hz" to 5,
            "500 Hz" to 2
        )

        frequencies.forEach { (label, interval) ->
            val chip = Chip(requireContext()).apply {
                text = label
                isCheckable = true
                isChecked = hardwareInterval == interval
                setChipBackgroundColorResource(R.color.chip_bg_selector)
                setChipStrokeColorResource(R.color.chip_stroke_selector)
                chipStrokeWidth = resources.displayMetrics.density * 1f
                setTextColor(androidx.core.content.ContextCompat.getColorStateList(requireContext(), R.color.chip_text_selector))
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        hardwareInterval = interval
                        samplingInterval = interval
                        etHwInterval.setText(interval.toString())
                        etSwInterval.setText(interval.toString())
                        updateFrequencyDisplay()
                        // Uncheck other chips
                        for (i in 0 until chipGroup.childCount) {
                            val other = chipGroup.getChildAt(i) as? Chip
                            if (other != this && other?.isChecked == true) {
                                other.isChecked = false
                            }
                        }
                    }
                }
            }
            chipGroup.addView(chip)
        }
    }

    private fun setupButtons() {
        view?.findViewById<MaterialButton>(R.id.btn_apply)?.setOnClickListener {
            val hw = etHwInterval.text.toString().toIntOrNull()
            val sw = etSwInterval.text.toString().toIntOrNull()
            val deviceName = etDeviceName.text?.toString()?.trim() ?: ""

            val storageManager = com.roadwatch.fl.util.StorageManager(requireContext())
            storageManager.setDeviceName(deviceName)

            if (hw != null && sw != null && hw in 1..1000 && sw in 1..1000) {
                hardwareInterval = hw
                samplingInterval = sw
                (activity as? MainActivity)?.getSensorService()?.setHardwareInterval(hw)
                (activity as? MainActivity)?.getSensorService()?.setSamplingInterval(sw.toLong())
                updateFrequencyDisplay()
            }
            android.widget.Toast.makeText(requireContext(), "Settings saved successfully", android.widget.Toast.LENGTH_SHORT).show()
        }

        view?.findViewById<MaterialButton>(R.id.btn_reset)?.setOnClickListener {
            hardwareInterval = 20
            samplingInterval = 20
            etHwInterval.setText("20")
            etSwInterval.setText("20")
            etDeviceName.setText("")
            updatePreviewFilename("")
            updateFrequencyDisplay()
        }

        view?.findViewById<MaterialButton>(R.id.btn_test)?.setOnClickListener {
            runMaxFrequencyTest()
        }
    }

    private fun updateFrequencyDisplay() {
        val hw = etHwInterval.text.toString().toIntOrNull() ?: hardwareInterval
        val sw = etSwInterval.text.toString().toIntOrNull() ?: samplingInterval
        tvHwFreq.text = "${(1000.0 / hw).format(1)} Hz"
        tvSwFreq.text = "${(1000.0 / sw).format(1)} Hz"
    }

    private fun updatePreviewFilename(name: String) {
        val sanitized = name.trim().replace(Regex("[^a-zA-Z0-9._-]"), "_").trim('_')
        val prefix = if (sanitized.isNotEmpty()) "${sanitized}_" else ""
        tvPreviewFilename.text = "Preview: ${prefix}recording_all_sensors_....csv"
    }

    private fun runMaxFrequencyTest() {
        tvTestResult.text = "Testing..."
        testResults.clear()

        val acc = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: run {
            tvTestResult.text = "Accelerometer not available"
            return
        }

        // Run 3 tests
        runTest(acc, 0)
    }

    private fun runTest(sensor: Sensor, runIndex: Int) {
        testCount = 0
        sensorManager.registerListener(this, sensor, 1000) // 1ms

        handler.postDelayed({
            sensorManager.unregisterListener(this)
            testResults.add(testCount)

            if (runIndex < 2) {
                // Next run
                handler.postDelayed({
                    runTest(sensor, runIndex + 1)
                }, 200)
            } else {
                // Show results
                val best = testResults.maxOrNull() ?: 0
                val worst = testResults.minOrNull() ?: 0
                val avg = testResults.average().toInt()
                val maxInterval = if (best > 0) Math.max(1, 1000 / best) else 20

                tvTestResult.text = "Best: $best Hz | Avg: $avg Hz | Worst: $worst Hz\nRecommended interval: ${maxInterval}ms ($best Hz)"
            }
        }, 3500) // 3s test + 0.5s warmup
    }

    override fun onSensorChanged(event: SensorEvent) {
        testCount++
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun setupBluetoothSyncUI() {
        val isSyncOn = bluetoothSyncManager.isSyncEnabled()
        switchBtSync.isChecked = isSyncOn
        layoutBtSyncDetails.visibility = if (isSyncOn) View.VISIBLE else View.GONE

        switchBtSync.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!checkAndRequestBtPermissions()) {
                    switchBtSync.isChecked = false
                    return@setOnCheckedChangeListener
                }
                bluetoothSyncManager.setSyncEnabled(true)
                layoutBtSyncDetails.visibility = View.VISIBLE
                val service = (activity as? MainActivity)?.getSensorService()
                service?.syncBluetoothServiceState()
            } else {
                bluetoothSyncManager.setSyncEnabled(false)
                layoutBtSyncDetails.visibility = View.GONE
                val service = (activity as? MainActivity)?.getSensorService()
                service?.syncBluetoothServiceState()
            }
        }

        btnBtConnect.setOnClickListener {
            if (!checkAndRequestBtPermissions()) return@setOnClickListener
            if (!bluetoothSyncManager.isBluetoothHardwareEnabled()) {
                Toast.makeText(requireContext(), "Please turn ON Bluetooth in device quick settings first", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            showPairedDevicesDialog()
        }

        btnBtDisconnect.setOnClickListener {
            bluetoothSyncManager.disconnect()
            Toast.makeText(requireContext(), "Disconnected from peer", Toast.LENGTH_SHORT).show()
        }

        btnTestBtLatency.setOnClickListener {
            runBluetoothLatencyBenchmark()
        }

        bluetoothSyncManager.registerStateListener(btStateListener)
        updateBtUI(bluetoothSyncManager.getConnectionState(), bluetoothSyncManager.getConnectedDeviceName())
    }

    private val btStateListener: (BluetoothSyncManager.ConnectionState, String?) -> Unit = { state, deviceName ->
        view?.post {
            if (isAdded) {
                updateBtUI(state, deviceName)
            }
        }
    }

    private fun updateBtUI(state: BluetoothSyncManager.ConnectionState, deviceName: String?) {
        if (!::tvBtStatus.isInitialized) return
        when (state) {
            BluetoothSyncManager.ConnectionState.CONNECTED -> {
                val count = bluetoothSyncManager.getConnectedPeerCount()
                tvBtStatus.text = "Connected ($count): ${deviceName ?: "Peers"}"
                tvBtStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.primary))
                btnBtConnect.visibility = View.VISIBLE
                btnBtConnect.text = "ADD / CONNECT PEER"
                btnBtDisconnect.visibility = View.VISIBLE
                btnBtDisconnect.text = if (count > 1) "DISCONNECT ALL ($count)" else "DISCONNECT"
                dividerBtLatency.visibility = View.VISIBLE
                layoutBtLatency.visibility = View.VISIBLE
            }
            BluetoothSyncManager.ConnectionState.CONNECTING -> {
                tvBtStatus.text = "Connecting to: ${deviceName ?: "Peer"}..."
                tvBtStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.tertiary))
                btnBtConnect.visibility = View.VISIBLE
                btnBtConnect.text = "CONNECT TO PEER"
                btnBtDisconnect.visibility = View.VISIBLE
                btnBtDisconnect.text = "CANCEL"
                dividerBtLatency.visibility = View.GONE
                layoutBtLatency.visibility = View.GONE
            }
            BluetoothSyncManager.ConnectionState.LISTENING -> {
                tvBtStatus.text = "Listening for peer connections..."
                tvBtStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.tertiary))
                btnBtConnect.visibility = View.VISIBLE
                btnBtConnect.text = "CONNECT TO PEER"
                btnBtDisconnect.visibility = View.GONE
                dividerBtLatency.visibility = View.GONE
                layoutBtLatency.visibility = View.GONE
            }
            BluetoothSyncManager.ConnectionState.DISCONNECTED -> {
                tvBtStatus.text = "Disconnected (Ready to connect)"
                tvBtStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.on_surface_variant))
                btnBtConnect.visibility = View.VISIBLE
                btnBtConnect.text = "CONNECT TO PEER"
                btnBtDisconnect.visibility = View.GONE
                dividerBtLatency.visibility = View.GONE
                layoutBtLatency.visibility = View.GONE
            }
            BluetoothSyncManager.ConnectionState.DISABLED -> {
                tvBtStatus.text = "Feature disabled"
                tvBtStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.on_surface_muted))
                btnBtConnect.visibility = View.VISIBLE
                btnBtConnect.text = "CONNECT TO PEER"
                btnBtDisconnect.visibility = View.GONE
                dividerBtLatency.visibility = View.GONE
                layoutBtLatency.visibility = View.GONE
            }
        }
    }

    private fun runBluetoothLatencyBenchmark() {
        btnTestBtLatency.isEnabled = false
        btnTestBtLatency.text = "TESTING..."
        val summary = bluetoothSyncManager.getConnectedDeviceSummary()
        tvBtLatencyResult.text = "Initiating ping sequence to $summary..."

        bluetoothSyncManager.runLatencyTest(
            totalPings = 5,
            onProgress = { current, total, rttMs ->
                tvBtLatencyResult.text = "Pinging peer ($current/$total)... RTT: ${rttMs} ms"
            },
            onComplete = { result ->
                btnTestBtLatency.isEnabled = true
                btnTestBtLatency.text = "TEST BLUETOOTH LATENCY"

                val samplePeriodMs = 10.0 // 100 Hz = 10 ms per sample
                val sampleDelay = result.oneWayLatencyMs / samplePeriodMs
                val offsetSign = if (result.avgClockOffsetMs >= 0) "+" else ""
                val offsetText = "${offsetSign}${"%.1f".format(result.avgClockOffsetMs)} ms"
                val impactText = if (sampleDelay < 1.0) {
                    "< 1 sample delay (${"%.1f".format(sampleDelay * 100)}% of 1 sample period)"
                } else {
                    "~${"%.1f".format(sampleDelay)} samples delay"
                }

                val sb = StringBuilder()
                val targetName = result.peerName.ifBlank { "Peer" }
                sb.append("LATENCY BENCHMARK RESULT ($targetName):\n")
                sb.append("• Round-Trip Time (RTT): Avg ${"%.1f".format(result.avgRttMs)} ms (Min: ${result.minRttMs} ms, Max: ${result.maxRttMs} ms)\n")
                sb.append("• One-Way Trigger Delay: ~${"%.1f".format(result.oneWayLatencyMs)} ms\n")
                sb.append("• Transport Jitter: ±${"%.1f".format(result.jitterMs)} ms\n")
                sb.append("• Clock Offset (Peer vs Local): $offsetText\n")
                sb.append("• Impact at 100 Hz (10ms): $impactText")

                tvBtLatencyResult.text = sb.toString()
            },
            onError = { errorMsg ->
                btnTestBtLatency.isEnabled = true
                btnTestBtLatency.text = "TEST BLUETOOTH LATENCY"
                tvBtLatencyResult.text = "Benchmark failed: $errorMsg"
                Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun checkAndRequestBtPermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val connectGranted = ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
            val scanGranted = ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED

            if (!connectGranted || !scanGranted) {
                requestPermissions(
                    arrayOf(
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN
                    ),
                    REQUEST_BT_PERMS
                )
                return false
            }
        }
        return true
    }

    private fun showPairedDevicesDialog() {
        val pairedDevices = bluetoothSyncManager.getPairedDevices()
        if (pairedDevices.isEmpty()) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("No Paired Devices")
                .setMessage("No paired Bluetooth devices were found.\n\nPlease pair your phones first in Android Settings > Bluetooth, then return here to connect.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val deviceNames = pairedDevices.map { device ->
            val isConn = bluetoothSyncManager.isPeerConnected(device.address)
            val status = if (isConn) " [CONNECTED]" else ""
            val name = try { device.name ?: "Unknown Device" } catch (e: SecurityException) { "Device" }
            "$name$status\n${device.address}"
        }.toTypedArray()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Connect to Paired Device")
            .setItems(deviceNames) { _, which ->
                val selectedDevice = pairedDevices[which]
                if (bluetoothSyncManager.isPeerConnected(selectedDevice.address)) {
                    Toast.makeText(requireContext(), "Already connected to this device", Toast.LENGTH_SHORT).show()
                    return@setItems
                }
                val name = try { selectedDevice.name ?: selectedDevice.address } catch (e: SecurityException) { selectedDevice.address }
                Toast.makeText(requireContext(), "Connecting to $name...", Toast.LENGTH_SHORT).show()
                bluetoothSyncManager.connectToDevice(selectedDevice)
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_BT_PERMS) {
            val allGranted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                switchBtSync.isChecked = true
                bluetoothSyncManager.setSyncEnabled(true)
                layoutBtSyncDetails.visibility = View.VISIBLE
                (activity as? MainActivity)?.getSensorService()?.syncBluetoothServiceState()
                Toast.makeText(requireContext(), "Bluetooth permissions granted", Toast.LENGTH_SHORT).show()
            } else {
                switchBtSync.isChecked = false
                Toast.makeText(requireContext(), "Bluetooth permission is required for multi-device sync", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        bluetoothSyncManager.unregisterStateListener(btStateListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
    }

    private fun Double.format(digits: Int) = "%.${digits}f".format(this)
}
