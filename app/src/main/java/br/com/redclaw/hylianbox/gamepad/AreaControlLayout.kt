package br.com.redclaw.hylianbox.gamepad

import android.view.KeyEvent

data class NormalizedRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun contains(x: Float, y: Float): Boolean = x >= left && x < right && y >= top && y < bottom
}

/** Logical controls available in the mapped-area overlay. */
enum class AreaControl {
    DPAD_SWIPE, ANALOG, L, START, C_LEFT, C_UP, C_DOWN, C_RIGHT, Z, R, A, B
}

enum class ZoneType { BUTTON_STICK, TOUCH, DPAD_SWIPE, ANALOG }

/** A normalized hit target measured from `mapeamento.png` (1366 x 768). */
data class AreaZone(
        val control: AreaControl,
        val rect: NormalizedRect,
        val zoneType: ZoneType,
        val keyCode: Int? = null
)

enum class GlowEdge { TOP, RIGHT, BOTTOM }

/** A colored outer-edge glow from the reference, never a visible control boundary. */
data class AreaGlow(
        val control: AreaControl,
        val edge: GlowEdge,
        val start: Float,
        val end: Float,
        val color: Int
)

/**
 * Touch map for the Pro / Touch Areas control mode.
 *
 * The reference's white rectangles and labels are annotations and are intentionally not rendered.
 * Their inner edges define the hit targets below. Only the colored outer-edge glows in [glows]
 * are drawn.
 */
object AreaControlLayout {
    const val COLOR_GRAY = 0xFFBDBDBD.toInt()
    const val COLOR_BLUE = 0xFF2979FF.toInt()
    const val COLOR_YELLOW = 0xFFFFF200.toInt()
    const val COLOR_RED = 0xFFFF1744.toInt()
    const val COLOR_PURPLE = 0xFFAA35FF.toInt()
    const val COLOR_GREEN = 0xFF64DD17.toInt()

    const val GLOW_IDLE_ALPHA = 0.58f
    const val GLOW_PRESSED_ALPHA = 1f
    const val GLOW_DEPTH_FRACTION = 0.032f
    const val FEEDBACK_ALPHA = 0.92f
    const val FEEDBACK_RADIUS_FRACTION = 0.026f
    const val ANALOG_REACH_FRACTION = 0.075f
    const val DRAG_THRESHOLD_DP = 12f
    const val DPAD_SWIPE_THRESHOLD_DP = 20f

    // Divisions measured from the reference: x=205/578/780/974/1167/1366.
    private const val DPAD_RIGHT = 0.1501f
    private const val LEFT_PANEL_RIGHT = 0.4231f
    private const val START_RIGHT = 0.5710f
    private const val C_LEFT_RIGHT = 0.7130f
    private const val C_DOWN_RIGHT = 0.8543f
    private const val A_RIGHT = 0.7796f

    // Divisions measured from the reference: y=109/225/322/473/654/768.
    private const val L_BOTTOM = 0.1419f
    private const val DPAD_BOTTOM = 0.2930f
    private const val TOP_ROW_BOTTOM = 0.4193f
    private const val R_BOTTOM = 0.6159f
    private const val C_UP_BOTTOM = 0.8516f

    val dpadSwipeZone = AreaZone(
            AreaControl.DPAD_SWIPE,
            NormalizedRect(0f, 0f, DPAD_RIGHT, DPAD_BOTTOM),
            ZoneType.DPAD_SWIPE
    )

    val analogZone = AreaZone(
            AreaControl.ANALOG,
            NormalizedRect(0f, L_BOTTOM, LEFT_PANEL_RIGHT, 1f),
            ZoneType.ANALOG
    )

    val touchZones = listOf(
            AreaZone(
                    AreaControl.L,
                    NormalizedRect(DPAD_RIGHT, 0f, LEFT_PANEL_RIGHT, L_BOTTOM),
                    ZoneType.TOUCH,
                    KeyEvent.KEYCODE_BUTTON_SELECT
            ),
            AreaZone(
                    AreaControl.START,
                    NormalizedRect(LEFT_PANEL_RIGHT, 0f, START_RIGHT, TOP_ROW_BOTTOM),
                    ZoneType.TOUCH,
                    KeyEvent.KEYCODE_BUTTON_START
            ),
            AreaZone(
                    AreaControl.C_UP,
                    NormalizedRect(LEFT_PANEL_RIGHT, TOP_ROW_BOTTOM, START_RIGHT, C_UP_BOTTOM),
                    ZoneType.BUTTON_STICK,
                    KeyEvent.KEYCODE_BUTTON_Y
            ),
            AreaZone(
                    AreaControl.Z,
                    NormalizedRect(LEFT_PANEL_RIGHT, C_UP_BOTTOM, START_RIGHT, 1f),
                    ZoneType.TOUCH,
                    KeyEvent.KEYCODE_BUTTON_L2
            ),
            AreaZone(
                    AreaControl.R,
                    NormalizedRect(START_RIGHT, TOP_ROW_BOTTOM, 1f, R_BOTTOM),
                    ZoneType.TOUCH,
                    KeyEvent.KEYCODE_BUTTON_R2
            )
    )

    val buttonStickZones = listOf(
            AreaZone(
                    AreaControl.C_LEFT,
                    NormalizedRect(START_RIGHT, 0f, C_LEFT_RIGHT, TOP_ROW_BOTTOM),
                    ZoneType.BUTTON_STICK,
                    KeyEvent.KEYCODE_BUTTON_L1
            ),
            AreaZone(
                    AreaControl.C_DOWN,
                    NormalizedRect(C_LEFT_RIGHT, 0f, C_DOWN_RIGHT, TOP_ROW_BOTTOM),
                    ZoneType.BUTTON_STICK,
                    KeyEvent.KEYCODE_BUTTON_X
            ),
            AreaZone(
                    AreaControl.C_RIGHT,
                    NormalizedRect(C_DOWN_RIGHT, 0f, 1f, TOP_ROW_BOTTOM),
                    ZoneType.BUTTON_STICK,
                    KeyEvent.KEYCODE_BUTTON_R1
            ),
            AreaZone(
                    AreaControl.A,
                    NormalizedRect(START_RIGHT, R_BOTTOM, A_RIGHT, 1f),
                    ZoneType.BUTTON_STICK,
                    KeyEvent.KEYCODE_BUTTON_A
            ),
            AreaZone(
                    AreaControl.B,
                    NormalizedRect(A_RIGHT, R_BOTTOM, 1f, 1f),
                    ZoneType.BUTTON_STICK,
                    KeyEvent.KEYCODE_BUTTON_B
            )
    )

    // Priority matters where DPAD and analog overlap in the annotated reference.
    val allZones: List<AreaZone> = listOf(dpadSwipeZone) + buttonStickZones + touchZones + analogZone

    /** Colored glows visible in the reference image, measured along the outer screen edge. */
    val glows = listOf(
            AreaGlow(AreaControl.DPAD_SWIPE, GlowEdge.TOP, 0f, DPAD_RIGHT, COLOR_GRAY),
            AreaGlow(AreaControl.L, GlowEdge.TOP, DPAD_RIGHT, LEFT_PANEL_RIGHT, COLOR_GRAY),
            AreaGlow(AreaControl.START, GlowEdge.TOP, LEFT_PANEL_RIGHT, START_RIGHT, COLOR_RED),
            AreaGlow(AreaControl.C_LEFT, GlowEdge.TOP, START_RIGHT, C_LEFT_RIGHT, COLOR_YELLOW),
            AreaGlow(AreaControl.C_DOWN, GlowEdge.TOP, C_LEFT_RIGHT, C_DOWN_RIGHT, COLOR_YELLOW),
            AreaGlow(AreaControl.C_RIGHT, GlowEdge.TOP, C_DOWN_RIGHT, 1f, COLOR_YELLOW),
            AreaGlow(AreaControl.R, GlowEdge.RIGHT, TOP_ROW_BOTTOM, R_BOTTOM, COLOR_PURPLE),
            AreaGlow(AreaControl.Z, GlowEdge.BOTTOM, LEFT_PANEL_RIGHT, START_RIGHT, COLOR_PURPLE),
            AreaGlow(AreaControl.A, GlowEdge.BOTTOM, START_RIGHT, A_RIGHT, COLOR_BLUE),
            AreaGlow(AreaControl.B, GlowEdge.BOTTOM, A_RIGHT, 1f, COLOR_GREEN)
    )

    fun hitTest(x: Float, y: Float): AreaZone? {
        if (x < 0f || x >= 1f || y < 0f || y >= 1f) return null
        return allZones.firstOrNull { it.rect.contains(x, y) }
    }

    fun zone(control: AreaControl): AreaZone = allZones.first { it.control == control }
}
