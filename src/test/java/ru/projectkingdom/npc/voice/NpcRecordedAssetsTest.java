package ru.projectkingdom.npc.voice;
import java.nio.file.*;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class NpcRecordedAssetsTest {
 @Test void allOneHundredFiftySixRecordingsArePresentWithMeasuredDurations()throws Exception{
  Path assets=Path.of("src/main/resources/assets/kingdomnpc");var durations=new Properties();
  try(var in=Files.newInputStream(assets.resolve("voice/recordings.properties"))){durations.load(in);}
  assertEquals(156,durations.size());String sounds=Files.readString(assets.resolve("sounds.json"));
  for(var bank:NpcVoiceBank.values())for(var line:NpcVoiceLine.values()){
   Path file=assets.resolve("sounds/voice/"+bank.id()+"/"+line.id()+".ogg");byte[] data=Files.readAllBytes(file);
   assertTrue(data.length>1000);assertEquals("OggS",new String(data,0,4,java.nio.charset.StandardCharsets.US_ASCII));
   int ms=Integer.parseInt(durations.getProperty(bank.id()+"."+line.id()));assertTrue(ms>=250&&ms<=10000);
   assertTrue(sounds.contains("\"voice."+bank.id()+"."+line.id()+"\""));
   assertTrue(sounds.contains("kingdomnpc:voice/"+bank.id()+"/"+line.id()));
  }
 }
}
