package br.com.redclaw.hylianbox.gamepad

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import br.com.redclaw.hylianbox.input.InputMapper
import com.swordfish.libretrodroid.GLRetroView
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

/**
 * Overlay de controles do modo Área Mapeada.
 *
 * Desenha apenas glows radiais (sem bordas, botões ou letras) para cada zona definida em
 * [AreaControlLayout]. O brilho intensifica ao pressionar.
 *
 * Trata todos os tipos de toque:
 * - [ZoneType.BUTTON_STICK]: toque puro + arraste analógico com bolinha de feedback
 * - [ZoneType.TOUCH]: pressiona/solta via KeyEvent
 * - [ZoneType.DPAD_SWIPE]: deslize direcional (4 direções)
 * - [ZoneType.ANALOG]: analógico flutuante (origem onde o dedo toca) + duplo-toque para auto-Z
 *
 * Suporta multitouch real: cada pointerId tem seu próprio estado, permitindo que o analógico, A e R
 * sejam pressionados simultaneamente.
 */
class AreaOverlayView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Referência ao RetroView para enviar eventos de input. */
    var retroView: GLRetroView? = null

    /** Sensibilidade do analógico N64 (0f..1f+). */
    var analogSensitivity: Float = 1f

    /** Sensibilidade do ButtonStick arraste (0f..1f). */
    var stickSensitivity: Float = 0.5f

    /** Se o Auto-Z está habilitado (duplo-toque no analógico alterna Z). */
    var autoZEnabled: Boolean = true

    /** Callback para rastrear pressionamento de C-buttons (Auto-Z). */
    var onCButtonDown: ((Int) -> Unit)? = null

    // ---- Analog stick state ----
    private var analogActive = false
    private var analogPointerId = -1
    private var analogCenterX = 0f
    private var analogCenterY = 0f
    private var analogKnobX = 0f
    private var analogKnobY = 0f
    private var analogMaxReachPx = 0f

    // ---- DPAD swipe state ----
    private var dpadActive = false
    private var dpadPointerId = -1
    private var dpadStartX = 0f
    private var dpadStartY = 0f
    private var dpadLastX = 0f
    private var dpadLastY = 0f

    // ---- ButtonStick states per pointerId ----
    private data class StickState(
            var active: Boolean = false,
            var pressed: Boolean = false,
            var dragging: Boolean = false,
            var downX: Float = 0f,
            var downY: Float = 0f,
            var thumbOffsetX: Float = 0f,
            var thumbOffsetY: Float = 0f,
            var keyCode: Int = 0,
            var stickLockedOut: Boolean = false
    )
    private val stickStates = mutableMapOf<Int, StickState>()

    // ---- Touch states per pointerId ----
    private data class TouchState(var active: Boolean = false, var keyCode: Int = 0)
    private val touchStates = mutableMapOf<Int, TouchState>()

    // ---- Auto-Z double-tap ----
    private var zHeldViaDoubleTap = false
    private var lastTapTime = 0L
    private var lastTapZone: AreaZone? = null

    private val density = resources.displayMetrics.density
    private val dragThresholdPx = AreaControlLayout.DRAG_THRESHOLD_DP * density

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val minDim = min(w, h)
        val radius = minDim * AreaControlLayout.GLOW_RADIUS_FRACTION

        // Draw glows for all zones
        for (zone in AreaControlLayout.allZones) {
            val cx = (zone.rect.left + zone.rect.right) / 2f * w
            val cy = (zone.rect.top + zone.rect.bottom) / 2f * h
            val isPressed = isZonePressed(zone)
            paint.color = if (isPressed) zone.pressedColor else zone.idleColor
            paint.alpha =
                    if (isPressed) {
                        (AreaControlLayout.GLOW_PRESSED_ALPHA * 255).toInt()
                    } else {
                        (AreaControlLayout.GLOW_IDLE_ALPHA * 255).toInt()
                    }
            canvas.drawCircle(cx, cy, radius, paint)
        }

        // Draw analog stick knob if active
        if (analogActive) {
            val cx = analogCenterX
            val cy = analogCenterY
            val knobRadius = radius * 0.4f
            paint.color = AreaControlLayout.COLOR_BLUE
            paint.alpha = (AreaControlLayout.GLOW_PRESSED_ALPHA * 255).toInt()
            canvas.drawCircle(cx + analogKnobX, cy + analogKnobY, knobRadius, paint)
        }

        // Draw ButtonStick thumb offsets
        for ((_, state) in stickStates) {
            if (state.dragging) {
                val zone =
                        AreaControlLayout.hitTest(
                                (state.downX + state.thumbOffsetX) / w,
                                (state.downY + state.thumbOffsetY) / h
                        )
                if (zone != null) {
                    val cx = (zone.rect.left + zone.rect.right) / 2f * w
                    val cy = (zone.rect.top + zone.rect.bottom) / 2f * h
                    paint.color = zone.pressedColor
                    paint.alpha = (AreaControlLayout.GLOW_PRESSED_ALPHA * 255).toInt()
                    canvas.drawCircle(
                            cx + state.thumbOffsetX,
                            cy + state.thumbOffsetY,
                            radius * 0.4f,
                            paint
                    )
                }
            }
        }
    }

    private fun isZonePressed(zone: AreaZone): Boolean {
        for ((_, state) in stickStates) {
            if (state.active && state.keyCode == zone.keyCode) return true
        }
        for ((_, state) in touchStates) {
            if (state.active && state.keyCode == zone.keyCode) return true
        }
        if (analogActive && zone.zoneType == ZoneType.ANALOG) return true
        if (dpadActive && zone.zoneType == ZoneType.DPAD_SWIPE) return true
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val pointerId = event.getPointerId(0)
                val x = event.getX(0)
                val y = event.getY(0)
                val nx = x / width
                val ny = y / height
                handleDown(pointerId, x, y, nx, ny)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = event.actionIndex
                val pointerId = event.getPointerId(idx)
                val x = event.getX(idx)
                val y = event.getY(idx)
                val nx = x / width
                val ny = y / height
                handleDown(pointerId, x, y, nx, ny)
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val pointerId = event.getPointerId(i)
                    val x = event.getX(i)
                    val y = event.getY(i)
                    val nx = x / width
                    val ny = y / height
                    handleMove(pointerId, x, y, nx, ny)
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val idx = event.actionIndex
                val pointerId = event.getPointerId(idx)
                val x = event.getX(idx)
                val y = event.getY(idx)
                handleUp(pointerId, x, y)
            }
            MotionEvent.ACTION_UP -> {
                val pointerId = event.getPointerId(0)
                val x = event.getX(0)
                val y = event.getY(0)
                handleUp(pointerId, x, y)
            }
            MotionEvent.ACTION_CANCEL -> {
                for (i in 0 until event.pointerCount) {
                    val pointerId = event.getPointerId(i)
                    handleUp(pointerId, event.getX(i), event.getY(i))
                }
            }
        }
        return true
    }

    private fun handleDown(pointerId: Int, x: Float, y: Float, nx: Float, ny: Float) {
        val zone = AreaControlLayout.hitTest(nx, ny)
        if (zone == null) return

        when (zone.zoneType) {
            ZoneType.BUTTON_STICK -> {
                val state =
                        StickState(
                                active = true,
                                pressed = true,
                                dragging = false,
                                downX = x,
                                downY = y,
                                keyCode = zone.keyCode ?: 0
                        )
                stickStates[pointerId] = state
                retroView?.sendKeyEvent(
                        KeyEvent.ACTION_DOWN,
                        InputMapper.mapKeyCode(zone.keyCode!!)
                )
                invalidate()
            }
            ZoneType.TOUCH -> {
                val state = TouchState(active = true, keyCode = zone.keyCode ?: 0)
                touchStates[pointerId] = state
                retroView?.sendKeyEvent(
                        KeyEvent.ACTION_DOWN,
                        InputMapper.mapKeyCode(zone.keyCode!!)
                )
                invalidate()
            }
            ZoneType.DPAD_SWIPE -> {
                dpadActive = true
                dpadPointerId = pointerId
                dpadStartX = x
                dpadStartY = y
                dpadLastX = x
                dpadLastY = y
                invalidate()
            }
            ZoneType.ANALOG -> {
                analogActive = true
                analogPointerId = pointerId
                analogCenterX = x
                analogCenterY = y
                analogKnobX = 0f
                analogKnobY = 0f
                analogMaxReachPx = min(width, height) * 0.075f
                invalidate()
            }
        }
    }

    private fun handleMove(pointerId: Int, x: Float, y: Float, nx: Float, ny: Float) {
        // ButtonStick drag
        val stickState = stickStates[pointerId]
        if (stickState != null && stickState.active) {
            if (!stickState.stickLockedOut) {
                val dx = x - stickState.downX
                val dy = y - stickState.downY
                val dist = hypot(dx, dy)
                if (!stickState.dragging && dist > dragThresholdPx) stickState.dragging = true
                if (stickState.dragging) {
                    val maxRadius =
                            min(
                                    (stickState.downX -
                                                    AreaControlLayout.buttonStickZones[0].rect
                                                            .left * width)
                                            .coerceAtLeast(0f),
                                    (AreaControlLayout.buttonStickZones[0].rect.right * width -
                                            stickState.downX)
                            )
                    val clampedDist = min(dist, maxRadius)
                    val magnitude = clampedDist / maxRadius * stickSensitivity
                    val nxDir = if (dist > 0) dx / dist else 0f
                    val nyDir = if (dist > 0) dy / dist else 0f
                    stickState.thumbOffsetX = nxDir * clampedDist
                    stickState.thumbOffsetY = nyDir * clampedDist
                    retroView?.sendMotionEvent(
                            GLRetroView.MOTION_SOURCE_ANALOG_LEFT,
                            nxDir * magnitude,
                            -nyDir * magnitude
                    )
                }
            }
            invalidate()
            return
        }

        // DPAD swipe
        if (dpadActive && pointerId == dpadPointerId) {
            val dx = x - dpadStartX
            val dy = y - dpadStartY
            val dist = hypot(dx, dy)
            val threshold = 20f
            if (dist > threshold) {
                val nxDir = dx / dist
                val nyDir = dy / dist
                // Determine dominant direction
                if (abs(nxDir) > abs(nyDir)) {
                    retroView?.sendMotionEvent(
                            GLRetroView.MOTION_SOURCE_DPAD,
                            if (nxDir > 0) 1f else -1f,
                            0f
                    )
                } else {
                    retroView?.sendMotionEvent(
                            GLRetroView.MOTION_SOURCE_DPAD,
                            0f,
                            if (nyDir > 0) 1f else -1f
                    )
                }
            }
            invalidate()
            return
        }

        // Analog stick
        if (analogActive && pointerId == analogPointerId) {
            val dx = x - analogCenterX
            val dy = y - analogCenterY
            val dist = hypot(dx, dy)
            val clampedDist = min(dist, analogMaxReachPx)
            val magnitude =
                    if (analogMaxReachPx > 0) clampedDist / analogMaxReachPx * analogSensitivity
                    else 0f
            val nxDir = if (dist > 0) dx / dist else 0f
            val nyDir = if (dist > 0) dy / dist else 0f
            analogKnobX = nxDir * clampedDist
            analogKnobY = nyDir * clampedDist
            retroView?.sendMotionEvent(
                    GLRetroView.MOTION_SOURCE_ANALOG_LEFT,
                    nxDir * magnitude,
                    nyDir * magnitude
            )
            invalidate()
            return
        }
    }

    private fun handleUp(pointerId: Int, x: Float, y: Float) {
        // ButtonStick
        val stickState = stickStates[pointerId]
        if (stickState != null && stickState.active) {
            if (stickState.dragging) {
                retroView?.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_LEFT, 0f, 0f)
            }
            retroView?.sendKeyEvent(KeyEvent.ACTION_UP, InputMapper.mapKeyCode(stickState.keyCode))
            stickStates.remove(pointerId)
            invalidate()
            return
        }

        // Touch
        val touchState = touchStates[pointerId]
        if (touchState != null && touchState.active) {
            retroView?.sendKeyEvent(KeyEvent.ACTION_UP, InputMapper.mapKeyCode(touchState.keyCode))
            touchStates.remove(pointerId)
            invalidate()
            return
        }

        // DPAD
        if (dpadActive && pointerId == dpadPointerId) {
            retroView?.sendMotionEvent(GLRetroView.MOTION_SOURCE_DPAD, 0f, 0f)
            dpadActive = false
            dpadPointerId = -1
            invalidate()
            return
        }

        // Analog
        if (analogActive && pointerId == analogPointerId) {
            retroView?.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_LEFT, 0f, 0f)
            analogActive = false
            analogPointerId = -1
            invalidate()
            return
        }
    }

    /**
     * Handle double-tap on the analog zone to toggle auto-Z. Returns true if a double-tap was
     * detected.
     */
    fun handleDoubleTap(x: Float, y: Float): Boolean {
        val nx = x / width
        val ny = y / height
        val zone = AreaControlLayout.hitTest(nx, ny)
        if (zone?.zoneType != ZoneType.ANALOG) return false

        val now = System.currentTimeMillis()
        if (now - lastTapTime < 300 && lastTapZone?.zoneType == ZoneType.ANALOG) {
            if (!autoZEnabled) return false
            zHeldViaDoubleTap = !zHeldViaDoubleTap
            val action = if (zHeldViaDoubleTap) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP
            retroView?.sendKeyEvent(action, InputMapper.mapKeyCode(KeyEvent.KEYCODE_BUTTON_L2))
            onCButtonDown?.invoke(KeyEvent.KEYCODE_BUTTON_L2)
            return true
        }
        lastTapTime = now
        lastTapZone = zone
        return false
    }

    /** Cancel any active gesture (called when switching modes). */
    fun cancelAll() {
        // Release all held keys
        for ((_, state) in stickStates) {
            if (state.active) {
                retroView?.sendKeyEvent(KeyEvent.ACTION_UP, InputMapper.mapKeyCode(state.keyCode))
                if (state.dragging) {
                    retroView?.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_LEFT, 0f, 0f)
                }
            }
        }
        for ((_, state) in touchStates) {
            if (state.active) {
                retroView?.sendKeyEvent(KeyEvent.ACTION_UP, InputMapper.mapKeyCode(state.keyCode))
            }
        }
        if (dpadActive) {
            retroView?.sendMotionEvent(GLRetroView.MOTION_SOURCE_DPAD, 0f, 0f)
        }
        if (analogActive) {
            retroView?.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_LEFT, 0f, 0f)
        }
        if (zHeldViaDoubleTap) {
            retroView?.sendKeyEvent(
                    KeyEvent.ACTION_UP,
                    InputMapper.mapKeyCode(KeyEvent.KEYCODE_BUTTON_L2)
            )
        }
        stickStates.clear()
        touchStates.clear()
        dpadActive = false
        analogActive = false
        zHeldViaDoubleTap = false
        invalidate()
    }
}
