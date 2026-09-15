package br.com.redclaw.hylianbox.gamepad

import android.graphics.RectF
import android.view.KeyEvent

/**
 * Zonas de toque mapeadas da imagem de referência (/home/kaizonaro/Imagens/mapeamento.png),
 * expressas como frações normalizadas (0f..1f) da largura e altura do overlay.
 *
 * As zonas são classificadas em:
 * - [ZoneType.BUTTON_STICK]: 5 zonas que comportam toque puro + arraste analógico (C-Left, C-Right,
 * C-Down, A, B)
 * - [ZoneType.TOUCH]: 6 zonas de toque simples (C-Up, L, Z, R, Start, Select)
 * - [ZoneType.DPAD_SWIPE]: zona de deslize direcional
 * - [ZoneType.ANALOG]: zona analógica flutuante
 *
 * As cores dos glows são amostradas da imagem de referência:
 * - CINZA: 0xFFA0A0A0 (START, SELECT, C-UP labels)
 * - AZUL: 0xFF3264E0 (analog stick area)
 * - AMARELO: 0xFFE0E020 (C-Left, C-Right, C-Down)
 * - VERMELHO: 0xFFE02020 (Z trigger)
 * - ROXO: 0xFFA020E0 (L, R buttons)
 * - VERDE: 0xFF60E020 (A, B buttons)
 */
data class AreaZone(
        val rect: RectF,
        val zoneType: ZoneType,
        val keyCode: Int? = null,
        /** Cor do glow ocioso em ARGB. */
        val idleColor: Int,
        /** Cor do glow pressionado em ARGB. */
        val pressedColor: Int
)

enum class ZoneType {
    BUTTON_STICK,
    TOUCH,
    DPAD_SWIPE,
    ANALOG
}

/**
 * Definição de todas as zonas de toque do modo Área Mapeada.
 *
 * As coordenadas são frações da largura/altura do overlay (0f..1f), medidas a partir da imagem de
 * referência mapeamento.png (1366x768).
 *
 * Layout da imagem (análise de pixels):
 * - Faixa superior (y~0-0.027): labels START, SELECT, C-UP
 * - Área principal (y~0.971-0.997): grade de botões no canto inferior
 * - Coluna esquerda (x~0.424-0.578): C-Left
 * - Coluna central (x~0.577-0.778): C-Right, C-Down
 * - Coluna direita (x~0.779-0.996): A, B
 * - Zona direita (x~0.984-0.999, y~0.417-0.618): L, Z, R
 * - DPAD e ANALOG: áreas separadas na esquerda
 */
object AreaControlLayout {

    // Cores dos glows amostradas da imagem de referência
    const val COLOR_GRAY = 0xFFA0A0A0.toInt()
    const val COLOR_BLUE = 0xFF3264E0.toInt()
    const val COLOR_YELLOW = 0xFFE0E020.toInt()
    const val COLOR_RED = 0xFFE02020.toInt()
    const val COLOR_PURPLE = 0xFFA020E0.toInt()
    const val COLOR_GREEN = 0xFF60E020.toInt()

    /** Opacidade base do glow ocioso (fração 0f..1f). */
    const val GLOW_IDLE_ALPHA = 0.15f

    /** Opacidade do glow pressionado (fração 0f..1f). */
    const val GLOW_PRESSED_ALPHA = 0.60f

    /** Raio do glow em fração da menor dimensão do overlay. */
    const val GLOW_RADIUS_FRACTION = 0.06f

    /** Threshold de arraste para ButtonStick em pixels. */
    const val DRAG_THRESHOLD_DP = 12f

    /**
     * Zonas ButtonStick (toque + arraste analógico). Posições medidas da imagem: y~0.971-0.997, 3
     * colunas no canto inferior.
     */
    val buttonStickZones =
            listOf(
                    AreaZone(
                            rect = RectF(0.424f, 0.971f, 0.578f, 0.997f),
                            zoneType = ZoneType.BUTTON_STICK,
                            keyCode = KeyEvent.KEYCODE_BUTTON_L1,
                            idleColor = COLOR_YELLOW,
                            pressedColor = 0xFFF9A825.toInt()
                    ),
                    AreaZone(
                            rect = RectF(0.577f, 0.971f, 0.778f, 0.997f),
                            zoneType = ZoneType.BUTTON_STICK,
                            keyCode = KeyEvent.KEYCODE_BUTTON_R1,
                            idleColor = COLOR_YELLOW,
                            pressedColor = 0xFFF9A825.toInt()
                    ),
                    AreaZone(
                            rect = RectF(0.779f, 0.971f, 0.996f, 0.997f),
                            zoneType = ZoneType.BUTTON_STICK,
                            keyCode = KeyEvent.KEYCODE_BUTTON_A,
                            idleColor = COLOR_GREEN,
                            pressedColor = 0xFF2E7D32.toInt()
                    ),
                    AreaZone(
                            rect = RectF(0.779f, 0.955f, 0.996f, 0.971f),
                            zoneType = ZoneType.BUTTON_STICK,
                            keyCode = KeyEvent.KEYCODE_BUTTON_B,
                            idleColor = COLOR_GREEN,
                            pressedColor = 0xFF2E7D32.toInt()
                    ),
                    AreaZone(
                            rect = RectF(0.577f, 0.955f, 0.778f, 0.971f),
                            zoneType = ZoneType.BUTTON_STICK,
                            keyCode = KeyEvent.KEYCODE_BUTTON_X,
                            idleColor = COLOR_YELLOW,
                            pressedColor = 0xFFF9A825.toInt()
                    )
            )

    /** Zonas de toque simples (pressiona/solta). */
    val touchZones =
            listOf(
                    AreaZone(
                            rect = RectF(0.434f, 0.017f, 0.564f, 0.027f),
                            zoneType = ZoneType.TOUCH,
                            keyCode = KeyEvent.KEYCODE_BUTTON_START,
                            idleColor = COLOR_GRAY,
                            pressedColor = 0xFFC0C0C0.toInt()
                    ),
                    AreaZone(
                            rect = RectF(0.550f, 0.017f, 0.629f, 0.027f),
                            zoneType = ZoneType.TOUCH,
                            keyCode = KeyEvent.KEYCODE_BUTTON_SELECT,
                            idleColor = COLOR_GRAY,
                            pressedColor = 0xFFC0C0C0.toInt()
                    ),
                    AreaZone(
                            rect = RectF(0.649f, 0.017f, 0.729f, 0.027f),
                            zoneType = ZoneType.TOUCH,
                            keyCode = KeyEvent.KEYCODE_BUTTON_Y,
                            idleColor = COLOR_YELLOW,
                            pressedColor = 0xFFF9A825.toInt()
                    ),
                    AreaZone(
                            rect = RectF(0.984f, 0.417f, 0.999f, 0.531f),
                            zoneType = ZoneType.TOUCH,
                            keyCode = KeyEvent.KEYCODE_BUTTON_L2,
                            idleColor = COLOR_PURPLE,
                            pressedColor = 0xFF8020C0.toInt()
                    ),
                    AreaZone(
                            rect = RectF(0.984f, 0.531f, 0.999f, 0.618f),
                            zoneType = ZoneType.TOUCH,
                            keyCode = KeyEvent.KEYCODE_BUTTON_R2,
                            idleColor = COLOR_PURPLE,
                            pressedColor = 0xFF8020C0.toInt()
                    ),
                    AreaZone(
                            rect = RectF(0.984f, 0.618f, 0.999f, 0.700f),
                            zoneType = ZoneType.TOUCH,
                            keyCode = KeyEvent.KEYCODE_BUTTON_START,
                            idleColor = COLOR_GRAY,
                            pressedColor = 0xFFC0C0C0.toInt()
                    )
            )

    /** Zona DPAD Swipe (deslize direcional). */
    val dpadSwipeZone =
            AreaZone(
                    rect = RectF(0.03f, 0.28f, 0.18f, 0.56f),
                    zoneType = ZoneType.DPAD_SWIPE,
                    keyCode = null,
                    idleColor = COLOR_GRAY,
                    pressedColor = 0xFFC0C0C0.toInt()
            )

    /** Zona analógica flutuante (origem onde o dedo toca). */
    val analogZone =
            AreaZone(
                    rect = RectF(0.03f, 0.63f, 0.18f, 0.91f),
                    zoneType = ZoneType.ANALOG,
                    keyCode = null,
                    idleColor = COLOR_BLUE,
                    pressedColor = 0xFF5080FF.toInt()
            )

    /** Todas as zonas combinadas, em ordem de prioridade (primeira = mais alta). */
    val allZones: List<AreaZone> by lazy {
        listOf(dpadSwipeZone, analogZone) + buttonStickZones + touchZones
    }

    /**
     * Retorna a zona que contém o ponto (x, y) em coordenadas normalizadas (0f..1f). Prioridade:
     * ButtonStick sobre TOUCH sobre DPAD/ANALOG.
     */
    fun hitTest(x: Float, y: Float): AreaZone? {
        for (zone in buttonStickZones) {
            if (x >= zone.rect.left &&
                            x <= zone.rect.right &&
                            y >= zone.rect.top &&
                            y <= zone.rect.bottom
            ) {
                return zone
            }
        }
        for (zone in touchZones) {
            if (x >= zone.rect.left &&
                            x <= zone.rect.right &&
                            y >= zone.rect.top &&
                            y <= zone.rect.bottom
            ) {
                return zone
            }
        }
        if (x >= dpadSwipeZone.rect.left &&
                        x <= dpadSwipeZone.rect.right &&
                        y >= dpadSwipeZone.rect.top &&
                        y <= dpadSwipeZone.rect.bottom
        ) {
            return dpadSwipeZone
        }
        if (x >= analogZone.rect.left &&
                        x <= analogZone.rect.right &&
                        y >= analogZone.rect.top &&
                        y <= analogZone.rect.bottom
        ) {
            return analogZone
        }
        return null
    }
}
