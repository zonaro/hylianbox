/* HylianBox — landing page logic
   User-facing copy in pt-BR, English and Spanish.
*/

const I18N = {
  pt: {
    "meta.title": "HylianBox — Ocarina, Majora e centenas de hacks no seu Android",
    "meta.desc": "O jeito mais fácil de jogar Ocarina of Time, Majora's Mask e centenas de hacks no Android. Loja com patch automático, RetroAchievements, Item Tracker, Auto-Ocarina, controles com ButtonStick e Auto-Z, gravador, dashboard no PC e mais. Grátis, GPL-3.0, sem ROMs inclusas.",
    "nav.features": "Recursos", "nav.catalog": "Aventuras", "nav.download": "Baixar",
    "hero.title.pre": "Hylian", "hero.title.accent": "Box",
    "hero.subtitle": "O jeito mais fácil de jogar Ocarina of Time, Majora's Mask e centenas de hacks no Android. Conquistas, tracker automático, Auto-Ocarina e controles que realmente funcionam — tudo a um toque.",
    "hero.cta": "Baixar para Android", "hero.secondaryCta": "Ver todos os recursos",
    "hero.badge1": "Grátis e sem anúncios", "hero.badge2": "PT-BR · EN · ES", "hero.badge3": "Sem ROMs inclusas",
    "features.title": "Tudo que você queria num Zelda de bolso", "features.sub": "Menos gambiarra, mais aventura. Cada recurso foi feito para você jogar melhor, não para configurar mais.",
    "start.title": "Comece em 3 toques", "start.sub": "Você só precisa da sua ROM legal. O resto o HylianBox resolve.",
    "start.step1": "Baixe o HylianBox no seu Android.",
    "start.step2": "Importe sua cópia legal de Ocarina of Time ou Majora's Mask (.z64, .v64 ou .n64).",
    "start.step3": "Escolha uma aventura na Loja e dê Play — patch e validação são automáticos.",
    "catalog.title": "Encontre sua próxima aventura", "catalog.sub": "Uma prévia do catálogo que você navega direto no app — com capa, autor e detalhes.",
    "catalog.loading": "Buscando aventuras...", "catalog.error": "Não foi possível mostrar as aventuras agora.", "catalog.retry": "Tentar novamente", "catalog.count": "aventuras para explorar",
    "card.author": "Por", "card.baseRom": "Jogo necessário", "card.coverFallback": "Aventura Zelda 64",
    "popup.close": "Fechar", "popup.author": "Criado por", "popup.version": "Versão", "popup.baseRom": "ROM base", "popup.tags": "Tags", "popup.screenshots": "Capturas", "popup.videos": "Vídeos", "popup.links": "Links do criador", "popup.changelog": "Novidades", "popup.compatibility": "Compatibilidade", "popup.completion": "Status", "popup.supportedGames": "Jogo", "popup.lastUpdated": "Atualizado em", "popup.source": "Fonte", "popup.noLinks": "Nenhum link externo disponível.",
    "download.title": "Baixe e jogue em 2 minutos", "download.sub": "Grátis, sem anúncios, sem ROMs inclusas. Atualizações direto do GitHub.",
    "download.lead": "Instale o APK agora ou adicione ao Obtanium e receba cada atualização automaticamente — sem precisar voltar ao site.", "download.versionLabel": "Versão mais recente:", "download.button": "Baixar APK",
    "download.obtainium": "Adicionar ao Obtanium", "download.obtainiumHint": "Atualizações automáticas pelo GitHub",
    "download.or": "ou", "download.note": "O HylianBox não inclui nem distribui ROMs. Após instalar, importe sua cópia legal de Ocarina of Time ou Majora's Mask em Configurações → Importar ROM base.",
    "legal.title": "Jogue respeitando os criadores", "legal.text": "HylianBox não distribui, baixa nem inclui ROMs. Usa apenas as ROMs que você importa legalmente. É software livre GPL-3.0, não afiliado à Nintendo. Todo o catálogo dá crédito aos autores originais.",
    "footer.text": "HylianBox é software livre sob GPL-3.0. Não afiliado à Nintendo.", "footer.source": "Ver código-fonte", "footer.license": "Licença GPL-3.0", "footer.releases": "Todas as versões",
    "f.store.title": "Loja de Hacks gigante", "f.store.desc": "Centenas de aventuras de OoT e MM a um toque. Escolha, baixe e jogue — o app baixa o patch, confere tudo e já deixa pronto. Funciona com BPS, IPS e xDelta, até dentro de ZIP.",
    "f.store.points": ["Catálogo curado + Hylian Modding com crédito ao autor", "Download e patch automático, sem mexer em arquivo", "BPS, IPS, xDelta — compactado ou não"],
    "f.retro.title": "Jogue OoT e MM com conquistas de verdade", "f.retro.desc": "Conecte sua conta RetroAchievements e transforme cada dungeon em caçada por troféus. Conquistas pipocam na tela, com pontos e selo — e leaderboards ficam no menu, sem atrapalhar a luta.",
    "f.retro.points": ["Desbloqueios com badge, som e notificação", "Leaderboards no menu do jogo, nunca sobre a gameplay", "Perfil completo com avatar, progresso e histórico"],
    "f.tracker.title": "Item Tracker automático", "f.tracker.desc": "Fazendo randomizer? O tracker lê seu save e marca tudo sozinho — itens, dungeons, músicas e upgrades. Funciona também no OoT e MM vanilla para acompanhar sua jornada sem planilha.",
    "f.tracker.points": ["5 abas: Itens, Locais, Músicas, Dicas e Upgrades", "Automático para randomizer, manual quando quiser", "Ícones extraídos da sua própria ROM + timer integrado"],
    "f.ocarina.title": "Auto-Ocarina — nunca mais trave numa música", "f.ocarina.desc": "Esqueceu a Sun's Song no meio da dungeon? Abra o menu, escolha a música e deixe o HylianBox tocar por você. Um HUD mostra cada nota — cancele quando quiser.",
    "f.ocarina.points": ["12 músicas de OoT + 11 de MM + customs do hack", "Toca sozinho (~330 ms por nota), você interrompe a qualquer hora", "Detecta se é OoT ou MM e esconde quando não precisa"],
    "f.controls.title": "Controles que entendem Zelda 64", "f.controls.desc": "Layout medido para OoT e MM, com analógico flutuante e dois truques que mudam tudo. Ajuste tamanho e sensibilidade do seu jeito.",
    "f.controls.points": ["ButtonStick: segure um botão C e arraste — ele vira analógico com 1 dedo", "Auto-Z: toque duplo no analógico segura o Z para travar a mira", "Sensibilidade e escala dos botões ajustáveis"],
    "f.gamepad.title": "Controle físico? Teste aqui mesmo", "f.gamepad.desc": "Conecte seu controle Bluetooth/USB e teste na hora, sem abrir jogo. Veja cada botão e analógico mapeado para o N64 antes de jogar — sem surpresa na hora do boss.",
    "f.gamepad.points": ["Compatível com a maioria dos controles", "Tela dedicada de teste, sem precisar iniciar core", "Mesmo mapeamento da gameplay"],
    "f.capture.title": "Grave e capture sem sair do jogo", "f.capture.desc": "Aquele puzzle perfeito ou boss sem dano merece ser guardado. Tire fotos (com ou sem botões na tela) e grave vídeos direto pelo menu de pausa. Tudo fica na galeria local.",
    "f.capture.points": ["Fotos instantâneas em 1 toque", "Gravação de gameplay com áudio (MediaProjection)", "Galeria local: ver, compartilhar ou apagar"],
    "f.dashboard.title": "Dashboard no PC, sem instalar nada", "f.dashboard.desc": "Ligue o servidor no celular e abra o navegador no PC — mesma Wi-Fi. Gerencie sua coleção, faça backup dos saves e até jogue por streaming. Nada para instalar no computador.",
    "f.dashboard.points": ["Acesse pelo navegador: coleção, saves e configurações", "Backup e restauração em ZIP com 1 clique", "Streaming via WebRTC para jogar na tela grande"],
    "f.saves.title": "Save States & Avanço Rápido", "f.saves.desc": "Salve a qualquer segundo e volte exatamente dali. Precisa farmar ou passar diálogo lento? Acelere em 2× com um toque — e volte ao normal quando quiser.",
    "f.saves.points": ["Save State instantâneo + Load State", "Fast Forward 2× para grind e cutscenes", "Saves separados por jogo, sem misturar"],
    "f.library.title": "Sua biblioteca vanilla, intacta", "f.library.desc": "Suas ROMs originais de OoT e MM ficam na biblioteca, jogáveis a qualquer hora. A ROM original nunca é alterada — o patch acontece numa cópia segura em cache.",
    "f.library.points": ["Jogue OoT e MM puros direto da biblioteca", "ROM original sempre preservada", "Validação por CRC antes de aplicar patch"],
    "f.import.title": "Importação em segundos", "f.import.desc": "Só precisa da sua ROM legal (.z64, .v64 ou .n64). O app normaliza o formato, valida o cabeçalho e guarda — pronto para qualquer hack.",
    "f.import.points": ["Configurações → Importar ROM base", "Normalização automática de byte-order", "Validação por gameCode + versão + CRC32"],
    "f.extras.title": "Tudo offline, tudo seu", "f.extras.desc": "Depois de instalar, jogue sem internet. Seus saves ficam no aparelho, com backup local e sem telemetria escondida. Visual Nintendo Switch, em PT-BR, English e Español.",
    "f.extras.points": ["Jogue offline após instalar", "Backup local em ZIP, sem nuvem obrigatória", "Interface Switch + sons suaves, sem poluição"]
  },
  en: {
    "meta.title": "HylianBox — Ocarina, Majora & hundreds of hacks on Android",
    "meta.desc": "The easiest way to play Ocarina of Time, Majora's Mask and hundreds of hacks on Android. Store with auto-patching, RetroAchievements, Item Tracker, Auto-Ocarina, ButtonStick & Auto-Z controls, recorder, PC dashboard and more. Free, GPL-3.0, no ROMs bundled.",
    "nav.features": "Features", "nav.catalog": "Adventures", "nav.download": "Download",
    "hero.title.pre": "Hylian", "hero.title.accent": "Box",
    "hero.subtitle": "The easiest way to play Ocarina of Time, Majora's Mask and hundreds of hacks on Android. Achievements, auto-tracker, Auto-Ocarina and controls that actually work — one tap away.",
    "hero.cta": "Get it for Android", "hero.secondaryCta": "See all features",
    "hero.badge1": "Free & no ads", "hero.badge2": "PT-BR · EN · ES", "hero.badge3": "No bundled ROMs",
    "features.title": "Everything you wanted in a pocket Zelda", "features.sub": "Less tinkering, more adventure. Every feature is built to make you play better, not configure more.",
    "start.title": "Start in 3 taps", "start.sub": "Just bring your legal ROM. HylianBox handles the rest.",
    "start.step1": "Download HylianBox on your Android device.",
    "start.step2": "Import your legal Ocarina of Time or Majora's Mask ROM (.z64, .v64 or .n64).",
    "start.step3": "Pick an adventure in the Store and hit Play — patching and validation are automatic.",
    "catalog.title": "Find your next adventure", "catalog.sub": "A preview of the catalog you browse right inside the app — with cover, author and details.",
    "catalog.loading": "Finding adventures...", "catalog.error": "We could not show the adventures right now.", "catalog.retry": "Try again", "catalog.count": "adventures to explore",
    "card.author": "By", "card.baseRom": "Game needed", "card.coverFallback": "Zelda 64 adventure",
    "popup.close": "Close", "popup.author": "By", "popup.version": "Version", "popup.baseRom": "Base ROM", "popup.tags": "Tags", "popup.screenshots": "Screenshots", "popup.videos": "Videos", "popup.links": "Creator links", "popup.changelog": "Changelog", "popup.compatibility": "Compatibility", "popup.completion": "Status", "popup.supportedGames": "Game", "popup.lastUpdated": "Updated", "popup.source": "Source", "popup.noLinks": "No external links available.",
    "download.title": "Download and play in 2 minutes", "download.sub": "Free, no ads, no bundled ROMs. Updates straight from GitHub.",
    "download.lead": "Install the APK now or add it to Obtanium and get every update automatically — no need to come back to the site.", "download.versionLabel": "Latest version:", "download.button": "Download APK",
    "download.obtainium": "Add to Obtanium", "download.obtainiumHint": "Automatic updates via GitHub",
    "download.or": "or", "download.note": "HylianBox does not include or distribute ROMs. After installing, import your legal Ocarina of Time or Majora's Mask copy in Settings → Import Base ROM.",
    "legal.title": "Play with respect for creators", "legal.text": "HylianBox does not distribute, download or include ROMs. It only uses ROMs you legally import. Free software under GPL-3.0, not affiliated with Nintendo. Every catalog entry credits its original author.",
    "footer.text": "HylianBox is free software under GPL-3.0. Not affiliated with Nintendo.", "footer.source": "View source code", "footer.license": "GPL-3.0 license", "footer.releases": "All releases",
    "f.store.title": "Huge Hack Store", "f.store.desc": "Hundreds of OoT and MM adventures one tap away. Pick, download and play — the app fetches the patch, validates everything and gets it ready. Works with BPS, IPS and xDelta, even inside ZIP.",
    "f.store.points": ["Curated catalog + Hylian Modding with author credit", "Automatic download & patching, no file juggling", "BPS, IPS, xDelta — zipped or not"],
    "f.retro.title": "Play OoT & MM with real achievements", "f.retro.desc": "Link your RetroAchievements account and turn every dungeon into a trophy hunt. Achievements pop with badge and points — leaderboards stay in the menu, never over gameplay.",
    "f.retro.points": ["Popups with badge, sound and notification", "Leaderboards in the game menu, never over action", "Full profile with avatar, progress and history"],
    "f.tracker.title": "Automatic Item Tracker", "f.tracker.desc": "Running a randomizer? The tracker reads your save and checks everything off — items, dungeons, songs and upgrades. Also works for vanilla OoT & MM to follow your journey without spreadsheets.",
    "f.tracker.points": ["5 tabs: Items, Locations, Songs, Hints & Upgrades", "Automatic for randomizer, manual whenever you want", "Icons extracted from your own ROM + built-in timer"],
    "f.ocarina.title": "Auto-Ocarina — never get stuck on a song", "f.ocarina.desc": "Forgot Sun's Song mid-dungeon? Open the menu, pick the song and let HylianBox play it for you. A HUD shows each note — cancel anytime.",
    "f.ocarina.points": ["12 OoT songs + 11 MM songs + hack customs", "Auto-plays (~330 ms per note), interrupt anytime", "Auto-detects OoT vs MM and hides when not needed"],
    "f.controls.title": "Controls that understand Zelda 64", "f.controls.desc": "Layout measured for OoT & MM, with floating stick and two game-changers. Resize and tune sensitivity your way.",
    "f.controls.points": ["ButtonStick: hold a C button and drag — it becomes an analog stick with 1 finger", "Auto-Z: double-tap the stick area to hold Z and lock on", "Adjustable sensitivity and button scale"],
    "f.gamepad.title": "Physical controller? Test it right here", "f.gamepad.desc": "Plug your Bluetooth/USB pad and test instantly, without launching a game. See every button and stick mapped to N64 before you play — no boss-fight surprises.",
    "f.gamepad.points": ["Works with most controllers", "Dedicated test screen, no core needed", "Same mapping as gameplay"],
    "f.capture.title": "Record & capture without leaving the game", "f.capture.desc": "That perfect puzzle or no-damage boss deserves to be saved. Snap photos (with or without on-screen buttons) and record video straight from the pause menu. Everything stays in your local gallery.",
    "f.capture.points": ["Instant photos in one tap", "Gameplay recording with audio (MediaProjection)", "Local gallery: view, share or delete"],
    "f.dashboard.title": "PC Dashboard, nothing to install", "f.dashboard.desc": "Turn on the server on your phone and open the browser on your PC — same Wi-Fi. Manage your collection, back up saves and even stream. Nothing to install on the computer.",
    "f.dashboard.points": ["Browser access: collection, saves and settings", "One-click ZIP backup & restore", "WebRTC streaming to play on the big screen"],
    "f.saves.title": "Save States & Fast Forward", "f.saves.desc": "Save at any second and resume exactly there. Need to farm or skip slow dialogue? Speed up 2× with one tap — and go back to normal whenever you want.",
    "f.saves.points": ["Instant Save State + Load State", "2× Fast Forward for grinding and cutscenes", "Per-game saves, no mixing"],
    "f.library.title": "Your vanilla library, untouched", "f.library.desc": "Your original OoT & MM ROMs stay in the library, playable anytime. The original ROM is never modified — patching happens on a safe cached copy.",
    "f.library.points": ["Play pure OoT & MM straight from the library", "Original ROM always preserved", "CRC validation before patching"],
    "f.import.title": "Import in seconds", "f.import.desc": "Just need your legal ROM (.z64, .v64 or .n64). The app normalizes the format, validates the header and stores it — ready for any hack.",
    "f.import.points": ["Settings → Import Base ROM", "Automatic byte-order normalization", "Validation by gameCode + version + CRC32"],
    "f.extras.title": "All offline, all yours", "f.extras.desc": "After installing, play without internet. Your saves stay on device, with local backup and no hidden telemetry. Switch-style UI in PT-BR, English and Español.",
    "f.extras.points": ["Play offline after install", "Local ZIP backup, no mandatory cloud", "Switch UI + soft sounds, no clutter"]
  },
  es: {
    "meta.title": "HylianBox — Ocarina, Majora y cientos de hacks en Android",
    "meta.desc": "La forma más fácil de jugar Ocarina of Time, Majora's Mask y cientos de hacks en Android. Tienda con parcheo automático, RetroAchievements, Item Tracker, Auto-Ocarina, controles ButtonStick y Auto-Z, grabador, dashboard en PC y más. Gratis, GPL-3.0, sin ROMs incluidas.",
    "nav.features": "Recursos", "nav.catalog": "Aventuras", "nav.download": "Descargar",
    "hero.title.pre": "Hylian", "hero.title.accent": "Box",
    "hero.subtitle": "La forma más fácil de jugar Ocarina of Time, Majora's Mask y cientos de hacks en Android. Logros, tracker automático, Auto-Ocarina y controles que realmente funcionan — a un toque.",
    "hero.cta": "Descargar para Android", "hero.secondaryCta": "Ver todos los recursos",
    "hero.badge1": "Gratis y sin anuncios", "hero.badge2": "PT-BR · EN · ES", "hero.badge3": "Sin ROMs incluidas",
    "features.title": "Todo lo que querías en un Zelda de bolsillo", "features.sub": "Menos configuración, más aventura. Cada recurso está hecho para que juegues mejor, no para configurar más.",
    "start.title": "Empieza en 3 toques", "start.sub": "Solo necesitas tu ROM legal. HylianBox se encarga del resto.",
    "start.step1": "Descarga HylianBox en tu Android.",
    "start.step2": "Importa tu copia legal de Ocarina of Time o Majora's Mask (.z64, .v64 o .n64).",
    "start.step3": "Elige una aventura en la Tienda y dale a Jugar — el parcheo y la validación son automáticos.",
    "catalog.title": "Encuentra tu próxima aventura", "catalog.sub": "Una vista previa del catálogo que navegas directo en la app — con portada, autor y detalles.",
    "catalog.loading": "Buscando aventuras...", "catalog.error": "No podemos mostrar las aventuras ahora.", "catalog.retry": "Intentar de nuevo", "catalog.count": "aventuras para explorar",
    "card.author": "Por", "card.baseRom": "Juego necesario", "card.coverFallback": "Aventura Zelda 64",
    "popup.close": "Cerrar", "popup.author": "Por", "popup.version": "Versión", "popup.baseRom": "ROM base", "popup.tags": "Etiquetas", "popup.screenshots": "Capturas", "popup.videos": "Vídeos", "popup.links": "Enlaces del creador", "popup.changelog": "Cambios", "popup.compatibility": "Compatibilidad", "popup.completion": "Estado", "popup.supportedGames": "Juego", "popup.lastUpdated": "Actualizado", "popup.source": "Fuente", "popup.noLinks": "No hay enlaces externos disponibles.",
    "download.title": "Descarga y juega en 2 minutos", "download.sub": "Gratis, sin anuncios, sin ROMs incluidas. Actualizaciones directo de GitHub.",
    "download.lead": "Instala el APK ahora o añádelo a Obtanium y recibe cada actualización automáticamente — sin volver al sitio.", "download.versionLabel": "Última versión:", "download.button": "Descargar APK",
    "download.obtainium": "Añadir a Obtanium", "download.obtainiumHint": "Actualizaciones automáticas vía GitHub",
    "download.or": "o", "download.note": "HylianBox no incluye ni distribuye ROMs. Tras instalar, importa tu copia legal de Ocarina of Time o Majora's Mask en Ajustes → Importar ROM base.",
    "legal.title": "Juega respetando a los creadores", "legal.text": "HylianBox no distribuye, descarga ni incluye ROMs. Solo usa las ROMs que importas legalmente. Es software libre GPL-3.0, no afiliado a Nintendo. Cada entrada del catálogo da crédito a su autor original.",
    "footer.text": "HylianBox es software libre bajo GPL-3.0. No está afiliado a Nintendo.", "footer.source": "Ver código fuente", "footer.license": "Licencia GPL-3.0", "footer.releases": "Todas las versiones",
    "f.store.title": "Tienda de hacks gigante", "f.store.desc": "Cientos de aventuras de OoT y MM a un toque. Elige, descarga y juega — la app baja el parche, valida todo y lo deja listo. Funciona con BPS, IPS y xDelta, incluso dentro de ZIP.",
    "f.store.points": ["Catálogo curado + Hylian Modding con crédito al autor", "Descarga y parcheo automático, sin tocar archivos", "BPS, IPS, xDelta — comprimido o no"],
    "f.retro.title": "Juega OoT y MM con logros de verdad", "f.retro.desc": "Conecta tu cuenta de RetroAchievements y convierte cada mazmorra en caza de trofeos. Los logros saltan en pantalla con insignia y puntos — y los leaderboards quedan en el menú, sin tapar la acción.",
    "f.retro.points": ["Desbloqueos con insignia, sonido y notificación", "Leaderboards en el menú del juego, nunca sobre la partida", "Perfil completo con avatar, progreso e historial"],
    "f.tracker.title": "Item Tracker automático", "f.tracker.desc": "¿Jugando randomizer? El tracker lee tu partida y marca todo solo — objetos, mazmorras, canciones y mejoras. También funciona en OoT y MM vanilla para seguir tu aventura sin hojas de cálculo.",
    "f.tracker.points": ["5 pestañas: Objetos, Lugares, Canciones, Pistas y Mejoras", "Automático para randomizer, manual cuando quieras", "Iconos extraídos de tu propia ROM + temporizador integrado"],
    "f.ocarina.title": "Auto-Ocarina — nunca te quedes atascado en una canción", "f.ocarina.desc": "¿Olvidaste Sun's Song en medio de la mazmorra? Abre el menú, elige la canción y deja que HylianBox la toque por ti. Un HUD muestra cada nota — cancela cuando quieras.",
    "f.ocarina.points": ["12 canciones de OoT + 11 de MM + customs del hack", "Toca solo (~330 ms por nota), interrumpe cuando quieras", "Detecta si es OoT o MM y se oculta cuando no hace falta"],
    "f.controls.title": "Controles que entienden Zelda 64", "f.controls.desc": "Diseño medido para OoT y MM, con stick flotante y dos trucos que lo cambian todo. Ajusta tamaño y sensibilidad a tu gusto.",
    "f.controls.points": ["ButtonStick: mantén un botón C y arrastra — se vuelve stick analógico con 1 dedo", "Auto-Z: doble toque en el stick para mantener Z y fijar objetivo", "Sensibilidad y escala de botones ajustables"],
    "f.gamepad.title": "¿Mando físico? Pruébalo aquí mismo", "f.gamepad.desc": "Conecta tu mando Bluetooth/USB y pruébalo al instante, sin abrir juego. Ve cada botón y stick mapeado a N64 antes de jugar — sin sorpresas en el jefe.",
    "f.gamepad.points": ["Compatible con la mayoría de mandos", "Pantalla dedicada de prueba, sin iniciar core", "Mismo mapeo que en partida"],
    "f.capture.title": "Graba y captura sin salir del juego", "f.capture.desc": "Ese puzzle perfecto o jefe sin daño merece guardarse. Haz fotos (con o sin botones en pantalla) y graba vídeo directo desde el menú de pausa. Todo queda en la galería local.",
    "f.capture.points": ["Fotos instantáneas con un toque", "Grabación con audio (MediaProjection)", "Galería local: ver, compartir o borrar"],
    "f.dashboard.title": "Dashboard en PC, nada que instalar", "f.dashboard.desc": "Enciende el servidor en el móvil y abre el navegador en el PC — misma Wi-Fi. Gestiona tu colección, haz copia de saves e incluso juega por streaming. Nada que instalar en el ordenador.",
    "f.dashboard.points": ["Acceso por navegador: colección, saves y ajustes", "Copia ZIP con 1 clic y restauración", "Streaming WebRTC para jugar en pantalla grande"],
    "f.saves.title": "Save States y Avance Rápido", "f.saves.desc": "Guarda en cualquier segundo y vuelve exactamente ahí. ¿Necesitas farmear o saltar diálogo lento? Acelera a 2× con un toque — y vuelve a normal cuando quieras.",
    "f.saves.points": ["Save State instantáneo + Load State", "Avance rápido 2× para farmeo y cinemáticas", "Saves separados por juego, sin mezclar"],
    "f.library.title": "Tu biblioteca vanilla, intacta", "f.library.desc": "Tus ROMs originales de OoT y MM quedan en la biblioteca, jugables en cualquier momento. La ROM original nunca se modifica — el parcheo ocurre en una copia segura en caché.",
    "f.library.points": ["Juega OoT y MM puros directo de la biblioteca", "ROM original siempre preservada", "Validación CRC antes de parchear"],
    "f.import.title": "Importación en segundos", "f.import.desc": "Solo necesitas tu ROM legal (.z64, .v64 o .n64). La app normaliza el formato, valida la cabecera y la guarda — lista para cualquier hack.",
    "f.import.points": ["Ajustes → Importar ROM base", "Normalización automática de byte-order", "Validación por gameCode + versión + CRC32"],
    "f.extras.title": "Todo offline, todo tuyo", "f.extras.desc": "Tras instalar, juega sin internet. Tus partidas quedan en el dispositivo, con copia local y sin telemetría oculta. Interfaz estilo Switch en PT-BR, English y Español.",
    "f.extras.points": ["Juega offline tras instalar", "Copia local ZIP, sin nube obligatoria", "Interfaz Switch + sonidos suaves, sin desorden"]
  }
};

const FEATURE_KEYS = ["store", "retro", "tracker", "ocarina", "controls", "gamepad", "capture", "dashboard", "saves", "library", "import", "extras"];
const FEATURE_ICONS = { store: "a", retro: "f", tracker: "o", ocarina: "e", controls: "j", gamepad: "k", capture: "m", dashboard: "n", saves: "p", library: "i", import: "q", extras: "r" };
const LIVE_CATALOG_URL = "https://cdn.jsdelivr.net/gh/zonaro/hylianbox@main/catalog/catalog.json";
const FALLBACK_CATALOG_URL = "https://raw.githubusercontent.com/zonaro/hylianbox/main/catalog/catalog.json";
let currentLang = "pt";

function detectLang() {
  const stored = localStorage.getItem("hylianbox_lang");
  if (stored && I18N[stored]) return stored;
  const nav = (navigator.language || "pt-BR").slice(0, 2).toLowerCase();
  return I18N[nav] ? nav : "pt";
}
function t(key) { return (I18N[currentLang] && I18N[currentLang][key]) || I18N.en[key] || key; }
function setLang(lang) {
  if (!I18N[lang]) return;
  currentLang = lang;
  localStorage.setItem("hylianbox_lang", lang);
  document.documentElement.lang = lang;
  renderStatic(); renderFeatures(); updateLangButtons();
  if (window.__catalogData) renderCatalog(window.__catalogData);
  if (window.__activeHack) openHackPopup(window.__activeHack);
  refreshGlyphs();
}
function updateLangButtons() {
  document.querySelectorAll(".lang-btn").forEach(function (btn) {
    const active = btn.dataset.lang === currentLang;
    btn.classList.toggle("active", active);
    btn.setAttribute("aria-pressed", active ? "true" : "false");
  });
}
function renderStatic() {
  document.title = t("meta.title");
  const metaDesc = document.querySelector('meta[name="description"]');
  if (metaDesc) metaDesc.setAttribute("content", t("meta.desc"));
  document.querySelectorAll("[data-i18n]").forEach(function (el) { el.textContent = t(el.dataset.i18n); });
}
function renderFeatures() {
  const grid = document.getElementById("feature-grid");
  if (!grid) return;
  grid.innerHTML = "";
  FEATURE_KEYS.forEach(function (key) {
    const card = document.createElement("article"); card.className = "feature-card";
    const icon = document.createElement("div"); icon.className = "feature-icon"; icon.textContent = FEATURE_ICONS[key]; icon.setAttribute("aria-hidden", "true");
    const title = document.createElement("h3"); title.textContent = t("f." + key + ".title");
    const desc = document.createElement("p"); desc.textContent = t("f." + key + ".desc");
    const points = document.createElement("ul"); points.className = "feature-points";
    const entries = t("f." + key + ".points");
    (Array.isArray(entries) ? entries : []).forEach(function (entry) { const item = document.createElement("li"); item.textContent = entry; points.appendChild(item); });
    card.append(icon, title, desc, points); grid.appendChild(card);
  });
}
function coverPlaceholder(name) {
  const placeholder = document.createElement("div"); placeholder.className = "placeholder";
  const icon = document.createElement("div"); icon.className = "ph-icon"; icon.textContent = "a";
  const label = document.createElement("div"); label.className = "ph-name"; label.textContent = name || t("card.coverFallback");
  placeholder.append(icon, label); return placeholder;
}
function youtubeId(url) {
  if (!url || typeof url !== "string") return null;
  var m = url.match(/(?:v=|youtu\.be\/|embed\/|shorts\/)([A-Za-z0-9_-]{11})/);
  if (m) return m[1];
  m = url.match(/youtube\.com\/watch\?.*v=([A-Za-z0-9_-]{11})/);
  return m ? m[1] : null;
}
function youtubeEmbedUrl(url) {
  var id = youtubeId(url);
  return id ? "https://www.youtube-nocookie.com/embed/" + id + "?rel=0&modestbranding=1" : null;
}
function isYoutubeUrl(url) {
  if (!url || typeof url !== "string") return false;
  return /youtube\.com|youtu\.be|youtube-nocookie\.com/i.test(url);
}
function renderCatalog(data) {
  window.__catalogData = data;
  var state = document.getElementById("catalog-state"); var grid = document.getElementById("catalog-grid"); var countEl = document.getElementById("catalog-count");
  if (!grid) return;
  var hacks = data && Array.isArray(data.hacks) ? data.hacks : [];
  grid.innerHTML = ""; if (state) state.style.display = "none";
  if (countEl) countEl.textContent = hacks.length + " " + t("catalog.count");
  hacks.forEach(function (hack) {
    var card = document.createElement("article"); card.className = "hack-card"; card.tabIndex = 0; card.setAttribute("role", "button"); card.setAttribute("aria-label", hack.name || "");
    var cover = document.createElement("div"); cover.className = "hack-cover";
    if (hack.coverImageUrl) {
      var image = document.createElement("img"); image.loading = "lazy"; image.alt = hack.name || t("card.coverFallback"); image.src = hack.coverImageUrl;
      image.addEventListener("error", function () { cover.innerHTML = ""; cover.appendChild(coverPlaceholder(hack.name)); }); cover.appendChild(image);
    } else cover.appendChild(coverPlaceholder(hack.name));
    var body = document.createElement("div"); body.className = "hack-body";
    var title = document.createElement("h3"); title.className = "hack-title"; title.textContent = hack.name || "";
    var meta = document.createElement("div"); meta.className = "hack-meta";
    meta.innerHTML = "<div>" + t("card.author") + ": <b>" + escapeHtml(hack.author || "-") + "</b></div>" + "<div>" + t("card.baseRom") + ": <b>" + escapeHtml(hack.baseRom && hack.baseRom.name ? hack.baseRom.name : "-") + "</b></div>";
    var desc = document.createElement("p"); desc.className = "hack-desc"; desc.textContent = hack.description || "";
    body.append(title, meta, desc);
    if (Array.isArray(hack.tags) && hack.tags.length) {
      var tags = document.createElement("div"); tags.className = "hack-tags";
      hack.tags.slice(0, 3).forEach(function (tag) { var chip = document.createElement("span"); chip.className = "chip"; chip.textContent = tag; tags.appendChild(chip); }); body.appendChild(tags);
    }
    card.append(cover, body);
    card.addEventListener("click", function () { openHackPopup(hack); });
    card.addEventListener("keydown", function (e) { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); openHackPopup(hack); } });
    grid.appendChild(card);
  });
}
function openHackPopup(hack) {
  window.__activeHack = hack;
  var popup = document.getElementById("hack-popup");
  if (!popup) return;
  var cover = document.getElementById("popup-cover");
  var title = document.getElementById("popup-title");
  var meta = document.getElementById("popup-meta");
  var desc = document.getElementById("popup-desc");
  var tagsEl = document.getElementById("popup-tags");
  var badgesEl = document.getElementById("popup-badges");
  var videosEl = document.getElementById("popup-videos");
  var screenshotsEl = document.getElementById("popup-screenshots");
  var linksEl = document.getElementById("popup-links");
  var changelogEl = document.getElementById("popup-changelog");
  var extraEl = document.getElementById("popup-extra");

  if (cover) {
    if (hack.coverImageUrl) { cover.src = hack.coverImageUrl; cover.alt = hack.name || ""; cover.hidden = false; cover.style.display = ""; }
    else { cover.hidden = true; cover.removeAttribute("src"); }
  }
  if (title) title.textContent = hack.name || "";
  if (meta) {
    var parts = [];
    if (hack.author) parts.push("<div>" + escapeHtml(t("popup.author")) + ": <b>" + escapeHtml(hack.author) + "</b></div>");
    if (hack.version) parts.push("<div>" + escapeHtml(t("popup.version")) + ": <b>" + escapeHtml(hack.version) + "</b></div>");
    if (hack.baseRom && hack.baseRom.name) parts.push("<div>" + escapeHtml(t("popup.baseRom")) + ": <b>" + escapeHtml(hack.baseRom.name) + "</b></div>");
    meta.innerHTML = parts.join("");
  }
  if (desc) desc.textContent = hack.description || "";
  if (tagsEl) {
    tagsEl.innerHTML = "";
    if (Array.isArray(hack.tags) && hack.tags.length) {
      hack.tags.forEach(function (tag) { var chip = document.createElement("span"); chip.className = "chip"; chip.textContent = tag; tagsEl.appendChild(chip); });
    }
  }
  if (badgesEl) {
    badgesEl.innerHTML = "";
    var badges = [];
    if (hack.supportedGames) badges.push({ label: t("popup.supportedGames"), value: hack.supportedGames });
    if (hack.completionStatus) badges.push({ label: t("popup.completion"), value: hack.completionStatus });
    if (hack.compatibility) badges.push({ label: t("popup.compatibility"), value: hack.compatibility });
    if (hack.lastUpdated) badges.push({ label: t("popup.lastUpdated"), value: hack.lastUpdated });
    badges.forEach(function (b) {
      var el = document.createElement("span"); el.className = "hack-popup-badge hack-popup-badge--accent";
      el.textContent = b.label + ": " + b.value; badgesEl.appendChild(el);
    });
  }
  if (videosEl) {
    videosEl.innerHTML = "";
    var videos = Array.isArray(hack.videos) ? hack.videos : [];
    // Also collect youtube from screenshots if any slipped through
    var screenshots = Array.isArray(hack.screenshots) ? hack.screenshots : [];
    screenshots.forEach(function (url) { if (isYoutubeUrl(url) && videos.indexOf(url) === -1) videos.push(url); });
    if (videos.length) {
      var vTitle = document.createElement("p"); vTitle.className = "hack-popup-links-title"; vTitle.textContent = t("popup.videos"); videosEl.appendChild(vTitle);
      videos.forEach(function (url) {
        var embed = youtubeEmbedUrl(url);
        if (embed) {
          var wrap = document.createElement("div"); wrap.className = "hack-popup-video";
          var iframe = document.createElement("iframe");
          iframe.src = embed; iframe.title = hack.name || "Video"; iframe.allow = "accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"; iframe.allowFullscreen = true; iframe.loading = "lazy"; iframe.referrerPolicy = "strict-origin-when-cross-origin";
          wrap.appendChild(iframe); videosEl.appendChild(wrap);
        } else {
          var a = document.createElement("a"); a.className = "hack-popup-video-link"; a.href = url; a.target = "_blank"; a.rel = "noopener"; a.textContent = url; videosEl.appendChild(a);
        }
      });
    }
  }
  if (screenshotsEl) {
    screenshotsEl.innerHTML = "";
    var shots = Array.isArray(hack.screenshots) ? hack.screenshots.filter(function (u) { return !isYoutubeUrl(u); }) : [];
    if (shots.length) {
      var sTitle = document.createElement("p"); sTitle.className = "hack-popup-links-title"; sTitle.textContent = t("popup.screenshots"); screenshotsEl.appendChild(sTitle);
      var grid = document.createElement("div"); grid.className = "hack-popup-screenshots";
      shots.forEach(function (url) {
        var a = document.createElement("a"); a.href = url; a.target = "_blank"; a.rel = "noopener";
        var img = document.createElement("img"); img.src = url; img.alt = hack.name || ""; img.loading = "lazy";
        img.addEventListener("error", function () { a.style.display = "none"; });
        a.appendChild(img); grid.appendChild(a);
      });
      screenshotsEl.appendChild(grid);
    }
  }
  if (linksEl) {
    linksEl.innerHTML = "";
    var links = Array.isArray(hack.developerLinks) ? hack.developerLinks : [];
    // Fallback: infer from downloadTarget if developerLinks empty
    if (!links.length && hack.downloadTarget) {
      var dt = hack.downloadTarget;
      if (dt.type === "github" && dt.repoUrl) links = [{ label: "GitHub", url: dt.repoUrl }];
      else if (dt.type === "external" && dt.url) links = [{ label: "Site", url: dt.url }];
    }
    var lTitle = document.createElement("p"); lTitle.className = "hack-popup-links-title"; lTitle.textContent = t("popup.links"); linksEl.appendChild(lTitle);
    if (links.length) {
      links.forEach(function (link) {
        var a = document.createElement("a"); a.className = "hack-popup-link"; a.href = link.url; a.target = "_blank"; a.rel = "noopener";
        var label = document.createElement("span"); label.textContent = link.label || "Site";
        var urlEl = document.createElement("small"); urlEl.textContent = link.url;
        a.append(label, document.createTextNode(" "), urlEl); linksEl.appendChild(a);
      });
    } else {
      var empty = document.createElement("p"); empty.style.color = "var(--text-secondary)"; empty.style.fontSize = "14px"; empty.textContent = t("popup.noLinks"); linksEl.appendChild(empty);
    }
  }
  if (changelogEl) {
    changelogEl.innerHTML = "";
    if (Array.isArray(hack.changelog) && hack.changelog.length) {
      var cTitle = document.createElement("p"); cTitle.className = "hack-popup-links-title"; cTitle.textContent = t("popup.changelog"); changelogEl.appendChild(cTitle);
      hack.changelog.forEach(function (entry) {
        var div = document.createElement("div"); div.className = "hack-popup-changelog-entry";
        var head = "";
        if (entry.date) head = "<strong>" + escapeHtml(entry.date) + "</strong> ";
        div.innerHTML = head + escapeHtml(entry.content || "");
        changelogEl.appendChild(div);
      });
    }
  }
  if (extraEl) {
    extraEl.innerHTML = "";
    var extras = [];
    if (hack.importSource && hack.importSource.modUrl) extras.push('<div>' + escapeHtml(t("popup.source")) + ': <a href="' + escapeHtml(hack.importSource.modUrl) + '" target="_blank" rel="noopener">' + escapeHtml(hack.importSource.modUrl) + '</a></div>');
    if (hack.downloadTarget) {
      var dt = hack.downloadTarget;
      if (dt.type === "direct" && dt.patch && dt.patch.url) extras.push('<div>Patch: <a href="' + escapeHtml(dt.patch.url) + '" target="_blank" rel="noopener">' + escapeHtml(dt.patch.url) + '</a></div>');
      else if (dt.type === "github" && dt.repoUrl) extras.push('<div>GitHub: <a href="' + escapeHtml(dt.repoUrl) + '" target="_blank" rel="noopener">' + escapeHtml(dt.repoUrl) + '</a></div>');
      else if (dt.type === "external" && dt.url) extras.push('<div>Link: <a href="' + escapeHtml(dt.url) + '" target="_blank" rel="noopener">' + escapeHtml(dt.url) + '</a></div>');
    } else if (hack.patch && hack.patch.url) {
      extras.push('<div>Patch: <a href="' + escapeHtml(hack.patch.url) + '" target="_blank" rel="noopener">' + escapeHtml(hack.patch.url) + '</a></div>');
    }
    extraEl.innerHTML = extras.join("");
  }

  popup.hidden = false; popup.setAttribute("aria-hidden", "false");
  document.body.style.overflow = "hidden";
  var closeBtn = popup.querySelector(".hack-popup-close");
  if (closeBtn) closeBtn.focus();
}
function closeHackPopup() {
  var popup = document.getElementById("hack-popup");
  if (!popup || popup.hidden) return;
  // Stop youtube playback by clearing iframes
  var videosEl = document.getElementById("popup-videos");
  if (videosEl) videosEl.innerHTML = "";
  popup.hidden = true; popup.setAttribute("aria-hidden", "true");
  document.body.style.overflow = "";
  window.__activeHack = null;
}
function showCatalogLoading() {
  var state = document.getElementById("catalog-state"); var grid = document.getElementById("catalog-grid");
  if (grid) grid.innerHTML = "";
  if (state) { state.style.display = "block"; state.innerHTML = '<div class="spinner"></div><div>' + t("catalog.loading") + "</div>"; }
}
function showCatalogError() {
  var state = document.getElementById("catalog-state"); var grid = document.getElementById("catalog-grid");
  if (grid) grid.innerHTML = "";
  if (state) {
    state.style.display = "block"; state.innerHTML = "<div>" + t("catalog.error") + "</div><button class=\"btn-retry\" id=\"catalog-retry\">" + t("catalog.retry") + "</button>";
    var button = document.getElementById("catalog-retry"); if (button) button.addEventListener("click", loadCatalog);
  }
}
function escapeHtml(str) { return String(str == null ? "" : str).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/\"/g, "&quot;").replace(/'/g, "&#39;"); }
async function fetchJson(url) { var response = await fetch(url, { cache: "no-store" }); if (!response.ok) throw new Error("HTTP " + response.status); return response.json(); }
async function loadCatalog() { showCatalogLoading(); try { renderCatalog(await fetchJson(LIVE_CATALOG_URL)); } catch (error) { try { renderCatalog(await fetchJson(FALLBACK_CATALOG_URL)); } catch (fallbackError) { showCatalogError(); } } }
function fetchLatestRelease() {
  var version = document.getElementById("download-version"); var date = document.getElementById("download-date"); if (!version) return;
  fetch("https://api.github.com/repos/zonaro/hylianbox/releases/latest", { cache: "no-store" }).then(function (response) { if (!response.ok) throw new Error("Latest release unavailable"); return response.json(); }).then(function (release) {
    if (!release || !release.tag_name) return; version.textContent = release.tag_name;
    if (date && release.published_at) { var published = new Date(release.published_at); if (!isNaN(published)) date.textContent = "(" + published.toISOString().slice(0, 10) + ")"; }
  }).catch(function () { /* Keep the bundled version when offline. */ });
}
function shouldSkipGlyphs(el) {
  if (!el || el.nodeType !== 1) return false; var tag = el.tagName.toLowerCase();
  if (tag === "img" || tag === "svg" || tag === "br" || tag === "script" || tag === "style") return true;
  if (el.hasAttribute("data-no-split") || el.classList.contains("glyph")) return true;
  if (el.closest && el.closest("#hack-popup")) return true;
  if (el.closest && el.closest(".hack-popup")) return true;
  var cls = typeof el.className === "string" ? el.className : ""; return /icon|symbol|ph-|hylian-symbol|feature-icon|hack-popup/i.test(cls);
}
function splitTextNode(node, counter) {
  var text = node.nodeValue; if (!text) return; var fragment = document.createDocumentFragment();
  for (var i = 0; i < text.length; i++) { var character = text.charAt(i); if (character === " " || character === " ") { fragment.appendChild(document.createTextNode(" ")); continue; } var glyph = document.createElement("span"); glyph.className = "glyph"; glyph.style.setProperty("--i", counter.n++); glyph.textContent = character; fragment.appendChild(glyph); }
  node.parentNode.replaceChild(fragment, node);
}
function splitElementGlyphs(el, counter) { Array.prototype.slice.call(el.childNodes).forEach(function (node) { if (node.nodeType === 3) splitTextNode(node, counter); else if (node.nodeType === 1 && !shouldSkipGlyphs(node) && node.id !== "catalog-grid" && node.id !== "hack-popup") splitElementGlyphs(node, counter); }); }
function splitGlyphs(zone) { var counter = { n: 0 }; splitElementGlyphs(zone, counter); zone.style.setProperty("--step", Math.min(30, Math.floor(1200 / Math.max(1, counter.n))) + "ms"); if (zone.classList.contains("font-awakened")) zone.querySelectorAll(".glyph").forEach(function (glyph) { glyph.classList.add("is-static"); }); }
function refreshGlyphs() { document.querySelectorAll("[data-type-zone]").forEach(splitGlyphs); }
function setupTypographyAwakening() {
  var zones = Array.from(document.querySelectorAll("[data-type-zone]")); var awaken = function (zone) { zone.classList.add("font-awakened"); };
  if ("IntersectionObserver" in window) { var observer = new IntersectionObserver(function (entries) { entries.forEach(function (entry) { if (entry.isIntersecting) { awaken(entry.target); observer.unobserve(entry.target); } }); }, { threshold: 0.15, rootMargin: "100px 0px" }); zones.forEach(function (zone) { observer.observe(zone); }); }
  var checkVisibility = function () { var triggerPoint = window.innerHeight * 0.85; zones.forEach(function (zone) { var rect = zone.getBoundingClientRect(); if (rect.top < triggerPoint && rect.bottom > 0) awaken(zone); }); };
  checkVisibility(); window.addEventListener("scroll", checkVisibility, { passive: true }); window.addEventListener("resize", checkVisibility, { passive: true });
}
document.addEventListener("DOMContentLoaded", function () {
  currentLang = detectLang(); document.documentElement.lang = currentLang; renderStatic(); renderFeatures(); updateLangButtons();
  document.querySelectorAll(".lang-btn").forEach(function (button) { button.addEventListener("click", function () { setLang(button.dataset.lang); }); });
  loadCatalog(); fetchLatestRelease(); refreshGlyphs(); setupTypographyAwakening();
  var popup = document.getElementById("hack-popup");
  if (popup) {
    popup.addEventListener("click", function (e) { if (e.target.hasAttribute("data-close-popup") || e.target.closest("[data-close-popup]")) closeHackPopup(); });
  }
  document.addEventListener("keydown", function (e) { if (e.key === "Escape") closeHackPopup(); });
});
