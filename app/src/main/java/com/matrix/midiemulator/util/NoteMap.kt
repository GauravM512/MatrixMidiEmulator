package com.matrix.midiemulator.util

/**
 * Maps the 8x8 pad grid to MIDI note numbers matching the Matrix/Mystrix drum rack layout.
 *
 * Grid layout (8 rows × 8 columns):
 *   Row 0 = bottom row → Notes 36-39 ... (left to right within groups of 4)
 *   Row 7 = top row
 *
 * The Mystrix uses an 8x8 grid where:
 *   Bottom-left  = Note 36
 *   Bottom-right = Note 99 (top-right in full 16x4, but 8x8 bottom-right)
 *
 * For the 8x8 drum rack:
 *   Row 0 (bottom): 36, 37, 38, 39, 40, 41, 42, 43
 *   Row 1:          44, 45, 46, 47, 48, 49, 50, 51
 *   Row 2:          52, 53, 54, 55, 56, 57, 58, 59
 *   Row 3:          60, 61, 62, 63, 64, 65, 66, 67
 *   Row 4:          68, 69, 70, 71, 72, 73, 74, 75
 *   Row 5:          76, 77, 78, 79, 80, 81, 82, 83
 *   Row 6:          84, 85, 86, 87, 88, 89, 90, 91
 *   Row 7 (top):    92, 93, 94, 95, 96, 97, 98, 99
 *
 * Touchbars: Notes 100-115 (16 touchbar segments)
 */
object NoteMap {

    const val GRID_ROWS = 8
    const val GRID_COLS = 8
    const val TOUCHBAR_COUNT = 16

    /** Touchbar start note */
    const val TOUCHBAR_BASE_NOTE = 100

    /** FN button CC numbers */
    const val FN_CC_1 = 121
    const val FN_CC_2 = 123

    /**
     * Get MIDI note number for a pad at (col, row) in the grid.
     * Row 0 = bottom row, Row 7 = top row (matching physical device orientation).
     */
    fun noteForPad(col: Int, row: Int): Int {
        require(col in 0 until GRID_COLS) { "Column $col out of range 0..${GRID_COLS - 1}" }
        require(row in 0 until GRID_ROWS) { "Row $row out of range 0..${GRID_ROWS - 1}" }
        return MidiConstants.DRUM_RACK_KEYMAP[row][col]
    }

    /**
     * Get the (col, row) for a given MIDI note, or null if not in grid range.
     */
    fun padForNote(note: Int): Pair<Int, Int>? {
        for (row in 0 until GRID_ROWS) {
            for (col in 0 until GRID_COLS) {
                if (MidiConstants.DRUM_RACK_KEYMAP[row][col] == note) {
                    return Pair(col, row)
                }
            }
        }
        return null
    }

    /**
     * Get MIDI note for touchbar index (0-15).
     */
    fun noteForTouchbar(index: Int): Int {
        require(index in 0 until TOUCHBAR_COUNT) { "Touchbar index $index out of range" }
        return TOUCHBAR_BASE_NOTE + index
    }

    /**
     * Get touchbar index for a MIDI note, or null if not in touchbar range.
     */
    fun touchbarForNote(note: Int): Int? {
        if (note < TOUCHBAR_BASE_NOTE || note >= TOUCHBAR_BASE_NOTE + TOUCHBAR_COUNT) return null
        return note - TOUCHBAR_BASE_NOTE
    }
}
