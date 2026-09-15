package br.com.redclaw.hylianbox.gamepad

/**
 * Seleciona o modo de overlay de controles exibido durante a emulação.
 *
 * [STANDARD] mantém o layout congelado do RadialGamePad (ButtonStick, FloatingJoystick,
 * RightTapZone) medido a partir da referência original.
 *
 * [AREA] substitui todo o overlay por zonas de toque sem botões, botões ou letras — apenas glows
 * radiais que intensificam ao pressionar, conforme a imagem de referência em
 * /home/kaizonaro/Imagens/mapeamento.png.
 */
enum class ControlOverlayMode {
    STANDARD,
    AREA;

    companion object {
        fun fromPref(value: String): ControlOverlayMode =
                when (value) {
                    "area" -> AREA
                    else -> STANDARD
                }

        fun toPref(mode: ControlOverlayMode): String =
                when (mode) {
                    AREA -> "area"
                    else -> "standard"
                }
    }
}
