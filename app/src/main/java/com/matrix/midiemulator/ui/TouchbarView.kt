package com.matrix.midiemulator.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.matrix.midiemulator.util.LedPalette
import com.matrix.midiemulator.util.NoteMap

/**
 * A horizontal strip of 16 touchbar segments, matching the Mystrix touchbar.
 * Each segment sends MIDI notes 100-115.
 */
class TouchbarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val segmentColors = IntArray(NoteMap.TOUCHBAR_COUNT) { LedPalette.OFF_COLOR }
    private val segmentPressed = BooleanArray(NoteMap.TOUCHBAR_COUNT) { false }

    private val segmentRect = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gap = 2f * resources.displayMetrics.density
    private val radius = 4f * resources.displayMetrics.density

    var onTouchListener: TouchbarEventListener? = null

    interface TouchbarEventListener {
        fun onSegmentPress(index: Int, velocity: Int)
        fun onSegmentRelease(index: Int)
        fun onSegmentAftertouch(index: Int, pressure: Int)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val segWidth = (width - gap * (NoteMap.TOUCHBAR_COUNT + 1)) / NoteMap.TOUCHBAR_COUNT

        for (i in 0 until NoteMap.TOUCHBAR_COUNT) {
            val left = gap + i * (segWidth + gap)
            val top = gap
            val right = left + segWidth
            val bottom = height - gap

            segmentRect.set(left, top, right, bottom)

            paint.color = segmentColors[i]
            paint.style = Paint.Style.FILL
            canvas.drawRoundRect(segmentRect, radius, radius, paint)

            if (segmentPressed[i]) {
                paint.color = 0x60FFFFFF.toInt()
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2f * resources.displayMetrics.density
                canvas.drawRoundRect(segmentRect, radius, radius, paint)
                paint.style = Paint.Style.FILL
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val segWidth = (width - gap * (NoteMap.TOUCHBAR_COUNT + 1)) / NoteMap.TOUCHBAR_COUNT

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = event.actionIndex
                val segIndex = getSegmentForX(event.getX(idx), segWidth)
                if (segIndex >= 0) {
                    segmentPressed[segIndex] = true
                    val vel = pressureToVelocity(event.getPressure(idx))
                    onTouchListener?.onSegmentPress(segIndex, vel)
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val idx = event.actionIndex
                val segIndex = getSegmentForX(event.getX(idx), segWidth)
                if (segIndex >= 0) {
                    segmentPressed[segIndex] = false
                    onTouchListener?.onSegmentRelease(segIndex)
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                segmentPressed.fill(false)
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun getSegmentForX(x: Float, segWidth: Float): Int {
        for (i in 0 until NoteMap.TOUCHBAR_COUNT) {
            val left = gap + i * (segWidth + gap)
            if (x in left..(left + segWidth)) return i
        }
        return -1
    }

    private fun pressureToVelocity(pressure: Float): Int {
        return (pressure.coerceIn(0f, 1f) * 126 + 1).toInt().coerceIn(1, 127)
    }

    fun setSegmentColor(index: Int, color: Int) {
        if (index in 0 until NoteMap.TOUCHBAR_COUNT) {
            segmentColors[index] = color
            invalidate()
        }
    }
}
