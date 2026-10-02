package com.roadwatch.fl.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.roadwatch.fl.MainActivity
import com.roadwatch.fl.R
import com.roadwatch.fl.bluetooth.BluetoothSyncManager
import com.roadwatch.fl.model.SensorData
import com.roadwatch.fl.util.StorageManager
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class SensorService : Service(), SensorEventListener, LocationListener {

    private val binder = LocalBinder()
    private lateinit var sensorManager: SensorManager
    private lateinit var locationManager: LocationManager
    private lateinit var storageManager: StorageManager

    private val buffer = CopyOnWriteArrayList<SensorData>()
    private val isRecording = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)

    private var hardwareInterval: Int = 20000 // microseconds (default 20ms = 50Hz)
    private var samplingInterval: Long = 20L // milliseconds (default 20ms = 50Hz)

    private var sampleCountInWindow = 0
    private var lastFrequencyCalcTime = 0L
    @Volatile private var liveFrequency: Double = 0.0

    private var scheduler: ScheduledExecutorService? = null

    // Latest sensor values
    private var latestAcc = FloatArray(3)
    private var latestGyro = FloatArray(3)
    private var latestMag = FloatArray(3)
    private var latestPressure = 0f
    private var latestIlluminance = 0f
    private var latestRotation = FloatArray(3)
    private var latestStepCount = 0
    private var latestLocation: Location? = null
    private var latestOrientation = FloatArray(3)
    private var latestGravity = FloatArray(3)
    private var latestRotationVector = FloatArray(4)
    private var hasHardwareGravity = false

    // Enabled sensors
    private val enabledSensors = mutableMapOf(
        "Accelerometer" to true,
        "Gyroscope" to true,
        "Magnetometer" to true,
        "Barometer" to true,
        "LightSensor" to true,
        "Orientation" to true,
        "Gravity" to true,
        "Rotation Vector" to true,
        "RotationVector" to true,
        "DeviceMotion" to true,
        "Pedometer" to true,
        "Location" to true
    )

    private var currentLabel = "normal"
    private var currentMotionState = "moving"
    private var sessionStartTime = 0L
    private var scheduledStartRunnable: Runnable? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var bluetoothSyncManager: BluetoothSyncManager
    private var wakeLock: PowerManager.WakeLock? = null

    // Callback for UI updates
    var onDataUpdate: ((SensorData) -> Unit)? = null
    var onRecordingStopped: ((java.io.File) -> Unit)? = null
    var onStateChange: ((recording: Boolean, paused: Boolean) -> Unit)? = null
    var onRemoteLabelChanged: ((String) -> Unit)? = null
    var onRemoteMotionStateChanged: ((String) -> Unit)? = null

    fun isRecording(): Boolean = isRecording.get()
    fun isPaused(): Boolean = isPaused.get()
    fun getLiveFrequency(): Double = liveFrequency
    fun getSamplingInterval(): Long = samplingInterval
    fun getSamplingFrequency(): Double = if (samplingInterval > 0) 1000.0 / samplingInterval else 0.0
    fun getHardwareInterval(): Int = hardwareInterval / 1000
    fun getHardwareFrequency(): Double = if (hardwareInterval > 0) 1000000.0 / hardwareInterval else 0.0

    fun getLatestLocation(): Location? {
        if (latestLocation == null) {
            updateLastKnownLocation()
        }
        return latestLocation
    }

    fun refreshLocationUpdates() {
        registerLocationUpdates()
    }

    inner class LocalBinder : Binder() {
        fun getService(): SensorService = this@SensorService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        storageManager = StorageManager(this)
        createNotificationChannel()
        registerLocationUpdates()
        bluetoothSyncManager = BluetoothSyncManager.getInstance(this)
        setupBluetoothSync()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val initialText = if (isRecording.get()) "Recording..." else "RoadWatch Sync Ready"
        startForeground(NOTIFICATION_ID, createNotification(initialText))
        updateNotificationBasedOnState()
        return START_STICKY
    }

    private fun setupBluetoothSync() {
        bluetoothSyncManager.registerCommandListener { command, payload ->
            when (command) {
                BluetoothSyncManager.CMD_START -> {
                    if (!isRecording.get()) {
                        val intent = Intent(this, SensorService::class.java)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(intent)
                        } else {
                            startService(intent)
                        }
                        val parts = payload?.split(":")
                        val remoteTarget = if (parts != null && parts.size >= 2) parts[1].toLongOrNull() else null
                        val localTarget = if (remoteTarget != null) {
                            bluetoothSyncManager.convertRemoteTimeToLocal(remoteTarget)
                        } else null
                        startRecording(broadcastToBt = false, rendezvousTargetTime = localTarget)
                    }
                }
                BluetoothSyncManager.CMD_PAUSE -> {
                    if (isRecording.get() && !isPaused.get()) {
                        pauseRecording(broadcastToBt = false)
                    }
                }
                BluetoothSyncManager.CMD_RESUME -> {
                    if (isRecording.get() && isPaused.get()) {
                        resumeRecording(broadcastToBt = false)
                    }
                }
                BluetoothSyncManager.CMD_STOP -> {
                    if (isRecording.get()) {
                        val remoteStop = payload?.toLongOrNull()
                        stopRecording(broadcastToBt = false, remoteStopTime = remoteStop)
                    }
                }
                BluetoothSyncManager.CMD_MOTION -> {
                    payload?.let { pl ->
                        val parts = pl.split(":", limit = 2)
                        val state = parts[0]
                        val originTime = if (parts.size >= 2) parts[1].toLongOrNull() else null
                        setMotionState(state, broadcastToBt = false, originTimestamp = originTime)
                        onRemoteMotionStateChanged?.invoke(state)
                    }
                }
                BluetoothSyncManager.CMD_LABEL -> {
                    payload?.let { pl ->
                        val parts = pl.split(":", limit = 2)
                        val label = parts[0]
                        val originTime = if (parts.size >= 2) parts[1].toLongOrNull() else null
                        setLabel(label, broadcastToBt = false, originTimestamp = originTime)
                        onRemoteLabelChanged?.invoke(label)
                    }
                }
            }
        }

        bluetoothSyncManager.registerStateListener { _, _ ->
            updateNotificationBasedOnState()
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RoadWatch:SyncWakeLock")
        }
        if (wakeLock?.isHeld == false) {
            wakeLock?.acquire(12 * 60 * 60 * 1000L) // 12h max
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun syncBluetoothServiceState() {
        if (bluetoothSyncManager.isSyncEnabled()) {
            val intent = Intent(this, SensorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            acquireWakeLock()
            updateNotificationBasedOnState()
        } else {
            if (!isRecording.get()) {
                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    fun startRecording(broadcastToBt: Boolean = true, rendezvousTargetTime: Long? = null) {
        scheduledStartRunnable?.let { mainHandler.removeCallbacks(it) }

        val isBtConnected = bluetoothSyncManager.isSyncEnabled() &&
                bluetoothSyncManager.getConnectionState() == BluetoothSyncManager.ConnectionState.CONNECTED

        if (broadcastToBt && isBtConnected) {
            // Schedule rendezvous start in 500ms so both devices start on the exact same millisecond
            val leadTimeMs = 500L
            val now = System.currentTimeMillis()
            val targetStartLocal = now + leadTimeMs

            bluetoothSyncManager.sendCommand(BluetoothSyncManager.CMD_START, "$leadTimeMs:$targetStartLocal")

            scheduledStartRunnable = Runnable {
                executeStartRecording(targetStartLocal)
            }
            mainHandler.postDelayed(scheduledStartRunnable!!, leadTimeMs)
            return
        }

        if (rendezvousTargetTime != null) {
            val now = System.currentTimeMillis()
            val delayMs = rendezvousTargetTime - now
            if (delayMs > 0) {
                scheduledStartRunnable = Runnable {
                    executeStartRecording(rendezvousTargetTime)
                }
                mainHandler.postDelayed(scheduledStartRunnable!!, delayMs)
                return
            } else {
                executeStartRecording(rendezvousTargetTime)
                return
            }
        }

        executeStartRecording(System.currentTimeMillis())
    }

    private fun executeStartRecording(startTimeMs: Long) {
        if (isRecording.get()) return
        buffer.clear()
        sessionStartTime = startTimeMs
        isRecording.set(true)
        isPaused.set(false)
        sampleCountInWindow = 0
        lastFrequencyCalcTime = 0L
        liveFrequency = 0.0
        acquireWakeLock()
        registerSensors()
        startSampling()
        updateNotificationBasedOnState()
        onStateChange?.invoke(true, false)
    }

    fun pauseRecording(broadcastToBt: Boolean = true) {
        isPaused.set(true)
        liveFrequency = 0.0
        sampleCountInWindow = 0
        lastFrequencyCalcTime = 0L
        scheduler?.shutdown()
        scheduler = null
        updateNotificationBasedOnState()
        onStateChange?.invoke(true, true)

        if (broadcastToBt && bluetoothSyncManager.isSyncEnabled()) {
            bluetoothSyncManager.sendCommand(BluetoothSyncManager.CMD_PAUSE)
        }
    }

    fun resumeRecording(broadcastToBt: Boolean = true) {
        isPaused.set(false)
        sampleCountInWindow = 0
        lastFrequencyCalcTime = 0L
        acquireWakeLock()
        startSampling()
        updateNotificationBasedOnState()
        onStateChange?.invoke(true, false)

        if (broadcastToBt && bluetoothSyncManager.isSyncEnabled()) {
            bluetoothSyncManager.sendCommand(BluetoothSyncManager.CMD_RESUME)
        }
    }

    fun stopRecording(broadcastToBt: Boolean = true, remoteStopTime: Long? = null) {
        scheduledStartRunnable?.let { mainHandler.removeCallbacks(it) }
        val now = System.currentTimeMillis()

        if (broadcastToBt && bluetoothSyncManager.isSyncEnabled()) {
            bluetoothSyncManager.sendCommand(BluetoothSyncManager.CMD_STOP, now.toString())
        }

        isRecording.set(false)
        isPaused.set(false)
        liveFrequency = 0.0
        sampleCountInWindow = 0
        lastFrequencyCalcTime = 0L
        scheduler?.shutdown()
        scheduler = null
        unregisterSensors()

        // If stopped by peer, trim samples recorded during radio transit delay
        if (remoteStopTime != null) {
            val localCutoff = bluetoothSyncManager.convertRemoteTimeToLocal(remoteStopTime)
            val safeCutoff = localCutoff.coerceAtLeast(sessionStartTime)
            while (buffer.isNotEmpty() && buffer.last().timestamp > safeCutoff) {
                buffer.removeAt(buffer.size - 1)
            }
        }

        if (buffer.isNotEmpty()) {
            val file = storageManager.saveSession(buffer.toList())
            onRecordingStopped?.invoke(file)
        }

        onStateChange?.invoke(false, false)

        if (bluetoothSyncManager.isSyncEnabled()) {
            updateNotificationBasedOnState()
        } else {
            releaseWakeLock()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    fun setHardwareInterval(intervalMs: Int) {
        hardwareInterval = intervalMs * 1000 // convert to microseconds
        if (isRecording.get()) {
            unregisterSensors()
            registerSensors()
        }
    }

    fun setSamplingInterval(intervalMs: Long) {
        samplingInterval = intervalMs
        if (isRecording.get() && !isPaused.get()) {
            scheduler?.shutdown()
            startSampling()
        }
    }

    fun setSensorEnabled(sensor: String, enabled: Boolean) {
        enabledSensors[sensor] = enabled
    }

    fun setLabel(label: String, broadcastToBt: Boolean = true, originTimestamp: Long? = null) {
        val changed = currentLabel != label
        currentLabel = label
        val now = System.currentTimeMillis()

        if (changed && broadcastToBt && bluetoothSyncManager.isSyncEnabled()) {
            bluetoothSyncManager.sendCommand(BluetoothSyncManager.CMD_LABEL, "$label:$now")
        } else if (originTimestamp != null && isRecording.get()) {
            // Retroactively update buffer samples captured during transit
            val localOrigin = bluetoothSyncManager.convertRemoteTimeToLocal(originTimestamp)
            retroactiveUpdateBufferLabel(label, localOrigin)
        }
    }

    private fun retroactiveUpdateBufferLabel(label: String, localCutoffTime: Long) {
        val now = System.currentTimeMillis()
        val safeCutoff = localCutoffTime.coerceIn(now - 3000L, now)
        var updatedCount = 0
        for (i in buffer.size - 1 downTo 0) {
            val sample = buffer[i]
            if (sample.timestamp >= safeCutoff) {
                sample.label = label
                updatedCount++
            } else {
                break
            }
        }
        Log.d(TAG, "Retroactively updated label to '$label' on $updatedCount samples (cutoff: $safeCutoff)")
    }

    fun getLabel(): String = currentLabel

    fun setMotionState(state: String, broadcastToBt: Boolean = true, originTimestamp: Long? = null) {
        val changed = currentMotionState != state
        currentMotionState = state
        val now = System.currentTimeMillis()

        if (changed && broadcastToBt && bluetoothSyncManager.isSyncEnabled()) {
            bluetoothSyncManager.sendCommand(BluetoothSyncManager.CMD_MOTION, "$state:$now")
        } else if (originTimestamp != null && isRecording.get()) {
            val localOrigin = bluetoothSyncManager.convertRemoteTimeToLocal(originTimestamp)
            retroactiveUpdateBufferMotionState(state, localOrigin)
        }
    }

    private fun retroactiveUpdateBufferMotionState(state: String, localCutoffTime: Long) {
        val now = System.currentTimeMillis()
        val safeCutoff = localCutoffTime.coerceIn(now - 3000L, now)
        var updatedCount = 0
        for (i in buffer.size - 1 downTo 0) {
            val sample = buffer[i]
            if (sample.timestamp >= safeCutoff) {
                sample.motionState = state
                updatedCount++
            } else {
                break
            }
        }
        Log.d(TAG, "Retroactively updated motion state to '$state' on $updatedCount samples")
    }

    fun getMotionState(): String = currentMotionState

    fun getBufferSize(): Int = buffer.size

    private fun registerSensors() {
        val acc = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        acc?.let { sensorManager.registerListener(this, it, hardwareInterval) }

        val gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        gyro?.let { sensorManager.registerListener(this, it, hardwareInterval) }

        val mag = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        mag?.let { sensorManager.registerListener(this, it, hardwareInterval) }

        val pressure = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
        pressure?.let { sensorManager.registerListener(this, it, hardwareInterval) }

        val light = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        light?.let { sensorManager.registerListener(this, it, hardwareInterval) }

        val rotation = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        rotation?.let { sensorManager.registerListener(this, it, hardwareInterval) }

        val step = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        step?.let { sensorManager.registerListener(this, it, hardwareInterval) }

        val gravity = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        gravity?.let { sensorManager.registerListener(this, it, hardwareInterval) }

        @Suppress("DEPRECATION")
        val orient = sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION)
        orient?.let { sensorManager.registerListener(this, it, hardwareInterval) }

        registerLocationUpdates()
    }

    private fun registerLocationUpdates() {
        updateLastKnownLocation()

        val providers = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            providers.add(LocationManager.FUSED_PROVIDER)
        }
        providers.add(LocationManager.GPS_PROVIDER)
        providers.add(LocationManager.NETWORK_PROVIDER)
        providers.add(LocationManager.PASSIVE_PROVIDER)

        for (provider in providers) {
            try {
                if (locationManager.isProviderEnabled(provider)) {
                    locationManager.requestLocationUpdates(
                        provider,
                        1000L,
                        0f,
                        this,
                        Looper.getMainLooper()
                    )
                }
            } catch (e: SecurityException) {
                // Permission not granted yet
            } catch (e: Exception) {
                // Provider not available or error
            }
        }
    }

    private fun updateLastKnownLocation() {
        val providers = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            providers.add(LocationManager.FUSED_PROVIDER)
        }
        providers.add(LocationManager.GPS_PROVIDER)
        providers.add(LocationManager.NETWORK_PROVIDER)
        providers.add(LocationManager.PASSIVE_PROVIDER)

        for (provider in providers) {
            try {
                if (locationManager.isProviderEnabled(provider)) {
                    val loc = locationManager.getLastKnownLocation(provider)
                    if (loc != null) {
                        val current = latestLocation
                        if (current == null || loc.time > current.time) {
                            latestLocation = loc
                        }
                    }
                }
            } catch (e: SecurityException) {
                // Permission not granted
            } catch (e: Exception) {
                // Error
            }
        }
    }

    private fun unregisterSensors() {
        sensorManager.unregisterListener(this)
    }

    private fun startSampling() {
        scheduler = Executors.newSingleThreadScheduledExecutor()
        scheduler?.scheduleAtFixedRate({
            if (!isPaused.get()) {
                val now = System.currentTimeMillis()

                sampleCountInWindow++
                if (lastFrequencyCalcTime == 0L) {
                    lastFrequencyCalcTime = now
                } else {
                    val elapsed = now - lastFrequencyCalcTime
                    if (elapsed >= 1000) {
                        liveFrequency = (sampleCountInWindow * 1000.0) / elapsed
                        sampleCountInWindow = 0
                        lastFrequencyCalcTime = now
                    }
                }
                val isOrientEnabled = enabledSensors["Orientation"] == true
                val isGravEnabled = enabledSensors["Gravity"] == true
                val isRotEnabled = enabledSensors["Rotation Vector"] == true || enabledSensors["RotationVector"] == true

                val data = SensorData(
                    timestamp = now,
                    accX = if (enabledSensors["Accelerometer"] == true) latestAcc[0] else 0f,
                    accY = if (enabledSensors["Accelerometer"] == true) latestAcc[1] else 0f,
                    accZ = if (enabledSensors["Accelerometer"] == true) latestAcc[2] else 0f,
                    gyroX = if (enabledSensors["Gyroscope"] == true) latestGyro[0] else 0f,
                    gyroY = if (enabledSensors["Gyroscope"] == true) latestGyro[1] else 0f,
                    gyroZ = if (enabledSensors["Gyroscope"] == true) latestGyro[2] else 0f,
                    magX = if (enabledSensors["Magnetometer"] == true) latestMag[0] else 0f,
                    magY = if (enabledSensors["Magnetometer"] == true) latestMag[1] else 0f,
                    magZ = if (enabledSensors["Magnetometer"] == true) latestMag[2] else 0f,
                    pressure = if (enabledSensors["Barometer"] == true) latestPressure else 0f,
                    illuminance = if (enabledSensors["LightSensor"] == true) latestIlluminance else 0f,
                    dmAlpha = if (enabledSensors["DeviceMotion"] == true) latestRotation[0] else 0f,
                    dmBeta = if (enabledSensors["DeviceMotion"] == true) latestRotation[1] else 0f,
                    dmGamma = if (enabledSensors["DeviceMotion"] == true) latestRotation[2] else 0f,
                    stepCount = if (enabledSensors["Pedometer"] == true) latestStepCount else 0,
                    latitude = if (enabledSensors["Location"] == true) latestLocation?.latitude else null,
                    longitude = if (enabledSensors["Location"] == true) latestLocation?.longitude else null,
                    speed = if (enabledSensors["Location"] == true) {
                        latestLocation?.let { if (it.hasSpeed()) it.speed else 0f }
                    } else null,
                    altitude = if (enabledSensors["Location"] == true) {
                        latestLocation?.let { if (it.hasAltitude()) it.altitude else null }
                    } else null,
                    orientAzimuth = if (isOrientEnabled) latestOrientation[0] else 0f,
                    orientPitch = if (isOrientEnabled) latestOrientation[1] else 0f,
                    orientRoll = if (isOrientEnabled) latestOrientation[2] else 0f,
                    gravX = if (isGravEnabled) latestGravity[0] else 0f,
                    gravY = if (isGravEnabled) latestGravity[1] else 0f,
                    gravZ = if (isGravEnabled) latestGravity[2] else 0f,
                    rotX = if (isRotEnabled) latestRotationVector[0] else 0f,
                    rotY = if (isRotEnabled) latestRotationVector[1] else 0f,
                    rotZ = if (isRotEnabled) latestRotationVector[2] else 0f,
                    rotScalar = if (isRotEnabled) latestRotationVector[3] else 0f,
                    label = currentLabel,
                    motionState = currentMotionState,
                    elapsedMs = Math.max(0L, now - sessionStartTime)
                )
                buffer.add(data)
                onDataUpdate?.invoke(data)
            }
        }, 0, samplingInterval, TimeUnit.MILLISECONDS)
    }

    fun getLatestSensorData(): SensorData {
        val now = System.currentTimeMillis()
        val isOrientEnabled = enabledSensors["Orientation"] == true
        val isGravEnabled = enabledSensors["Gravity"] == true
        val isRotEnabled = enabledSensors["Rotation Vector"] == true || enabledSensors["RotationVector"] == true

        return SensorData(
            timestamp = now,
            accX = if (enabledSensors["Accelerometer"] == true) latestAcc[0] else 0f,
            accY = if (enabledSensors["Accelerometer"] == true) latestAcc[1] else 0f,
            accZ = if (enabledSensors["Accelerometer"] == true) latestAcc[2] else 0f,
            gyroX = if (enabledSensors["Gyroscope"] == true) latestGyro[0] else 0f,
            gyroY = if (enabledSensors["Gyroscope"] == true) latestGyro[1] else 0f,
            gyroZ = if (enabledSensors["Gyroscope"] == true) latestGyro[2] else 0f,
            magX = if (enabledSensors["Magnetometer"] == true) latestMag[0] else 0f,
            magY = if (enabledSensors["Magnetometer"] == true) latestMag[1] else 0f,
            magZ = if (enabledSensors["Magnetometer"] == true) latestMag[2] else 0f,
            pressure = if (enabledSensors["Barometer"] == true) latestPressure else 0f,
            illuminance = if (enabledSensors["LightSensor"] == true) latestIlluminance else 0f,
            dmAlpha = if (enabledSensors["DeviceMotion"] == true) latestRotation[0] else 0f,
            dmBeta = if (enabledSensors["DeviceMotion"] == true) latestRotation[1] else 0f,
            dmGamma = if (enabledSensors["DeviceMotion"] == true) latestRotation[2] else 0f,
            stepCount = if (enabledSensors["Pedometer"] == true) latestStepCount else 0,
            latitude = if (enabledSensors["Location"] == true) latestLocation?.latitude else null,
            longitude = if (enabledSensors["Location"] == true) latestLocation?.longitude else null,
            speed = if (enabledSensors["Location"] == true) {
                latestLocation?.let { if (it.hasSpeed()) it.speed else 0f }
            } else null,
            altitude = if (enabledSensors["Location"] == true) {
                latestLocation?.let { if (it.hasAltitude()) it.altitude else null }
            } else null,
            orientAzimuth = if (isOrientEnabled) latestOrientation[0] else 0f,
            orientPitch = if (isOrientEnabled) latestOrientation[1] else 0f,
            orientRoll = if (isOrientEnabled) latestOrientation[2] else 0f,
            gravX = if (isGravEnabled) latestGravity[0] else 0f,
            gravY = if (isGravEnabled) latestGravity[1] else 0f,
            gravZ = if (isGravEnabled) latestGravity[2] else 0f,
            rotX = if (isRotEnabled) latestRotationVector[0] else 0f,
            rotY = if (isRotEnabled) latestRotationVector[1] else 0f,
            rotZ = if (isRotEnabled) latestRotationVector[2] else 0f,
            rotScalar = if (isRotEnabled) latestRotationVector[3] else 0f,
            label = currentLabel,
            motionState = currentMotionState,
            elapsedMs = if (sessionStartTime > 0L) Math.max(0L, now - sessionStartTime) else 0L
        )
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                latestAcc = event.values.clone()
                if (!hasHardwareGravity) {
                    val alpha = 0.8f
                    latestGravity[0] = alpha * latestGravity[0] + (1 - alpha) * event.values[0]
                    latestGravity[1] = alpha * latestGravity[1] + (1 - alpha) * event.values[1]
                    latestGravity[2] = alpha * latestGravity[2] + (1 - alpha) * event.values[2]
                }
            }
            Sensor.TYPE_GYROSCOPE -> latestGyro = event.values.clone()
            Sensor.TYPE_MAGNETIC_FIELD -> latestMag = event.values.clone()
            Sensor.TYPE_PRESSURE -> latestPressure = event.values[0]
            Sensor.TYPE_LIGHT -> latestIlluminance = event.values[0]
            Sensor.TYPE_ROTATION_VECTOR -> {
                latestRotation = event.values.clone()
                latestRotationVector[0] = event.values[0]
                latestRotationVector[1] = event.values[1]
                latestRotationVector[2] = event.values[2]
                latestRotationVector[3] = if (event.values.size > 3) event.values[3] else 0f

                // Compute orientation angles from rotation vector as modern reliable fallback
                try {
                    val rMatrix = FloatArray(9)
                    val orientAngles = FloatArray(3)
                    SensorManager.getRotationMatrixFromVector(rMatrix, event.values)
                    SensorManager.getOrientation(rMatrix, orientAngles)
                    var azimuthDeg = Math.toDegrees(orientAngles[0].toDouble()).toFloat()
                    if (azimuthDeg < 0) azimuthDeg += 360f
                    latestOrientation[0] = azimuthDeg
                    latestOrientation[1] = Math.toDegrees(orientAngles[1].toDouble()).toFloat()
                    latestOrientation[2] = Math.toDegrees(orientAngles[2].toDouble()).toFloat()
                } catch (e: Exception) {
                    // Ignore math errors
                }
            }
            Sensor.TYPE_STEP_COUNTER -> latestStepCount = event.values[0].toInt()
            Sensor.TYPE_GRAVITY -> {
                latestGravity = event.values.clone()
                hasHardwareGravity = true
            }
            @Suppress("DEPRECATION")
            Sensor.TYPE_ORIENTATION -> {
                latestOrientation = event.values.clone()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onLocationChanged(location: Location) {
        val current = latestLocation
        if (current == null || location.time - current.time >= 2000L || location.accuracy <= current.accuracy) {
            latestLocation = location
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Sensor Recording",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("RoadWatch FL")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, createNotification(text))
    }

    private fun updateNotificationBasedOnState() {
        val text = when {
            isRecording.get() -> {
                if (isPaused.get()) "Recording Paused" else "Recording Active (${liveFrequency.format(1)} Hz)"
            }
            bluetoothSyncManager.isSyncEnabled() -> {
                val state = bluetoothSyncManager.getConnectionState()
                val peer = bluetoothSyncManager.getConnectedDeviceName()
                when (state) {
                    BluetoothSyncManager.ConnectionState.CONNECTED -> "BT Sync Connected: ${peer ?: "Peer"}"
                    BluetoothSyncManager.ConnectionState.CONNECTING -> "BT Sync Connecting to ${peer ?: "Peer"}..."
                    BluetoothSyncManager.ConnectionState.LISTENING -> "BT Sync Active (Ready for peer)"
                    else -> "BT Sync Ready"
                }
            }
            else -> "Standby"
        }
        updateNotification(text)
    }

    private fun Double.format(digits: Int) = "%.${digits}f".format(this)

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
        unregisterSensors()
        try {
            locationManager.removeUpdates(this)
        } catch (e: Exception) {
            // Ignore
        }
        scheduler?.shutdown()
    }

    companion object {
        private const val TAG = "SensorService"
        private const val CHANNEL_ID = "sensor_recording_channel"
        private const val NOTIFICATION_ID = 1
    }
}
