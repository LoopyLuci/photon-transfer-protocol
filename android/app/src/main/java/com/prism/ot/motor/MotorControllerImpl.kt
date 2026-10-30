package com.prism.ot.motor

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.prism.ot.core.PrismError
import com.prism.ot.core.PrismLogger
import com.prism.ot.core.PrismResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Native `BluetoothGatt` implementation of [MotorController].
 *
 * Design decisions:
 *  - **No third-party BLE library.** The motor protocol is four
 *    characteristics and one command byte; the Nordic `ble-ktx` wrapper
 *    would add ~500 KB and a coroutine abstraction that we would still
 *    have to serialize ourselves. A hand-rolled queue is smaller and
 *    easier to audit for this narrow surface.
 *  - **`autoConnect = false`.** The controller owns reconnection with
 *    exponential backoff. Platform auto-connect is opaque and can hold a
 *    stale GATT object indefinitely.
 *  - **MTU 517 + `CONNECTION_PRIORITY_HIGH`.** Requested immediately after
 *    service discovery. This is the documented combination for maximizing
 *    BLE throughput.
 *  - **Write-without-response for speed commands.** At 5–20 Hz, waiting
 *    for a write acknowledgement halves the control rate.
 *  - **Heartbeat every 5 s.** Prevents the OS from widening the connection
 *    interval during idle periods, which would otherwise introduce
 *    hundreds of milliseconds of latency.
 */
@SuppressLint("MissingPermission")
@Singleton
class MotorControllerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: PrismLogger,
) : MotorController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _connectionState = MutableStateFlow<MotorConnectionState>(
        MotorConnectionState.Idle,
    )
    override val connectionState: StateFlow<MotorConnectionState> =
        _connectionState.asStateFlow()

    private val _telemetry = MutableStateFlow<MotorTelemetry?>(null)
    override val telemetry: StateFlow<MotorTelemetry?> = _telemetry.asStateFlow()

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    @Volatile private var gatt: BluetoothGatt? = null
    @Volatile private var queue: GattOperationQueue? = null
    @Volatile private var reconnectJob: Job? = null
    @Volatile private var heartbeatJob: Job? = null
    @Volatile private var manualDisconnect = false
    @Volatile private var reconnectAttempt = 0
    @Volatile private var targetMac: String? = null
    @Volatile private var negotiatedMtu: Int = MotorGattSpec.FALLBACK_MTU

    private val scanResults = ConcurrentHashMap<String, DiscoveredMotor>()

    // ------------------------------------------------------------------
    // Scanning
    // ------------------------------------------------------------------

    override suspend fun scanForMotors(timeoutMillis: Long): PrismResult<List<DiscoveredMotor>> =
        runCatching {
            requireBluetoothPermission()
            val bluetoothAdapter = adapter ?: error("Bluetooth adapter unavailable")
            require(bluetoothAdapter.isEnabled) { "Bluetooth is disabled" }

            scanResults.clear()
            val scanner = bluetoothAdapter.bluetoothLeScanner
                ?: error("BLE scanner unavailable")

            val callback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    val device = result.device ?: return
                    if (result.scanRecord?.serviceUuids?.any {
                            it.uuid == MotorGattSpec.SERVICE
                        } != true
                    ) return
                    scanResults[device.address] = DiscoveredMotor(
                        macAddress = device.address,
                        displayName = result.scanRecord?.deviceName
                            ?: device.name
                            ?: "PRISM mass",
                        rssi = result.rssi,
                    )
                }

                override fun onScanFailed(errorCode: Int) {
                    logger.e(TAG, "Scan failed: $errorCode")
                }
            }

            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            scanner.startScan(null, settings, callback)
            try {
                delay(timeoutMillis)
            } finally {
                runCatching { scanner.stopScan(callback) }
            }
            scanResults.values.sortedByDescending { it.rssi }
        }.fold(
            onSuccess = { PrismResult.Success(it) },
            onFailure = { t ->
                logger.e(TAG, "scanForMotors failed", t)
                PrismResult.Failure(
                    if (t is SecurityException) PrismError.BluetoothUnavailable
                    else PrismError.MotorConnectionFailed(t.message ?: "scan"),
                    t,
                )
            },
        )

    // ------------------------------------------------------------------
    // Connection
    // ------------------------------------------------------------------

    override suspend fun connect(macAddress: String): PrismResult<Unit> = runCatching {
        requireBluetoothPermission()
        val bluetoothAdapter = adapter ?: error("Bluetooth adapter unavailable")
        require(bluetoothAdapter.isEnabled) { "Bluetooth is disabled" }

        disconnectInternal(silent = true)
        manualDisconnect = false
        targetMac = macAddress
        reconnectAttempt = 0

        _connectionState.value = MotorConnectionState.Connecting(macAddress)
        val device = bluetoothAdapter.getRemoteDevice(macAddress)
            ?: error("Unknown device $macAddress")

        val connected = suspendCancellableCoroutine<BluetoothGatt> { cont ->
            val callback = buildGattCallback(cont)
            val newGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(context, false, callback)
            }
            if (newGatt == null) {
                cont.resumeWithException(IllegalStateException("connectGatt returned null"))
            }
        }
        gatt = connected

        awaitServicesDiscovered(connected)
        _connectionState.value = MotorConnectionState.DiscoveringServices(macAddress)

        val opQueue = GattOperationQueue(logger, scope).also { queue = it }
        opQueue.start()

        negotiateMtu(connected)
        requestConnectionPriorityHigh(connected)
        enableTelemetryNotifications(connected)

        _connectionState.value = MotorConnectionState.Ready(
            macAddress = macAddress,
            mtu = negotiatedMtu,
            connectionPriority = MotorConnectionState.Priority.HIGH,
            negotiatedRotationHz = 0f,
        )
        startHeartbeat()
        Unit
    }.fold(
        onSuccess = { PrismResult.Success(it) },
        onFailure = { t ->
            logger.e(TAG, "connect($macAddress) failed", t)
            _connectionState.value = MotorConnectionState.Failed(
                macAddress,
                PrismError.MotorConnectionFailed(t.message ?: "connect"),
            )
            runCatching { gatt?.close() }
            gatt = null
            PrismResult.Failure(
                PrismError.MotorConnectionFailed(t.message ?: "connect"),
                t,
            )
        },
    )

    override suspend fun disconnect() {
        manualDisconnect = true
        reconnectJob?.cancel()
        disconnectInternal(silent = false)
    }

    private fun disconnectInternal(silent: Boolean) {
        heartbeatJob?.cancel()
        heartbeatJob = null
        queue?.stop()
        queue = null
        runCatching { gatt?.disconnect() }
        runCatching { gatt?.close() }
        gatt = null
        if (!silent) {
            _connectionState.value = MotorConnectionState.Disconnected(
                targetMac,
                MotorConnectionState.Disconnected.Reason.USER_REQUEST,
            )
        }
    }

    private suspend fun awaitServicesDiscovered(gatt: BluetoothGatt) {
        withTimeout(MotorGattSpec.DISCOVERY_TIMEOUT_MS) {
            suspendCancellableCoroutine<Unit> { cont ->
                val previous = serviceDiscoveryContinuation
                serviceDiscoveryContinuation = cont
                if (!gatt.discoverServices()) {
                    serviceDiscoveryContinuation = previous
                    cont.resumeWithException(IllegalStateException("discoverServices() rejected"))
                }
            }
        }
    }

    private fun negotiateMtu(gatt: BluetoothGatt) {
        val accepted = gatt.requestMtu(MotorGattSpec.TARGET_MTU)
        if (!accepted) {
            negotiatedMtu = MotorGattSpec.FALLBACK_MTU
            logger.w(TAG, "requestMtu(${MotorGattSpec.TARGET_MTU}) rejected; using $negotiatedMtu")
        }
    }

    private fun requestConnectionPriorityHigh(gatt: BluetoothGatt) {
        val accepted = gatt.requestConnectionPriority(
            BluetoothGatt.CONNECTION_PRIORITY_HIGH,
        )
        if (!accepted) logger.w(TAG, "requestConnectionPriority(HIGH) rejected")
    }

    private fun enableTelemetryNotifications(gatt: BluetoothGatt) {
        val service = gatt.getService(MotorGattSpec.SERVICE) ?: run {
            logger.e(TAG, "Motor service not found")
            return
        }
        val telemetryChar = service.getCharacteristic(MotorGattSpec.CHAR_TELEMETRY) ?: return
        gatt.setCharacteristicNotification(telemetryChar, true)
        val cccd = telemetryChar.getDescriptor(MotorGattSpec.CCCD) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(
                cccd,
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE,
            )
        } else {
            @Suppress("DEPRECATION")
            cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(cccd)
        }
    }

    // ------------------------------------------------------------------
    // Commands
    // ------------------------------------------------------------------

    override suspend fun setTargetRotationHz(hz: Float): PrismResult<Unit> = runCatching {
        val activeGatt = gatt ?: error("Not connected")
        val service = activeGatt.getService(MotorGattSpec.SERVICE)
            ?: error("Motor service missing")
        val characteristic = service.getCharacteristic(MotorGattSpec.CHAR_SPEED)
            ?: error("Speed characteristic missing")

        val payload = ByteBuffer.allocate(MotorGattSpec.SPEED_PAYLOAD_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putFloat(hz)
            .array()

        val writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        val accepted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activeGatt.writeCharacteristic(characteristic, payload, writeType) ==
                BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            characteristic.writeType = writeType
            @Suppress("DEPRECATION")
            characteristic.value = payload
            @Suppress("DEPRECATION")
            activeGatt.writeCharacteristic(characteristic)
        }
        check(accepted) { "writeCharacteristic rejected" }

        val ready = _connectionState.value
        if (ready is MotorConnectionState.Ready) {
            _connectionState.value = ready.copy(negotiatedRotationHz = hz)
        }
        Unit
    }.fold(
        onSuccess = { PrismResult.Success(it) },
        onFailure = { t ->
            logger.w(TAG, "setTargetRotationHz($hz) failed: ${t.message}")
            PrismResult.Failure(
                PrismError.MotorCommandFailed(t.message ?: "write"),
                t,
            )
        },
    )

    override suspend fun sendControl(command: Byte): PrismResult<Unit> = runCatching {
        val activeGatt = gatt ?: error("Not connected")
        val service = activeGatt.getService(MotorGattSpec.SERVICE)
            ?: error("Motor service missing")
        val characteristic = service.getCharacteristic(MotorGattSpec.CHAR_CONTROL)
            ?: error("Control characteristic missing")

        val payload = byteArrayOf(command)
        val accepted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activeGatt.writeCharacteristic(
                characteristic,
                payload,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
            ) == BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            characteristic.value = payload
            @Suppress("DEPRECATION")
            activeGatt.writeCharacteristic(characteristic)
        }
        check(accepted) { "writeCharacteristic rejected" }
        Unit
    }.fold(
        onSuccess = { PrismResult.Success(it) },
        onFailure = { t ->
            logger.w(TAG, "sendControl(${command}) failed: ${t.message}")
            PrismResult.Failure(
                PrismError.MotorCommandFailed(t.message ?: "control"),
                t,
            )
        },
    )

    override suspend fun requestHighThroughput(): PrismResult<Unit> = runCatching {
        val activeGatt = gatt ?: error("Not connected")
        requestConnectionPriorityHigh(activeGatt)
        Unit
    }.fold(
        onSuccess = { PrismResult.Success(it) },
        onFailure = { t ->
            PrismResult.Failure(
                PrismError.MotorCommandFailed(t.message ?: "priority"),
                t,
            )
        },
    )

    // ------------------------------------------------------------------
    // Heartbeat
    // ------------------------------------------------------------------

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (true) {
                delay(MotorGattSpec.HEARTBEAT_INTERVAL_MS)
                val activeGatt = gatt ?: break
                val service = activeGatt.getService(MotorGattSpec.SERVICE) ?: break
                val control = service.getCharacteristic(MotorGattSpec.CHAR_CONTROL) ?: break
                // A zero-length write is rejected by most stacks; a read of
                // the control characteristic is the least invasive keep-alive.
                @Suppress("DEPRECATION")
                runCatching { activeGatt.readCharacteristic(control) }
            }
        }
    }

    // ------------------------------------------------------------------
    // GATT callback
    // ------------------------------------------------------------------

    private fun buildGattCallback(
        connectionContinuation: kotlinx.coroutines.CancellableContinuation<BluetoothGatt>,
    ): BluetoothGattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(
            gatt: BluetoothGatt,
            status: Int,
            newState: Int,
        ) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        if (connectionContinuation.isActive) {
                            connectionContinuation.resume(gatt)
                        }
                    } else {
                        if (connectionContinuation.isActive) {
                            connectionContinuation.resumeWithException(
                                IllegalStateException("GATT status $status"),
                            )
                        }
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    logger.w(TAG, "Disconnected (status=$status)")
                    val reason = if (status == BluetoothGatt.GATT_SUCCESS) {
                        MotorConnectionState.Disconnected.Reason.USER_REQUEST
                    } else {
                        MotorConnectionState.Disconnected.Reason.GATT_STATUS_ERROR
                    }
                    _connectionState.value = MotorConnectionState.Disconnected(
                        targetMac,
                        reason,
                    )
                    queue?.failAll(IllegalStateException("GATT disconnected"))
                    if (!manualDisconnect) scheduleReconnect()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val cont = serviceDiscoveryContinuation
            serviceDiscoveryContinuation = null
            if (status == BluetoothGatt.GATT_SUCCESS) {
                cont?.resume(Unit)
            } else {
                cont?.resumeWithException(
                    IllegalStateException("Service discovery failed: $status"),
                )
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                negotiatedMtu = mtu
                logger.i(TAG, "MTU negotiated: $mtu")
            } else {
                logger.w(TAG, "MTU change failed: $status")
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            queue?.completeOldest(GattOperationQueue.Op.Write::class.java)
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            queue?.completeOldest(GattOperationQueue.Op.Read::class.java, value)
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            queue?.completeOldest(GattOperationQueue.Op.WriteDescriptor::class.java)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            if (characteristic.uuid == MotorGattSpec.CHAR_TELEMETRY &&
                value.size >= MotorGattSpec.TELEMETRY_PAYLOAD_BYTES
            ) {
                val buf = ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN)
                _telemetry.value = MotorTelemetry(
                    actualRotationHz = buf.float,
                    currentMilliAmps = buf.int.toLong() and 0xFFFF_FFFFL,
                    deviceTimestampMillis = buf.int.toLong() and 0xFFFF_FFFFL,
                    receivedAtEpochMillis = System.currentTimeMillis(),
                )
            }
        }
    }

    @Volatile private var serviceDiscoveryContinuation:
        kotlinx.coroutines.CancellableContinuation<Unit>? = null

    // ------------------------------------------------------------------
    // Reconnection
    // ------------------------------------------------------------------

    private fun scheduleReconnect() {
        val mac = targetMac ?: return
        if (manualDisconnect) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            while (!manualDisconnect) {
                reconnectAttempt++
                val delayMillis = backoffMillis(reconnectAttempt)
                _connectionState.value = MotorConnectionState.Reconnecting(
                    mac,
                    reconnectAttempt,
                    delayMillis,
                )
                delay(delayMillis)
                val result = connect(mac)
                if (result is PrismResult.Success) {
                    reconnectAttempt = 0
                    return@launch
                }
            }
        }
    }

    private fun backoffMillis(attempt: Int): Long {
        val base = 1_000L
        val capped = base shl (attempt - 1).coerceIn(0, 5)
        return capped.coerceAtMost(30_000L)
    }

    // ------------------------------------------------------------------
    // Permissions
    // ------------------------------------------------------------------

    private fun requireBluetoothPermission() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                android.Manifest.permission.BLUETOOTH_SCAN,
                android.Manifest.permission.BLUETOOTH_CONNECT,
            )
        } else {
            listOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }
        permissions.forEach { perm ->
            check(
                ContextCompat.checkSelfPermission(context, perm) ==
                    PackageManager.PERMISSION_GRANTED,
            ) { "Bluetooth permission $perm not granted" }
        }
    }

    companion object {
        private const val TAG = "MotorController"
    }
}
