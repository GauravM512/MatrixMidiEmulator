package com.matrix.midiemulator.util

/**
 * The 128-color default palette used by MatrixOS for note-based LED feedback.
 * When the host sends NoteOn(note, velocity) to set a pad color, the velocity
 * value indexes into this palette. The MIDI channel selects which palette (Ch0 = Matrix default).
 *
 * This is the standard Matrix palette with 128 colors spanning the spectrum.
 */
object LedPalette {

    const val OFF_COLOR = 0xFF808080.toInt()

    /** 128 colors as RGB int values */
    val colors: IntArray
        get() = PaletteRuntime.snapshot()

    /**
     * Get color for a palette index (0-127).
     */
    fun getColor(index: Int): Int {
        return PaletteRuntime.getColor(index)
    }

    /**
     * Convert 6-bit RGB (0-63 per channel) to Android ARGB color.
     * Used for Apollo SysEx color data.
     */
    fun sixBitToColor(r6: Int, g6: Int, b6: Int): Int {
        return PaletteRuntime.getDirectColor(r6, g6, b6)
    }
}
