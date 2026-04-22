package com.matrix.midiemulator.midi

import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiInputPort
import android.media.midi.MidiOutputPort
import android.media.midi.MidiReceiver as AndroidMidiReceiver
import android.util.Log
import com.matrix.midiemulator.util.MidiMessageBuilder

/**
 * Manages the MIDI device connection for the Matrix emulator.
 * Handles sending MIDI data to the host (PC) and provides the
 * output port for the MidiReceiver to listen on.
 *
 * When Android is connected via USB in "MIDI" mode, the system creates
 * a virtual MIDI device. This manager wraps that device for bidirectional
 * communication.
 */
class MidiManager(private val listener: MidiReceiver.MidiLedListener) {

    companion object {
        private const val TAG = "MidiManager"
    }

    private var midiDevice: MidiDevice? = null
    private var outputPort: MidiOutputPort? = null
    private var inputPort: MidiInputPort? = null
    private var midiReceiver: MidiReceiver? = null
    private var midiDispatcher: AndroidMidiReceiver? = null
    private var isConnected = false

    /**
     * Called when the MidiDeviceService opens the device.
     * Sets up the input/output ports and registers the LED listener.
     */
    fun onDeviceOpened(device: MidiDevice) {
        midiDevice = device
        val info = device.info
        val deviceName = info.properties.getString(MidiDeviceInfo.PROPERTY_NAME) ?: "Unknown MIDI Device"

        Log.i(TAG, "MIDI device opened: $deviceName")

        // Open output port (receives data FROM host) for LED feedback
        try {
            outputPort = device.openOutputPort(0)
            outputPort?.let { port ->
                midiReceiver = com.matrix.midiemulator.midi.MidiReceiver(listener)
                midiDispatcher = object : AndroidMidiReceiver() {
                    override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
                        midiReceiver?.onSend(msg, offset, count, timestamp)
                    }
                }
                port.connect(midiDispatcher)
                Log.i(TAG, "Output port 0 connected for LED feedback")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open output port", e)
        }

        // Get input port (sends data TO host)
        try {
            inputPort = device.openInputPort(0)
            Log.i(TAG, "Input port 0 opened for sending key data")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open input port", e)
        }

        isConnected = true
    }

    /**
     * Called when the MIDI device is closed.
     */
    fun onDeviceClosed() {
        Log.i(TAG, "MIDI device closed")
        outputPort?.let {
            midiDispatcher?.let { dispatcher -> it.disconnect(dispatcher) }
        }
        outputPort = null
        inputPort = null
        midiDevice = null
        midiReceiver = null
        midiDispatcher = null
        isConnected = false
    }

    /**
     * Send raw MIDI bytes to the host.
     */
    fun sendMidi(data: ByteArray) {
        inputPort?.let { port ->
            try {
                port.send(data, 0, data.size)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send MIDI data", e)
            }
        }
    }

    /**
     * Send NoteOn for key press.
     */
    fun sendNoteOn(note: Int, velocity: Int) {
        sendMidi(MidiMessageBuilder.noteOn(note, velocity))
    }

    /**
     * Send NoteOn vel=0 for key release.
     */
    fun sendNoteOff(note: Int) {
        sendMidi(MidiMessageBuilder.noteOff(note))
    }

    /**
     * Send Polyphonic Aftertouch for key hold/pressure.
     */
    fun sendAftertouch(note: Int, pressure: Int) {
        sendMidi(MidiMessageBuilder.polyAftertouch(note, pressure))
    }

    /**
     * Send FN button press.
     */
    fun sendFnPress() {
        sendMidi(MidiMessageBuilder.fnPress())
    }

    /**
     * Send FN button release.
     */
    fun sendFnRelease() {
        sendMidi(MidiMessageBuilder.fnRelease())
    }

    fun connected(): Boolean = isConnected
}
