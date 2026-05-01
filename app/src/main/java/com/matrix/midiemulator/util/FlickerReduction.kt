package com.matrix.midiemulator.util

class FlickerReduction(
    private val stfuDelay: Int = 1
) {
    private val stfuMap = IntArray(128) { -1 }
    var enabled: Boolean = true

    fun handleNoteOn(note: Int, velocity: Int): Boolean {
        if (!enabled) return true

        return when {
            velocity == 0 && stfuMap[note] == -1 -> {
                // First vel=0 → start countdown
                stfuMap[note] = stfuDelay
                false
            }
            velocity == 0 && stfuMap[note] >= 0 -> {
                // Repeated vel=0 → don't reset, let countdown expire
                false
            }
            velocity > 0 -> {
                // New color → cancel countdown
                stfuMap[note] = -1
                true
            }
            else -> true
        }
    }

    fun tick(): List<Int> {
        if (!enabled) return emptyList()

        val notesToTurnOff = mutableListOf<Int>()
        for (note in 0..127) {
            when {
                stfuMap[note] > 0 -> stfuMap[note]--
                stfuMap[note] == 0 -> {
                    notesToTurnOff.add(note)
                    stfuMap[note] = -1
                }
            }
        }
        return notesToTurnOff
    }

    fun clearAll() {
        stfuMap.fill(-1)
    }
}