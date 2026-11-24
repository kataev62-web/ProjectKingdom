# Манифест зависимостей NPC

Снимок 24.11.2026. Источник исходной конфигурации — build.gradle на 407f68f330da2c1572a6a3c94a526e9dcb8ccc66, versions.json из ProjectKingdom-Private-Server-Test.zip и ProjectKingdom-KLauncher-1.20.1.zip. Серверный профиль base задаёт Forge 47.3.0 и тот же TFC/Create/Patchouli, что клиентский. Профиль запуска KLauncher задаёт MCA 7.6.23, Architectury 9.2.14 и отдельно указывает Forge 47.4.10 для полного набора с Siegeworks и дополнениями. Для NPC сохранён Forge 47.3.0. Документ относится к зафиксированным профилям и исходникам, фактически установленные моды рабочего сервера не инспектировались.

JAR загружены из официальных ссылок ниже. Для Modrinth проверен исходный SHA-512 из API, затем вычислен SHA-256. Для Forge проверен SHA-1 0deb8e547a9e3f098a4c9f082903499703c52f5d из базового пакета. [Машиночитаемая фиксация](../../scripts/npc-unified-life/dependencies.lock.json) содержит полные URL. SHA исходных ZIP относится к полученным архивам GitHub, Git commit отдельно фиксирует их содержимое.

## Бинарники

| Версия | Файл | Источник | SHA-256 |
| --- | --- | --- | --- |
| 7.6.23+1.20.1 | `minecraft-comes-alive-7.6.23+1.20.1-universal.jar` | [Modrinth](https://modrinth.com/mod/minecraft-comes-alive-reborn/version/fx0b1OnC) | `96131567142cc06bde3ed49f74e85614c0f9af5b60ccceafe0ac0db907068a85` |
| 7.7.0-beta.2+1.20.1 | `minecraft-comes-alive-7.7.0-beta.2+1.20.1-universal.jar` | [Modrinth](https://modrinth.com/mod/minecraft-comes-alive-reborn/version/LNwJsOHE) | `a202fbf1354786e7beeaabddfc9e79396b0a088acbec3b996837ebb0d9883c0a` |
| 1.20.1-1.4.0-Forge | `playerengine-forge-1.20.1-1.4.0.jar` | [Modrinth](https://modrinth.com/mod/playerengine/version/wFlD9yTq) | `82ed90f8704aa9283923216220747d0eb28a4a4659610408d3e7bc8f793b99c4` |
| 1.20.1-1.4.0-Forge | `player2npc-forge-1.20.1-1.4.0.jar` | [Modrinth](https://modrinth.com/mod/player2npc/version/IjeXWi8p) | `fae539d756808127113524183eb6c96d590ac0eb387760a4fe2510801c7315f3` |
| 1.2.3-forge1.20.1 | `betterworld-1.2.3-forge1.20.1.jar` | [Modrinth](https://modrinth.com/mod/betterworld-village/version/GTVKkD2T) | `853dc18f5b63c4975bcf5552367fdca43428c914020deb34df0f7b244e18e6f0` |
| 1.2.8-forge1.20.1 | betterworld-1.2.8-forge1.20.1.jar | [Modrinth](https://modrinth.com/mod/betterworld-village/version/A2JecgBb) | 480331f2080fcb8f21c5ec01e8b4b504d5edef49bda4eb25bbc4d7d0c0adf8b2 |
| 3.2.26 | `TerraFirmaCraft-Forge-1.20.1-3.2.26.jar` | [Modrinth](https://modrinth.com/mod/terrafirmacraft/version/XJqVIULL) | `93ea2fb5a1188f4dc64092fc9159ccaddd95bb5e8ae4f3c4a9e409ea5ff90de8` |
| 1.20.1-0.5.1.j | `create-1.20.1-0.5.1.j.jar` | [Modrinth](https://modrinth.com/mod/create/version/6R069CcK) | `a18763be04f6a7921d2e3bc5e9150b0bb5bc89b5ef45a607e93939972021f932` |
| 9.2.14+forge | `architectury-9.2.14-forge.jar` | [Modrinth](https://modrinth.com/mod/architectury-api/version/1MKTLiiG) | `218b471d0b8a1f6cda14cfc1beb9eeb0df54304500acc6c5613d9b88ec65d9af` |
| 1.20.1-85-forge | `Patchouli-1.20.1-85-FORGE.jar` | [Modrinth](https://modrinth.com/mod/patchouli/version/94dtOLgZ) | `05f7b5d52f6b8f0fd7f8b4822fa07a192d1394d83a264309c9d221d6d4fd21c5` |
| 1.20.1-47.3.0 | `forge-1.20.1-47.3.0-installer.jar` | [Forge Maven](https://maven.minecraftforge.net/net/minecraftforge/forge/1.20.1-47.3.0/) | `6018abce95cc058768e365865fd7cf3bd31b5c553231daf885dc385fcd6d875a` |

## Исходники

| Назначение | Ревизия | Архив SHA-256 |
| --- | --- | --- |
| mca-source | [5e56d0d5b155b9175260673c99a6d074d62809a5](https://codeload.github.com/Luke100000/minecraft-comes-alive/zip/5e56d0d5b155b9175260673c99a6d074d62809a5) | `661c95c586fbae8058c8ebb9891861189733cdc05ce60c0c7da2d799b100f41b` |
| mca-next-source | [45a83cff9bda0f48422fd4a01e8e52fd85c9bfd4](https://codeload.github.com/Luke100000/minecraft-comes-alive/zip/45a83cff9bda0f48422fd4a01e8e52fd85c9bfd4) | `0299c86326485f8f1436eb73ff93729b840e211af9cad1b76d3d55c26a75c466` |
| engine-source | [4a885a4f19c1ccce37a0004771965fd4b098a484](https://codeload.github.com/Goodbird-git/PlayerEngine/zip/4a885a4f19c1ccce37a0004771965fd4b098a484) | `a828618e2f96821f49d661d7694660defa3c8b4b8b39cbbd10ae2e0997615131` |
| player2-source | [32f6645ff42f3b556f779155eff454ac38abe35c](https://codeload.github.com/Goodbird-git/Player2NPC/zip/32f6645ff42f3b556f779155eff454ac38abe35c) | `b76de30b0b0205f0a668e6f75cec2a8b2aeb8c03e542d401b0d2aca3aed55cd6` |
| tfc-source | [51388b52ecf1f876d74184d9e60e9ea03f7ba3a6](https://codeload.github.com/TerraFirmaCraft/TerraFirmaCraft/zip/51388b52ecf1f876d74184d9e60e9ea03f7ba3a6) | `5ea0e0e061b887ec8a17260bca487da45ec045bff559175e631a16059255c7c5` |
| create-source | [d39f89983a6c36dcacda6635c2094c826703ed49](https://codeload.github.com/Creators-of-Create/Create/zip/d39f89983a6c36dcacda6635c2094c826703ed49) | `5821f1bcb7d7206b0f9b383b5c2a3f0089b56a076050ab0147bc709b064ec67c` |

Исходники MCA 7.6.23 и 7.7.0-beta.2 и TFC v3.2.26 взяты с соответствующих тегов. Для Create взята финальная ревизия ветки mc1.20.1/0.5.1 от даты выпуска. [Wiki Create](https://github.com/Creators-of-Create/Create/wiki/Depending-on-Create/366760a1b0317640b80553efc20ff441118f3821) указывает соответствующий dev-билд 0.5.1.j-55; связь конкретного JAR и коммита не подтверждена метаданными JAR. Для PlayerEngine и Player2NPC закреплены публичные ревизии веток 1.20.1, они объявляют 1.1.11 и 1.1.7. Для точных 1.4.0 используются опубликованные бинарники. BetterWorld предоставляет релиз, но официальный исходный архив не найден.

## Зависимости и условия работы с кодом

| Мод | Диапазон Forge для данного JAR | Зависимости | Условия работы с кодом |
| --- | --- | --- | --- |
| MCA 7.6.23 | 47.0.0+ | Architectury ≥9.0.8, закреплена 9.2.14 | GPL-3.0-only на странице проекта. Проверить обязательства перед распространением связанной сборки или использованного кода. |
| MCA 7.7.0-beta.2 | 47.0.0+ | Architectury не требуется | GPL-3.0-only. Контрольная ветка исследования. |
| TFC 3.2.26 | 47.3.0 входит в диапазон ≥47.2.6 | Patchouli ≥1.20.1-81-FORGE; JEI необязателен | EUPL-1.2 на проекте и JAR. |
| Create 0.5.1.j | 47.1.3+ | Flywheel 0.6.11-13 и Registrate MC1.20-1.3.3 внутри JAR; отдельные копии не нужны | MIT в конкретном теге/ветке и этом JAR. Метаданные новых выпусков могут отличаться. |
| PlayerEngine 1.4.0 | 47+ по mods.toml | Architectury ≥9.2.14; schematic4j 1.1.0 внутри JAR | LGPL-3.0 в исходном LICENSE и в JAR, MIT в каталоге Modrinth. Для заимствования требуется установить лицензию соответствующего коммита и отдельных компонентов. |
| Player2NPC 1.4.0 | 47+ по mods.toml | Architectury ≥9.2.14 и PlayerEngine ≥1.20.1-1.4.0 | MIT на Modrinth; в ветке нет отдельного LICENSE, поле JAR — шаблон. Код из 1.4.0 не переносится в Kingdom без подтверждения условий. |
| BetterWorld 1.2.3 | 47+ по mods.toml | Forge, Minecraft | MIT в релизе, API и mods.toml. Свой SteveEntity, отдельные правила активов. Публичная документация используется для собственной реализации. |
| Architectury 9.2.14 | 46+ | Forge, Minecraft | LGPL-3.0-only по Modrinth. |
| Patchouli 1.20.1-85-FORGE | 47.1.3+ | Forge, Minecraft | CC-BY-NC-SA-3.0 по Modrinth, нужен только внешний JAR. |

## Воспроизведение

Требуются Python 3, curl, JDK 17 и сеть к источникам. Для этой проверки использован Temurin 17.0.21.1+2 Linux x64. [Архив Temurin](https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.21.1%2B2/OpenJDK17U-jdk_x64_linux_hotspot_17.0.21.1_2.tar.gz), SHA-256 0b0ac5eab6db6abaec68c9913bcddb31321e5da411c45073d520d9c125e6f7b4. На Windows можно использовать JDK 17 с JAVA_HOME и gradlew.bat. Gradle Wrapper 8.8 проверяет SHA-256 дистрибутива. ForgeGradle закреплён на 6.0.54, точное значение разрешённое исходной сборкой.

По умолчанию загрузка идёт в соседний с репозиторием каталог kingdom-npc-assets. Профили core, playerengine, player2, betterworld, betterworld-new и mca-next предназначены для отдельных тестовых каталогов. Полный профиль 47.4.10, Siegeworks и дополнения KLauncher лежат вне целевого базового набора исследования.

```bash
python3 scripts/npc-unified-life/prepare.py
python3 scripts/npc-unified-life/prepare.py --verify-only
./gradlew clean test build
python3 scripts/npc-unified-life/prepare.py --profile core --server-dir ../npc-test-core
```

Чтобы добавить собранный Kingdom, перед staging передать его через --kingdom-jar. Для отдельного тестового сервера открыть подготовленный каталог, запустить установленной Java 17 команду java -jar <путь к forge-1.20.1-47.3.0-installer.jar> --installServer и выполнить ./run.sh nogui (Windows: run.bat nogui). Для нового тестового мира TFC задать level-type=tfc:overworld в server.properties. Для запуска мира владелец принимает Minecraft EULA, сервер задаётся на 127.0.0.1 со своим портом. Для автоматической проверки запуска из корня проекта выполнить python3 scripts/npc-unified-life/smoke.py ../npc-test-core. Скрипт проверяет готовность мира и останавливает сервер командой stop. Пустой каталог профиля нужен для исключения смешивания разных ревизий. В исследовании тестовые каталоги и сторонние JAR хранятся вне Git.

В окружении с HTTP(S)-прокси передать JVM -Dhttp.proxyHost / -Dhttp.proxyPort / -Dhttps.proxyHost / -Dhttps.proxyPort и стандартный доверенный CA вашей среды через JAVA_TOOL_OPTIONS. Первая сборка требует загрузки ForgeGradle, Forge, Mojang mappings, зависимостей Maven и выполнения MCP setup. Для воспроизводимости не заменять зависимость вручную на JAR с другим хешем.

[Результаты проверок профилей](COMPATIBILITY.md).
