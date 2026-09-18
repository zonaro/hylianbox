# Visual Identity — Nintendo Switch UI (MANDATORY)

**Nintendo Switch UI is the mandatory visual standard for ALL screens** (existing and future). This is a custom native implementation inspired by the Nintendo Switch HOME menu aesthetic (as seen in NS_Launcher / FLauncher). No Material 3 Expressive requirements remain. The only exemption is the **RadialGamePad touch-control LAYOUT** (Rule 14 — control placement, button-stick modes, floating joystick, auto-Z, physical-controller mirroring remain frozen). Interactive in-game modal surfaces follow the same fullscreen Switch screen language as normal screens; only transient, non-interactive gameplay status layers use compact overlay presentation.

> Every interactive or highlighted Switch UI element inherits the accent color selected by the user. Focus, selection, active controls, sliders, toggles, buttons, circular buttons, icons, badges and decorative highlights must not keep their own cyan, amber, violet or other fixed accent. The sole exception is the five destination glyphs in the Home dock: Loja red, Galeria cyan, Conquistas gold, Teste de Controle green and Configurações white; their focus ring still inherits the configured accent.

---

## 1. Design Tokens

| Token                  | Dark Mode              | Light Mode                | Usage                                                |
| ---------------------- | ---------------------- | ------------------------- | ---------------------------------------------------- |
| `bg_primary`           | `#2D2D2D`              | `#F0F0F0`                 | Main background (Library Home, grid screens)         |
| `bg_panel`             | `#1E1E1E` – `#2A2A2A`  | `#FFFFFF`                 | Dialogs, cards                                       |
| `accent`               | User-selected          | User-selected             | Focus, selection, active controls, buttons, icons and highlights |
| `status_warning`       | Semantic token         | Semantic token            | Warning/error status only; never generic emphasis or actions |
| `text_primary`         | `#FFFFFF`              | `#333333`                 | Primary text (titles, labels)                        |
| `text_secondary`       | `#9E9E9E`              | `#666666`                 | Secondary text (hints, "(default)" suffixes, footer) |
| `scrim`                | `rgba(0,0,0,0.5–0.6)`  | `rgba(0,0,0,0.3–0.4)`     | Modal backdrop                                       |
| `dock_circle`          | `#555555`              | `#FFFFFF` (subtle shadow) | Dock button backgrounds                              |
| `card_radius`          | `4–6 dp` (near-square) | `4–6 dp`                  | Game cards (home row, grid)                          |
| `dialog_radius`        | `12–16 dp`             | `12–16 dp`                | Dialogs, controllers modal                           |
| `card_aspect`          | 1:1 (square)           | 1:1 (square)              | Home row ~220dp@1080p, grid ~170dp@1080p             |
| `dock_button_diameter` | `~50 dp`               | `~50 dp`                  | Circular dock icons                                  |

> All colors defined as CSS-variable-style names in `colors.xml` (`color_primary`, `color_surface`, etc.). No hardcoded colors in layouts.

---

## 2. Focus System

- **D-pad / click focus** drives a **2–3 dp accent border** on the focused element + **label above the focused card** in the selected accent, 18sp medium.
- **Unfocused cards:** 10% black overlay dimming.
- **Dialog rows:** full-width border in the selected accent.
- **Circular "All Games" card:** accent border and icon, opens fullscreen grid.
- **Home dock buttons:** accent focus ring; destination glyphs use the fixed exception palette (Loja red, Galeria cyan, Conquistas gold, Teste de Controle green, Configurações white).

---

## 3. Component Inventory (Native Kotlin, Hand-Styled)

| Component            | Description                                                                                                                                                               |
| -------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `SwitchHomeRow`      | Horizontal scrollable row of square game cards + circular "Todos os Jogos" card at end                                                                                    |
| `SwitchGameCard`     | Square card (1:1), cover image, game title overlay on focus, focus border, dimming overlay                                                                                |
| `SwitchAllGamesCard` | Circular card (charcoal fill, accent 2×2 grid icon, accent border on focus)                                                                                                 |
| `SwitchGridScreen`   | Fullscreen grid ("Todos os Jogos"): header icon+title "Todos os Jogos" 20sp bold + thin separator, smaller square cards (~170dp), search/filter bar, ghosted placeholders |
| `SwitchDock`         | Fixed bottom dock: 5 circular buttons (Loja red, Galeria cyan, Conquistas gold, Teste de Controle green, Configurações white), ~50dp diameter; focus ring inherits the configured accent |
| `SwitchFooterHints`  | Bottom bar: gamepad status indicator, gray 11–12sp                                                                                                                        |
| `SwitchDialog`       | Centered modal for dialogs opened outside gameplay: scrim, box ~40% width, radius 12–16dp, bg `#3A3A3C`, header icon+title 18sp, rows 48–52dp with icon+text, focused row = accent border outline |
| Fullscreen gameplay modal | Presentation contract for every interactive surface owned by `GameActivity`: opaque edge-to-edge Switch screen with normal screen header/content/footer structure and no popup shell; implemented with a dialog/overlay lifecycle so the running game remains alive underneath |
| `SwitchFocusBorder`  | Dynamic drawable: accent 2–3dp stroke, transparent fill, for focus indication                                                                                             |
| `SfxManager`         | SoundPool wrapper: focus-move tick, select, back, panel open/close; CC0/generated only; volume respect; toggle in settings                                                |
| `ThemeManager`       | Runtime dark/light switch, persists preference, applies tokens above                                                                                                      |

---

## 4. Screen-by-Screen Mapping

| Screen                    | Switch Style Applied                                                                                                                                          | Notes                                                                  |
| ------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------- |
| Splash                    | **HylianBox gold/green palette** (Dolfi original art), same structural layout as NS Launcher splash (flanking iconic shapes + two-line logo "Hylian" / "BOX") | No Nintendo IP (no Joy-Con shapes, no Nintendo logos)                  |
| Library Home              | `SwitchHomeRow` + `SwitchAllGamesCard` + `SwitchDock` + `SwitchFooterHints`                                                                                   | Vanilla games first, then store hacks                                  |
| Todos os Jogos (Grid)     | `SwitchGridScreen`                                                                                                                                            | All entries together (vanilla + hacks + seeds), search/filter          |
| Store                     | Fullscreen grid with Switch cards                                                                                                                             | Store hacks as Switch cards; detail bottom sheet → SwitchDialog style  |
| Settings                  | `SettingsActivity` fullscreen with category navigation + sections (incl. "Aparência": theme, interface sounds, accent color)                                  | Appearance options moved from the removed home quick-options panel     |
| RetroAchievements         | `SwitchGridScreen` (games with RA), `SwitchDialog` (detail outside gameplay), fullscreen gameplay modal (in-game achievements/leaderboards)                   | Achievement unlock/progress remains a compact non-interactive toast    |
| GameActivity In-Game Menu | Fullscreen gameplay modals for menu and every interactive descendant (Auto-Ocarina picker, Item Tracker, RA lists, selectors, capture choices, confirmations and editors); compact overlays only for transient status | **RadialGamePad touch layout FROZEN (Rule 14)** — only chrome restyled |
| Gallery                   | `SwitchGridScreen`-style gallery                                                                                                                              | View / share / delete captures                                         |

---

## 5. Fullscreen Modal Surfaces During Gameplay

- **Scope is behavioral, not class-name based:** any surface that accepts focus, selection, scrolling, text entry, confirmation, or another user action while a game is running is an interactive gameplay surface, including new surfaces added later.
- **Lifecycle:** keep it hosted by `GameActivity` as a modal `Dialog`, `DialogFragment`, or equivalent overlay. Opening it must not launch a replacement Activity, recreate `GameActivity`, unload the core, or discard the active ROM/save/RA/tracker state.
- **Visual result:** cover the entire immersive game window with an opaque themed background. Use the same Switch screen hierarchy, safe-area/inset handling, typography, focus border, controller traversal and footer/action hints as normal fullscreen screens.
- **Forbidden popup cues:** no scrim, translucent window, visible gameplay around/behind the content, centered card, floating elevation, rounded outer shell, outside margin, or outside-tap dismissal. `MATCH_PARENT` on the window alone is insufficient if its content still looks like a centered dialog.
- **Navigation:** Back dismisses only the topmost surface and restores focus to its parent or to gameplay. Nested choices remain in the same modal stack; they must not close/recreate the game session.
- **Transient exception:** achievement unlock/progress toasts, Auto-Ocarina playback-note/progress HUD, recording indicators, and brief loading/error/status messages remain compact, non-interactive, self-dismissing overlays. They do not receive focus, block controller input beyond their explicitly documented behavior, or adopt fullscreen presentation.
- **System-owned exception:** Android permission/consent UI may retain the platform presentation because the app does not control that window.

---

## 6. Sound Rules

- **Required SFX:** focus-move ("toc"), select, back, panel open, panel close.
- **Source:** CC0 or synthesized only. **NEVER extract from NS_Launcher APK** (copyright).
- **Storage:** `res/raw/sfx_focus_move.ogg`, `sfx_select.ogg`, `sfx_back.ogg`, `sfx_panel_open.ogg`, `sfx_panel_close.ogg`.
- **Volume:** Respects system media volume; mute toggle in Settings > Appearance.
- **Implementation:** `SfxManager` (SoundPool) — low latency, preloaded.

---

## 7. Licensing Guard

- UI is an **original native implementation** inspired by the Switch aesthetic.
- **NEVER commit Nintendo assets/fonts/sounds** or files extracted from the NS_Launcher APK (extends Hard Rule 2).
- FLauncher is GPLv3 — consulting its code is allowed; this project stays GPLv3.
- All generated assets (Dolfi) are original, CC0/public domain or GPL-3.0 compatible.

---

## 8. i18n / No-Emoji

- Hard Rules 7–8 still apply: every user-facing string in `strings.xml` (pt-BR default `values/`, `values-en/`, `values-es/`), zero hardcoded strings, no emojis in code/resources.

### Gameplay control overlays

- **Standard — Controls** keeps the frozen RadialGamePad/ButtonStick geometry unchanged.
- **Pro — Touch Areas** uses normalized hit regions measured from `mapeamento.png`. The reference's white borders, labels and button shapes are annotations only and must never be drawn.
- Pro renders only the reference's gray, red, yellow, purple, blue and green outer-edge glows. The active region intensifies its glow; relative analog and C-Left/C-Down/C-Right/A/B drags show a movable feedback dot.
- DPAD is directional swipe; double-tapping the relative analog uses the same Auto-Z toggle behavior as Standard.

---

## 9. Asset Delivery (Dolfi)

All icons/covers generated by **Dolfi** (see `.agents/dolfi.md`):
- App icon (deferred — reuses Ludere launcher icons for now)
- Hack category icons, RA trophy/leaderboard icons, Switch dock icons, focus assets
- PNG cover placeholders for hacks without `coverImageUrl`
- Zelda-gold splash artwork
- Gallery/capture icons (`ic_gallery`, `ic_screenshot`, `ic_record`, `ic_stop`)

**Style reference:** Dark surface `#1B1B1B` / Switch background `#2D2D2D`; all interactive color comes from the user-selected accent. Fixed brand/art colors are restricted to non-interactive artwork such as the splash. Clean, bold, simple geometry; near-square corners (4–6dp); avoid intricate details that conflict with generous component sizing.
