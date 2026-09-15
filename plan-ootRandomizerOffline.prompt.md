## Plan: Randomizer OoT offline no HylianBox

TL;DR: Embutir o gerador oficial OoT-Randomizer (Python 3.13 + numpy, MIT, v9.1.x) para rodar 100% offline no app via runtime Python embarcado (Chaquopy), com tela exclusiva de Seeds fora do catalogo principal, configuracao fiel ao site, codigo visual da seed com icones extraidos da ROM do usuario, ROM patcheada salva como rom_rando_<id> e aberta no emulador via resolver, e compartilhamento via .zpf + settings-string + spoiler JSON. Politica upstream-first: todo consulta e implementacao referencia o codigo-fonte original vendorizado (nunca wiki ou memoria), com versao do randomizer exibida na tela de seeds e por seed, e processo de sync para incorporar melhorias futuras.

Principio upstream-first (vale para todas as fases):
- Fonte de verdade e o codigo original em third_party/ootr (espelho verbatim do tag upstream + manifesto com commit, __version__ de version.py, branch_url, data de sync). Toda implementacao Kotlin/Python cita arquivo, funcao e linhas upstream (ex: N64Patch.apply_patch_file, Spoiler.build_file_hash, Main.patch_and_output, SettingsList/SettingTypes).
- Nada e reimplementado de cabeca ou da wiki: schema de settings e gerado de SettingsList.py + SettingTypes.py, HASH_ICONS e PASSWORD_NOTES sao copiados de Spoiler.py, ZpfApplier espelha N64Patch.py linha a linha, validacoes espelham Main.resolve_settings.
- Sync futuro via tools/sync_ootr_upstream.sh (fetch tag, diff, atualiza vendor + schema + CHANGELOG em .agents/RANDOMIZER.md, roda testes de regressao). Cada seed grava ootrVersion + ootrCommit para alertar divergencia entre devices.

Contexto apurado:
- HylianBox e Kotlin nativo, pacote br.com.redclaw.hylianbox, minSdk 24, target 35, DI manual em HylianBoxApp/AppContainer, Switch UI obrigatoria, i18n pt-BR/en/es, sem hardcoded strings, sem emojis.
- Fluxo atual: BaseRomRepository importa ROM, Storage guarda rom_<hackId>, GameRomResolver resolve, RetroView e o unico ponto onde bytes chegam ao core, InstalledLibrary junta vanilla + loja.
- Tracker ja extrai icones 32x32 da ROM em filesDir/tracker_assets/<crc32> via RomAssetExtractor + TrackerAssetCache + OotIconMap + EquippedItemIconMap + SongIconMap, com fallback ic_tracker_fallback.
- OoT-Randomizer: Main.py com resolve_settings, generate (build_world_graphs, place_items, make_spoiler), patch_and_output; saidas .zpf (ZPFv1, zlib + XOR + DMA, definido em N64Patch.py create_patch_file e apply_patch_file), .z64 comprimido via bin/Compress nativo (tem Compress_ARM32 e Compress_ARM64), .wad via gzinject, spoiler/settings/cosmetics JSON; Spoiler.py define HASH_ICONS com 32 nomes, file_hash com 5 indices, PASSWORD_NOTES com A e C-down/right/left/up, password com 6 notas; Settings.py e SettingsList.py tem cerca de 150 opcoes; requirements.txt e so numpy==2.5.2; LICENSE e MIT com pastas GPLv2+; exige OoT US 1.0 ou JP 1.0; versao atual 9.1.36.
- Decisoes do usuario: motor 100% offline (porte Python), fidelidade total de opcoes, compartilhar .zpf + settings-string + spoiler log, bloquear base ROM incompativel com mensagem i18n, codigo visual a decidir no plano (proposta: 5 icones file_hash + 6 notas password).

Fase 0 — Spike de viabilidade + vendor upstream (bloqueante para resto)
1. Validar Chaquopy com Python 3.13 + numpy 2.5.2 em arm64-v8a e armeabi-v7a, medir import de Settings, World, Main e memoria (alerta: N64Patch usa numpy array de 134M uint16, risco de OOM, avaliar largeHeap e geracao em ForegroundService). Paralelo com passo 2.
2. Validar execucao do bin/Compress_ARM64 no Android (permissao exec, chamada via process, ou fallback JNI) e leitura de data_path, ASM, data JSON dentro de assets/python. Paralelo com passo 1.
3. Vendorizar fonte original verbatim em third_party/ootr (git subtree ou copia do tag + third_party/ootr/UPSTREAM.json com repo, tag, commit, __version__ de version.py, branch_url, branch_identifier, data de sync) e lista de arquivos embarcados (py raiz, data, ASM, data/icons, data/Music excluido, bin/Compress_ARM32/64), com aviso MIT em About/Licencas. Nenhuma consulta de regra usa wiki ou memoria quando o .py responde — em duvida, ler o .py. Depende de 1 e 2.

Fase 1 — Motor offline embarcado a partir do vendor
4. Criar modulo randomizer/engine: copiar third_party/ootr para app/src/main/python/ootr no build (sem editar o Python, apenas config de data_path/output_dir via wrapper), wrapper Kotlin OotrEngine com API generate(settingsJson, baseRomPath, outDir) via Chaquopy, rodando em Dispatchers.IO dentro de ForegroundService com progresso (poll de log ou callback), retry ate 10 tentativas em ShuffleError como Main.main faz. Toda duvida de comportamento se resolve lendo third_party/ootr/Main.py, Settings.py, World.py. Depende da Fase 0.
5. Adaptar IO do Python para Android sem forkar logica: mapear settings.rom para path da BaseRomRepository, settings.output_dir para filesDir/randomizer/out, settings.create_patch_file=true, create_compressed_rom=false na primeira versao (gerar .zpf + descomprimido, comprimir depois), create_spoiler conforme escolha, desabilitar create_wad_file e custom models/voices na v1. Mudancas Android-only vivem no wrapper Kotlin, nunca em patch no .py vendorizado. Depende de 4.
6. Implementar ZpfApplier em Kotlin como espelho linha a linha de third_party/ootr/N64Patch.apply_patch_file (header ZPFv1, dma_start, xor_range, xor_address, moves de DMA, blocos XOR, zlib), com comentario de cabecalho citando arquivo/funcao upstream e versao, reutilizando patcher/n64/RomNormalizer, ChecksumCalculator, PatcherException, e expor via PatcherFacade.applyZpf. Qualquer divergencia futura do upstream atualiza este arquivo no sync. Permite aplicar .zpf importado sem Python. Paralelo com 4 e 5.

Fase 2 — Persistencia isolada de seeds
7. Criar randomizer/data/RandomizerSeed (id seedId, settingsString, seed string, fileHash 5 ints, password 6 ints, baseCrc32, ootrVersion + ootrCommit lidos de UPSTREAM.json/version.py no momento da geracao, createdAt, spoilerPath, settingsPath, zpfPath, romPath, mensagem custom) e RandomizerSeedRepository com JSON em filesDir/randomizer_seeds.json + migracao com versao de schema. Excluir de InstalledLibrary.entries e de MergedCatalogRepository.
8. Estender Storage com rom/rando (rom_rando_<seedId>), sram_rando_<seedId>, state_rando_<seedId> e funcoes de delete seguindo uninstallHackFiles, e estender GameRomResolver com RANDO_PREFIX rando_ para resolver para Storage sem copiar ROM (Regra 9). Depende de 7.
9. Garantir RA por seed: calcular RaHashService so da ROM final patcheada em Storage, cache por rando_<id>, nunca da base (Regra 21), e manter hardcore default OFF. Paralelo com 8.

Fase 3 — Lista exclusiva + codigo visual + versao visivel
10. Criar randomizer/ui/RandomizerSeedsActivity em estilo SwitchGridScreen (header com titulo + badge de versao do randomizer lida de UPSTREAM.json/__version__ de version.py, ex: Randomizer v9.1.36, grid 1:1, busca, foco cyan, SfxManager, ThemeManager), acessada por novo botao na SwitchDock ou card na LibraryActivity, listando so seeds locais com capa gerada (fundo OoT + 5 icones file_hash em linha + tooltip). Cada card/detalhe exibe ootrVersion da seed e alerta quando difere da versao embarcada atual. Clique abre no emulador via LibraryMenuHostDelegate.launchGame adaptado para id rando_. OotrVersionProvider centraliza a leitura (UPSTREAM.json + version.py do vendor) para header, detalhe, share e About.
11. Criar SeedIconMapper a partir de third_party/ootr/Spoiler.py (HASH_ICONS via build_file_hash, PASSWORD_NOTES via build_password, sem copiar de wiki): mapear cada um dos 32 HASH_ICONS para PNG em tracker_assets/<baseCrc32> via OotIconMap, EquippedItemIconMap e SongIconMap, com fallback ic_tracker_fallback quando nao houver correspondencia (ex: SOLD OUT, Cucco, Mushroom). Garantir extracao previa via RomAssetExtractor.extractAll antes de renderizar. Detalhe da seed mostra tambem as 6 notas password_lock (A/C) e settings-string copiavel. Depende de 10 e da Fase 2.
12. Acoes por seed: Jogar, Ver spoiler (se gerado), Compartilhar, Renomear mensagem, Apagar (remove rom/sram/state + json). Fora do catalogo principal por construcao. Depende de 11.

Fase 4 — Configuracao fiel ao vendor
13. Criar randomizer/ui/SeedConfigActivity com abas espelhando third_party/ootr/SettingsList.py (ROM Options com output, plando, presets Load/Save/Remove; Main Rules Open/World/Shuffle/Dungeon; Detailed Logic com tricks + advanced; Starting Inventory; Other Timesavers/Hints/Gameplay/Item Pool; Cosmetics; SFX; Generation com seed vazia=aleatoria e settings-string import/export). Gerar UI a partir de schema extraido do vendor via script tools/gen_ootr_settings_schema.py lendo SettingsList.py + SettingTypes.py (nunca lista manual ou wiki) para nao divergir. Validar tudo antes de gerar (Regra 16) espelhando Main.resolve_settings e bloquear base diferente de US/JP 1.0 com erro i18n.
14. Botao Gerar: valida base ROM via BaseRomRepository.findByCrc32, chama OotrEngine em ForegroundService, salva .zpf + _Settings.json + _Spoiler.json em filesDir/randomizer/<seedId>, aplica no vanilla via ZpfApplier ou copia do descomprimido para rom_rando_<seedId>, registra no repositorio e volta para lista. Depende de Fase 1, 2 e 13.

Fase 5 — Compartilhamento e importacao com versao
15. Share via FileProvider: enviar .zpf + texto com settings-string + seed + ootrVersion/ootrCommit (para o receptor saber se o gerador difere), e opcional _Spoiler.json (com aviso de spoiler/cheating em corridas). Receber .zpf/.zpfz via SAF, aplicar com ZpfApplier sobre o vanilla do usuario, reconstruir RandomizerSeed (file_hash e password lidos do spoiler se presente, senao marcadores desconhecidos; ootrVersion do spoiler quando houver, senao marcada como desconhecida com alerta) e listar. Reutilizar ZipExtractor para .zpfz multiworld (Player ID P1..Pn). Depende de Fase 2 e passo 6.
16. Dashboard parity: expor seeds em CollectionRoutes e BackupRoutes so leitura/exclusao, sem gerar pelo browser na v1 (Regra 17 so exige paridade de Settings do app, mas evitar divergencia). Paralelo com 15.

Fase 6 — Sync continuo do upstream (processo permanente, nao so v1)
20. Criar tools/sync_ootr_upstream.sh: fetch do tag novo, diff contra third_party/ootr, atualiza vendor + UPSTREAM.json + schema de settings + SeedIconMapper/HASH_ICONS + ZpfApplier se N64Patch.py mudou, roda testes de regressao (round-trip .zpf, schema, gerar seed tiny) e registra CHANGELOG em .agents/RANDOMIZER.md. Documentar em .agents/RANDOMIZER.md a politica: nenhuma regra de randomizer entra sem referencia ao .py e sem bump de versao visivel.

Fase 7 — Polimento obrigatorio
17. i18n total em values/strings.xml (pt-BR default) + values-en + values-es, termo Seed inalterado, zero hardcoded, KDoc em APIs publicas (Wally).
18. Switch UI compliance (Chululu): foco cyan, dimming, footer hints, sons CC0 em res/raw, sem assets Nintendo, sem emojis, Dolfi para icone da secao Randomizer e placeholder de capa.
19. Testes: JVM para RandomizerSeedRepository, SeedIconMapper, ZpfApplier round-trip (gerar .zpf pequeno via host e aplicar), GameRomResolver rando_, schema de settings; instrumentados para fluxo gerar-listar-jogar e import .zpf; fixtures sem ROM (bytes sinteticos). Atualizar .agents com RANDOMIZER.md e FEATURES.md.

Relevant files:
- app/src/main/java/br/com/redclaw/hylianbox/views/LibraryActivity.kt — ponto de entrada, dock e home row, adicionar acesso a seeds sem misturar com InstalledLibrary.entries
- app/src/main/java/br/com/redclaw/hylianbox/views/InstalledLibrary.kt — garantir exclusao de seeds do catalogo principal
- app/src/main/java/br/com/redclaw/hylianbox/views/HackLibrarySource.kt — referencia de HackLibraryEntry e BadgeType, criar modelo proprio RandomizerSeed separado
- app/src/main/java/br/com/redclaw/hylianbox/repositories/Storage.kt — estender com rom_rando, sram_rando, state_rando e delete
- app/src/main/java/br/com/redclaw/hylianbox/repositories/GameRomResolver.kt — adicionar RANDO_PREFIX e rota para Storage
- app/src/main/java/br/com/redclaw/hylianbox/data/local/BaseRomRepository.kt — validacao de base US/JP 1.0 via findByCrc32 e getById
- app/src/main/java/br/com/redclaw/hylianbox/patcher/PatcherFacade.kt — expor applyZpf ao lado de BPS/IPS/XDELTA
- app/src/main/java/br/com/redclaw/hylianbox/patcher/PatcherException.kt — reusar SourceChecksumMismatch e PatchFormatError
- app/src/main/java/br/com/redclaw/hylianbox/tracker/assets/RomAssetExtractor.kt — extracao previa para codigo visual
- app/src/main/java/br/com/redclaw/hylianbox/tracker/assets/cache/TrackerAssetCache.kt — layout filesDir/tracker_assets/<crc32>
- app/src/main/java/br/com/redclaw/hylianbox/tracker/assets/mapping/OotIconMap.kt — base do mapeamento HASH_ICONS para PNG
- app/src/main/java/br/com/redclaw/hylianbox/tracker/assets/mapping/EquippedItemIconMap.kt — complementar icones equipaveis
- app/src/main/java/br/com/redclaw/hylianbox/tracker/assets/mapping/SongIconMap.kt — notas de ocarina para password_lock
- app/src/main/java/br/com/redclaw/hylianbox/retroachievements/install/RaHashService.kt — hash so da ROM final patcheada
- app/src/main/java/br/com/redclaw/hylianbox/ui/switchui/SwitchGridActivity.kt — template para RandomizerSeedsActivity
- app/src/main/java/br/com/redclaw/hylianbox/ui/switchui/SwitchDock.kt — novo acesso Randomizer
- app/build.gradle.kts — dependencia Chaquopy, python, jniLibs, largeHeap avaliacao, copia de third_party/ootr para python/ootr no build
- third_party/ootr/ + third_party/ootr/UPSTREAM.json — espelho verbatim do tag upstream (Main.py com main, resolve_settings, generate, patch_and_output, from_patch_file; Spoiler.py com HASH_ICONS, PASSWORD_NOTES, build_file_hash, build_password; N64Patch.py com create_patch_file, apply_patch_file; Settings.py, SettingsList.py, SettingTypes.py para schema; Rom.py; version.py com __version__; requirements.txt numpy==2.5.2; bin/Compress_ARM32/64)
- randomizer/engine/OotrVersionProvider.kt — leitura central de UPSTREAM.json + version.py para header, detalhe, share e About
- tools/gen_ootr_settings_schema.py — gera schema lendo SettingsList.py + SettingTypes.py do vendor
- tools/sync_ootr_upstream.sh — sync futuro de tags, diff, regressao, CHANGELOG
- .agents/RANDOMIZER.md — politica upstream-first, versao embarcada, historico de syncs

Verification:
1. Spike Chaquopy: importar ootr.Main do vendor em device arm64 e gerar seed tiny (plando minimo) sem OOM, anexar log e tempo.
2. Compress_ARM64 do vendor executa e produz .z64 valido a partir do descomprimido, ou documentar fallback.
3. Teste JVM ZpfApplier: apply de .zpf real sobre base sintetica bate com saida do Python do vendor (referencia N64Patch.apply_patch_file do tag pinado).
4. Gerar seed OoT US 1.0 no app, ver rom_rando_<id> em Storage, abrir via GameActivity sem copiar ROM, checar save isolado.
5. Lista mostra 5 icones extraidos da ROM do usuario (sem fallback indevido) + 6 notas, seeds fora de Todos os Jogos e da Store.
6. Compartilhar .zpf via Sharesheet e importar em outro device com mesmo vanilla resulta em ROM jogavel e mesma settings-string.
7. Lint i18n: zero strings hardcoded, chaves presentes em values, values-en, values-es; Switch UI sem popup, foco navegavel por gamepad.
8. Tela de seeds exibe versao do randomizer (header + detalhe + share) lida de UPSTREAM.json/version.py, cada seed carrega ootrVersion/ootrCommit, e divergencia gera alerta; teste de regressao do sync valida schema, HASH_ICONS e round-trip .zpf contra o vendor.

Decisions:
- Inclui: porte offline via Python embarcado a partir do vendor verbatim, fidelidade total de opcoes via schema gerado do vendor, .zpf + spoiler + settings persistidos, seeds isoladas fora do catalogo, ambos file_hash 5 icones e password 6 notas, bloqueio de base invalida, versao do randomizer visivel no header da tela de seeds e por seed, sync futuro via script.
- Exclui v1: multiworld co-op sync, .wad Wii, custom models .zobj e custom music .ootrs, cosmetic-only repatch, plando avancado editavel (so importar arquivo), geracao pelo Dashboard browser.
- Assume: MIT permite bundle com atribuicao e GPL-3.0 compativel, manter LICENSE e branch_url do vendor; usuario importa OoT US/JP 1.0 legalmente; geracao exige espera longa em ForegroundService com cancelamento; nenhuma implementacao de randomizer sem citar o .py de origem.

Further Considerations:
1. Risco de memoria do numpy de 134M uint16 em celulares low-end, mitigar com WorkManager, chunk, ou gerar .zpf sem manter ROM dupla em heap.
2. Tamanho do APK vai crescer com python + numpy + data OoTR, avaliar App Bundle e download sob demanda do payload do gerador.
3. Divergencia de versao OoTR quebra settings-string entre devices, por isso fixar ootrVersion por seed e exibir na UI.
