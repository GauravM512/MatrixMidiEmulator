package com.matrix.midiemulator.ui.layout

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import com.matrix.midiemulator.util.LedPalette
import com.matrix.midiemulator.util.NoteMap

internal class LaunchpadX(
    density: Float,
    gap: Float
) : BasePadLayout(density, gap) {

    private var edgeSize = 0f

    override fun recomputeMetrics(width: Int, height: Int) {
        val size = viewSize(width, height)
        val cell = (size - gap * 10f) / 9f
        edgeSize = cell
        gridLeft = 0f
        gridTop = cell + gap
        cellWidth = cell
        cellHeight = cell
    }

    override fun draw(canvas: Canvas, state: PadRenderState) {
        drawEdgeButtons(canvas, state)
        drawCenterPads(canvas, state, 0f)
    }

    override fun noteAt(x: Float, y: Float): Int? {
        return edgeNoteAt(x, y) ?: centerPadNoteAt(x, y)
    }

    private fun drawEdgeButtons(canvas: Canvas, state: PadRenderState) {
        val top = gap
        val right = gridInnerRight() + gap

        for (i in 0 until NoteMap.GRID_COLS) {
            drawEdgeButton(canvas, 28 + i, padLeftForCol(i), top, state)
        }

        for (i in 0 until NoteMap.GRID_ROWS) {
            drawEdgeButton(canvas, 100 + i, right, gridInnerTop() + i * (cellHeight + gap), state)
        }

        drawEdgeButton(canvas, 27, right, top, state)
    }

    private fun drawEdgeButton(canvas: Canvas, note: Int, left: Float, top: Float, state: PadRenderState) {
        val edgeColor = edgeColorForNote(note, state)
        val litColor = if (edgeColor == LedPalette.OFF_COLOR) edgeColor else applyEffectBrightness(edgeColor, state.brightnessScale)
        scratchRect.set(left, top, left + edgeSize, top + edgeSize)

        if (edgeColor != LedPalette.OFF_COLOR) {
            val glowRadius = edgeSize * 0.88f
            paint.shader = RadialGradient(
                scratchRect.centerX(),
                scratchRect.centerY(),
                glowRadius,
                withAlpha(litColor, scaledAlpha(72, state.brightnessScale)),
                withAlpha(litColor, 0),
                Shader.TileMode.CLAMP
            )
            paint.style = Paint.Style.FILL
            canvas.drawCircle(scratchRect.centerX(), scratchRect.centerY(), glowRadius, paint)
            paint.shader = null
        }

        paint.style = Paint.Style.FILL
        paint.color = 0xFF111111.toInt()
        canvas.drawRect(scratchRect, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f * density
        paint.color = if (edgeColor == LedPalette.OFF_COLOR) applyPadBrightness(0xFF777777.toInt(), state.brightnessScale) else withAlpha(litColor, 245)
        canvas.drawRect(scratchRect, paint)

        if (note == 27) {
            drawCornerButton(canvas, edgeColor, litColor, state.brightnessScale)
        }

        if (state.padPressed[note]) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f * density
            paint.color = 0xAAFFFFFF.toInt()
            canvas.drawRect(scratchRect, paint)
        }
    }

    private fun drawCornerButton(canvas: Canvas, edgeColor: Int, litColor: Int, brightnessScale: Float) {
        val innerRadius = edgeSize * 0.28f
        paint.style = if (edgeColor == LedPalette.OFF_COLOR) Paint.Style.STROKE else Paint.Style.FILL
        paint.strokeWidth = 1.5f * density
        paint.color = if (edgeColor == LedPalette.OFF_COLOR) applyPadBrightness(0xFF5F6771.toInt(), brightnessScale) else withAlpha(litColor, 245)
        canvas.drawCircle(scratchRect.centerX(), scratchRect.centerY(), innerRadius, paint)

        if (edgeColor == LedPalette.OFF_COLOR) {
            paint.style = Paint.Style.FILL
            paint.color = applyPadBrightness(0xFF5F6771.toInt(), brightnessScale)
            canvas.drawCircle(scratchRect.centerX(), scratchRect.centerY(), innerRadius * 0.72f, paint)
        }
    }

    private fun edgeNoteAt(x: Float, y: Float): Int? {
        val top = gap
        val right = gridInnerRight() + gap

        fun hitsSquare(left: Float, top: Float): Boolean {
            return x in left..(left + edgeSize) && y in top..(top + edgeSize)
        }

        for (i in 0 until NoteMap.GRID_COLS) {
            if (hitsSquare(padLeftForCol(i), top)) return 28 + i
        }

        for (i in 0 until NoteMap.GRID_ROWS) {
            if (hitsSquare(right, gridInnerTop() + i * (cellHeight + gap))) return 100 + i
        }

        return null
    }
}
