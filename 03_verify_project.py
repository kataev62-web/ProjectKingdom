from pathlib import Path
import re,zipfile
p=Path(__file__).resolve().parent
sources=list((p/'src/main/java').rglob('*.java'))
assert len(sources)>=10, 'Missing source files'
assert (p/'src/main/resources/META-INF/mods.toml').is_file()
toml=(p/'src/main/resources/META-INF/mods.toml').read_text()
gradle=(p/'build.gradle').read_text()
assert 'modId="kingdomnpc"' in toml
assert "version = '0.1.0-stage0-dev'" in gradle
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
