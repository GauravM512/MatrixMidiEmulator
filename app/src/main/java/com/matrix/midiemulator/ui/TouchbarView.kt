package com.matrix.midiemulator.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.ViewConfiguration
import android.view.MotionEvent
import android.view.View
import com.matrix.midiemulator.util.LedPalette
import com.matrix.midiemulator.util.NoteMap
import kotlin.math.min

/**
 * A horizontal strip of 16 touchbar segments, matching the Mystrix touchbar.
 * Each segment sends MIDI notes 100-115.
 */
class TouchbarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val VISIBLE_SEGMENTS = 8
    }

    private val segmentColors = IntArray(NoteMap.TOUCHBAR_COUNT) { LedPalette.OFF_COLOR }
    private val segmentPressed = BooleanArray(NoteMap.TOUCHBAR_COUNT) { false }
    private val segmentPressure = FloatArray(NoteMap.TOUCHBAR_COUNT) { 0f }
    private val activePointerSegments = mutableMapOf<Int, Int>()
    private val pointerLastY = mutableMapOf<Int, Float>()

    private val segmentRect = RectF()
    private val barRect = RectF()
    private val leftNavRect = RectF()
    private val rightNavRect = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density
    private val gap = 4f * density
    private val radius = 5f * density
    private val outerRadius = 12f * density
    private val inset = 4f * density
    private val navSize = 28f * density
    private val navGap = 8f * density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

    private var selectedPage = 8
    private var visibleStartIndex = 0

    var onTouchListener: TouchbarEventListener? = null

    interface TouchbarEventListener {
        fun onSegmentPress(index: Int, velocity: Int)
        fun onSegmentRelease(index: Int)
        fun onSegmentAftertouch(index: Int, pressure: Int)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.drawColor(0xFF1A1A1A.toInt())

        val layout = computeLayoutMetrics()
        barRect.set(
            layout.startX - inset,
            layout.topY - inset,
            layout.startX + layout.totalWidth + inset,
            layout.topY + layout.segmentSize + inset
        )
        paint.style = Paint.Style.FILL
        paint.color = 0xFF171717.toInt()
        canvas.drawRoundRect(barRect, outerRadius, outerRadius, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f * density
        paint.color = 0xFF2A2A2A.toInt()
        canvas.drawRoundRect(barRect, outerRadius, outerRadius, paint)

        updateNavigationRects(layout)

        drawNavButton(canvas, leftNavRect, '<', visibleStartIndex > 0)
        drawNavButton(canvas, rightNavRect, '>', visibleStartIndex < NoteMap.TOUCHBAR_COUNT - VISIBLE_SEGMENTS)

        for (slot in 0 until VISIBLE_SEGMENTS) {
            val segmentIndex = visibleStartIndex + slot
            val left = layout.startX + slot * (layout.segmentSize + gap)
            val top = layout.topY
            val right = left + layout.segmentSize
            val bottom = top + layout.segmentSize

            segmentRect.set(left, top, right, bottom)

            paint.color = segmentColors[segmentIndex]
            paint.style = Paint.Style.FILL
            canvas.drawRoundRect(segmentRect, radius, radius, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f * density
            val isSelected = selectedPage == segmentIndex + 1
            paint.color = when {
                segmentPressed[segmentIndex] -> 0x66FFFFFF.toInt()
                isSelected -> 0xFF4AB6D8.toInt()
                else -> 0xFF313131.toInt()
            }
            canvas.drawRoundRect(segmentRect, radius, radius, paint)

            if (segmentPressed[segmentIndex] || isSelected) {
                paint.color = 0x33FFFFFF.toInt()
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2f * density
                canvas.drawRoundRect(segmentRect, radius, radius, paint)
                paint.style = Paint.Style.FILL
            }

            paint.textSize = 14f * density
            paint.textAlign = Paint.Align.CENTER
            paint.color = if (isSelected) 0xFF9EE0FF.toInt() else 0xFF8D95A1.toInt()
            val textY = segmentRect.centerY() - (paint.descent() + paint.ascent()) / 2f
            canvas.drawText((segmentIndex + 1).toString(), segmentRect.centerX(), textY, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = event.actionIndex
                val pointerId = event.getPointerId(idx)
                pointerLastY[pointerId] = event.getY(idx)
                handleTouchDown(
                    pointerId,
                    event.getX(idx),
                    event.getY(idx),
                    event.getPressure(idx)
                )
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val pointerId = event.getPointerId(i)
                    val y = event.getY(i)
                    val lastY = pointerLastY[pointerId]
                    if (lastY != null) {
                        val dy = y - lastY
                        if (kotlin.math.abs(dy) > touchSlop) {
                            shiftVisibleWindow(if (dy > 0f) -1 else 1)
                            pointerLastY[pointerId] = y
                        }
                    } else {
                        pointerLastY[pointerId] = y
                    }
                    handleTouchMove(
                        pointerId,
                        event.getX(i),
                        y,
                        event.getPressure(i)
                    )
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val idx = event.actionIndex
                val pointerId = event.getPointerId(idx)
                pointerLastY.remove(pointerId)
                handleTouchUp(pointerId)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                pointerLastY.clear()
                clearAllTouches()
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun getSegmentForPosition(x: Float, y: Float): Int? {
        val layout = computeLayoutMetrics()
        for (slot in 0 until VISIBLE_SEGMENTS) {
            val left = layout.startX + slot * (layout.segmentSize + gap)
            val top = layout.topY
            val right = left + layout.segmentSize
            val bottom = top + layout.segmentSize
            if (x in left..right && y in top..bottom) return visibleStartIndex + slot
        }
        return null
    }

    private fun handleTouchDown(pointerId: Int, x: Float, y: Float, pressure: Float) {
        val layout = computeLayoutMetrics()
        updateNavigationRects(layout)
        if (leftNavRect.contains(x, y)) {
            shiftVisibleWindow(-1)
            return
        }
        if (rightNavRect.contains(x, y)) {
            shiftVisibleWindow(1)
            return
        }

        val segmentIndex = getSegmentForPosition(x, y) ?: return
        activePointerSegments[pointerId] = segmentIndex
        pressSegment(segmentIndex, pressure)
        invalidate()
    }

    private fun handleTouchMove(pointerId: Int, x: Float, y: Float, pressure: Float) {
        val previousSegment = activePointerSegments[pointerId]
        val currentSegment = getSegmentForPosition(x, y)

        if (previousSegment == currentSegment) {
            if (currentSegment != null && segmentPressed[currentSegment]) {
                val currentPressure = pressureToVelocity(pressure)
                val oldPressure = pressureToVelocity(segmentPressure[currentSegment])
                if (kotlin.math.abs(currentPressure - oldPressure) > 2) {
                    segmentPressure[currentSegment] = pressure
                    onTouchListener?.onSegmentAftertouch(currentSegment, currentPressure)
                    invalidate()
                }
            }
            return
        }

        if (previousSegment != null) {
            releaseSegment(previousSegment)
        }

        if (currentSegment != null) {
            activePointerSegments[pointerId] = currentSegment
            pressSegment(currentSegment, pressure)
        } else {
            activePointerSegments.remove(pointerId)
        }

        invalidate()
    }

    private fun handleTouchUp(pointerId: Int) {
        val segmentIndex = activePointerSegments.remove(pointerId) ?: return
        releaseSegment(segmentIndex)
        invalidate()
    }

    private fun clearAllTouches() {
        val pressedSegments = activePointerSegments.values.toSet()
        activePointerSegments.clear()
        for (segmentIndex in pressedSegments) {
            segmentPressed[segmentIndex] = false
            segmentPressure[segmentIndex] = 0f
            onTouchListener?.onSegmentRelease(segmentIndex)
        }
    }

    private fun pressSegment(segmentIndex: Int, pressure: Float) {
        if (!segmentPressed[segmentIndex]) {
            segmentPressed[segmentIndex] = true
            segmentPressure[segmentIndex] = pressure
            selectedPage = segmentIndex + 1
            val velocity = pressureToVelocity(pressure)
            onTouchListener?.onSegmentPress(segmentIndex, velocity)
        }
    }

    private fun releaseSegment(segmentIndex: Int) {
        if (segmentPressed[segmentIndex]) {
            segmentPressed[segmentIndex] = false
            segmentPressure[segmentIndex] = 0f
            onTouchListener?.onSegmentRelease(segmentIndex)
        }
    }

    private data class LayoutMetrics(
        val startX: Float,
        val topY: Float,
        val segmentSize: Float,
        val totalWidth: Float
    )

    private fun computeLayoutMetrics(): LayoutMetrics {
        val availableWidth = width - inset * 2 - navSize * 2 - navGap * 2 - gap * (VISIBLE_SEGMENTS - 1)
        val availableHeight = height - inset * 2
        val segmentSize = min(availableHeight, availableWidth / VISIBLE_SEGMENTS)
        val totalWidth = segmentSize * VISIBLE_SEGMENTS + gap * (VISIBLE_SEGMENTS - 1)
        val startX = (width - totalWidth) / 2f
        val topY = (height - segmentSize) / 2f
        return LayoutMetrics(startX, topY, segmentSize, totalWidth)
    }

    private fun updateNavigationRects(layout: LayoutMetrics) {
        leftNavRect.set(
            layout.startX - navGap - navSize,
            layout.topY + (layout.segmentSize - navSize) / 2f,
            layout.startX - navGap,
            layout.topY + (layout.segmentSize + navSize) / 2f
        )
        rightNavRect.set(
            layout.startX + layout.totalWidth + navGap,
            layout.topY + (layout.segmentSize - navSize) / 2f,
            layout.startX + layout.totalWidth + navGap + navSize,
            layout.topY + (layout.segmentSize + navSize) / 2f
        )
    }

    private fun shiftVisibleWindow(delta: Int) {
        val maxStart = NoteMap.TOUCHBAR_COUNT - VISIBLE_SEGMENTS
        visibleStartIndex = (visibleStartIndex + delta).coerceIn(0, maxStart)
        invalidate()
    }

    private fun drawNavButton(canvas: Canvas, rect: RectF, symbol: Char, enabled: Boolean) {
        paint.style = Paint.Style.FILL
        paint.color = if (enabled) 0xFF2B2E33.toInt() else 0xFF1F2125.toInt()
        canvas.drawOval(rect, paint)

        paint.style = Paint.Style.FILL
        paint.color = if (enabled) 0xFF8E8E8E.toInt() else 0xFF5F6368.toInt()
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 18f * density
        val textY = rect.centerY() - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(symbol.toString(), rect.centerX(), textY, paint)
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

    fun setSelectedPage(pageNumber: Int) {
        selectedPage = pageNumber.coerceIn(1, NoteMap.TOUCHBAR_COUNT)
        val selectedIndex = selectedPage - 1
        if (selectedIndex < visibleStartIndex) {
            visibleStartIndex = selectedIndex
        } else if (selectedIndex >= visibleStartIndex + VISIBLE_SEGMENTS) {
            visibleStartIndex = selectedIndex - VISIBLE_SEGMENTS + 1
        }
        invalidate()
    }
}
