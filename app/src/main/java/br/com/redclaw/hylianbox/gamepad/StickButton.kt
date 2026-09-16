package br.com.redclaw.hylianbox.gamepad

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import br.com.redclaw.hylianbox.input.InputMapper
import com.swordfish.libretrodroid.GLRetroView
import kotlin.math.hypot
import kotlin.math.min

/**
 * Botão que opera como ButtonStick individual quando [stickEnabled] está ON.
 *
 * - Toque simples: pressiona/solta [targetKeyCode] (como botão normal).
 * - Toque + arraste com UM ÚNICO dedo: mantém [targetKeyCode] pressionado e move o analógico N64
 * (MOTION_SOURCE_ANALOG_LEFT) proporcional ao deslocamento, escalado por [sensitivity].
 * - Com 2+ dedos na tela o ButtonStick é desarmado: o botão continua pressionado mas NÃO move o
 * analógico — prioridade total do [FloatingJoystick]/analógico. Só volta a arrastar no próximo
 * toque.
 *
 * Quando [stickEnabled] está OFF, comporta-se como botão puro (sem analógico).
 */
class StickButton(
        context: Context,
        val targetKeyCode: Int,
        private val label: String,
        private val theme: Theme,
        private val supportsAnalogDrag: Boolean = true,
        private val hapticEnabled: Boolean = true
) : View(context) {

    data class Theme(val normal: Int, val pressed: Int, val text: Int)

    companion object {
        private const val DRAG_THRESHOLD_DP = 12f
        private const val OCARINA_HOLD_MS = 600L

        val YELLOW_THEME = Theme(0xFFFFEB3B.toInt(), 0xFFF9A825.toInt(), Color.DKGRAY)
        val BLUE_THEME = Theme(0xFF2196F3.toInt(), 0xFF1565C0.toInt(), Color.WHITE)
        val GREEN_THEME = Theme(0xFF4CAF50.toInt(), 0xFF2E7D32.toInt(), Color.WHITE)
        val NEUTRAL_THEME = Theme(0x44FFFFFF.toInt(), 0x88FFFFFF.toInt(), Color.WHITE)
    }

    var retroView: GLRetroView? = null
    var sensitivity: Float = 0.5f
    var stickEnabled: Boolean = true
    var isOcarinaButton: Boolean = false
    var onOcarinaHold: (() -> Unit)? = null
    private val handler = Handler(Looper.getMainLooper())
    private var ocarinaHoldRunnable: Runnable? = null

    // Overlay uses physical pixels and the system density so the global UI
    // scale (UiScaleManager) never affects the emulator controls.
    private val dragThresholdPx = DRAG_THRESHOLD_DP * Resources.getSystem().displayMetrics.density

    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var pressed = false
    private var thumbOffsetX = 0f
    private var thumbOffsetY = 0f
    private var activePointerId = -1
    /** Quando true, o arraste analógico fica bloqueado até o próximo ACTION_DOWN (multi-toque). */
    private var stickLockedOut = false

    val isDragging: Boolean
        get() = dragging

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
            }
    private val badgeBgPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xCC000000.toInt()
                style = Paint.Style.FILL
            }
    private val badgeBorderPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                // System density: overlay stays immune to the global UI scale.
                strokeWidth = 1.5f * Resources.getSystem().displayMetrics.density
            }
    private val badgeTextPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
                color = Color.WHITE
            }
    private var icon: Bitmap? = null
    private var badgeCount: Int? = null

    /** Replaces the button label with an equipped-item icon, or restores the label for null. */
    fun setIcon(bitmap: Bitmap?) {
        icon = bitmap
        invalidate()
    }

    /**
     * Shows a quantity badge (ammo count) at the button's bottom-right edge, or hides it for null.
     * Only items with a quantity concept ever receive a non-null count (see ItemAmmoResolver);
     * everything else keeps no badge.
     */
    fun setBadge(count: Int?) {
        badgeCount = count
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) / 2f

        backgroundPaint.color = if (pressed && !dragging) theme.pressed else theme.normal
        canvas.drawCircle(cx, cy, radius, backgroundPaint)

        if (dragging && stickEnabled) {
            thumbPaint.color = theme.pressed
            canvas.drawCircle(cx + thumbOffsetX, cy + thumbOffsetY, radius * 0.4f, thumbPaint)
        } else {
            val currentIcon = icon
            if (currentIcon != null) {
                val half = radius * 0.68f
                canvas.drawBitmap(
                        currentIcon,
                        null,
                        RectF(cx - half, cy - half, cx + half, cy + half),
                        null
                )
            } else {
                textPaint.color = theme.text
                textPaint.textSize = radius * 0.6f
                canvas.drawText(
                        label,
                        cx,
                        cy - (textPaint.ascent() + textPaint.descent()) / 2,
                        textPaint
                )
            }
            drawBadge(canvas, cx, cy, radius)
        }
    }

    private fun drawBadge(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val count = badgeCount ?: return
        val text = count.toString()
        badgeTextPaint.textSize = radius * 0.38f
        val textWidth = badgeTextPaint.measureText(text)
        val padH = radius * 0.16f
        val padV = radius * 0.10f
        val halfW = textWidth / 2f + padH
        val halfH = badgeTextPaint.textSize / 2f + padV
        // Bottom-right edge, slightly inset so it never clips outside the view.
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

    /**
     * Chamado pelo [GamepadOverlayLayout] quando um segundo dedo entra — cancela o arraste
     * imediatamente.
     */
    fun cancelDragFromOverlay() {
        if (!dragging) {
            stickLockedOut = true
            return
        }
        dragging = false
        stickLockedOut = true
        thumbOffsetX = 0f
        thumbOffsetY = 0f
        retroView?.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_LEFT, 0f, 0f)
        invalidate()
    }

    /**
     * Releases any held press before the view is detached (e.g. overlay hot-swap Standard <-> Pro):
     * sends ACTION_UP when pressed, zeroes a dragging analog and cancels the pending ocarina hold.
     * Safe no-op when idle.
     */
    fun release() {
        cancelOcarinaHold()
        if (dragging) {
            retroView?.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_LEFT, 0f, 0f)
        }
        if (pressed) {
            retroView?.sendKeyEvent(KeyEvent.ACTION_UP, InputMapper.mapKeyCode(targetKeyCode))
        }
        resetState()
        invalidate()
    }

    private fun isSinglePointer(event: MotionEvent): Boolean {
        val parentLayout = parent as? GamepadOverlayLayout
        if (parentLayout != null) return parentLayout.isSinglePointer()
        // Fallback se o parent ainda não for GamepadOverlayLayout (ex.: testes)
        return event.pointerCount == 1
    }

    private fun resetState() {
        dragging = false
        pressed = false
        thumbOffsetX = 0f
        thumbOffsetY = 0f
        activePointerId = -1
        stickLockedOut = false
        cancelOcarinaHold()
    }

    private fun cancelOcarinaHold() {
        ocarinaHoldRunnable?.let { handler.removeCallbacks(it) }
        ocarinaHoldRunnable = null
    }

    private fun scheduleOcarinaHold() {
        if (!isOcarinaButton || onOcarinaHold == null) return
        cancelOcarinaHold()
        val r = Runnable {
            if (pressed && !dragging && !stickLockedOut) {
                onOcarinaHold?.invoke()
            }
        }
        ocarinaHoldRunnable = r
        handler.postDelayed(r, OCARINA_HOLD_MS)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val maxRadius = min(width, height) / 2f
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(0)
                downX = event.getX(0)
                downY = event.getY(0)
                dragging = false
                pressed = true
                stickLockedOut = false
                if (hapticEnabled) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                retroView?.sendKeyEvent(KeyEvent.ACTION_DOWN, InputMapper.mapKeyCode(targetKeyCode))
                scheduleOcarinaHold()
                invalidate()
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = event.actionIndex
                val x = event.getX(idx)
                val y = event.getY(idx)
                val inside = x >= 0 && x < width && y >= 0 && y < height
                if (!pressed && inside) {
                    // Segundo dedo caiu exatamente neste botão -> pressiona como botão puro
                    activePointerId = event.getPointerId(idx)
                    downX = x
                    downY = y
                    dragging = false
                    pressed = true
                    stickLockedOut = true
                    if (hapticEnabled) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    retroView?.sendKeyEvent(
                            KeyEvent.ACTION_DOWN,
                            InputMapper.mapKeyCode(targetKeyCode)
                    )
                    invalidate()
                } else {
                    // Segundo dedo em outro lugar (ex.: analógico) -> desarma ButtonStick
                    stickLockedOut = true
                    if (dragging) {
                        dragging = false
                        thumbOffsetX = 0f
                        thumbOffsetY = 0f
                        retroView?.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_LEFT, 0f, 0f)
                        invalidate()
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (!stickEnabled || !supportsAnalogDrag) return true
                // Prioridade do analógico: com 2+ dedos, nunca arrasta
                if (stickLockedOut || !isSinglePointer(event)) {
                    if (dragging) {
                        dragging = false
                        thumbOffsetX = 0f
                        thumbOffsetY = 0f
                        retroView?.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_LEFT, 0f, 0f)
                        invalidate()
                    }
                    stickLockedOut = true
                    return true
                }
                val idx = event.findPointerIndex(activePointerId)
                if (idx == -1) return true
                val x = event.getX(idx)
                val y = event.getY(idx)
                val dx = x - downX
                val dy = y - downY
                val dist = hypot(dx, dy)
                if (!dragging && dist > dragThresholdPx) {
                    dragging = true
                    cancelOcarinaHold()
                }
                if (!dragging) return true
                val clampedDist = min(dist, maxRadius)
                val nx = if (dist > 0) dx / dist else 0f
                val ny = if (dist > 0) dy / dist else 0f
                thumbOffsetX = nx * clampedDist
                thumbOffsetY = ny * clampedDist
                val magnitude = clampedDist / maxRadius * sensitivity
                retroView?.sendMotionEvent(
                        GLRetroView.MOTION_SOURCE_ANALOG_LEFT,
                        nx * magnitude,
                        -ny * magnitude
                )
                invalidate()
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val liftedId = event.getPointerId(event.actionIndex)
                if (liftedId == activePointerId) {
                    // O dedo do botão foi levantado (outro dedo ainda na tela) -> solta botão
                    if (dragging) {
                        retroView?.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_LEFT, 0f, 0f)
                    }
                    retroView?.sendKeyEvent(
                            KeyEvent.ACTION_UP,
                            InputMapper.mapKeyCode(targetKeyCode)
                    )
                    resetState()
                    invalidate()
                } else {
                    // Outro dedo levantado, mantém botão pressionado mas continua bloqueado até
                    // soltar
                    // (evita que o arraste volte no meio do gesto)
                }
            }
            MotionEvent.ACTION_UP -> {
                // Último dedo levantado
                if (dragging && stickEnabled && supportsAnalogDrag) {
                    retroView?.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_LEFT, 0f, 0f)
                }
                retroView?.sendKeyEvent(KeyEvent.ACTION_UP, InputMapper.mapKeyCode(targetKeyCode))
                resetState()
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                if (dragging && stickEnabled && supportsAnalogDrag) {
                    retroView?.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_LEFT, 0f, 0f)
                }
                // Cancela sem soltar key se já foi solto? Garante soltura
                if (pressed) {
                    retroView?.sendKeyEvent(
                            KeyEvent.ACTION_UP,
                            InputMapper.mapKeyCode(targetKeyCode)
                    )
                }
                resetState()
                invalidate()
            }
        }
        return true
    }
}
