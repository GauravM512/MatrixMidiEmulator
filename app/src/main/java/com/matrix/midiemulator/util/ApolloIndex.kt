package com.matrix.midiemulator.util

/**
 * Converts between Apollo/Launchpad indices and MatrixOS note numbers.
 *
 * Apollo uses a 10x10 coordinate grid where index = row * 10 + col.
 * The 8x8 pad area is indices 11-88.
 * Special indices: 0 = global fill, 100-109 = row fill, 110-119 = column fill.
 */
object ApolloIndex {

    /**
     * Convert an Apollo index to a MatrixOS note number.
     * Returns null for global fill (index 0), row/column fills, or invalid indices.
     */
    fun toNote(index: Int): Int? {
        if (index < 0 || index > 119) return null
        if (index == 0 || isRowFill(index) || isColumnFill(index)) return null

        val x = index % 10 - 1
        val y = 8 - (index / 10)

        if (x in 0..7 && y in 0..7) {
            return NoteMap.noteForPad(x, 7 - y)
        }

        if (x == 8 && y in 0..7) {
            return 100 + y
        }

        if (y == -1 && x in 0..7) {
            return 28 + x
        }

        if (y == 8 && x in 0..7) {
            return 123 - x
        }

        if (x == -1 && y in 0..7) {
            return 108 + y
        }

        if (index == 99) {
            return 27
        }

        return null
    }

    fun isAddress(index: Int): Boolean {
        return index == 0 || isRowFill(index) || isColumnFill(index) || toNote(index) != null
    }

    fun isRowFill(index: Int): Boolean {
        return index in 100..109 && rowFillRow(index) in 0..7
    }

    fun isColumnFill(index: Int): Boolean {
        return index in 110..119 && columnFillColumn(index) in 0..7
    }

    /**
     * Get row number for a row-fill index (100-109).
     * Row 0 = top, Row 7 = bottom.
     */
    fun rowFillRow(index: Int): Int {
        return 108 - index
    }

    /**
     * Get column number for a column-fill index (110-119).
     * Column 0 = left, Column 7 = right.
     */
    fun columnFillColumn(index: Int): Int {
        return index - 111
    }
}
