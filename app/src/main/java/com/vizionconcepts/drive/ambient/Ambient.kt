package com.vizionconcepts.drive.ambient

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

data class LightDevice(val name: String, val address: String)

data class AmbientState(
    val scanning: Boolean = false,
    val found: List<LightDevice> = emptyList(),
    val connectedTo: String = "",
    val connecting: Boolean = false,
    val on: Boolean = true,
    val brightness: Int = 80,
    val color: Long = 0xFFFF3B30,
    val error: String = "",
)

class AmbientLightManager(private val context: Context) {

    private val _state = MutableStateFlow(AmbientState())
    val state: StateFlow<AmbientState> = _state.asStateFlow()

    private val main = Handler(Looper.getMainLooper())
    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private val queue = ArrayDeque<ByteArray>()
    private var writing = false

    private val bt get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.scanRecord?.deviceName ?: runCatching { result.device.name }.getOrNull()
            if (!ElkBledom.looksLikeController(name)) return
            val dev = LightDevice(name ?: result.device.address, result.device.address)
            _state.update { s -> if (s.found.any { it.address == dev.address }) s else s.copy(found = s.found + dev) }
        }
        override fun onScanFailed(errorCode: Int) {
            _state.update { it.copy(scanning = false, error = "Scan failed ($errorCode)") }
        }
    }

    @SuppressLint("MissingPermission")
    fun scan(seconds: Long = 8) {
        val scanner = bt?.bluetoothLeScanner ?: run {
            _state.update { it.copy(error = "Bluetooth is off") }; return
        }
        _state.update { it.copy(scanning = true, found = emptyList(), error = "") }
        runCatching { scanner.startScan(scanCallback) }.onFailure {
            _state.update { s -> s.copy(scanning = false, error = it.message ?: "Scan failed") }
            return
        }
        main.postDelayed({ stopScan() }, seconds * 1000)
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        runCatching { bt?.bluetoothLeScanner?.stopScan(scanCallback) }
        _state.update { it.copy(scanning = false) }
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String) {
        if (address.isBlank()) return
        disconnect()
        val device: BluetoothDevice = runCatching { bt?.getRemoteDevice(address) }.getOrNull() ?: return
        _state.update { it.copy(connecting = true, error = "") }
        gatt = device.connectGatt(context, true, callback, BluetoothDevice.TRANSPORT_LE)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        runCatching { gatt?.close() }
        gatt = null; writeChar = null
        synchronized(queue) { queue.clear(); writing = false }
        _state.update { it.copy(connectedTo = "", connecting = false) }
    }

    fun setPower(on: Boolean) { _state.update { it.copy(on = on) }; send(ElkBledom.power(on)) }

    fun setBrightness(percent: Int) { _state.update { it.copy(brightness = percent) }; send(ElkBledom.brightness(percent)) }

    fun setColor(argb: Long) {
        _state.update { it.copy(color = argb) }
        val (r, g, b) = ElkBledom.rgbOf(argb)
        send(ElkBledom.color(r, g, b))
    }

    fun playEffect(index: Int) = send(ElkBledom.effect(index))

    private fun send(frame: ByteArray) {
        synchronized(queue) {
            if (queue.size > 8) queue.removeFirst() // drop stale slider updates
            queue.addLast(frame)
        }
        pump()
    }

    @SuppressLint("MissingPermission")
    private fun pump() {
        val g = gatt ?: return
        val c = writeChar ?: return
        val next: ByteArray = synchronized(queue) {
            if (writing || queue.isEmpty()) return
            writing = true
            queue.removeFirst()
        }
        val type = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        val ok = if (Build.VERSION.SDK_INT >= 33) {
            g.writeCharacteristic(c, next, type) == BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            run { c.writeType = type; c.value = next; g.writeCharacteristic(c) }
        }
        if (!ok) synchronized(queue) { writing = false }
        // Controllers don't reliably ack no-response writes, so pace them ourselves.
        main.postDelayed({ synchronized(queue) { writing = false }; pump() }, 40)
    }

    private val callback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                g.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                writeChar = null
                _state.update { it.copy(connectedTo = "", connecting = false) }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val ch = g.getService(SERVICE)?.getCharacteristic(WRITE)
                ?: g.services.flatMap { it.characteristics }.firstOrNull {
                    it.properties and (BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or BluetoothGattCharacteristic.PROPERTY_WRITE) != 0
                }
            if (ch == null) {
                _state.update { it.copy(connecting = false, error = "Not an ELK-BLEDOM controller") }
                return
            }
            writeChar = ch
            _state.update { it.copy(connecting = false, connectedTo = g.device.address) }
            val s = _state.value
            setPower(s.on); setBrightness(s.brightness); setColor(s.color)
        }
    }

    companion object {
        private val SERVICE: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
        private val WRITE: UUID = UUID.fromString("0000fff3-0000-1000-8000-00805f9b34fb")
    }
}
