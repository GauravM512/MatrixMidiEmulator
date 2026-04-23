package com.matrix.midiemulator.util

import android.graphics.Color
import kotlin.math.pow

object PaletteRuntime {
    private const val OFF_COLOR = 0xFF808080.toInt()
    private const val GAMMA = 0.6
    private const val BASE_GRAY = 70

    @Volatile
    private var activeColors: IntArray = asOpaqueArgb(MidiConstants.PALETTE)
    
    @Volatile
    private var isCustomPalette = false

    fun setActiveColors(colors: IntArray, isCustom: Boolean = false) {
        activeColors = asOpaqueArgb(colors)
        isCustomPalette = isCustom
    }

    fun getColor(index: Int): Int {
        if (index <= 0) return OFF_COLOR
        val color = activeColors[index.coerceIn(0, 127)]
        return if (isCustomPalette) applyGamma(color) else color
    }

    fun snapshot(): IntArray {
        return activeColors.copyOf()
    }

    fun resetToDefault() {
        activeColors = asOpaqueArgb(MidiConstants.PALETTE)
        isCustomPalette = false
    }

    private fun asOpaqueArgb(colors: IntArray): IntArray {
        val converted = IntArray(128)
        for (i in converted.indices) {
            val src = colors.getOrElse(i) { MidiConstants.PALETTE[i] }
            converted[i] = 0xFF000000.toInt() or (src and 0x00FFFFFF)
        }
        return converted
    }

    private fun applyGamma(color: Int): Int {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)

        val correctedR = applyGammaChannel(r, grayWeight = 1.0, gammaWeight = 1.1)
        val correctedG = applyGammaChannel(g, grayWeight = 1.0, gammaWeight = 1.1)
        val correctedB = applyGammaChannel(b, grayWeight = 1.0, gammaWeight = 1.1)

        return Color.rgb(correctedR, correctedG, correctedB)
    }

    private fun applyGammaChannel(channel: Int, grayWeight: Double, gammaWeight: Double): Int {
        val normalized = channel / 255.0
        val gammaChannel = (normalized.pow(GAMMA) * 255).toInt()
        val result = (BASE_GRAY * grayWeight * (1.0 - normalized) + gammaChannel * gammaWeight).toInt()
        return result.coerceIn(0, 255)
    }
}