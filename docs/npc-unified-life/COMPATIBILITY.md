# Проверка совместимости

Платформа проверки — Linux, Minecraft 1.20.1, Temurin 17.0.21.1+2 и выделенный Forge 47.3.0. Установка Forge Installer завершилась сообщением The server installed successfully. Все серверные проверки выполнены в отдельных каталогах, с новым или синтетическим миром на loopback. Критерий smoke — мир достиг Done и корректно сохранился после stop. Это проверка загрузки, без клиентской сессии и игровых сценариев профессий.

| Профиль | Проверенные JAR | Результат на выделенном сервере |
| --- | --- | --- |
| core | TFC 3.2.26, Create 0.5.1.j, Patchouli 85, Architectury 9.2.14, MCA 7.6.23 | Мир готов, Done (32.772s), штатное завершение. |
| playerengine | core + PlayerEngine 1.4.0 | Мир готов, Done (31.430s), штатное завершение. PlayerEngine выдаёт предупреждение о ClientBlockBreakMixin и ошибку DistCleaner при попытке найти MultiPlayerGameMode. Физические операции NPC в этой проверке не вызывались. |
| player2 | playerengine + Player2NPC 1.4.0 | Мир готов, Done (31.619s), штатное завершение. Указанная ошибка mixin и дополнительные предупреждения Player2NPC. Ключи Player2 не задавались, живой API и спавн компаньона не проверялись. |
| betterworld | BetterWorld 1.2.3 отдельно | Не проходит CONSTRUCT. FMLModContainer пытается вызвать BetterWorld.<init>() и получает NoSuchMethodException. |
| betterworld-new | BetterWorld 1.2.8 отдельно | Та же ошибка CONSTRUCT и NoSuchMethodException. |
| mca-next | core, MCA 7.7.0-beta.2 вместо 7.6.23, Architectury оставлена | Мир готов, Done (27.409s), штатное завершение. Есть предупреждение об опциональном mixin и сообщение DistCleaner о LocalPlayer. |
| Kingdom + core | JAR Project Kingdom из проверяемой сборки и mods core | Новый мир с пресетом tfc:overworld достиг Done (27.662s). Проверены ответ на list, штатный stop, сохранение всех измерений и код выхода 0. |

Результат BetterWorld воспроизводится отдельно от Kingdom и других модов. Контракт конструктора в обоих JAR требует FMLJavaModLoadingContext, тогда как 47.3.0 при создании мода запрашивает конструктор без аргументов. Это несовместимость JavaFML на этапе загрузки. Для Kingdom предусмотрена реализация поведения на MCA без этого JAR.

PlayerEngine и Player2NPC имеют рабочий профиль загрузки и подтверждённые ограничения среды. Для интеграции в существующего жителя применяется прямой серверный адаптер Kingdom, так как совместимость исполнительного стека PlayerEngine с MCA и Forge BreakEvent не подтверждается запуском мира.

## Границы проверки

- Живой клиент, меню, применение физического действия и работа с MCA Brain относятся к реализации Codex.
- MCA 7.7 beta не включена в профиль core. Проверка синтетического сохранения описана ниже. Миграция рабочего мира, семьи, имущества и связей между модами требует копии конкретного мира.
- Нет доступа к живой конфигурации сервера владельца. Зафиксированные версии TFC/Create взяты из обоих архивов конфигурации владельца.

## Сборка и перенос MCA

Исходный checkout 407f68f330da2c1572a6a3c94a526e9dcb8ccc66 на JDK 17: ./gradlew clean test build → BUILD SUCCESSFUL, 570 тестов без ошибок и пропусков, прошёл reobfJar. В холодном окружении Gradle и Java используют явные параметры HTTPS-прокси и хранилище доверенных сертификатов. С закреплённым ForgeGradle 6.0.54 и Wrapper 8.8 повторно прошёл ./gradlew --offline clean test build (570 тестов). При прогретом кэше ForgeGradle сообщил о недоступности индекса клиентских ресурсов, серверной сборке это не помешало.

В копии синтетического мира MCA 7.6.23 был создан mca:male_villager с запоминаемым UUID и киркой в Inventory. После save-all flush и штатного завершения заменён только JAR MCA, выбран 7.7.0-beta.2, оставлена Architectury. Новая версия загрузила мир. Серверная команда data get подтвердила тот же UUID и тот же стек с Damage=7 и пользовательским NBT KingdomProbe. Семья, назначения работы и экономика рабочего мира в этом эксперименте не моделировались.

Проверенный базовый JAR kingdomnpc-0.1.0-stage0-voice3.3.jar имеет SHA-256 37582dc5be85c9e9b0c07038193d1d1d099edb43da5a95469489dedf798eb0a1. Проверка prepare.py по lock-файлу покрыла 17 файлов, включая исходные ZIP. Чистая выгрузка профиля betterworld и повторная проверка прошли. Проба повреждённого JAR завершилась ошибкой SHA-256.

## Текущий профиль 25.11.2026

Отдельная базовая проверка без Kingdom: Java Temurin 17.0.17+10, Minecraft 1.20.1, Forge 47.4.10, MCA 7.7.1-beta.5, Townstead 0.8.0, TFC 3.2.26, Create 0.5.1.j и Patchouli 85. Официальный Forge Installer: `The server installed successfully`. Все шесть артефактов профиля проверены по SHA-256 через `prepare.py --profile current-client --verify-only`. Новый TFC-мир достиг `Done (15.080s)`, команда `stop` сохранила измерения, процесс завершился с кодом 0. Локальный журнал: `/workspace/kingdom-npc-assets/current-baseline-smoke.log`, установленная база: `/workspace/.cloud-setup/forge47410`. Это проверка загрузки зависимостей, а не доказательство работы Kingdom или клиентского интерфейса.

В логе текущей пары MCA/Townstead есть нефатальные `RuntimeDistCleaner` ошибки попыток загрузить клиентские `Minecraft`/`Screen` и ошибка получения Yggdrasil public key в изолированной среде. Они воспроизводятся без Kingdom. Клиентский запуск и перенос рабочего мира этим тестом не проверены; MCA release notes предупреждают о несовместимости старых beta-сохранений.

Бинарный аудит через JDK 17 `javap -p -c`:

| Граница | Подтверждённый контракт |
| --- | --- |
| MCA entity | `forge.net.conczin.mca.entity.VillagerEntityMCA extends net.minecraft.world.entity.npc.Villager`; реестровые ID прежние. `m_35311_()` возвращает личный `SimpleContainer`; ванильный вызов `resident.getInventory()` остаётся корректным. |
| MCA inventory | `forge.net.conczin.mca.entity.UpdatableInventory` напрямую наследует `SimpleContainer`, объявляет только конструктор `(int)` и `update(Entity)`. Нет переопределений getItem/setItem/removeItem. Проверка допустимого кошелька должна разрешать это точное имя и прежнее `forge.net.mca.entity.UpdatableInventory` с проверкой непосредственного суперкласса. |
| MCA family | `getRelationships()` сохранился; `EntityRelationship.getPartnerUUID()` возвращает `Optional<UUID>`. `FamilyTreeNode` теперь использует `partner():UUID`, `id():UUID`; цепочка `getFamilyEntry().getPartner().getUuid()` текущему бинарнику не соответствует. |
| MCA brains | `getMCABrain()` — ванильный `Brain`; `getVillagerBrain()` — отдельный MCA wrapper. `m_35483_(ServerLevel)` пересоздаёт Brain и вызывает `VillagerTasksMCA.initializeTasks`; адаптер не должен удерживать старый экземпляр Brain после refresh. |
| Townstead extra tick | `VillagerServerTickMixin` внедряется в MCA `m_8107_()` HEAD/TAIL. HEAD вызывает `FatigueVillagerTicker.preAiStep()` и `CollapsePlayback.beforeAi()`; TAIL — `VillagerServerTickDispatcher.tick(MCA)`. В dispatcher отдельно выполняются потребление, назначение профессии, инструменты, hangout, guard/needs; одной очистки WALK_TARGET недостаточно. |
| Townstead behavior | Конкретные рабочие Behavior имеют SRG bridges `m_6114_(ServerLevel,LivingEntity):boolean` и `m_6737_(ServerLevel,LivingEntity,long):boolean`. Они позволяют отложить запуск/продолжение работы при lease, оставив штатный `stop` cleanup. `StationWorkTask`/`DiscoveredStationWorkTask` наследуют `ProducerWorkTask`. |
| Townstead emergency | `FatigueVillagerTicker.preAiStep` может при fatigue ≥16 вызвать `ShiftScheduleApplier.overrideToRest`, установить `needs.restOverrideActive` и прервать движение. `CollapsePlayback.beforeAi` при активном collapse останавливает Brain/navigation, стирает movement memories и обнуляет горизонтальную скорость. `CollapsePlayback.active(MCA)` и `needs.restOverrideActive()` доступны для обнаружения аварийного отдыха; lease следует освободить, чтобы native dispatcher продолжил восстановление. |

Townstead API предоставляет снимки состояния, отдельного публичного per-entity work opt-out в проверенном `TownsteadAPI` нет. Опциональные mixin-адаптеры Kingdom используют реальные имена методов Townstead 0.8.0 без compile-зависимости на MCA/Townstead; их интеграционная проверка относится к отдельному запуску собранного Kingdom. За пределами активной смены они пропускают нативные вызовы. Никакой NoAI для блокировки ходьбы текущий профиль не требует.
