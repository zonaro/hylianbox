## Plan: FPS Dual-Analog Input-Only OoT+MM

TL;DR: Adaptar o plano FPS dual-analogico para as regras HylianBox como recurso somente-input (sem RAM write, sem patch ROM, sem Ship of Harkinian, sem Lua). Esquerdo move / direito mira via mapeamento existente mupen64plus alt-map. Fora do FPS, o mesmo stick direito move a camera livremente sem deteccao de estado via RAM — o mapeamento e unico e vale nos dois modos. Vale para OoT + MM vanilla e hacks com fallback generico. Opt-in ON por padrao, bloqueado em RA Hardcore. Touch reusa layout congelado; fisico separa sticks.

**Steps**

Fase 0 — Validacao tecnica (paralelo)
1. Confirmar em `config.xml` que `mupen64plus-alt-map=True` mapeia `MOTION_SOURCE_ANALOG_RIGHT` para C-buttons e `ANALOG_LEFT` para analogico N64. Validar em OoT/MM vanilla: (a) em primeira pessoa o core ignora `ANALOG_LEFT` vindo do stick esquerdo (limite conhecido, sem workaround nesta fase); (b) em terceira pessoa, `ANALOG_RIGHT` (C-buttons) move a camera livremente — mapear em OoT (C-esq/dir giram, C-cima entra em mira, C-baixo recentra) e em MM (mesma base, validar bolha Deku/bumerangue Zora). Registrar diferencas OoT vs MM como presets de sensibilidade.
2. Mapear familias `OOT` / `MM` / `CUSTOM` via `BaseRomLibrarySource` + `HackLibraryEntry.family` + IDs `vanilla_<crc32>` vs `canonicalId`. Definir fallback generico para hacks. Definir que "modo FPS" neste plano e toggle manual (sem leitura de RAM): input-only nao le `stateFlags`/camera, entao nao ha deteccao automatica de primeira pessoa.

Fase 1 — Settings, i18n, Dashboard (depends on 1-2)
3. Novas prefs em `utils/CorePrefs.kt` seguindo padrao `pref_tracker_auto_tracking`: `pref_fps_dual_analog` (bool, default false), `pref_fps_sensitivity` (float, mira em 1a pessoa), `pref_fps_invert_y` (bool), `pref_fps_deadzone` (float), `pref_free_camera` (bool, default true, so efetivo quando dual-analog ON — controla camera livre em 3a pessoa). Guard: se `getRaHardcore` true, dual-analog + free-camera forcados OFF com aviso SwitchDialog. Precedencia: quando dual-analog ON, separacao de sticks prevalece sobre `button_stick_enabled` (sem soma esq+dir); quando OFF, comportamento ButtonStick legado inalterado.
4. Strings em `values/strings.xml` (pt-BR) + `values-en/` + `values-es/` (Regra 8, sem hardcoded, sem emoji). Termos: Modo FPS, Sensibilidade da mira, Inverter Y, Zona morta, Camera livre (analógico direito).
5. Paridade Dashboard em `dashboard/server/SettingsRoutes.kt` + mesma validacao/persistencia/efeitos (Regra 17). Sensivel: nada sensivel aqui, sem write-only.

Fase 2 — Fisico dual-analog + camera livre (depends on 3)
6. Estender `input/ControllerInput.kt` `processMotionEvent`: quando dual-analog ON e Hardcore OFF, esquerdo (`AXIS_X/Y`) sempre para `MOTION_SOURCE_ANALOG_LEFT`; direito (`AXIS_Z/RZ`) sempre para `MOTION_SOURCE_ANALOG_RIGHT` com sensibilidade/inversao/deadzone de mira. Nao combinar sticks como no ButtonStick ON. Sem deteccao de primeira pessoa (input-only nao le RAM): o mesmo mapeamento vale nos dois modos — em 1a pessoa o core interpreta como mira, em 3a pessoa como camera livre via C-buttons (C-esq/dir giram, C-cima mira, C-baixo recentra). Se `pref_free_camera` OFF, direito so atua em 1a pessoa por toggle manual; se ON (default), atua sempre. Manter `menuCallback`, `EXCLUDED_KEYS`, `KEYCOMBO_MENU`, `getPort`.
7. Estender `input/N64ControllerMapping.kt` `Profile` com `fpsEnabled` + `freeCameraEnabled` sem quebrar `currentProfile` nem `n64ControlForPhysicalKey` / `rightStickControlsN64Analog`. Reuso em `views/GamepadTesterView.kt` para visualizar sem iniciar core (mostrar ANALOG_LEFT + ANALOG_RIGHT simultaneos).

Fase 3 — Touch sem quebrar layout congelado (depends on 3, parallel with 6)
8. STANDARD: sem novas posicoes (Regra 14). Camera livre em 3a pessoa no touch JA existe via botoes C (`gamepad/GamePadConfig.kt` BUTTON_L1/R1/X/Y + `gamepad/StickButton.kt` + `gamepad/GamePad.kt`): tap em C-esq/dir gira, C-baixo recentra. Nada a criar — apenas garantir que com dual-analog ON o tuning de sensibilidade/deadzone do stick direito fisico nao quebre os botoes C touch, e que `FloatingJoystick.kt` (esquerdo) continue com prioridade em multitouch via `GamepadOverlayLayout` (StickButton vira botao puro com 2+ dedos). Nenhum `PadPlacement` novo. `gamepad/RightTapZone.kt` legado nao e usado para camera livre (so `targetKeyCode` unico, sem analogico).
9. AREA (Pro): camera livre + movimento simultaneo JA suportados por multitouch real em `gamepad/AreaOverlayView.kt` (estados por `pointerId`: ANALOG + BUTTON_STICK + TOUCH). Expor dual-analog/free-camera como tuning de `analogSensitivity`, `stickSensitivity`, `autoZEnabled` + `AreaControlLayout` existente. Sem novas `AreaZone`.
10. Orquestrar em `viewmodels/GameActivityViewModel.kt` `setupGamePads`: aplicar modo `ControlOverlayMode` (`gamepad/ControlOverlayMode.kt`) + `overlay_scale` + prefs dual-analog/free-camera. Respeitar invariantes: overlay INVISIBLE nunca GONE, criacao apos primeiro layout, recreate total em perda de GL, `super.onDestroy` antes de dispose. Menu Controles ganha toggles FPS + Camera livre seguindo padrao `button_stick`/`auto_z` em `buildMenuSections`.

Fase 4 — Deteccao e fallback (depends on 6-10)
11. Resolver jogo via `repositories/GameRomResolver.kt`: `vanilla_*` para base importada, senao `Storage.rom(hackId/canonicalId)`. Familia decide preset de sensibilidade (mira FPS + camera livre podem ter ganhos distintos OoT vs MM); `CUSTOM`/desconhecido usa preset generico. Sem leitura de RAM para esta decisao (input-only).
12. Sem alteracao em `retroview/RetroView.kt` alem de `getCoreVariables` existente. Regra 10 mantida: ROM via `gameFilePath`, `config_load_bytes=false`. Sem mudanca de core variables para camera livre (alt-map ja expoe C-buttons no stick direito).

Fase 5 — Testes, docs, QA (depends on 11)
13. Unit JVM: mapeamento fisico FPS, deadzone/inversao, Profile, fallback familia, guard Hardcore. Fixtures sinteticas, sem ROM real (Regras 1-2).
14. Instrumentado + manual: `GamepadTesterView`, OoT vanilla mira 1a pessoa, MM vanilla (arco/hookshot/bolha Deku/bumerangue Zora), 1 hack OoT + 1 hack MM em fallback, Hardcore ON bloqueia, Dashboard paridade, i18n sem chaves faltantes.
15. Docs Wally: KDoc em classes/funcoes publicas, README/AGENTS se expor setting. QA Chululu: chrome Switch UI (`VISUAL_IDENTITY.md`), sem mudanca de layout touch.

**Relevant files**
- `app/src/main/java/br/com/redclaw/hylianbox/input/ControllerInput.kt` — separar sticks em `processMotionEvent`, aplicar sensibilidade/inversao/deadzone
- `app/src/main/java/br/com/redclaw/hylianbox/input/N64ControllerMapping.kt` — estender `Profile`, `currentProfile`, `rightStickControlsN64Analog`
- `app/src/main/java/br/com/redclaw/hylianbox/input/InputMapper.kt` — reusar `mapKeyCode`, sem mudar alt-map
- `app/src/main/java/br/com/redclaw/hylianbox/gamepad/FloatingJoystick.kt` — tuning `sensitivity`, `maxReachPx`
- `app/src/main/java/br/com/redclaw/hylianbox/gamepad/AreaOverlayView.kt` — tuning `analogSensitivity`, `stickSensitivity`
- `app/src/main/java/br/com/redclaw/hylianbox/gamepad/GamePadConfig.kt` — FROZEN, nao adicionar `PadPlacement`
- `app/src/main/java/br/com/redclaw/hylianbox/gamepad/ControlOverlayMode.kt` — reusar STANDARD/AREA
- `app/src/main/java/br/com/redclaw/hylianbox/viewmodels/GameActivityViewModel.kt` — `setupGamePads`, menu overlay_scale/control_mode, guard Hardcore
- `app/src/main/java/br/com/redclaw/hylianbox/utils/CorePrefs.kt` — novas prefs FPS + `getRaHardcore` guard
- `app/src/main/java/br/com/redclaw/hylianbox/retroview/RetroView.kt` — sem mudanca de carga ROM, so `getCoreVariables`
- `app/src/main/java/br/com/redclaw/hylianbox/repositories/GameRomResolver.kt` — deteccao vanilla vs hack
- `app/src/main/java/br/com/redclaw/hylianbox/views/GamepadTesterView.kt` — espelhar mapeamento sem core
- `app/src/main/res/values/config.xml` — `config_variables` alt-map, `config_left_analog`
- `app/src/main/res/values/strings.xml` + `values-en/` + `values-es/` — i18n FPS
- `app/src/main/java/br/com/redclaw/hylianbox/dashboard/server/SettingsRoutes.kt` — paridade Dashboard

**Verification**
1. `./gradlew :app:testDebugUnitTest` para mapeamento, prefs, fallback, guard Hardcore
2. `./gradlew :app:connectedAndroidTest` para import base, resolver, carga via `gameFilePath`
3. Manual Gamepad Tester: esquerdo move analogico, direito pulsa C-buttons, com/sem FPS
4. Manual OoT vanilla + MM vanilla em 1a pessoa: direito mira, esquerdo pronto para 3a pessoa; confirmar sem walk-while-aim (limite documentado)
5. Manual 1 hack OoT + 1 hack MM: fallback generico sem crash
6. Manual Hardcore ON: FPS bloqueado com aviso; OFF: liberado
7. Manual Dashboard: mesmas opcoes/validacao que Settings nativo
8. Checar i18n: zero chaves faltantes en/es, zero strings hardcoded, sem emoji

**Decisions**
- Abordagem: somente input (sem RAM write, sem patch BPS/Xdelta, sem SoH, sem Lua). Vetor relativo a yaw da camera do plano original descartado pois exige escrita de posicao.
- Escopo: tudo com fallback seguro. Vanilla OoT/MM preset dedicado; hacks/CUSTOM preset generico. Sem offsets de RAM por versao.
- Controles: ambos touch + fisico. Touch sem novo layout (Regra 14).
- Hardcore/opt-in: opt-in default OFF (Regra 15), bloqueado em Hardcore ON mesmo sendo input-only (conservador ate validacao RAdmin).
- Excluido: Ship of Harkinian `z_player.c` / `z_camera.c`, scripts RetroArch/BizHawk, MIPS ASM, patcher `BpsApplier`/`XdeltaApplier` para movimento, `AutoTrackerPoller`/`SaveContextWriter` writes, `getMemoryRegion` writes.
- MM coberto: mesmos sticks; diferencas (bolha Deku, bumerangue Zora, formas) tratadas como mesmo perfil input, sem logica por forma.
- Camera livre: fora do FPS, o mesmo stick direito move a camera via C-buttons sem deteccao de RAM. Mapeamento unico vale nos dois modos; `pref_free_camera` (default ON) controla se atua em 3a pessoa. Touch STANDARD ja tem isso via botoes C; AREA via multitouch existente.

**Further Considerations**
1. Walk-while-aim real exigiria RAM-assist estilo tracker ou patch MIPS por versao, com custo RA hash (Regra 21), validacao tripla CRC e risco anti-colisao. Propor como plano separado se desejado.
2. Presets por jogo (OoT vs MM sensibilidade distinta, mira vs camera livre) podem ser calibrados apos feedback de hardware; manter default unico nesta fase.
