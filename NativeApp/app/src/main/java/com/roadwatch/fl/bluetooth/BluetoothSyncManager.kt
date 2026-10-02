package com.roadwatch.fl.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

class BluetoothSyncManager private constructor(private val appContext: Context) {

    enum class ConnectionState {
        DISABLED,
        DISCONNECTED,
        LISTENING,
        CONNECTING,
        CONNECTED
    }

    data class PeerInfo(
        val address: String,
        val name: String,
        @Volatile var clockOffsetMs: Double = 0.0,
        @Volatile var oneWayLatencyMs: Double = 40.0,
        @Volatile var isCalibrated: Boolean = false
    )

    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("roadwatch_prefs", Context.MODE_PRIVATE)

    private val bluetoothManager =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val bluetoothAdapter: BluetoothAdapter? =
        bluetoothManager?.adapter ?: @Suppress("DEPRECATION") BluetoothAdapter.getDefaultAdapter()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val stateListeners = CopyOnWriteArrayList<(ConnectionState, String?) -> Unit>()
    private val commandListeners = CopyOnWriteArrayList<(command: String, payload: String?) -> Unit>()

    private var acceptThread: AcceptThread? = null
    private val connectThreads = CopyOnWriteArrayList<ConnectThread>()
    private val connectedPeers = ConcurrentHashMap<String, ConnectedThread>()
    private val peerInfoMap = ConcurrentHashMap<String, PeerInfo>()

    private var currentState: ConnectionState = ConnectionState.DISCONNECTED
    private var connectedDeviceName: String? = null

    // Global / Average Clock Calibration State across peers
    @Volatile var calibratedClockOffsetMs: Double = 0.0
    @Volatile var calibratedOneWayLatencyMs: Double = 40.0
    @Volatile var isClockCalibrated: Boolean = false

    // Benchmark state
    private var latencyTestCallback: ((LatencyTestResult) -> Unit)? = null
    private var latencyProgressCallback: ((current: Int, total: Int, rttMs: Long) -> Unit)? = null
    private var latencyErrorCallback: ((String) -> Unit)? = null
    private var latencyTestActive = false
    private var activeTestPeerAddress: String? = null
    private val latencyRtts = mutableListOf<Long>()
    private val latencyOffsets = mutableListOf<Double>()
    private var currentPingSeq = 0
    private var targetPingTotal = 5
    private var pingTimeoutRunnable: Runnable? = null

    // Message deduplication cache to prevent circular relay storms (stores key -> arrival timestamp)
    private val recentMessages = ConcurrentHashMap<String, Long>()

    /**
     * Converts a timestamp from a remote peer into the local device's clock frame:
     * LocalTime = RemoteTime - (RemoteClock - LocalClock).
     */
    fun convertRemoteTimeToLocal(remoteTs: Long, peerAddress: String? = null): Long {
        val offset = if (peerAddress != null) {
            peerInfoMap[peerAddress]?.clockOffsetMs ?: calibratedClockOffsetMs
        } else {
            calibratedClockOffsetMs
        }
        return if (isClockCalibrated) {
            (remoteTs - offset).toLong()
        } else {
            remoteTs
        }
    }

    companion object {
        private const val TAG = "BluetoothSync"
        private const val SERVICE_NAME = "RoadWatchSync"
        private val ROADWATCH_UUID: UUID =
            UUID.fromString("e0cbf06c-cd8b-4647-bb8a-263b43f0f974")

        const val PREF_KEY_BT_SYNC = "bluetooth_sync_enabled"

        // Commands
        const val CMD_START = "CMD:START"
        const val CMD_PAUSE = "CMD:PAUSE"
        const val CMD_RESUME = "CMD:RESUME"
        const val CMD_STOP = "CMD:STOP"
        const val CMD_MOTION = "CMD:MOTION"
        const val CMD_LABEL = "CMD:LABEL"
        const val CMD_PING = "CMD:PING"
        const val CMD_PONG = "CMD:PONG"
        const val CMD_OFFSET = "CMD:OFFSET"

        data class LatencyTestResult(
            val sampleRtts: List<Long>,
            val minRttMs: Long,
            val maxRttMs: Long,
            val avgRttMs: Double,
            val oneWayLatencyMs: Double,
            val avgClockOffsetMs: Double,
            val jitterMs: Double,
            val peerName: String = ""
        )

        @Volatile
        private var instance: BluetoothSyncManager? = null

        fun getInstance(context: Context): BluetoothSyncManager {
            return instance ?: synchronized(this) {
                instance ?: BluetoothSyncManager(context.applicationContext).also { instance = it }
            }
        }
    }

    init {
        if (isSyncEnabled()) {
            currentState = ConnectionState.DISCONNECTED
            startListening()
        } else {
            currentState = ConnectionState.DISABLED
        }
    }

    fun isBluetoothSupported(): Boolean = bluetoothAdapter != null

    fun isBluetoothHardwareEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    fun isSyncEnabled(): Boolean = prefs.getBoolean(PREF_KEY_BT_SYNC, false)

    fun setSyncEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_KEY_BT_SYNC, enabled).apply()
        if (enabled) {
            updateState(ConnectionState.DISCONNECTED, null)
            startListening()
        } else {
            stopAll()
            updateState(ConnectionState.DISABLED, null)
        }
    }

    fun getConnectionState(): ConnectionState = currentState

    fun getConnectedDeviceName(): String? = connectedDeviceName

    fun getConnectedPeerCount(): Int = connectedPeers.size

    fun getConnectedPeers(): List<PeerInfo> = peerInfoMap.values.toList()

    fun isPeerConnected(address: String): Boolean = connectedPeers.containsKey(address)

    fun getConnectedDeviceSummary(): String {
        val peers = peerInfoMap.values.toList()
        return when (peers.size) {
            0 -> "No peers connected"
            1 -> peers[0].name
            2 -> "${peers[0].name}, ${peers[1].name} (2 peers)"
            else -> "${peers.size} peers: ${peers.joinToString { it.name }}"
        }
    }

    fun hasPermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val connectGranted = ContextCompat.checkSelfPermission(
                appContext,
                android.Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
            return connectGranted
        }
        return true
    }

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothDevice> {
        if (!hasPermissions() || bluetoothAdapter == null) return emptyList()
        return try {
            bluetoothAdapter.bondedDevices?.toList() ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching bonded devices", e)
            emptyList()
        }
    }

    @Synchronized
    fun startListening() {
        if (!isSyncEnabled() || !hasPermissions() || bluetoothAdapter?.isEnabled != true) {
            return
        }

        // If AcceptThread is already alive, keep it accepting additional peers
        if (acceptThread?.isAlive == true) return

        try {
            acceptThread = AcceptThread()
            acceptThread?.start()
            if (connectedPeers.isEmpty() && connectThreads.isEmpty()) {
                updateState(ConnectionState.LISTENING, null)
            }
            Log.i(TAG, "Bluetooth server started. Listening for incoming sync connections...")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AcceptThread", e)
            if (connectedPeers.isEmpty()) {
                updateState(ConnectionState.DISCONNECTED, null)
            }
        }
    }

    @Synchronized
    fun connectToDevice(device: BluetoothDevice) {
        if (!isSyncEnabled() || !hasPermissions() || bluetoothAdapter?.isEnabled != true) return

        val address = device.address
        val existing = connectedPeers[address]
        if (existing != null && existing.isConnected()) {
            Log.i(TAG, "Device $address is already connected")
            return
        }

        // Clean up any stale connecting attempt for this specific device
        connectThreads.removeAll {
            if (it.deviceAddress == address) {
                it.cancel()
                true
            } else false
        }

        val name = try { device.name ?: address } catch (e: SecurityException) { address }
        val newConnectThread = ConnectThread(device)
        connectThreads.add(newConnectThread)
        newConnectThread.start()

        if (connectedPeers.isEmpty()) {
            updateState(ConnectionState.CONNECTING, name)
        }
        Log.i(TAG, "Connecting to Bluetooth peer: $name ($address)")
    }

    @Synchronized
    fun disconnect() {
        stopAll()
        if (isSyncEnabled()) {
            updateState(ConnectionState.DISCONNECTED, null)
            startListening()
        } else {
            updateState(ConnectionState.DISABLED, null)
        }
    }

    @Synchronized
    fun disconnectPeer(address: String) {
        connectedPeers[address]?.cancel()
        connectedPeers.remove(address)
        peerInfoMap.remove(address)
        updatePrimaryCalibration()
        refreshConnectionState()
    }

    /**
     * Broadcasts a command to all connected peers, optionally excluding one peer (e.g. during relaying).
     */
    fun sendCommand(command: String, payload: String? = null, excludeAddress: String? = null) {
        if (connectedPeers.isEmpty()) return
        val message = if (payload.isNullOrBlank()) command else "$command:$payload"
        connectedPeers.forEach { (address, peerThread) ->
            if (excludeAddress == null || address != excludeAddress) {
                peerThread.write(message)
            }
        }
    }

    /**
     * Sends a command directly to a single peer (e.g. for PING/PONG responses).
     */
    fun sendCommandToPeer(peerAddress: String, command: String, payload: String? = null) {
        val peerThread = connectedPeers[peerAddress] ?: return
        val message = if (payload.isNullOrBlank()) command else "$command:$payload"
        peerThread.write(message)
    }

    fun registerStateListener(listener: (ConnectionState, String?) -> Unit) {
        stateListeners.add(listener)
        listener(currentState, connectedDeviceName)
    }

    fun unregisterStateListener(listener: (ConnectionState, String?) -> Unit) {
        stateListeners.remove(listener)
    }

    fun registerCommandListener(listener: (command: String, payload: String?) -> Unit) {
        commandListeners.add(listener)
    }

    fun unregisterCommandListener(listener: (command: String, payload: String?) -> Unit) {
        commandListeners.remove(listener)
    }

    private fun updateState(state: ConnectionState, deviceName: String?) {
        currentState = state
        connectedDeviceName = deviceName
        mainHandler.post {
            stateListeners.forEach { it(state, deviceName) }
        }
    }

    private fun refreshConnectionState() {
        val count = connectedPeers.size
        when {
            count > 0 -> {
                val summary = getConnectedDeviceSummary()
                updateState(ConnectionState.CONNECTED, summary)
            }
            connectThreads.isNotEmpty() -> {
                val targetName = connectThreads.firstOrNull()?.let {
                    try { it.device.name ?: it.deviceAddress } catch (e: SecurityException) { it.deviceAddress }
                } ?: "Peer"
                updateState(ConnectionState.CONNECTING, targetName)
            }
            isSyncEnabled() -> {
                updateState(ConnectionState.LISTENING, null)
                startListening()
            }
            else -> {
                updateState(ConnectionState.DISABLED, null)
            }
        }
    }

    private fun recordRecentMessage(key: String): Boolean {
        val now = System.currentTimeMillis()
        val iter = recentMessages.entries.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            if (now - entry.value > 5000L) {
                iter.remove()
            }
        }
        val prev = recentMessages.putIfAbsent(key, now)
        return prev == null
    }

    private fun dispatchCommand(line: String, senderAddress: String) {
        Log.d(TAG, "Received bluetooth command from $senderAddress: $line")
        val parts = line.split(":", limit = 3)
        if (parts.isEmpty()) return

        val cmdPrefix = if (parts.size >= 2) "${parts[0]}:${parts[1]}" else parts[0]
        val payload = if (parts.size >= 3) parts[2] else null

        // Auto-reply to PING benchmark directly to the sender with remote timestamp
        if (cmdPrefix == CMD_PING && payload != null) {
            val t2 = System.currentTimeMillis()
            sendCommandToPeer(senderAddress, CMD_PONG, "$payload:$t2")
            return
        }

        // Handle PONG response for latency benchmark from active test peer
        if (cmdPrefix == CMD_PONG && payload != null) {
            if (activeTestPeerAddress == null || activeTestPeerAddress == senderAddress) {
                handlePongResponse(payload, senderAddress)
            }
            return
        }

        // Handle calibrated clock offset notification from peer
        if (cmdPrefix == CMD_OFFSET && payload != null) {
            val offset = payload.toDoubleOrNull()
            if (offset != null) {
                val peer = peerInfoMap[senderAddress]
                if (peer != null) {
                    peer.clockOffsetMs = offset
                    peer.isCalibrated = true
                }
                updatePrimaryCalibration()
                Log.i(TAG, "Synced calibrated clock offset from peer $senderAddress: ${offset}ms")
            }
            return
        }

        // Prevent circular relay storm across multi-device networks
        val deduplicationKey = "$cmdPrefix:${payload ?: ""}"
        if (!recordRecentMessage(deduplicationKey)) {
            Log.d(TAG, "Duplicate message ignored (anti-echo loop): $deduplicationKey")
            return
        }

        // If we are connected to multiple peers (star topology Hub), relay to all other peers
        if (connectedPeers.size > 1) {
            sendCommand(cmdPrefix, payload, excludeAddress = senderAddress)
        }

        // Dispatch command to local service/UI
        mainHandler.post {
            commandListeners.forEach { listener ->
                listener(cmdPrefix, payload)
            }
        }
    }

    private fun handlePongResponse(payload: String, senderAddress: String) {
        val t3 = System.currentTimeMillis()
        val pongParts = payload.split(":")
        if (pongParts.size >= 3) {
            val seq = pongParts[0].toIntOrNull() ?: 0
            val t1 = pongParts[1].toLongOrNull() ?: 0L
            val t2 = pongParts[2].toLongOrNull() ?: 0L

            if (latencyTestActive && seq == currentPingSeq && t1 > 0L) {
                pingTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
                val rtt = Math.max(0L, t3 - t1)
                val offset = ((t2 - t1) + (t2 - t3)) / 2.0
                latencyRtts.add(rtt)
                latencyOffsets.add(offset)

                mainHandler.post {
                    latencyProgressCallback?.invoke(currentPingSeq, targetPingTotal, rtt)
                }

                if (currentPingSeq < targetPingTotal) {
                    mainHandler.postDelayed({ sendNextPing() }, 100)
                } else {
                    mainHandler.post { finishLatencyTest(senderAddress) }
                }
            }
        }
    }

    fun runLatencyTest(
        targetPeerAddress: String? = null,
        totalPings: Int = 5,
        onProgress: (current: Int, total: Int, rttMs: Long) -> Unit,
        onComplete: (LatencyTestResult) -> Unit,
        onError: (String) -> Unit
    ) {
        if (connectedPeers.isEmpty()) {
            onError("No peer devices are connected")
            return
        }
        if (latencyTestActive) {
            onError("Benchmark already in progress")
            return
        }

        val target = targetPeerAddress ?: connectedPeers.keys.firstOrNull()
        if (target == null || !connectedPeers.containsKey(target)) {
            onError("Target peer is not connected")
            return
        }

        latencyTestActive = true
        activeTestPeerAddress = target
        latencyRtts.clear()
        latencyOffsets.clear()
        currentPingSeq = 0
        targetPingTotal = totalPings
        latencyProgressCallback = onProgress
        latencyTestCallback = onComplete
        latencyErrorCallback = onError

        sendNextPing()
    }

    private fun sendNextPing() {
        val target = activeTestPeerAddress
        if (!latencyTestActive || target == null || !connectedPeers.containsKey(target)) {
            latencyTestActive = false
            return
        }
        currentPingSeq++
        val t1 = System.currentTimeMillis()
        sendCommandToPeer(target, CMD_PING, "$currentPingSeq:$t1")

        pingTimeoutRunnable = Runnable {
            if (latencyTestActive) {
                Log.w(TAG, "Ping $currentPingSeq to $target timed out")
                if (currentPingSeq < targetPingTotal) {
                    mainHandler.postDelayed({ sendNextPing() }, 100)
                } else {
                    finishLatencyTest(target)
                }
            }
        }
        mainHandler.postDelayed(pingTimeoutRunnable!!, 1500)
    }

    private fun finishLatencyTest(peerAddress: String) {
        latencyTestActive = false
        val activeTarget = activeTestPeerAddress ?: peerAddress
        activeTestPeerAddress = null
        pingTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }

        if (latencyRtts.isEmpty()) {
            latencyErrorCallback?.invoke("No response received from peer device (timeout)")
            return
        }
        val minRtt = latencyRtts.minOrNull() ?: 0L
        val maxRtt = latencyRtts.maxOrNull() ?: 0L
        val avgRtt = latencyRtts.average()
        val oneWay = avgRtt / 2.0
        val avgOffset = if (latencyOffsets.isNotEmpty()) latencyOffsets.average() else 0.0

        val jitter = if (latencyRtts.size > 1) {
            latencyRtts.map { Math.abs(it - avgRtt) }.average()
        } else 0.0

        val peer = peerInfoMap[activeTarget]
        if (peer != null) {
            peer.clockOffsetMs = avgOffset
            peer.oneWayLatencyMs = oneWay
            peer.isCalibrated = true
        }

        updatePrimaryCalibration()

        // Notify peer of its inverse clock offset
        sendCommandToPeer(activeTarget, CMD_OFFSET, (-avgOffset).toString())

        val peerName = peer?.name ?: activeTarget
        val result = LatencyTestResult(
            sampleRtts = latencyRtts.toList(),
            minRttMs = minRtt,
            maxRttMs = maxRtt,
            avgRttMs = avgRtt,
            oneWayLatencyMs = oneWay,
            avgClockOffsetMs = avgOffset,
            jitterMs = jitter,
            peerName = peerName
        )
        latencyTestCallback?.invoke(result)
    }

    private fun updatePrimaryCalibration() {
        val calibrated = peerInfoMap.values.filter { it.isCalibrated }
        if (calibrated.isNotEmpty()) {
            calibratedClockOffsetMs = calibrated.map { it.clockOffsetMs }.average()
            calibratedOneWayLatencyMs = calibrated.map { it.oneWayLatencyMs }.average()
            isClockCalibrated = true
        } else {
            isClockCalibrated = false
        }
    }

    @Synchronized
    private fun manageConnectedSocket(socket: BluetoothSocket, peerAddress: String, peerName: String) {
        val existing = connectedPeers[peerAddress]
        if (existing != null && existing.isConnected()) {
            Log.i(TAG, "Already connected to $peerName ($peerAddress), ignoring redundant connection")
            try { socket.close() } catch (e: Exception) {}
            return
        }

        existing?.cancel()

        peerInfoMap.getOrPut(peerAddress) {
            PeerInfo(address = peerAddress, name = peerName)
        }

        val newConnected = ConnectedThread(socket, peerAddress, peerName)
        connectedPeers[peerAddress] = newConnected
        newConnected.start()

        // Remove any matching connectThread for this address
        connectThreads.removeAll { it.deviceAddress == peerAddress }

        refreshConnectionState()
        Log.i(TAG, "Successfully connected with peer: $peerName ($peerAddress). Total peers: ${connectedPeers.size}")

        // Trigger automatic clock offset calibration for this newly connected peer
        runBackgroundClockCalibrationForPeer(peerAddress)
    }

    private fun runBackgroundClockCalibrationForPeer(peerAddress: String) {
        mainHandler.postDelayed({
            if (connectedPeers.containsKey(peerAddress) && !latencyTestActive) {
                runLatencyTest(
                    targetPeerAddress = peerAddress,
                    totalPings = 3,
                    onProgress = { _, _, _ -> },
                    onComplete = { result ->
                        Log.i(TAG, "Background clock calibration ready for $peerAddress: offset=${result.avgClockOffsetMs}ms, latency=${result.oneWayLatencyMs}ms")
                    },
                    onError = { err ->
                        Log.d(TAG, "Background calibration postponed for $peerAddress: $err")
                    }
                )
            }
        }, 400)
    }

    private fun onPeerConnectionLost(peerAddress: String) {
        val info = peerInfoMap[peerAddress]
        val name = info?.name ?: peerAddress
        Log.w(TAG, "Peer connection lost: $name ($peerAddress)")

        connectedPeers.remove(peerAddress)
        peerInfoMap.remove(peerAddress)
        updatePrimaryCalibration()

        if (activeTestPeerAddress == peerAddress && latencyTestActive) {
            latencyTestActive = false
            pingTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
            latencyErrorCallback?.invoke("Connection lost to target peer during test")
        }

        mainHandler.post {
            refreshConnectionState()
        }
    }

    @Synchronized
    private fun stopAll() {
        acceptThread?.cancel()
        acceptThread = null

        connectThreads.forEach { it.cancel() }
        connectThreads.clear()

        connectedPeers.values.forEach { it.cancel() }
        connectedPeers.clear()
        peerInfoMap.clear()
        updatePrimaryCalibration()
    }

    // Server socket thread (AcceptThread): Keeps listening to accept multiple peers
    private inner class AcceptThread : Thread("BT-AcceptThread") {
        @Volatile private var isCancelled = false
        private var serverSocket: BluetoothServerSocket? = null

        init {
            try {
                if (hasPermissions()) {
                    serverSocket = try {
                        bluetoothAdapter?.listenUsingInsecureRfcommWithServiceRecord(SERVICE_NAME, ROADWATCH_UUID)
                    } catch (e: Exception) {
                        Log.w(TAG, "Insecure server socket failed, falling back to secure", e)
                        bluetoothAdapter?.listenUsingRfcommWithServiceRecord(SERVICE_NAME, ROADWATCH_UUID)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error opening server socket", e)
            }
        }

        override fun run() {
            while (!isCancelled) {
                val socket: BluetoothSocket? = try {
                    serverSocket?.accept()
                } catch (e: Exception) {
                    if (!isCancelled) {
                        Log.d(TAG, "Accept exception (loop terminated): ${e.message}")
                    }
                    break
                }

                if (socket != null) {
                    val peerDevice = socket.remoteDevice
                    val peerAddress = peerDevice?.address ?: "UNKNOWN"
                    val peerName = try {
                        peerDevice?.name ?: peerAddress
                    } catch (e: SecurityException) {
                        peerAddress
                    }
                    synchronized(this@BluetoothSyncManager) {
                        if (!isCancelled) {
                            manageConnectedSocket(socket, peerAddress, peerName)
                        } else {
                            try { socket.close() } catch (e: Exception) {}
                        }
                    }
                    // Loop continues so additional peers can connect!
                }
            }

            try {
                serverSocket?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Could not close server socket", e)
            }
        }

        fun cancel() {
            isCancelled = true
            try {
                serverSocket?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Could not close accept socket", e)
            }
        }
    }

    // Client connection thread (ConnectThread)
    private inner class ConnectThread(val device: BluetoothDevice) : Thread("BT-ConnectThread-${device.address}") {
        val deviceAddress: String = device.address
        @Volatile private var isHandedOff = false
        @Volatile private var isCancelled = false
        @Volatile private var currentSocket: BluetoothSocket? = null

        @SuppressLint("MissingPermission")
        override fun run() {
            if (hasPermissions()) {
                bluetoothAdapter?.cancelDiscovery()
            }

            var activeSocket: BluetoothSocket? = null
            var connected = false

            // Attempt 1: Insecure RFCOMM
            if (!isCancelled) {
                try {
                    Log.d(TAG, "Attempting insecure RFCOMM socket with UUID to $deviceAddress...")
                    val sock = device.createInsecureRfcommSocketToServiceRecord(ROADWATCH_UUID)
                    currentSocket = sock
                    sock.connect()
                    activeSocket = sock
                    connected = true
                    Log.i(TAG, "Connected successfully via insecure RFCOMM to $deviceAddress!")
                } catch (e: Exception) {
                    Log.w(TAG, "Insecure RFCOMM failed: ${e.message}. Trying secure RFCOMM...")
                    try { currentSocket?.close() } catch (ce: Exception) {}
                    currentSocket = null
                    activeSocket = null
                }
            }

            // Attempt 2: Secure RFCOMM
            if (!connected && !isCancelled) {
                try {
                    Log.d(TAG, "Attempting secure RFCOMM socket with UUID to $deviceAddress...")
                    val sock = device.createRfcommSocketToServiceRecord(ROADWATCH_UUID)
                    currentSocket = sock
                    sock.connect()
                    activeSocket = sock
                    connected = true
                    Log.i(TAG, "Connected successfully via secure RFCOMM to $deviceAddress!")
                } catch (e: Exception) {
                    Log.w(TAG, "Secure RFCOMM failed: ${e.message}. Trying reflection channel 1...")
                    try { currentSocket?.close() } catch (ce: Exception) {}
                    currentSocket = null
                    activeSocket = null
                }
            }

            // Attempt 3: Reflection fallback insecure channel 1
            if (!connected && !isCancelled) {
                try {
                    val m = device.javaClass.getMethod("createInsecureRfcommSocket", Int::class.javaPrimitiveType)
                    val sock = m.invoke(device, 1) as? BluetoothSocket
                    if (sock != null) {
                        currentSocket = sock
                        sock.connect()
                        activeSocket = sock
                        connected = true
                        Log.i(TAG, "Connected successfully via reflection channel 1 to $deviceAddress!")
                    }
                } catch (e: Exception) {
                    try { currentSocket?.close() } catch (ce: Exception) {}
                    currentSocket = null
                    activeSocket = null
                }
            }

            // Attempt 4: Reflection fallback secure channel 1
            if (!connected && !isCancelled) {
                try {
                    val m = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                    val sock = m.invoke(device, 1) as? BluetoothSocket
                    if (sock != null) {
                        currentSocket = sock
                        sock.connect()
                        activeSocket = sock
                        connected = true
                        Log.i(TAG, "Connected successfully via reflection secure channel 1 to $deviceAddress!")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "All connection attempts to $deviceAddress failed", e)
                    try { currentSocket?.close() } catch (ce: Exception) {}
                    currentSocket = null
                    activeSocket = null
                }
            }

            if (isCancelled || !connected || activeSocket == null) {
                try { activeSocket?.close() } catch (e: Exception) {}
                connectThreads.remove(this)
                mainHandler.post {
                    refreshConnectionState()
                }
                return
            }

            val peerName = try { device.name ?: deviceAddress } catch (e: SecurityException) { deviceAddress }
            isHandedOff = true
            synchronized(this@BluetoothSyncManager) {
                connectThreads.remove(this)
                if (!isCancelled) {
                    manageConnectedSocket(activeSocket, deviceAddress, peerName)
                } else {
                    try { activeSocket.close() } catch (e: Exception) {}
                }
            }
        }

        fun cancel() {
            isCancelled = true
            if (!isHandedOff) {
                try {
                    currentSocket?.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Could not close connect socket for $deviceAddress", e)
                }
            }
        }
    }

    // Connected communication thread (ConnectedThread) per peer
    private inner class ConnectedThread(
        private val socket: BluetoothSocket,
        val peerAddress: String,
        val peerName: String
    ) : Thread("BT-ConnectedThread-$peerAddress") {
        @Volatile private var isClosing = false
        private val inputStream = socket.inputStream
        private val outputStream: OutputStream = socket.outputStream
        private val writeExecutor = Executors.newSingleThreadExecutor()

        fun isConnected(): Boolean = !isClosing && socket.isConnected

        override fun run() {
            val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
            try {
                while (!isClosing) {
                    val line = reader.readLine() ?: break
                    if (line.isNotBlank()) {
                        dispatchCommand(line.trim(), peerAddress)
                    }
                }
            } catch (e: Exception) {
                if (!isClosing) {
                    Log.w(TAG, "Connection interrupted for $peerName ($peerAddress): ${e.message}")
                }
            } finally {
                val wasClosing = isClosing
                cancel()
                synchronized(this@BluetoothSyncManager) {
                    if (!wasClosing) {
                        onPeerConnectionLost(peerAddress)
                    }
                }
            }
        }

        fun write(message: String) {
            if (isClosing) return
            writeExecutor.execute {
                try {
                    val bytes = (message + "\n").toByteArray(Charsets.UTF_8)
                    synchronized(outputStream) {
                        outputStream.write(bytes)
                        outputStream.flush()
                    }
                    Log.d(TAG, "Sent bluetooth command to $peerName: $message")
                } catch (e: Exception) {
                    Log.e(TAG, "Error writing command to $peerName: $message", e)
                    if (!isClosing) {
                        cancel()
                        synchronized(this@BluetoothSyncManager) {
                            onPeerConnectionLost(peerAddress)
                        }
                    }
                }
            }
        }

        fun cancel() {
            isClosing = true
            try {
                writeExecutor.shutdownNow()
            } catch (e: Exception) {}
            try {
                socket.close()
            } catch (e: Exception) {
                Log.e(TAG, "Could not close socket for $peerAddress", e)
            }
        }
    }
}
