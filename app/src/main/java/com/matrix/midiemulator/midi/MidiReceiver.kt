package com.matrix.midiemulator.midi

import android.media.midi.MidiReceiver as AndroidMidiReceiver
import android.util.Log
import com.matrix.midiemulator.util.LedPalette
import com.matrix.midiemulator.util.NoteMap

/**
 * Parses incoming MIDI messages from the host (PC) and dispatches LED color updates.
 *
 * Supported protocols:
 * 1. Note-based palette: NoteOn(note, velocity) → velocity indexes 128-color palette
 * 2. Apollo SysEx 0x5E: Regular fill with 6-bit RGB per LED
 * 3. Apollo SysEx 0x5F: Batch fill with RLE encoding
 */
class MidiReceiver(
    private val listener: MidiLedListener
) : AndroidMidiReceiver() {

    companion object {
        private const val TAG = "MidiReceiver"

        // SysEx manufacturer ID for Apollo/Matrix
        private const val SYSEX_MANUFACTURER = 0x5E
        private const val SYSEX_BATCH = 0x5F
        private const val SYSEX_START = 0xF0.toByte()
        private const val SYSEX_END = 0xF7.toByte()
    }

    private var sysExBuffer = mutableListOf<Byte>()
    private var collectingSysEx = false
    private var runningStatus = 0
    private var expectedDataBytes = 0
    private var dataByteCount = 0
    private val dataBytes = IntArray(2)

    /** Current palette selection based on MIDI channel of last NoteOn */
    private var currentPalette = 0

    interface MidiLedListener {
        /** Called when a pad LED color changes (note 36-99) */
        fun onPadColorChange(note: Int, color: Int)

        /** Called when an edge LED color changes (notes 28-35, 100-107, 108-115, 116-123) */
        fun onEdgeColorChange(note: Int, color: Int)

        /** Called when all LEDs should be cleared */
        fun onClearAll()
    }

    override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
        val end = offset + count
        for (i in offset until end) {
            processByte(msg[i].toInt() and 0xFF)
        }
    }

    private fun processByte(byte: Int) {
        // SysEx start always resets channel message assembly.
        if (byte == 0xF0) {
            collectingSysEx = true
            sysExBuffer.clear()
            sysExBuffer.add(byte.toByte())
            dataByteCount = 0
            return
        }

        if (collectingSysEx) {
            sysExBuffer.add(byte.toByte())
            if (byte == 0xF7) {
                collectingSysEx = false
                processSysEx(sysExBuffer.toByteArray())
                sysExBuffer.clear()
            }
            return
        }

        // Realtime messages can interleave anywhere.
        if (byte >= 0xF8) {
            return
        }

        if (byte and 0x80 != 0) {
            if (byte in 0x80..0xEF) {
                runningStatus = byte
                expectedDataBytes = channelDataLength(byte)
                dataByteCount = 0
            } else {
                // System common messages are ignored by this receiver.
                runningStatus = 0
                expectedDataBytes = 0
                dataByteCount = 0
            }
            return
        }

        if (runningStatus == 0 || expectedDataBytes == 0) {
            return
        }

        dataBytes[dataByteCount] = byte
        dataByteCount++
        if (dataByteCount >= expectedDataBytes) {
            val d1 = dataBytes[0]
            val d2 = if (expectedDataBytes > 1) dataBytes[1] else 0
            handleChannelMessage(runningStatus, d1, d2)
            dataByteCount = 0
        }
    }

    private fun channelDataLength(statusByte: Int): Int {
        return when (statusByte and 0xF0) {
            0xC0, 0xD0 -> 1
            else -> 2
        }
    }

    private fun handleChannelMessage(statusByte: Int, data1: Int, data2: Int) {
        val status = statusByte and 0xF0
        val channel = statusByte and 0x0F
        when (status) {
            0x90 -> {
                val note = data1 and 0xFF
                val velocity = data2 and 0xFF
                handleNoteOn(note, velocity, channel)
            }
            0x80 -> {
                val note = data1 and 0xFF
                handleNoteOff(note)
            }
            0xB0 -> {
                handleCc(data1 and 0xFF, data2 and 0xFF, channel)
            }
        }
    }

    private fun handleCc(cc: Int, value: Int, channel: Int) {
        // Many hosts/drivers use CC for direct LED values; map known note ranges as a fallback.
        if (cc in 36..115) {
            handleNoteOn(cc, value, channel)
            return
        }
        if (cc == 0 && value == 0) {
            listener.onClearAll()
        }
    }

    /**
     * Handle incoming NoteOn (LED color from host).
     * Velocity indexes into the palette; channel selects palette.
     */
    private fun handleNoteOn(note: Int, velocity: Int, channel: Int) {
        currentPalette = channel

        val color = if (velocity == 0) {
            LedPalette.OFF_COLOR
        } else {
            LedPalette.getColor(velocity)
        }

        // Check if it's a grid pad or touchbar
        val padPos = NoteMap.padForNote(note)
        if (padPos != null) {
            listener.onPadColorChange(note, color)
            return
        }

        // Check if it's an edge note
        if (isEdgeNote(note)) {
            listener.onEdgeColorChange(note, color)
            return
        }
    }

    /** Helper to check if a note corresponds to an edge segment. */
    private fun isEdgeNote(note: Int): Boolean {
        return (note in 28..35 || // Top edge
                note in 100..107 || // Right edge
                note in 108..115 || // Left edge
                note in 116..123)    // Bottom edge
    }

    private fun handleNoteOff(note: Int) {
        val padPos = NoteMap.padForNote(note)
        if (padPos != null) {
            listener.onPadColorChange(note, LedPalette.OFF_COLOR)
            return
        }

        if (isEdgeNote(note)) {
            listener.onEdgeColorChange(note, LedPalette.OFF_COLOR)
        }
    }

    /**
     * Process a complete SysEx message.
     * Format 1: F0 5E <index><R6><G6><B6> [...] F7  (individual LED fill)
     * Format 2: F0 5F <data...> F7                      (batch/RLE fill)
     */
    private fun processSysEx(data: ByteArray) {
        if (data.size < 3) return

        val manufacturer = data[1].toInt() and 0xFF

        when (manufacturer) {
            SYSEX_MANUFACTURER -> processSysEx5E(data)
            SYSEX_BATCH -> processSysEx5F(data)
            else -> Log.w(TAG, "Unknown SysEx manufacturer: 0x${manufacturer.toString(16)}")
        }
    }

    /**
     * Apollo SysEx 0x5E — Regular fill.
     * Format: F0 5E <note><R6><G6><B6> <note><R6><G6><B6> ... F7
     * Each group is 4 bytes: note index + 6-bit RGB
     */
    private fun processSysEx5E(data: ByteArray) {
        // Data starts after F0 5E, ends before F7
        var i = 2
        while (i + 3 < data.size - 1) { // -1 for F7
            val note = data[i].toInt() and 0xFF
            val r6 = data[i + 1].toInt() and 0x3F
            val g6 = data[i + 2].toInt() and 0x3F
            val b6 = data[i + 3].toInt() and 0x3F

            val color = LedPalette.sixBitToColor(r6, g6, b6)

            val padPos = NoteMap.padForNote(note)
            if (padPos != null) {
                listener.onPadColorChange(note, color)
            } else {
                // Check if it's an edge note
                if (isEdgeNote(note)) {
                    listener.onEdgeColorChange(note, color)
                }
            }

            i += 4
        }
    }

    /**
     * Apollo SysEx 0x5F — Batch fill with optional RLE.
     * Simplified implementation: treat as sequential 4-byte groups like 0x5E.
     * Full RLE support can be added later.
     */
    private fun processSysEx5F(data: ByteArray) {
        // For now, process similarly to 0x5E
        // Full RLE decoding would require more complex parsing
        var i = 2
        while (i + 3 < data.size - 1) {
            val note = data[i].toInt() and 0xFF
            val r6 = data[i + 1].toInt() and 0x3F
            val g6 = data[i + 2].toInt() and 0x3F
            val b6 = data[i + 3].toInt() and 0x3F

            val color = LedPalette.sixBitToColor(r6, g6, b6)

            val padPos = NoteMap.padForNote(note)
            if (padPos != null) {
                listener.onPadColorChange(note, color)
            } else {
                // Check if it's an edge note
                if (isEdgeNote(note)) {
                    listener.onEdgeColorChange(note, color)
                }
            }

            i += 4
        }
    }
}
