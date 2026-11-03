from pathlib import Path
import json,re,zipfile
p=Path(__file__).resolve().parent
sources=list((p/'src/main/java').rglob('*.java'))
assert len(sources)>=10, 'Missing source files'
assert (p/'src/main/resources/META-INF/mods.toml').is_file()
toml=(p/'src/main/resources/META-INF/mods.toml').read_text()
gradle=(p/'build.gradle').read_text()
assert 'modId="kingdomnpc"' in toml
gradle_version=re.search(r"(?m)^version\s*=\s*['\"]([^'\"]+)['\"]",gradle)
mod_version=re.search(r'(?m)^version\s*=\s*"([^\"]+)"',toml)
assert gradle_version and mod_version, 'Missing release version'
assert gradle_version.group(1)==mod_version.group(1), 'Gradle and mods.toml versions disagree'
assert gradle_version.group(1)=='0.1.0-stage0-voice3.3-ai1', 'Unexpected AI1 release version'
installer=(p/'02_install_windows.bat').read_text()
assert 'kingdomnpc-'+gradle_version.group(1)+'.jar' in installer, 'Installer selects a different release'
for name in ['KingdomNpcMod','KingdomNpcData','KingdomNpcMenu','KingdomTerritories','NpcLogistics','NpcFieldWork','NpcAutonomyPlanner']:
 assert any(f.name==name+'.java' for f in sources),name
assert 'level.hasChunkAt' in (p/'src/main/java/ru/projectkingdom/npc/NpcFieldWork.java').read_text()
print('OK: project structure, versions, core Java source files, loaded-chunk guard')
print('ATTENTION: static verification only, NOT a Forge compilation or Minecraft gameplay test')

assert (p/'src/main/java/ru/projectkingdom/npc/KingdomClaimProtection.java').exists()
print('OK: claim protection source present')

assert (p/"src/main/java/ru/projectkingdom/npc/KingdomCurrency.java").exists()
assert (p/"src/main/resources/data/kingdomnpc/recipes/wallet.json").exists()
print("OK: currency source and wallet recipe")

assert (p/"src/main/java/ru/projectkingdom/npc/KingdomPayrollData.java").exists()
assert "case PAYROLL ->" in (p/"src/main/java/ru/projectkingdom/npc/KingdomMainMenu.java").read_text()
print("OK: payroll SavedData and menu wiring present")

core=p/'src/main/java/ru/projectkingdom/core'
for name in ['IdentitySavedData','AuthGate','MigrationGate','SpawnResolver','BookModel','KingdomBookItem','KingdomBookGuardian','ModuleRegistry','PaymentProvider']:
 assert (core/(name+'.java')).is_file(),name
assert (p/'src/test/java/ru/projectkingdom/core/Stage0GameCoreAcceptanceTest.java').is_file()
print('OK: Stage 0 identity, auth boundary, migrations, spawn, permanent Book, module isolation and acceptance tests')

assistant=p/'src/main/java/ru/projectkingdom/assistant'
assistant_classes=[
 'KingdomAssistantData','KingdomAssistantEntity','KingdomAssistantRegistry',
 'WorldSurveyService','SettlementAuditService','AssistantPreviewService',
 'AssistantCreativeBuildService','KingdomAssistantBuildData','AssistantDiagnosticsRegistry',
 'FunctionalAuditService','ModpackAuditService','KingdomAssistantMemoryData',
 'AssistantPlanner','RuleBasedAssistantPlanner','HybridAssistantPlanner',
 'AssistantPlanValidator','AssistantModelClient','DisabledAssistantModelClient',
 'AssistantReportStore'
]
for name in assistant_classes:
 assert (assistant/(name+'.java')).is_file(),name
registry=(assistant/'KingdomAssistantRegistry.java').read_text()
mod_main=(p/'src/main/java/ru/projectkingdom/npc/KingdomNpcMod.java').read_text()
assert 'KingdomAssistantEntity::new' in registry and 'REGISTRY_NAME = "kingdom_assistant"' in registry
assert 'KingdomAssistantRegistry.register' in mod_main
print('OK: assistant domain, dedicated entity and Forge registration')

sessions=(p/'src/main/java/ru/projectkingdom/npc/KingdomArchitectSessions.java').read_text()
assert 'Purpose { SURVIVAL_SITE, ASSISTANT_CREATIVE }' in sessions
assert 'KingdomSiteBuilder.placeAt' in sessions
assert 'session.purpose() == Purpose.ASSISTANT_CREATIVE' in sessions
survey=(assistant/'WorldSurveyService.java').read_text()
build_data=(assistant/'KingdomAssistantBuildData.java').read_text()
build_service=(assistant/'AssistantCreativeBuildService.java').read_text()
reports=(assistant/'AssistantReportStore.java').read_text()
assert 'MAX_SAMPLED_COLUMNS = 9_216' in survey
assert 'MAX_CELLS = 8_192' in build_data
assert 'MAX_CHANGES_PER_TICK = 16' in build_service
assert 'MAX_REPORT_BYTES = 1_048_576' in reports and 'MAX_REPORTS = 20' in reports
assert 'StandardCopyOption.ATOMIC_MOVE' in reports
print('OK: preview purpose split and bounded survey, build and report limits')

rules_path=p/'src/main/resources/data/kingdomnpc/assistant/mod_rules.json'
rules=json.loads(rules_path.read_text(encoding='utf-8'))
installed={item['modId']:item['recommendation'] for item in rules['installed']}
for mod_id in ['minecraft','forge','kingdomnpc','tfc','create','patchouli']:
 assert installed.get(mod_id)=='KEEP',mod_id
assert installed.get('minecolonies')=='REMOVE'
assert any(item.get('modId')=='spark' and item.get('recommendation')=='ADD'
           for item in rules['additions'])
print('OK: explicit safe mod-audit rules')

assistant_tests=p/'src/test/java/ru/projectkingdom/assistant'
for name in ['KingdomAssistantAuthorizationTest','WorldSurveyServiceTest',
             'AssistantPreviewAuthorizationTest','AssistantCreativeBuildServiceTest',
             'AssistantPlanValidatorTest','KingdomAssistantMemoryDataTest',
             'AssistantReportStoreTest']:
 assert (assistant_tests/(name+'.java')).is_file(),name
network=(p/'src/main/java/ru/projectkingdom/network/KingdomNetwork.java').read_text()
assert 'PROTOCOL="13-ai1"' in network
assert (p/'docs/ASSISTANT_AI_TEST_GUIDE_RU.md').is_file()
print('OK: assistant regression tests, AI1 protocol and manual acceptance guide')
