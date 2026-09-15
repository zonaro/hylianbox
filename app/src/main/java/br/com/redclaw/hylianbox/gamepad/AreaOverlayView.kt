package br.com.redclaw.hylianbox.gamepad

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import br.com.redclaw.hylianbox.input.InputMapper
import com.swordfish.libretrodroid.GLRetroView
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

/**
 * Invisible mapped controls for Pro / Touch Areas mode.
 *
 * It renders no borders, labels or button shapes. The only idle chrome is the set of colored edge
 * glows measured from the reference. Active gestures intensify their glow; the relative analog and
 * Button Stick gestures add a small movable feedback dot.
 */
class AreaOverlayView(context: Context) : View(context) {
    var retroView: GLRetroView? = null
    var analogSensitivity: Float = 1f
    var stickSensitivity: Float = 0.5f
    var stickEnabled: Boolean = true
    var autoZEnabled: Boolean = true
    var onControlDown: ((Int) -> Unit)? = null
    var onAnalogDoubleTap: (() -> Unit)? = null

    // Kept in parity with the Standard buttons' existing long-press Ocarina shortcut.
    var onOcarinaHold: (() -> Unit)? = null
    var isOcarinaEquipped: ((Int) -> Boolean)? = null

    // Equipped-item icons for Pro mode: icon only, no button/background/border/label.
    private var equippedIcons: Map<AreaControl, Bitmap?> = emptyMap()
    private var equippedBadges: Map<AreaControl, Int?> = emptyMap()

    private data class StickState(
            val zone: AreaZone,
            val downX: Float,
            val downY: Float,
            var dragging: Boolean = false,
            var lockedOut: Boolean = false,
            var offsetX: Float = 0f,
            var offsetY: Float = 0f
    )

    private data class TouchState(val zone: AreaZone, val downX: Float, val downY: Float)

    private val stickStates = mutableMapOf<Int, StickState>()
    private val touchStates = mutableMapOf<Int, TouchState>()

    private var analogPointerId = -1
    private var analogCenterX = 0f
    private var analogCenterY = 0f
    private var analogOffsetX = 0f
    private var analogOffsetY = 0f
    private var analogMoved = false

    private var dpadPointerId = -1
    private var dpadStartX = 0f
    private var dpadStartY = 0f

    private var lastAnalogTapAt = 0L
    private var lastAnalogTapX = 0f
    private var lastAnalogTapY = 0f

    private val handler = Handler(Looper.getMainLooper())
    private val ocarinaHolds = mutableMapOf<Int, Runnable>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { alpha = 140 }
    private val badgeBgPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xCC000000.toInt()
                style = Paint.Style.FILL
            }
    private val badgeBorderPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
            }
    private val badgeTextPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
                color = Color.WHITE
            }
    // Overlay uses physical pixels and the system density so the global UI
    // scale (UiScaleManager) never affects the emulator controls.
    private val density = Resources.getSystem().displayMetrics.density
    private val dragThresholdPx = AreaControlLayout.DRAG_THRESHOLD_DP * density
    private val dpadThresholdPx = AreaControlLayout.DPAD_SWIPE_THRESHOLD_DP * density

    /** Shows equipped-item icons centered in the mapped zones (icon only, no button chrome). */
    fun setEquippedIcons(icons: Map<AreaControl, Bitmap?>, badges: Map<AreaControl, Int?>) {
        equippedIcons = icons
        equippedBadges = badges
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawEdgeGlows(canvas)
        drawEquippedIcons(canvas)
        drawGestureFeedback(canvas)
    }

    private fun drawEquippedIcons(canvas: Canvas) {
        if (equippedIcons.isEmpty() || width <= 0 || height <= 0) return
        badgeBorderPaint.strokeWidth = 1.5f * density
        equippedIcons.forEach { (control, bitmap) ->
            if (bitmap == null || bitmap.isRecycled) return@forEach
            val zone =
                    try {
                        AreaControlLayout.zone(control)
                    } catch (e: NoSuchElementException) {
                        return@forEach
                    }
            val left = zone.rect.left * width
            val top = zone.rect.top * height
            val right = zone.rect.right * width
            val bottom = zone.rect.bottom * height
            val zoneW = right - left
            val zoneH = bottom - top
            if (zoneW <= 0f || zoneH <= 0f) return@forEach
            val cx = (left + right) / 2f
            val cy = (top + bottom) / 2f
            val half = min(zoneW, zoneH) * 0.55f / 2f
            if (half <= 0f) return@forEach
            canvas.drawBitmap(
                    bitmap,
                    null,
                    RectF(cx - half, cy - half, cx + half, cy + half),
                    iconPaint
            )
            drawEquippedBadge(canvas, control, cx, cy, half)
        }
    }

    private fun drawEquippedBadge(
            canvas: Canvas,
            control: AreaControl,
            cx: Float,
            cy: Float,
            radius: Float
    ) {
        val count = equippedBadges[control] ?: return
        val text = count.toString()
        badgeTextPaint.textSize = radius * 0.38f
        val textWidth = badgeTextPaint.measureText(text)
        val padH = radius * 0.16f
        val padV = radius * 0.10f
        val halfW = textWidth / 2f + padH
        val halfH = badgeTextPaint.textSize / 2f + padV
        val badgeCx = cx + radius * 0.62f
        val badgeCy = cy + radius * 0.62f
        val rect = RectF(badgeCx - halfW, badgeCy - halfH, badgeCx + halfW, badgeCy + halfH)
        val corner = halfH
        canvas.drawRoundRect(rect, corner, corner, badgeBgPaint)
        canvas.drawRoundRect(rect, corner, corner, badgeBorderPaint)
        canvas.drawText(
                text,
                badgeCx,
                badgeCy - (badgeTextPaint.ascent() + badgeTextPaint.descent()) / 2,
                badgeTextPaint
        )
    }

    private fun drawEdgeGlows(canvas: Canvas) {
        val depth = min(width, height) * AreaControlLayout.GLOW_DEPTH_FRACTION
        AreaControlLayout.glows.forEach { glow ->
            val pressed = isControlPressed(glow.control)
            val alpha =
                    if (pressed) AreaControlLayout.GLOW_PRESSED_ALPHA
                    else AreaControlLayout.GLOW_IDLE_ALPHA
            val solid = withAlpha(glow.color, alpha)
            val clear = withAlpha(glow.color, 0f)
            when (glow.edge) {
                GlowEdge.TOP -> {
                    val left = glow.start * width
                    val right = glow.end * width
                    paint.shader =
                            LinearGradient(0f, 0f, 0f, depth, solid, clear, Shader.TileMode.CLAMP)
                    canvas.drawRect(left, 0f, right, depth, paint)
                }
                GlowEdge.RIGHT -> {
                    val top = glow.start * height
                    val bottom = glow.end * height
                    paint.shader =
                            LinearGradient(
                                    width.toFloat(),
                                    0f,
                                    width - depth,
                                    0f,
                                    solid,
                                    clear,
                                    Shader.TileMode.CLAMP
                            )
                    canvas.drawRect(width - depth, top, width.toFloat(), bottom, paint)
                }
                GlowEdge.BOTTOM -> {
                    val left = glow.start * width
                    val right = glow.end * width
                    paint.shader =
                            LinearGradient(
                                    0f,
                                    height.toFloat(),
                                    0f,
                                    height - depth,
                                    solid,
                                    clear,
                                    Shader.TileMode.CLAMP
                            )
                    canvas.drawRect(left, height - depth, right, height.toFloat(), paint)
                }
            }
        }
        paint.shader = null
    }

    private fun drawGestureFeedback(canvas: Canvas) {
        val reach = min(width, height) * AreaControlLayout.ANALOG_REACH_FRACTION
        val dotRadius = min(width, height) * AreaControlLayout.FEEDBACK_RADIUS_FRACTION

        if (analogPointerId != -1) {
            paint.color = withAlpha(AreaControlLayout.COLOR_BLUE, 0.22f)
            canvas.drawCircle(analogCenterX, analogCenterY, reach, paint)
            paint.color = withAlpha(AreaControlLayout.COLOR_BLUE, AreaControlLayout.FEEDBACK_ALPHA)
            canvas.drawCircle(
                    analogCenterX + analogOffsetX,
                    analogCenterY + analogOffsetY,
                    dotRadius,
                    paint
            )
        }

        stickStates.values.filter { it.dragging }.forEach { state ->
            paint.color = withAlpha(colorFor(state.zone.control), AreaControlLayout.FEEDBACK_ALPHA)
            canvas.drawCircle(
                    state.downX + state.offsetX,
                    state.downY + state.offsetY,
                    dotRadius,
                    paint
            )
        }

        // C-Up has no outer-edge glow in the supplied reference; give it press feedback without
        // inventing a persistent boundary or label.
        touchStates.values.filter { it.zone.control == AreaControl.C_UP }.forEach { state ->
            paint.color = withAlpha(AreaControlLayout.COLOR_YELLOW, 0.36f)
            canvas.drawCircle(state.downX, state.downY, dotRadius * 1.35f, paint)
        }
    }

    private fun isControlPressed(control: AreaControl): Boolean =
            stickStates.values.any { it.zone.control == control } ||
                    touchStates.values.any { it.zone.control == control } ||
                    (control == AreaControl.ANALOG && analogPointerId != -1) ||
                    (control == AreaControl.DPAD_SWIPE && dpadPointerId != -1)

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (width <= 0 || height <= 0) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                handleDown(event.getPointerId(index), event.getX(index), event.getY(index))
                if (event.pointerCount > 1) lockOutButtonSticks()
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount > 1) lockOutButtonSticks()
                for (index in 0 until event.pointerCount) {
                    handleMove(event.getPointerId(index), event.getX(index), event.getY(index))
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val index = event.actionIndex
                handleUp(event.getPointerId(index), event.getX(index), event.getY(index))
            }
            MotionEvent.ACTION_UP -> {
                handleUp(event.getPointerId(0), event.getX(0), event.getY(0))
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> cancelActiveTouches(keepAutoZ = true)
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun handleDown(pointerId: Int, x: Float, y: Float) {
        val zone = AreaControlLayout.hitTest(x / width, y / height) ?: return
        when (zone.zoneType) {
            ZoneType.BUTTON_STICK -> {
                val keyCode = zone.keyCode ?: return
                stickStates[pointerId] = StickState(zone, x, y)
                sendKey(KeyEvent.ACTION_DOWN, keyCode)
                onControlDown?.invoke(keyCode)
                scheduleOcarinaHold(pointerId, keyCode)
            }
            ZoneType.TOUCH -> {
                val keyCode = zone.keyCode ?: return
                touchStates[pointerId] = TouchState(zone, x, y)
                sendKey(KeyEvent.ACTION_DOWN, keyCode)
                onControlDown?.invoke(keyCode)
                scheduleOcarinaHold(pointerId, keyCode)
            }
            ZoneType.DPAD_SWIPE ->
                    if (dpadPointerId == -1) {
                        dpadPointerId = pointerId
                        dpadStartX = x
                        dpadStartY = y
                    }
            ZoneType.ANALOG ->
                    if (analogPointerId == -1) {
                        analogPointerId = pointerId
                        analogCenterX = x
                        analogCenterY = y
                        analogOffsetX = 0f
                        analogOffsetY = 0f
                        analogMoved = false
                    }
        }
        invalidate()
    }

    private fun handleMove(pointerId: Int, x: Float, y: Float) {
        stickStates[pointerId]?.let { state ->
            if (stickEnabled && !state.lockedOut) {
                val dx = x - state.downX
                val dy = y - state.downY
                val distance = hypot(dx, dy)
                if (!state.dragging && distance > dragThresholdPx) {
                    state.dragging = true
                    cancelOcarinaHold(pointerId)
                }
                if (state.dragging) {
                    val reach = min(width, height) * AreaControlLayout.ANALOG_REACH_FRACTION
                    val clamped = min(distance, reach)
                    val unitX = if (distance > 0f) dx / distance else 0f
                    val unitY = if (distance > 0f) dy / distance else 0f
                    state.offsetX = unitX * clamped
                    state.offsetY = unitY * clamped
                    val magnitude = if (reach > 0f) clamped / reach * stickSensitivity else 0f
                    retroView?.sendMotionEvent(
                            GLRetroView.MOTION_SOURCE_ANALOG_LEFT,
                            unitX * magnitude,
                            -unitY * magnitude
                    )
                }
            }
            invalidate()
            return
        }

        if (pointerId == dpadPointerId) {
            val dx = x - dpadStartX
            val dy = y - dpadStartY
            if (hypot(dx, dy) >= dpadThresholdPx) {
                if (abs(dx) >= abs(dy)) {
                    retroView?.sendMotionEvent(
                            GLRetroView.MOTION_SOURCE_DPAD,
                            if (dx > 0f) 1f else -1f,
                            0f
                    )
                } else {
                    retroView?.sendMotionEvent(
                            GLRetroView.MOTION_SOURCE_DPAD,
                            0f,
                            if (dy > 0f) 1f else -1f
                    )
                }
            } else {
                retroView?.sendMotionEvent(GLRetroView.MOTION_SOURCE_DPAD, 0f, 0f)
            }
            invalidate()
            return
        }

        if (pointerId == analogPointerId) {
            val dx = x - analogCenterX
            val dy = y - analogCenterY
            val distance = hypot(dx, dy)
            if (distance > dragThresholdPx) analogMoved = true
            val reach = min(width, height) * AreaControlLayout.ANALOG_REACH_FRACTION
            val clamped = min(distance, reach)
            val unitX = if (distance > 0f) dx / distance else 0f
            val unitY = if (distance > 0f) dy / distance else 0f
            analogOffsetX = unitX * clamped
            analogOffsetY = unitY * clamped
            val magnitude = if (reach > 0f) clamped / reach * analogSensitivity else 0f
            retroView?.sendMotionEvent(
                    GLRetroView.MOTION_SOURCE_ANALOG_LEFT,
                    unitX * magnitude,
                    unitY * magnitude
            )
            invalidate()
        }
    }

    private fun handleUp(pointerId: Int, x: Float, y: Float) {
        stickStates.remove(pointerId)?.let { state ->
            cancelOcarinaHold(pointerId)
            if (state.dragging) releaseAnalog()
            sendKey(KeyEvent.ACTION_UP, state.zone.keyCode ?: return@let)
            invalidate()
            return
        }
        touchStates.remove(pointerId)?.let { state ->
            cancelOcarinaHold(pointerId)
            sendKey(KeyEvent.ACTION_UP, state.zone.keyCode ?: return@let)
            invalidate()
            return
        }
        if (pointerId == dpadPointerId) {
            releaseDpad()
            dpadPointerId = -1
            invalidate()
            return
        }
        if (pointerId == analogPointerId) {
            releaseAnalog()
            if (!analogMoved) registerAnalogTap(x, y)
            analogPointerId = -1
            analogOffsetX = 0f
            analogOffsetY = 0f
            invalidate()
        }
    }

    private fun registerAnalogTap(x: Float, y: Float) {
        val now = android.os.SystemClock.uptimeMillis()
        val closeEnough = hypot(x - lastAnalogTapX, y - lastAnalogTapY) <= 48f * density
        if (autoZEnabled && now - lastAnalogTapAt in 1..300 && closeEnough) {
            onAnalogDoubleTap?.invoke()
            lastAnalogTapAt = 0L
        } else {
            lastAnalogTapAt = now
            lastAnalogTapX = x
            lastAnalogTapY = y
        }
    }

    private fun lockOutButtonSticks() {
        var released = false
        stickStates.values.forEach { state ->
            state.lockedOut = true
            if (state.dragging) {
                state.dragging = false
                state.offsetX = 0f
                state.offsetY = 0f
                released = true
            }
        }
        if (released) releaseAnalog()
    }

    private fun sendKey(action: Int, keyCode: Int) {
        retroView?.sendKeyEvent(action, InputMapper.mapKeyCode(keyCode))
    }

    private fun releaseAnalog() {
        retroView?.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_LEFT, 0f, 0f)
    }

    private fun releaseDpad() {
        retroView?.sendMotionEvent(GLRetroView.MOTION_SOURCE_DPAD, 0f, 0f)
    }

    private fun scheduleOcarinaHold(pointerId: Int, keyCode: Int) {
        if (onOcarinaHold == null || isOcarinaEquipped?.invoke(keyCode) != true) return
        cancelOcarinaHold(pointerId)
        val runnable = Runnable {
            val held =
                    stickStates[pointerId]?.let { !it.dragging && !it.lockedOut } == true ||
                            touchStates.containsKey(pointerId)
            if (held) onOcarinaHold?.invoke()
        }
        ocarinaHolds[pointerId] = runnable
        handler.postDelayed(runnable, 600L)
    }

    private fun cancelOcarinaHold(pointerId: Int) {
        ocarinaHolds.remove(pointerId)?.let(handler::removeCallbacks)
    }

    /** Releases every transient input before the overlay is detached. */
    fun cancelAll() = cancelActiveTouches(keepAutoZ = true)

    private fun cancelActiveTouches(keepAutoZ: Boolean) {
        stickStates.values.forEach { state ->
            state.zone.keyCode?.let { sendKey(KeyEvent.ACTION_UP, it) }
        }
        touchStates.values.forEach { state ->
            state.zone.keyCode?.let { sendKey(KeyEvent.ACTION_UP, it) }
        }
        if (stickStates.values.any { it.dragging } || analogPointerId != -1) releaseAnalog()
        if (dpadPointerId != -1) releaseDpad()
        ocarinaHolds.values.forEach(handler::removeCallbacks)
        ocarinaHolds.clear()
        stickStates.clear()
        touchStates.clear()
        analogPointerId = -1
        dpadPointerId = -1
        if (!keepAutoZ) lastAnalogTapAt = 0L
        invalidate()
    }

    override fun onDetachedFromWindow() {
        cancelActiveTouches(keepAutoZ = false)
        super.onDetachedFromWindow()
    }

    private fun colorFor(control: AreaControl): Int =
            when (control) {
                AreaControl.A -> AreaControlLayout.COLOR_BLUE
                AreaControl.B -> AreaControlLayout.COLOR_GREEN
                AreaControl.C_LEFT, AreaControl.C_UP, AreaControl.C_DOWN, AreaControl.C_RIGHT ->
                        AreaControlLayout.COLOR_YELLOW
                AreaControl.START -> AreaControlLayout.COLOR_RED
                AreaControl.Z, AreaControl.R -> AreaControlLayout.COLOR_PURPLE
                else -> AreaControlLayout.COLOR_GRAY
            }

    private fun withAlpha(color: Int, alpha: Float): Int =
            (color and 0x00FFFFFF) or ((alpha.coerceIn(0f, 1f) * 255).toInt() shl 24)
}
