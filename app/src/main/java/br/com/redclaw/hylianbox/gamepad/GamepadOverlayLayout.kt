package br.com.redclaw.hylianbox.gamepad

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout

/**
 * Container do overlay de controles que rastreia quantos dedos estão na tela.
 *
 * Prioridade do analógico: quando há mais de um dedo, [StickButton] deve se comportar
 * como botão puro (sem arraste analógico). O analógico ([FloatingJoystick] / RadialGamePad)
 * sempre tem prioridade.
 *
 * O ButtonStick só funciona com um único dedo (toque + arraste com o mesmo dedo).
 * Usar o segundo dedo cancela o arraste e o botão volta a ser normal até o próximo toque.
 */
class GamepadOverlayLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    var activePointerCount: Int = 0
        private set

    /** True quando há exatamente um dedo na tela — único caso em que ButtonStick pode arrastar. */
    fun isSinglePointer(): Boolean = activePointerCount == 1

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> activePointerCount = 1
            MotionEvent.ACTION_POINTER_DOWN -> activePointerCount++
            MotionEvent.ACTION_POINTER_UP -> activePointerCount = (activePointerCount - 1).coerceAtLeast(0)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> activePointerCount = 0
        }

        // Se entrou segundo dedo, cancela imediatamente qualquer arraste de StickButton
        // para que o analógico (FloatingJoystick) tenha prioridade total.
        if (activePointerCount > 1) {
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                if (child is StickButton && child.isDragging) {
                    child.cancelDragFromOverlay()
                }
                // DoubleTapContainer que envolve o FloatingJoystick
                if (child is DoubleTapContainer) {
                    for (j in 0 until child.childCount) {
                        val inner = child.getChildAt(j)
                        if (inner is StickButton && inner.isDragging) inner.cancelDragFromOverlay()
                    }
                }
            }
        }

        return super.dispatchTouchEvent(ev)
    }
}
