# Адаптер MCA 7.6.23

Исходный тег [7.6.23+1.20.1](https://github.com/Luke100000/minecraft-comes-alive/tree/5e56d0d5b155b9175260673c99a6d074d62809a5), контрольный [7.7.0-beta.2](https://github.com/Luke100000/minecraft-comes-alive/tree/45a83cff9bda0f48422fd4a01e8e52fd85c9bfd4). Исходные имена методов ниже используют Yarn. Сборка Kingdom использует Mojang official mappings. В универсальном JAR Forge-классы физически лежат под forge/net/mca, а серверный лог подтверждает runtime-имя forge.net.mca.entity.VillagerEntityMCA. В dev-профиле проверить имя класса, переданное трансформеру, отдельным запуском. Для первого внедрения подходят ванильные типы на границе и узкие вызовы MCA через адаптер. Подключение универсального JAR в compileOnly требует отдельной проверки ремаппинга, включая Forge relocation. Тест совместимости адаптера не должен автоматически переключаться на другую ветку MCA.

| Зона | Точки привязки |
| --- | --- |
| Идентичность | net.mca.entity.EntitiesMCA, VillagerEntityMCA extends VillagerEntity. ID типов mca:male_villager и mca:female_villager. UUID ванильной сущности, class name и временный entity/network ID не служат ключами записи. |
| Brain | VillagerEntityMCA.getVillagerBrain / reinitializeBrain; net.mca.entity.ai.brain.VillagerBrain, VillagerTasksMCA и net.mca.entity.ai.SchedulesMCA. |
| Расписание | VillagerTasksMCA строит наборы CORE, WORK, MEET, REST, IDLE, PANIC. WORK зависит от JOB_SITE; WALK_TARGET используется задачами перемещения. Chore и ванильный VillagerProfession остаются внутренними понятиями MCA. |
| Семья | VillagerEntityMCA.getRelationships, BreedableRelationship, net.mca.server.world.data.FamilyTree и FamilyTreeNode. Проверить родство и супругов на сервере по UUID. |
| Предметы | VillagerEntityMCA.getInventory, createMenu, onInvChange. MCA выдаёт свой инвентарь через меню 9×3. Рабочий доступ — view на реальные слоты, MCA отвечает за сохранение. |
| Движение и аварии | VillagerEntityMCA.getNavigation с отдельной веткой для езды. PANIC, сон, бой и активный разговор возвращают навигацию MCA. При STOP снять только принадлежащую Kingdom задачу движения. |
| Сохранение | VillagerEntityMCA.readCustomDataFromNbt / writeCustomDataToNbt сохраняют отношения и инвентарь. Kingdom хранит собственный SavedData отдельно. |
| Смерть и превращение | VillagerEntityMCA.onDeath и relations.onDeath; convertTo переносит UUID при зомбификации. Проверять преобразование и выгрузку отдельно от окончательной смерти. |
| Взаимодействие | VillagerEntityMCA.interactMob, net.mca.client.gui.InteractScreen. Добавить действия «Работа» и «Зарплата» с серверной проверкой права, живой сущности, измерения, дистанции и действующего контракта. |
| Здания | net.mca.client.gui.BlueprintScreen, network.c2s.ReportBuildingMessage, server.world.data.VillageManager / Village / Building. Привязать результат обследования к существующему ID здания Kingdom, пока принадлежность и права подтверждает Kingdom. |

Точку подавления конфликтующих задач ставить на запуск конкретного источника маршрута в Brain, с активным разрешением на смену. Постоянное NoAI обрывает штатную жизнь MCA. После освобождения смены восстановить нормальное расписание, не записывая чужие memories обратно из устаревшей копии. Не изменять код MCA в этой ветке исследования.

У проверенных тегов 7.6.23 и 7.7.0-beta.2 пути ключевых Java-классов остаются net.mca, а Forge-упаковка — forge/net/mca. Для сравнения этих двух JAR полноценного переименования пакета не обнаружено. В 7.7 beta убрана обязательная Architectury и изменена упаковка клиентских и платформенных частей. Допуск к миграции требует отдельного прогрева копии сохранения с UUID, браком, детьми, отношениями, инвентарём с NBT, именами, зданиями и данными Kingdom, с повторной загрузкой и планом восстановления из резервной копии. Выбранная 7.6.23 соответствует профилю запуска из архива владельца.

## Конфигурация существующего клиента

В манифесте KLauncher лежит mca.json с overwriteOriginalVillagers=true, overwriteOriginalZombieVillagers=false, allowedSpawnReasons=[structure], enableVillagerChatAI=false. Это профиль владельца для запуска, серверные smoke использовали новые конфигурации по умолчанию. Перед миграцией сверить реальные настройки сервера. У Kingdom есть собственные спавны, рекрутинг и общий instanceof Villager, поэтому ограничение причин спавна MCA само по себе не гарантирует, что каждый старый или созданный Kingdom житель уже преобразован. Поменять штатные точки рождения/найма Kingdom, проверить конверсию на копии и сохранить обратную связь по неподдерживаемым сущностям.
