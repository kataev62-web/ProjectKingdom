# Issue #73 — BLOCKED: PR #72 не интегрирован

База review-ветки: PR #71 `49db1b411c43653c29b55275126cdf9116b3236c`.
Проверены #72 `c4bf168f60f68d74daaa989d3a806cb7d5d15699`, `8a951cd23508252ea11f0ca34152c00966971dec` и актуальный при ревизии `6bdf94853af9968c0f2fce97c5593aef7560d0d7`. Последний добавляет повторный нагрев, но не исправляет приведённые ниже методы. Исходный `e0aeca0` из Issue не выдан за текущий HEAD.

Ancestry проверена: #72 действительно содержит #66 `43ac75f34aa1c555e71dcbc1bf944223eaebce13`, несмотря на base=main. При будущем переносе сравнивать дельту от #66, уже включённого в #71, не применять #66 повторно. Общие исходники сейчас не переносились вообще.

## Критический R1: native production Recipe ID
На настоящем dedicated Forge 47.3.0 + TFC 3.2.26 + Create 0.5.1.j + Patchouli 1.20.1-85-forge read-only javaagent проверил класс, загруженный **TransformingClassLoader**, а не deobfuscated Gradle-класс:
```
REVIEW73 loader=cpw.mods.modlauncher.TransformingClassLoader@51ec2856
REVIEW73 getId=MISSING
REVIEW73 m_6423_=public net.minecraft.resources.ResourceLocation net.dries007.tfc.common.recipes.AnvilRecipe.m_6423_()
```
В `KingdomTfcSmithWork` вызовы `candidate.getClass().getMethod("getId")` в availableRecipes, selectedRecipe и anvilWork не разрешаются. availableRecipes глотает ReflectiveOperationException и возвращает List.of(): настоящий каталог оказывается пуст, выбранный recipe не разрешается. Наличие `AnvilRecipe.getAll`, getTier и прохождение обычных JUnit/startup этого не исправляет.

Это API-проба на ранее одобренном изолированном стенде **базы #71**, с уже принятой владельцем EULA, не запуск производственных действий #72. Диагностический агент не трансформирует классы и не меняет бизнес-логику. Сервер дошёл до Done, штатно остановлен, мир сохранён, exit 0. Никакой новой интегрированной версии с #72 не создано.

Минимальное предложение владельцу: единый mapping-aware доступ к Recipe ID, например Forge ObfuscationReflectionHelper для SRG `m_6423_`, во всех перечисленных местах. Затем regression на production namespace и настоящий выбор рецепта/ковка. Исправления в ветку Ники не внесены.

## R2: четыре красных поведенческих проверки консервации
Независимый fixture вызывает **actual private methods кандидата** через reflection. У него настоящие Minecraft ItemStack/Container и Forge ItemStackHandler; не скопированный алгоритм и не проверка подстрок.

| Сценарий | Ожидание | Наблюдение |
|---|---|---|
| fromChest: source getter возвращает копию | raw + forge = 10 | raw 10 + forge 1 = **11** |
| fromChest: handler отклоняет setter | raw + forge = 10 | raw 9 + forge 0 = **9** |
| intoChest: setter отклоняет запись | false, anvil не очищать | **true**; tick затем очищает anvil и засчитывает доставку |
| intoChest: merge в copying getter | запас 10 + 2 = 12 | запас **10**, возврат true |

Это доказательство дефекта при указанных контрактных реализациях, **не утверждение, что vanilla ChestBlockEntity обязательно возвращает копии** или что в живом TFC-мире уже воспроизведён дюп. Передача сейчас зависит от мутабельности getter/безусловной успешности setter, а такая гарантия не проверена. Native handler в фикстуре заменён контролируемой реализацией отказа; поведение реальных TFC handlers при всех отказах ещё требует игровой проверки.

Минимальный патч владельцу: реальное извлечение/вставка с подтверждением количества/NBT и обработкой отказа; только после подтверждённого переноса очищать source и засчитывать Order. Не заменять это проверкой подстроки setItem/shrink. Изменений бизнес-логики в этой review-ветке нет.

## Матрица независимой ревизии
| Область | Установлено | Не подтверждено / риск |
|---|---|---|
| Рецепты | native getAll(Level,ItemStack,int), getTier и chooseRecipe существуют в TFC 3.2.26 | R1 блокирует получение/сверку recipe ID в production |
| Сырьё/топливо/нагрев | предусмотрены настоящие raw stacks, fuel tag, CharcoalForgeBlockEntity.light, HeatCapability/IHeat.canWork; нет виртуального результата из new ItemStack | R2; действительное горение/температура/NBT capability в игровом цикле не выполнены |
| Ковка | native AnvilBlockEntity.work(ServerPlayer,ForgeStep), слот молота, реальные ForgeSteps/Rules; новые материалы ограничены одним heating input | R1; полный цикл NPC/рецепт/настоящий металл не проверен |
| Склад | source/destination загружены и проверяются права, используется BE instanceof Container | R2; двойные сундуки не объединяются; capability-only TFC storage не покрыты; не заявлять поддержку всех типов |
| Заказы | отдельный SavedData, один order на enterprise, worker UUID/contract проверки; исходные state/pause/reload тесты проходят | сохранение порядка Order/машин/чанков при crash не проверено; malformed NBT и потеря physical output требуют fail-closed восстановления |
| Два NPC | order привязан к одному worker; остальные на том же предприятии отклоняются до work | совместный доступ к машине/контейнеру из разных назначений и manual player interaction не проверены в мире |
| Рестарт/владение | есть room/core/dimension/loaded/owner проверки, обычные NBT round-trip тесты | нет испытания с настоящим рестартом во время нагрева/ковки/доставки на копии пользовательского мира |

## Команды и результаты
Java 17 / Wrapper 8.8. Кандидат проверялся в архивном дереве без .git, вне рабочей ветки #71.

- c4bf168, `./gradlew test build` с 4 независимыми тестами: **587 executed, 4 failed, 0 errors/skipped**, остальные 583 passed; exit 1.
- 8a951cd, targeted `test --tests '*KingdomSmithyPhysicalTransfer*'`: 2 авторских теста прошли, 4 независимых не прошли.
- 6bdf948, полный `./gradlew test build` с фикстурой: **590 executed, 4 failed, 0 errors/skipped**, остальные 586 passed; exit 1 (36s). `results.json` содержит точные failures.
- `03_verify_project.py` кандидата: PASS (статическая проверка).
- Review-база #71: `./gradlew test build` PASS (33s; test UP-TO-DATE от базы с 592 PASS), static PASS. Это НЕ одобрение #72.
- Upstream CI #72 на 8a951cd был SUCCESS; 6bdf948 на момент чтения не имел проверки. Статус привязан к SHA, успех предыдущего HEAD не переносится на следующий. Наши красные проверки не входят в upstream suite.
- API-проба: реальный Forge/TFC namespace, вывод выше; startup и остановка базы PASS. Клиент/NPC/производство #72 не запускались и не объявляются успешными.

Воспроизвести кандидат после `git fetch origin pull/72/head:refs/remotes/origin/pr72` и настройки Java17/GRADLE_USER_HOME:
```bash
bash validation/pr72_review/run_review.sh 6bdf94853af9968c0f2fce97c5593aef7560d0d7
```
Скрипт создаёт НОВЫЙ mktemp checkout-архив, добавляет fixture и сохраняет logs/XML. Ожидается ненулевой код, пока консервация нарушена. Каталог намеренно не удаляется. Не запускать фикстуру как подтверждение полного игрового цикла даже после будущего PASS.

Для проверки production API на отдельном временном стенде (никаких production-файлов):
```bash
PROBE_DIR=$(mktemp -d)
javac -d "$PROBE_DIR" validation/pr72_review/TfcRecipeApiAgent.java
printf 'Premain-Class: TfcRecipeApiAgent\n' > "$PROBE_DIR/MANIFEST.MF"
jar cfm "$PROBE_DIR/api-agent.jar" "$PROBE_DIR/MANIFEST.MF" -C "$PROBE_DIR" TfcRecipeApiAgent.class
```
Передать `JAVA_TOOL_OPTIONS="-javaagent:$PROBE_DIR/api-agent.jar"` процессу ранее описанного runtime_smoke runner на новом изолированном стенде с точным JAR базы/модами; запуск лишь при явно принятой владельцем EULA. Не применять агент к действующему серверу. Нужные маркеры REVIEW73 находятся в console.log; INCONCLUSIVE нельзя считать отсутствием метода.

## Решение / границы
**Интеграция остановлена.** Никаких merge/cherry-pick #72 в эту ветку; она остаётся на базе #71 плюс ревизия/фикстуры и отключение misleading RC upload. Новая готовая RC и artifact с заказами кузницы НЕ публикуются. CI этой review-ветки проверяет только безопасную базу/компиляцию diagnostic agent, не скрывает красные тесты кандидата. Не сливать draft как функциональную интеграцию.

Нике переданы причины и минимальные предложения: https://github.com/kataev62-web/ProjectKingdom/pull/72#issuecomment-6987078675 . После исправления R1/R2 повторить аудит точного нового HEAD, затем и только затем интеграцию с #71, сборку, startup нового JAR и игровую приёмку (настоящая ковка, полный склад, два NPC, pause/restart).
Main, ветки Ники/MiniMax, server/**, Settlement*, склад Kilo и пользовательские миры не изменены. Последующие HEAD #72 требуют новой проверки: этот отчёт не выдаёт будущие изменения за уже проверенные.
