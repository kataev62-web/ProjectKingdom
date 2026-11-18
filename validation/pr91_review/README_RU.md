# Issue #91 — повторный аудит: R1/R2 закрыты, новый R3 BLOCKED

Точный HEAD #72 на старте: `638ce74193ca4173a677569b8adf3e0d50a9611f`; upstream CI SUCCESS: https://github.com/kataev62-web/ProjectKingdom/actions/runs/44368811257 . База #71: `49db1b411c43653c29b55275126cdf9116b3236c`. Результаты привязаны к этим SHA, не к будущим изменениям.

## Устранённые блокеры #84
- Четыре прежних независимых теста вызова actual private методов проходят: copying getter, rejected setter, ложная доставка, merge в копию. Из fixture #84 изменено только имя класса (в #72 автор добавила одноимённый test), сами четыре сценария сохранены.
- Ещё два независимых теста проверяют сохранение количества/вложенного ItemStack NBT и отказ полного склада. Синтетический tag в JUnit не выдаётся за реальную TFC HeatCapability или выполненный нагрев.
- Native production API проверен на **настоящем зарегистрированном рецепте**, в реальном Forge 47.3.0/TFC 3.2.26/Create 0.5.1.j/Patchouli 1.20.1-85-forge classloader, с JAR кандидата #72:
```
PROBE91 loader=cpw.mods.modlauncher.TransformingClassLoader@11900483
PROBE91 native=tfc:anvil/black_bronze_rod adapter=tfc:anvil/black_bronze_rod
PROBE91 PASS
```
Агент получает уже существующий AnvilRecipe из серверного RecipeManager, сравнивает native SRG ID и исправленный приватный typed adapter recipeId. Он не создаёт рецепт, не трансформирует классы/предметы. Прежний reflective getId в production по-прежнему отсутствует, но исправленный типизированный вызов работает — это больше не blocker.
- Тестовый сервер кандидата дошёл до Done, ответил на list, сохранил мир и завершился exit 0. Ранее явно принятая владельцем EULA использована только для нового одобренного disposable стенда, не для production.

## #74 и ancestry
#72 содержит #66; #71 уже содержит #66, поэтому повторного применения #66 не требуется. Проверенный #74 `db0df290671cd5742d09f6d2cbdc99071af0b26e` НЕ является предком #72, но лимиты native item/container в intoChest уже сохранены, а `KingdomSmithyPhysicalGuardsTest.java` идентичен между этими HEAD. Отдельных необходимых deltas #74 не найдено; слияние старой ветки могло вернуть устаревшие helpers. Ничего из неё не перенесено повторно.

## Новый blocker R3: native партия не согласована с заказом
Официальный JAR TFC 3.2.26, `data/tfc/recipes/anvil/black_bronze_rod.json`, задаёт результат `tfc:metal/rod/black_bronze`, **count=2**. `javap -c AnvilRecipe` подтверждает, что assemble использует ItemStackProvider.getStack, не принудительный единичный результат. Это тот же настоящий рецепт, найденный production API probe.

Меню разрешает quantity=1 по умолчанию (1..64) и передаёт его в OrdersData.create без проверки native batch. Воспроизведение actual класса кандидата:
```
create(firm, worker, "tfc:anvil/black_bronze_rod", "tfc:metal/ingot/black_bronze", 1) = true
working(firm, worker) = true
ready(firm, worker, "tfc:metal/rod/black_bronze", 2) = false
phase = WORKING
create(replacement quantity=2) = false
```
Нормальный двухпредметный результат отклонён условием count>remaining. После native ковки tick не устанавливает READY, предмет остаётся в наковальне и затем определяется как другой материал; активный заказ нельзя заменить через меню. Это **блокировка производственного заказа**, не доказательство дюпа. Живая ковка NPC не выполнялась: доказан конфликт actual state-machine с официальным native result и достижимым текущим menu default.

`IndependentSmithyBatchReviewTest` читает count из официального JAR, не из выдуманного рецепта. Тест допускает безопасный отказ до начала работы или корректный учёт native партии; на текущем коде оба отсутствуют. Возможный будущий UI-only preflight нужно отдельно проверить на достижимость create, а не искусственно требовать TFC-зависимости от SavedData. Минимальное предложение владельцу: определить единицы количества (операции/изделия), до расхода сырья отклонять или нормализовать некратный заказ либо учитывать целую партию. Не обрезать/создавать предметы ради счётчика. Проверить quantity 1/2/3, remaining меньше партии и безопасное восстановление уже застрявших заказов.

Нике передан точный repro: https://github.com/kataev62-web/ProjectKingdom/pull/72#issuecomment-6988347034 . Её бизнес-код/ветки не менялись.

## Фактически запущено
- Архив точного #72 + старые 4 independent tests: полный `./gradlew test build` PASS (52s).
- Расширенные 6 independent transfer/NBT tests: PASS (25s).
- До обнаружения R3 локальная tentative интеграция #71+#72: `./gradlew test build` PASS (38s), **620 tests, 0 failures/errors/skips**. Этот green НЕ является приёмкой: новый batch-case ещё не входил в suite.
- После добавления R3: группа independent tests — **7 выполнено, 6 passed, 1 failed**, 0 errors/skipped, exit 1 (21s). Красный именно nativeTwoRodBatchMustBeSupportedOrRejectedBeforeWork; results.json содержит assertion и runtime markers.
- Static `03_verify_project.py` и 5 stdlib runner tests PASS.
- Review-ветка возвращена на чистую бизнес-базу #71; полный test/build PASS (44s). Ни candidate-интеграция, ни её JAR не опубликованы; локальный scratch/stash сохранён для диагностики, пользовательские файлы не удалялись.

## Воспроизведение без слияния
Настройте Java17/Gradle, возьмите официальный TFC JAR из mods.lock.json и новый каталог. Снимок кандидата не должен подменяться main:
```bash
git fetch origin pull/72/head:refs/remotes/origin/pr72
AUDIT=$(mktemp -d)
git archive 638ce74193ca4173a677569b8adf3e0d50a9611f | tar -x -C "$AUDIT"
cp validation/pr91_review/IndependentSmithy*ReviewTest.java "$AUDIT/src/test/java/ru/projectkingdom/npc/"
export TFC_REVIEW_JAR=/absolute/path/TerraFirmaCraft-Forge-1.20.1-3.2.26.jar
cd "$AUDIT"
bash ./gradlew test --tests '*IndependentSmithy*ReviewTest' --no-daemon --max-workers=2
```
Ожидается 6 green + 1 red, а не скрытый PASS. `TFC_REVIEW_JAR` нужен только независимому тесту официального JSON. XML находятся в build/test-results/test. Будущие фиксы требуют повторной проверки нового точного SHA.

Для production API: скомпилировать `RecipeAdapterAgent.java` через javac --release 17; собрать agent JAR с `Premain-Class: RecipeAdapterAgent`; передать JAVA_TOOL_OPTIONS=-javaagent:/absolute/path/agent.jar runner на JAR **кандидата**, не базы #71. Сервер запускается лишь после законного принятия EULA владельцем, в новой пустой папке. Подробная процедура runner унаследована от #71; PROBE91 FAIL/INCONCLUSIVE нельзя считать PASS. Реальный API-прогон уже выполнен, результаты выше.

## Решение
**Новая RC не публикуется до исправления R3.** Публичная ветка/PR — база #71 + независимые fixtures, probe, отчёт и отключённые upload/package шаги CI. В ней нет бизнес-изменений #72. CI review-ветки проверяет только базу и компиляцию probe; green не выдаётся за тест кузницы или отсутствие R3.
Main, server/**/Settlement*, Kilo, фермер #88, экономика #89, MiniMax и исходный пользовательский мир не изменены. Вход клиента, настоящая NPC-ковка/доставка и crash-atomicity не проверены. После фикса R3 нужны повторный аудит, новая интеграция и startup именно нового JAR; сейчас делать это преждевременно.
