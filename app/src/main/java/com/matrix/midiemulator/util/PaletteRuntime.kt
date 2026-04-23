package com.matrix.midiemulator.util

object PaletteRuntime {
    private const val OFF_COLOR = 0xFF808080.toInt()

    @Volatile
    private var activeColors: IntArray = asOpaqueArgb(MidiConstants.PALETTE)

    fun setActiveColors(colors: IntArray) {
        activeColors = asOpaqueArgb(colors)
    }

    fun getColor(index: Int): Int {
        if (index <= 0) return OFF_COLOR
        return activeColors[index.coerceIn(0, 127)]
    }

    fun snapshot(): IntArray {
        return activeColors.copyOf()
    }

    fun resetToDefault() {
        activeColors = asOpaqueArgb(MidiConstants.PALETTE)
    }

    private fun asOpaqueArgb(colors: IntArray): IntArray {
        val converted = IntArray(128)
        for (i in converted.indices) {
            val src = colors.getOrElse(i) { MidiConstants.PALETTE[i] }
            converted[i] = 0xFF000000.toInt() or (src and 0x00FFFFFF)
        }
        return converted
    }
}