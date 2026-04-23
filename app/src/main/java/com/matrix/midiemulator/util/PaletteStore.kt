package com.matrix.midiemulator.util

import android.content.Context
import android.graphics.Color
import java.io.File

data class PaletteSlot(
    val slotId: Int,
    val name: String,
    val colors: IntArray
)

object PaletteStore {
    private const val SLOT_FILE_PREFIX = "palette_slot_"
    private const val SLOT_FILE_SUFFIX = ".txt"
    private val slotLineRegex = Regex("""^(\d+),\s*(\d+)\s+(\d+)\s+(\d+);?$""")

    fun slotFile(context: Context, slotId: Int): File {
        return File(context.filesDir, "$SLOT_FILE_PREFIX${slotId + 1}$SLOT_FILE_SUFFIX")
    }

    fun parsePaletteText(text: String, slotId: Int, fallbackName: String = "Imported Palette"): PaletteSlot {
        val colors = IntArray(128) { LedPalette.OFF_COLOR }
        var name = fallbackName

        for (line in text.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            if (trimmed.startsWith("# NAME:", ignoreCase = true)) {
                name = trimmed.substringAfter(":").trim().ifEmpty { fallbackName }
                continue
            }

            val match = slotLineRegex.find(trimmed) ?: continue
            val index = match.groupValues[1].toInt()
            val r6 = match.groupValues[2].toInt()
            val g6 = match.groupValues[3].toInt()
            val b6 = match.groupValues[4].toInt()

            if (index in 0 until 128) {
                colors[index] = Color.rgb(
                    expand6bit(r6),
                    expand6bit(g6),
                    expand6bit(b6)
                )
            }
        }

        return PaletteSlot(slotId, name, colors)
    }

    fun serializePalette(slot: PaletteSlot): String {
        val builder = StringBuilder()
        builder.append("# NAME: ").append(slot.name).append('\n')
        for (index in 0 until 128) {
            val color = slot.colors[index]
            builder.append(index)
                .append(", ")
                .append(compress8bit(Color.red(color)))
                .append(' ')
                .append(compress8bit(Color.green(color)))
                .append(' ')
                .append(compress8bit(Color.blue(color)))
                .append(';')
                .append('\n')
        }
        return builder.toString().trimEnd()
    }

    fun saveSlot(context: Context, slot: PaletteSlot): File {
        val file = slotFile(context, slot.slotId)
        file.writeText(serializePalette(slot))
        return file
    }

    fun loadSlot(context: Context, slotId: Int): PaletteSlot? {
        val file = slotFile(context, slotId)
        if (!file.exists()) return null
        return parsePaletteText(file.readText(), slotId, "Slot ${slotId + 1}")
    }

    fun applySelectedPalette(context: Context) {
        val selectedSlot = AppPreferences.getActivePaletteSlot(context)
        val colors = if (selectedSlot == 0) {
            MidiConstants.PALETTE.copyOf()
        } else {
            loadSlot(context, selectedSlot - 1)?.colors ?: MidiConstants.PALETTE.copyOf()
        }
        PaletteRuntime.setActiveColors(colors)
    }

    fun saveAndApply(context: Context, slot: PaletteSlot) {
        saveSlot(context, slot)
        if (AppPreferences.getActivePaletteSlot(context) == slot.slotId + 1) {
            PaletteRuntime.setActiveColors(slot.colors)
        }
    }

    private fun expand6bit(value: Int): Int {
        return (value.coerceIn(0, 63) * 255f / 63f).toInt()
    }

    private fun compress8bit(value: Int): Int {
        return (value.coerceIn(0, 255) * 63f / 255f).toInt()
    }
}