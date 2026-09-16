package br.com.redclaw.hylianbox.gamepad

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import com.swordfish.libretrodroid.GLRetroView
import kotlin.math.hypot
import kotlin.math.min

/**
 * A "floating"/relative analog stick spanning the whole empty left side of the screen: touching
 * down anywhere within its bounds starts a virtual stick centered on that exact point, instead of
 * requiring the touch to land on a fixed graphic -- matching how most mobile games implement
 * movement sticks. Real buttons layered on top (L, D-pad) claim their own touches first via normal
 * view z-order, so this view only ever sees touches that land on genuinely empty space. A static
 * hint circle at [hintX]/[hintY] marks the stick's usual resting spot when idle, purely cosmetic.
 *
 * Suporta multi-toque: o analógico pode ser iniciado com o segundo dedo enquanto um botão
 * (StickButton) está segurado com o primeiro — prioridade total do analógico.
 */
class FloatingJoystick(context: Context) : View(context) {
    companion object {
        private const val BASE_COLOR = 0x33FFFFFF
        private const val KNOB_COLOR = 0x88FFFFFF.toInt()
        private const val HINT_COLOR = 0x44FFFFFF
    }

    var retroView: GLRetroView? = null

    /** 0f..1f multiplier applied to the drag's analog magnitude; live-adjustable from Settings. */
    var sensitivity: Float = 1f

    var hintX: Float = 0f
    var hintY: Float = 0f
    var hintRadius: Float = 0f

    /** How far (px) the finger can drag from the touch-down point before magnitude maxes out. */
    var maxReachPx: Float = 0f

    private var active = false
    private var activePointerId = -1
    private var centerX = 0f
    private var centerY = 0f
    private var knobOffsetX = 0f
    private var knobOffsetY = 0f

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BASE_COLOR }
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = KNOB_COLOR }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = HINT_COLOR }

    /**
     * Releases a held analog before the view is detached (e.g. overlay hot-swap Standard <-> Pro).
     * Safe no-op when idle.
     */
    fun release() {
        if (active) {
            retroView?.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_LEFT, 0f, 0f)
        }
        active = false
        activePointerId = -1
        knobOffsetX = 0f
        knobOffsetY = 0f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (active) {
            canvas.drawCircle(centerX, centerY, maxReachPx, basePaint)
            canvas.drawCircle(
                    centerX + knobOffsetX,
                    centerY + knobOffsetY,
                    maxReachPx * 0.4f,
                    knobPaint
            )
        } else {
            canvas.drawCircle(hintX, hintY, hintRadius, hintPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                // Inicia o analógico com o dedo que caiu dentro desta view.
                // Se já está ativo, ignora novo dedo (mantém o primeiro).
                if (active) return true
                val idx = event.actionIndex
                activePointerId = event.getPointerId(idx)
                active = true
                centerX = event.getX(idx)
                centerY = event.getY(idx)
                knobOffsetX = 0f
                knobOffsetY = 0f
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (!active) return true
                val idx = event.findPointerIndex(activePointerId)
                if (idx == -1) return true
                val dx = event.getX(idx) - centerX
                val dy = event.getY(idx) - centerY
                val dist = hypot(dx, dy)
                val clampedDist = min(dist, maxReachPx)
                val nx = if (dist > 0) dx / dist else 0f
                val ny = if (dist > 0) dy / dist else 0f
                knobOffsetX = nx * clampedDist
                knobOffsetY = ny * clampedDist

                val magnitude = if (maxReachPx > 0) clampedDist / maxReachPx * sensitivity else 0f
                retroView?.sendMotionEvent(
                        GLRetroView.MOTION_SOURCE_ANALOG_LEFT,
                        nx * magnitude,
                        ny * magnitude
                )
                invalidate()
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val liftedId = event.getPointerId(event.actionIndex)
                if (liftedId == activePointerId) {
                    // Dedo do analógico levantado — reseta, mesmo que outro dedo (botão) continue
                    active = false
                    activePointerId = -1
                    knobOffsetX = 0f
                    knobOffsetY = 0f
                    retroView?.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_LEFT, 0f, 0f)
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (active) {
                    retroView?.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_LEFT, 0f, 0f)
                }
                active = false
                activePointerId = -1
                knobOffsetX = 0f
                knobOffsetY = 0f
                invalidate()
            }
        }
        return true
    }
}
