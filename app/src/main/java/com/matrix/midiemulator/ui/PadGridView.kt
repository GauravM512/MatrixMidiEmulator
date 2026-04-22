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
import kotlin.math.min

/**
 * Custom view that renders an 8×8 grid of LED pads matching the Matrix/Mystrix layout.
 * Each pad is a rounded rectangle that displays its current LED color and responds
 * to touch events for MIDI note on/off/aftertouch.
 */
class PadGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val GRID_ROWS = NoteMap.GRID_ROWS
        private const val GRID_COLS = NoteMap.GRID_COLS
        private const val PAD_GAP = 4f // dp gap between pads
    }

    /** Current LED colors for each pad (indexed by MIDI note) */
    private val padColors = IntArray(128) { LedPalette.OFF_COLOR }

    /** Whether each pad is currently pressed */
    private val padPressed = BooleanArray(128) { false }

    /** Touch pressure for each pad (for aftertouch) */
    private val padPressure = FloatArray(128) { 0f }

    /** Active note bound to each active pointer ID */
    private val activePointerNotes = mutableMapOf<Int, Int>()

    /** Number of active pointers currently pressing each note */
    private val noteTouchCounts = IntArray(128) { 0 }

    private val padRect = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var cellWidth = 0f
    private var cellHeight = 0f
    private var gap = 0f

    /** Callback for MIDI events */
    var onPadEventListener: PadEventListener? = null

    interface PadEventListener {
        fun onPadPress(note: Int, velocity: Int)
        fun onPadRelease(note: Int)
        fun onPadAftertouch(note: Int, pressure: Int)
    }

    init {
        val density = resources.displayMetrics.density
        gap = PAD_GAP * density
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = min(
            MeasureSpec.getSize(widthMeasureSpec),
            MeasureSpec.getSize(heightMeasureSpec)
        )
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY)
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cellWidth = (w - gap * (GRID_COLS + 1)) / GRID_COLS
        cellHeight = (h - gap * (GRID_ROWS + 1)) / GRID_ROWS
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        for (row in 0 until GRID_ROWS) {
            for (col in 0 until GRID_COLS) {
                val note = NoteMap.noteForPad(col, row)
                val left = gap + col * (cellWidth + gap)
                val top = gap + (GRID_ROWS - 1 - row) * (cellHeight + gap) // Flip: row 0 = bottom

                padRect.set(left, top, left + cellWidth, top + cellHeight)

                // Draw pad background color
                paint.color = padColors[note]
                paint.style = Paint.Style.FILL
                val radius = 8f * resources.displayMetrics.density
                canvas.drawRoundRect(padRect, radius, radius, paint)

                // Draw press highlight border
                if (padPressed[note]) {
                    paint.color = 0x80FFFFFF.toInt()
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 3f * resources.displayMetrics.density
                    canvas.drawRoundRect(padRect, radius, radius, paint)
                    paint.style = Paint.Style.FILL
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = event.actionIndex
                handleTouchDown(
                    event.getPointerId(idx),
                    event.getX(idx),
                    event.getY(idx),
                    event.getPressure(idx)
                )
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    handleTouchMove(
                        event.getPointerId(i),
                        event.getX(i),
                        event.getY(i),
                        event.getPressure(i)
                    )
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val idx = event.actionIndex
                handleTouchUp(event.getPointerId(idx))
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                clearAllTouches()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun getPadForPosition(x: Float, y: Float): Int? {
        for (row in 0 until GRID_ROWS) {
            for (col in 0 until GRID_COLS) {
                val left = gap + col * (cellWidth + gap)
                val top = gap + (GRID_ROWS - 1 - row) * (cellHeight + gap)
                val right = left + cellWidth
                val bottom = top + cellHeight

                if (x in left..right && y in top..bottom) {
                    return NoteMap.noteForPad(col, row)
                }
            }
        }
        return null
    }

    private fun handleTouchDown(pointerId: Int, x: Float, y: Float, pressure: Float) {
        val note = getPadForPosition(x, y) ?: return
        activePointerNotes[pointerId] = note
        pressNote(note, pressure)
        invalidate()
    }

    private fun handleTouchMove(pointerId: Int, x: Float, y: Float, pressure: Float) {
        val previousNote = activePointerNotes[pointerId]
        val currentNote = getPadForPosition(x, y)

        if (previousNote == currentNote) {
            if (currentNote != null && padPressed[currentNote]) {
                val newPressure = pressureToVelocity(pressure)
                val oldPressure = pressureToVelocity(padPressure[currentNote])
                if (Math.abs(newPressure - oldPressure) > 2) {
                    padPressure[currentNote] = pressure
                    onPadEventListener?.onPadAftertouch(currentNote, newPressure)
                    invalidate()
                }
            }
            return
        }

        if (previousNote != null) {
            releaseNote(previousNote)
        }

        if (currentNote != null) {
            activePointerNotes[pointerId] = currentNote
            pressNote(currentNote, pressure)
        } else {
            activePointerNotes.remove(pointerId)
        }

        invalidate()
    }

    private fun handleTouchUp(pointerId: Int) {
        val note = activePointerNotes.remove(pointerId) ?: return
        releaseNote(note)
        invalidate()
    }

    private fun clearAllTouches() {
        val notesToRelease = activePointerNotes.values.toSet()
        activePointerNotes.clear()
        for (note in notesToRelease) {
            noteTouchCounts[note] = 0
            if (padPressed[note]) {
                padPressed[note] = false
                padPressure[note] = 0f
                onPadEventListener?.onPadRelease(note)
            }
        }
        invalidate()
    }

    private fun pressNote(note: Int, pressure: Float) {
        noteTouchCounts[note] += 1
        padPressure[note] = pressure
        if (!padPressed[note]) {
            padPressed[note] = true
            val velocity = pressureToVelocity(pressure)
            onPadEventListener?.onPadPress(note, velocity)
        }
    }

    private fun releaseNote(note: Int) {
        noteTouchCounts[note] = (noteTouchCounts[note] - 1).coerceAtLeast(0)
        if (noteTouchCounts[note] == 0 && padPressed[note]) {
            padPressed[note] = false
            padPressure[note] = 0f
            onPadEventListener?.onPadRelease(note)
        }
    }

    private fun pressureToVelocity(pressure: Float): Int {
        // Touch pressure typically ranges 0.0-1.0, map to MIDI 1-127
        return (pressure.coerceIn(0f, 1f) * 126 + 1).toInt().coerceIn(1, 127)
    }

    /**
     * Set the LED color for a pad by MIDI note number.
     */
    fun setPadColor(note: Int, color: Int) {
        if (note in 36..99) {
            padColors[note] = color
            invalidate()
        }
    }

    /**
     * Clear all pad colors.
     */
    fun clearAll() {
        padColors.fill(LedPalette.OFF_COLOR)
        invalidate()
    }
}
