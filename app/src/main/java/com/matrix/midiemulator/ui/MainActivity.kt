package com.matrix.midiemulator.ui

import android.content.Intent
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.matrix.midiemulator.R
import com.matrix.midiemulator.midi.MatrixMidiDeviceService
import com.matrix.midiemulator.midi.MidiReceiver
import com.matrix.midiemulator.midi.UsbMidiBridge
import com.matrix.midiemulator.util.AppPreferences
import com.matrix.midiemulator.util.MidiMessageBuilder
import com.matrix.midiemulator.util.LedPalette
import com.matrix.midiemulator.util.NoteMap

/**
 * Main activity that displays the pad grid and manages the MIDI connection.
 */
class MainActivity : AppCompatActivity(), MidiReceiver.MidiLedListener {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var padGrid: PadGridView
    private lateinit var touchbarContainer: LinearLayout
    private lateinit var touchbar: TouchbarView
    private lateinit var statusText: TextView
    private lateinit var statusIndicator: View
    private lateinit var fnButtonContainer: View
    private lateinit var fnButton: TextView
    private lateinit var deviceNameText: TextView
    private lateinit var settingsButton: TextView

    private var isConnected = false
    private var isFnPressed = false
    private var usbBridge: UsbMidiBridge? = null
    private var bridgeParser: MidiReceiver? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val statusTicker = object : Runnable {
        override fun run() {
            val service = MatrixMidiDeviceService.instance
            val bridge = usbBridge
            if (service != null) {
                val base = if (isConnected) getString(R.string.status_connected) else getString(R.string.status_disconnected)
                val bridgeStats = bridge?.statsSnapshot() ?: "B_TX=0 B_RX=0 B_CAN_TX=false B_CAN_RX=false"
                statusText.text = "$base | ${service.getStatsSnapshot()} | $bridgeStats"
            }
            mainHandler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Ensure service instance is live before first touch events.
        startService(Intent(this, MatrixMidiDeviceService::class.java))
        usbBridge = UsbMidiBridge(this)
        bridgeParser = MidiReceiver(this)
        usbBridge?.setPacketListener { data, timestamp ->
            bridgeParser?.onSend(data, 0, data.size, timestamp)
        }

        initViews()
        setupPadGrid()
        setupTouchbar()
        setupFnButton()
        setupSettingsButton()
        applyUserPreferences()
        checkMidiConnection()
        mainHandler.post(statusTicker)
    }

    override fun onResume() {
        super.onResume()
        // Register as LED listener
        MatrixMidiDeviceService.ledListener = this
        connectUsbBridgeAsync()
        applyUserPreferences()
        checkMidiConnection()
        mainHandler.post(statusTicker)
    }

    override fun onPause() {
        super.onPause()
        // Don't unregister — keep receiving LED data
        mainHandler.removeCallbacks(statusTicker)
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacks(statusTicker)
        usbBridge?.close()
        usbBridge = null
        bridgeParser = null
    }

    private fun connectUsbBridgeAsync() {
        val bridge = usbBridge ?: return
        if (!bridge.isSupported()) return
        Thread {
            val idx = bridge.getRecommendedTargetIndex()
            if (idx >= 0) {
                bridge.openOutputByIndex(idx)
            }
            mainHandler.post { checkMidiConnection() }
        }.start()
    }

    private fun initViews() {
        padGrid = findViewById(R.id.padGrid)
        touchbarContainer = findViewById(R.id.touchbarContainer)
        statusText = findViewById(R.id.statusText)
        statusIndicator = findViewById(R.id.statusIndicator)
        fnButtonContainer = findViewById(R.id.fnButtonContainer)
        fnButton = findViewById(R.id.fnButton)
        deviceNameText = findViewById(R.id.deviceNameText)
        settingsButton = findViewById(R.id.settingsButton)
    }

    private fun setupSettingsButton() {
        settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun applyUserPreferences() {
        deviceNameText.visibility = if (AppPreferences.isTitleVisible(this)) View.VISIBLE else View.GONE
        fnButtonContainer.visibility = if (AppPreferences.isFnVisible(this)) View.VISIBLE else View.GONE
        if (::touchbar.isInitialized) {
            touchbar.setSelectedPage(AppPreferences.getSelectedPage(this))
        }
    }

    private fun setupPadGrid() {
        padGrid.onPadEventListener = object : PadGridView.PadEventListener {
            override fun onPadPress(note: Int, velocity: Int) {
                sendToHost(MidiMessageBuilder.noteOn(note, velocity))
            }

            override fun onPadRelease(note: Int) {
                sendToHost(MidiMessageBuilder.noteOff(note))
            }

            override fun onPadAftertouch(note: Int, pressure: Int) {
                sendToHost(MidiMessageBuilder.polyAftertouch(note, pressure))
            }
        }
    }

    private fun setupTouchbar() {
        // Programmatically create and add the touchbar view
        touchbar = TouchbarView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }
        touchbarContainer.addView(touchbar)

        touchbar.onTouchListener = object : TouchbarView.TouchbarEventListener {
            override fun onSegmentPress(index: Int, velocity: Int) {
                AppPreferences.setSelectedPage(this@MainActivity, index + 1)
                touchbar.setSelectedPage(index + 1)
                val note = NoteMap.noteForTouchbar(index)
                sendToHost(MidiMessageBuilder.noteOn(note, velocity))
            }

            override fun onSegmentRelease(index: Int) {
                val note = NoteMap.noteForTouchbar(index)
                sendToHost(MidiMessageBuilder.noteOff(note))
            }

            override fun onSegmentAftertouch(index: Int, pressure: Int) {
                val note = NoteMap.noteForTouchbar(index)
                sendToHost(MidiMessageBuilder.polyAftertouch(note, pressure))
            }
        }
    }

    private fun setupFnButton() {
        fnButton.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    isFnPressed = true
                    fnButton.setBackgroundColor(0xFF6C63FF.toInt())
                    sendToHost(MidiMessageBuilder.fnPress())
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isFnPressed = false
                    fnButton.setBackgroundColor(LedPalette.OFF_COLOR)
                    sendToHost(MidiMessageBuilder.fnRelease())
                    true
                }
                else -> false
            }
        }
    }

    private fun checkMidiConnection() {
        val midiMgr = getSystemService(MIDI_SERVICE) as? MidiManager
        if (midiMgr != null) {
            // Check if our MIDI device is already available
            val devices = midiMgr.devices
            for (device in devices) {
                if (device.properties.getString(MidiDeviceInfo.PROPERTY_NAME) == "Matrix Emulator") {
                    onConnected()
                    return
                }
            }

            // Register for device connection callback
            midiMgr.registerDeviceCallback(object : MidiManager.DeviceCallback() {
                override fun onDeviceAdded(info: MidiDeviceInfo) {
                    if (info.properties.getString(MidiDeviceInfo.PROPERTY_NAME) == "Matrix Emulator") {
                        mainHandler.post { onConnected() }
                    }
                }

                override fun onDeviceRemoved(info: MidiDeviceInfo) {
                    if (info.properties.getString(MidiDeviceInfo.PROPERTY_NAME) == "Matrix Emulator") {
                        mainHandler.post { onDisconnected() }
                    }
                }
            }, mainHandler)
        }

        // Also check via service
        val serviceInstance = MatrixMidiDeviceService.instance
        val bridgeConnected = usbBridge?.canSend() == true || usbBridge?.canReceive() == true
        if (serviceInstance?.isConnectedToHost() == true || bridgeConnected) {
            onConnected()
        } else {
            onDisconnected()
        }
    }

    private fun onConnected() {
        isConnected = true
        statusText.text = getString(R.string.status_connected)
        statusIndicator.setBackgroundResource(R.drawable.circle_green)
    }

    private fun onDisconnected() {
        isConnected = false
        statusText.text = getString(R.string.status_disconnected)
        statusIndicator.setBackgroundResource(R.drawable.circle_red)
    }

    private fun sendToHost(data: ByteArray) {
        val bridge = usbBridge
        if (bridge?.canSend() == true) {
            try {
                bridge.sendMidi(data)
                return
            } catch (e: Exception) {
                Log.w(TAG, "Bridge send failed, falling back to service", e)
            }
        }

        val service = MatrixMidiDeviceService.instance
        val sent = service?.sendToHost(data) == true
        if (!sent) {
            Log.w(TAG, "MIDI host endpoint not open yet — message buffered")
        }
    }

    // === MidiLedListener implementation ===

    override fun onPadColorChange(note: Int, color: Int) {
        mainHandler.post {
            padGrid.setPadColor(note, color)
        }
    }

    override fun onTouchbarColorChange(index: Int, color: Int) {
        mainHandler.post {
            padGrid.setEdgeSegmentColor(index, color)
        }
    }

    override fun onClearAll() {
        mainHandler.post {
            padGrid.clearAll()
        }
    }
}
