# Итог интеграции ECC — 04.11.2026

## Установка и безопасность

- Официальный `ecc@ecc` **2.2.3** установлен через Codex marketplace из `affaan-m/ECC`, закреплённого SHA `b38be6a7f6350964d59328b52f0624e1d1d552d1`.
- CLI: `0.166.0-alpha.1`. Установка через `plugin add` поддерживается актуальным Codex; документация ECC требует 0.146.0+ и не использует старый `plugin install`.
- Повторный marketplace add вернул `alreadyAdded: true`; в installed inventory ровно один `ecc@ecc` версии 2.2.3, enabled=true. Конфликтующего legacy sync и проектных AGENTS/.codex файлов до этой работы не найдено.
- Официальный cache checker: все ссылки манифеста на skills/MCP/hooks/assets разрешаются внутри установленного кэша. Хэши манифеста, Codex hook definition и MCP config совпадают с проверенным upstream.
- Свежий native app-server `skills/list`: **350 ECC-навыков + 9 ранее имеющихся built-ins**, ошибок чтения 0 в каждом проекте. Документация/манифест рекламирует 281; отчёт использует наблюдаемые 350, не выдавая их все за исполненные.
- Проверенные effective settings из `config/read`: hooks=false; ECC enabled=true; Chrome DevTools MCP enabled=false. Никакой hook trust не выдавался, hooks/MCP не исполнялись, npm зависимости и старый sync не устанавливались.
- Первый вариант установки отклонён автоматической проверкой. После дополнительного аудита был отдельно одобрен безопасный вариант: сначала постоянное отключение hooks и browser MCP, затем официальный plugin add. Ограничение hooks глобально для этого native CLI; перед изменением не было установленного native plugin, пользовательского hook-файла или явного hooks preference. Другие модели, sandbox, approvals и пользовательские настройки не менялись.
- Проекты пока untrusted для native CLI, поэтому проектные .codex слои **игнорируются**. Автодоверие не выставлялось. Действуют уже проверенные user-level guards; проектные файлы дублируют их для будущих доверенных сессий и не форсируют plugin enablement.
- Текущий долгоживущий cloud chat не перезагружает собственный каталог инструментов автоматически. Для использования native ECC skill surface откройте новую соответствующую Codex-сессию. Наличие в skills/list и проверка кэша не означают выполнения/семантической проверки всех 350 навыков.

## Проектные изменения

Добавлены `AGENTS.md`, `.codex/config.toml`, `.github/PULL_REQUEST_TEMPLATE.md`, `docs/ecc/{WORKFLOW.md,SECURITY_REVIEW.md,STATUS.md,upstream.json,UPSTREAM_LICENSE.txt}`. Ранее существующих файлов с этими именами не было; README, runtime, тесты, зависимости, build scripts и workflows сохранены. В проекты не скопированы 350 навыков, hooks, MCP launcher, чужие агенты или глобальные ECC profiles; полный официальный bundle существует только в штатном пользовательском plugin cache.

Выборочные процессы: `ecc:verification-loop`, `ecc:security-review`, `ecc:tdd-workflow` и подходящие языковые рекомендации. Java service-примеры не превращают Forge в Spring/Quarkus; generic npm/TypeScript/coverage команды не навязываются Python/Java проектам. Отдельного нативного Codex code-review role/Claude slash-command не объявляем: review lanes заданы в AGENTS/PR-шаблоне, независимое ревью текущего diff выполнено.

Сканирование секретов выводит только имена файлов/категории; `auth.json`, значения env/ключи и пользовательские session memories не читались/не копировались. Установка не переносится автоматически в локальный Codex или любую новую cloud machine: локальные шаги/аудит и проверка версии описаны в WORKFLOW.md. Автоматической установки/обновления/доверия проектные файлы не выполняют.

## Project Kingdom: фактические проверки

База: `3700c3b56a266fe0d1ea1a96d1196934144a2d80`, Java toolchain 17, Gradle wrapper 8.8, Minecraft 1.20.1, Forge 47.3.0, JUnit BOM 5.10.3. Механика и данные игры не менялись.

| Проверка | Итог |
|---|---|
| `python3 03_verify_project.py` | PASS, только статическая структура/версии/wiring |
| JDK | Установлен отдельно Temurin 17.0.21+9; архив проверен по официальной SHA256 `8b9b611d917e3dfd8419d3566b5949226f69b052fed7e109c1368add1dfe8711`; java/javac 17.0.21 |
| `bash validation/identity_names_harness/run.sh` | PASS, проверка имён на настоящем javac --release 17 / Java 17 |
| `bash validation/warehouse_room_harness/run.sh` | PASS, 8 кейсов: floor/diagonal/retry/height/no_storage/rights/dimension/upper_limit |
| `bash validation/stage1_harness/run.sh` | **FAIL**, exit 1, 189 compiler diagnostics: старый harness/source list/stubs не включает новые enterprise/property/container зависимости. Исходники игры не исправлялись ради него |
| `./gradlew test build --no-daemon --max-workers=2 --stacktrace --console=plain` | **BLOCKED**, Gradle 8.8 скачан и запущен на JDK 17 через штатный proxy/CA; разрешение ForgeGradle останавливается на сетевом доступе |
| Forge metadata | Прямой безопасный запрос к maven.minecraftforge.net через штатный proxy получает `Tunnel connection failed: 403 Forbidden`; домен не разрешён текущей политикой |
| Forge compile/JUnit/gameplay этого SHA | **Не подтверждены**: до компиляции/JUnit текущий локальный Gradle не дошёл. Модель/мир/клиент/сервер не запускались |
| Существующий CI | Найден успешный build https://github.com/kataev62-web/ProjectKingdom/actions/runs/41762898868, но для другого SHA `eddde53d85616b5c13ef586d7798d694fc1c0c08`; это не доказательство полной совместимости текущего 3700c3b |

Системная Java 21 не заменялась: JDK 17 выбран только для целевых команд. Proxy properties и доверенный CA восстановили доступ wrapper, TLS не отключался. Не добавлялись сетевые `*`-разрешения и не менялись Forge/Gradle версии/проверки Vosk. Чтобы подтвердить текущую полную Forge-сборку, нужен разрешённый доступ к конкретным Maven/Minecraft хостам либо выполнение существующего CI для нужного SHA. Старый stage1 harness требует отдельной test-maintenance работы; эти проблемы не считаются исправленными интеграцией ECC.
