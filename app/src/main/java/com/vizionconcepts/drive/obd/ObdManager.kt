package com.vizionconcepts.drive.obd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

enum class ObdState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

data class ObdSnapshot(
    val state: ObdState = ObdState.DISCONNECTED,
    val message: String = "",
    val adapterName: String = "",
    val values: Map<ObdPid, Float> = emptyMap(),
    val supported: Set<ObdPid> = emptySet(),
    val dtcs: List<String>? = null,      // null = not scanned yet
    val dtcBusy: Boolean = false,
) {
    operator fun get(pid: ObdPid): Float? = values[pid]
    val connected get() = state == ObdState.CONNECTED
}

data class PairedAdapter(val name: String, val address: String)

/**
 * ELM327 over Bluetooth Classic (SPP). One connection, one polling loop.
 * Fast PIDs (RPM, speed) are polled every cycle, slower ones round-robin.
 */
class ObdManager(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val io = Mutex()
    private val _state = MutableStateFlow(ObdSnapshot())
    val state: StateFlow<ObdSnapshot> = _state.asStateFlow()

    private var socket: BluetoothSocket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null
    private var pollJob: Job? = null

    /** Delay after ATZ reset and between commands; raise for slow clone adapters. */
    var initDelayMs = 1000L
    var commandDelayMs = 0L

    private val adapter: BluetoothAdapter?
        get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    @SuppressLint("MissingPermission")
    fun pairedAdapters(): List<PairedAdapter> = runCatching {
        adapter?.bondedDevices.orEmpty()
            .map { PairedAdapter(it.name ?: it.address, it.address) }
            .sortedByDescending { looksLikeObd(it.name) }
    }.getOrDefault(emptyList())

    private fun looksLikeObd(name: String) =
        listOf("OBD", "ELM", "VLINK", "VGATE", "ICAR", "KONNWEI", "V-LINK", "OBDII")
            .any { name.uppercase().contains(it) }

    @SuppressLint("MissingPermission")
    fun connect(address: String) {
        if (address.isBlank()) return
        disconnect()
        pollJob = scope.launch {
            _state.value = ObdSnapshot(state = ObdState.CONNECTING, message = "Connecting…")
            try {
                val device: BluetoothDevice = adapter?.getRemoteDevice(address)
                    ?: error("Bluetooth is off")
                adapter?.cancelDiscovery()
                val s = openSocket(device)
                socket = s; input = s.inputStream; output = s.outputStream
                initialise()
                val supported = querySupported()
                _state.update {
                    it.copy(
                        state = ObdState.CONNECTED,
                        adapterName = device.name ?: address,
                        message = "Connected",
                        supported = supported,
                    )
                }
                pollLoop(supported)
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                Log.w(TAG, "OBD connect failed", t)
                closeQuietly()
                _state.value = ObdSnapshot(state = ObdState.ERROR, message = t.message ?: "Connection failed")
            }
        }
    }

    fun disconnect() {
        pollJob?.cancel()
        pollJob = null
        closeQuietly()
        _state.value = ObdSnapshot()
    }

    /** Mode 03: read stored trouble codes. */
    fun scanDtcs() {
        if (!_state.value.connected) return
        scope.launch {
            _state.update { it.copy(dtcBusy = true) }
            val raw = runCatching { command("03", timeoutMs = 5000) }.getOrDefault("")
            _state.update { it.copy(dtcBusy = false, dtcs = ElmParser.dtcs(raw)) }
        }
    }

    /** Mode 04: clear codes and the check-engine light. Engine should be off, ignition on. */
    fun clearDtcs(onDone: (Boolean) -> Unit) {
        if (!_state.value.connected) return onDone(false)
        scope.launch {
            _state.update { it.copy(dtcBusy = true) }
            val raw = runCatching { command("04", timeoutMs = 5000) }.getOrDefault("")
            val ok = raw.uppercase().contains("44")
            val after = runCatching { command("03", timeoutMs = 5000) }.getOrDefault("")
            _state.update { it.copy(dtcBusy = false, dtcs = ElmParser.dtcs(after)) }
            withContext(Dispatchers.Main) { onDone(ok) }
        }
    }

    // ---- internals ----

    @SuppressLint("MissingPermission")
    private fun openSocket(device: BluetoothDevice): BluetoothSocket {
        val secure = runCatching {
            device.createRfcommSocketToServiceRecord(SPP).also { it.connect() }
        }
        if (secure.isSuccess) return secure.getOrThrow()
        // Many cheap adapters only accept insecure RFCOMM.
        return device.createInsecureRfcommSocketToServiceRecord(SPP).also { it.connect() }
    }

    private suspend fun initialise() {
        command("ATZ", timeoutMs = 3000)
        delay(initDelayMs)
        for (cmd in listOf("ATE0", "ATL0", "ATS0", "ATH0", "ATAT1", "ATSP0")) command(cmd)
        // First real request triggers protocol search; give it time.
        command("0100", timeoutMs = 10_000)
    }

    /** Reads the 0100/0120/0140 support bitmaps. Falls back to "try everything". */
    private suspend fun querySupported(): Set<ObdPid> {
        val bits = mutableSetOf<Int>()
        for (base in listOf(0x00, 0x20, 0x40)) {
            val raw = command("01%02X".format(base), timeoutMs = 3000)
            val data = ElmParser.mode01Data(raw, base) ?: break
            if (data.size < 4) break
            val mask = (data[0].toLong() shl 24) or (data[1].toLong() shl 16) or (data[2].toLong() shl 8) or data[3].toLong()
            for (i in 0 until 32) if (mask and (1L shl (31 - i)) != 0L) bits += base + i + 1
            if (mask and 1L == 0L) break
        }
        val found = ObdPid.entries.filter { it.code in bits }.toSet()
        return found.ifEmpty { ObdPid.entries.toSet() }
    }

    private suspend fun pollLoop(supported: Set<ObdPid>) {
        val fast = supported.filter { it.priority == 0 }
        val slow = supported.filter { it.priority > 0 }.sortedBy { it.priority }
        var i = 0
        var failures = 0
        while (currentCoroutineContext().isActive && socket?.isConnected == true) {
            val batch = fast + listOfNotNull(slow.getOrNull(i % slow.size.coerceAtLeast(1)))
            i++
            for (pid in batch) {
                val raw = try {
                    command(pid.request)
                } catch (c: CancellationException) {
                    throw c
                } catch (t: Throwable) {
                    failures++; ""
                }
                val v = ElmParser.mode01Data(raw, pid.code)?.let(pid::decode)
                if (v != null) {
                    failures = 0
                    _state.update { it.copy(values = it.values + (pid to v)) }
                }
                if (commandDelayMs > 0) delay(commandDelayMs)
            }
            if (failures > 12) error("Adapter stopped responding")
        }
    }

    /** Sends one command and reads until the ELM `>` prompt. */
    private suspend fun command(cmd: String, timeoutMs: Long = 1500): String = io.withLock {
        val out = output ?: error("Not connected")
        val inp = input ?: error("Not connected")
        while (inp.available() > 0) inp.read() // drop stale bytes
        out.write((cmd + "\r").toByteArray())
        out.flush()
        val sb = StringBuilder()
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (inp.available() > 0) {
                val c = inp.read()
                if (c < 0) error("Stream closed")
                if (c.toChar() == '>') return@withLock sb.toString().trim()
                sb.append(c.toChar())
            } else {
                delay(5)
            }
        }
        sb.toString().trim()
    }

    private fun closeQuietly() {
        runCatching { input?.close() }
        runCatching { output?.close() }
        runCatching { socket?.close() }
        input = null; output = null; socket = null
    }

    companion object {
        private const val TAG = "ObdManager"
        private val SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
