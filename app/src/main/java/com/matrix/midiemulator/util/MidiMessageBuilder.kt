package com.matrix.midiemulator.util

/**
 * MIDI message builder utilities matching the MatrixOS protocol.
 *
 * Protocol summary:
 *   Key Press:     NoteOn (0x90, note, velocity 1-127)
 *   Key Hold:      Polyphonic Aftertouch (0xA0, note, pressure 1-127)
 *   Key Release:   NoteOn with velocity=0 (0x90, note, 0x00)
 *   FN Button:     CC 121 val=127 + CC 123 val=0 (press) / CC 121 val=0 + CC 123 val=0 (release)
 */
object MidiMessageBuilder {

    // Status bytes
    private const val NOTE_ON = 0x90
    private const val POLY_AFTERTOUCH = 0xA0
    private const val CONTROL_CHANGE = 0xB0

    /**
     * Build a NoteOn message for key press.
     * @param note MIDI note number (36-99 for grid, 100-115 for touchbar)
     * @param velocity 1-127 based on touch pressure
     */
    fun noteOn(note: Int, velocity: Int): ByteArray {
        return byteArrayOf(
            NOTE_ON.toByte(),
            note.coerceIn(0, 127).toByte(),
            velocity.coerceIn(1, 127).toByte()
        )
    }

    /**
     * Build a NoteOn with velocity=0 for key release.
     * MatrixOS uses NoteOn vel=0 instead of NoteOff.
     */
    fun noteOff(note: Int): ByteArray {
        return byteArrayOf(
            NOTE_ON.toByte(),
            note.coerceIn(0, 127).toByte(),
            0x00.toByte()
        )
    }

    /**
     * Build a Polyphonic Aftertouch message for key hold/pressure.
     * @param note MIDI note number
     * @param pressure 1-127 based on ongoing touch pressure
     */
    fun polyAftertouch(note: Int, pressure: Int): ByteArray {
        return byteArrayOf(
            POLY_AFTERTOUCH.toByte(),
            note.coerceIn(0, 127).toByte(),
            pressure.coerceIn(1, 127).toByte()
        )
    }

    /**
     * Build FN button press messages (two CC messages).
     */
    fun fnPress(): ByteArray {
        return byteArrayOf(
            CONTROL_CHANGE.toByte(), NoteMap.FN_CC_1.toByte(), 127.toByte(),
            CONTROL_CHANGE.toByte(), NoteMap.FN_CC_2.toByte(), 0x00.toByte()
        )
    }

    /**
     * Build FN button release messages (two CC messages).
     */
    fun fnRelease(): ByteArray {
        return byteArrayOf(
            CONTROL_CHANGE.toByte(), NoteMap.FN_CC_1.toByte(), 0x00.toByte(),
            CONTROL_CHANGE.toByte(), NoteMap.FN_CC_2.toByte(), 0x00.toByte()
        )
    }
}
