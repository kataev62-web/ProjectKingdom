package ru.projectkingdom.npc.voice;
import java.nio.file.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class NpcRecordedPlaybackContractTest {
 @Test void recordedOutputNeverSynthesizesOrCreatesTemporaryWavs()throws Exception{
  String client=Files.readString(Path.of("src/main/java/ru/projectkingdom/npc/voice/client/NpcVoiceClient.java"));
  assertFalse(client.contains("synthesize("));assertFalse(client.contains("java.nio.file"));
  assertFalse(Files.exists(Path.of("src/main/java/ru/projectkingdom/npc/voice/client/WindowsSpeechSynthesizer.java")));
  assertFalse(Files.exists(Path.of("src/main/java/ru/projectkingdom/npc/voice/client/NpcSpeechPlayback.java")));
 }
 @Test void subtitleSeparatesRecordedWordsFromDynamicDetails(){
  var bubbles=new NpcSpeechBubbleStore(2);var id=UUID.randomUUID();
  bubbles.put(id,"Анна",NpcVoiceLine.IDENTITY.text(),"female",0,100,"Профессия: фермер");
  var speech=bubbles.find(id,0).orElseThrow();assertEquals("Расскажу о себе.",speech.text());assertEquals("Профессия: фермер",speech.details());
  bubbles.clear();assertTrue(bubbles.find(id,1).isEmpty());
 }
 @Test void fourSlotsCloseReplacedEvictedAndDisconnectedAudio(){
  var queue=new NpcSpeechPlaybackQueue<Probe>(4);var first=new Probe();queue.put(new UUID(0,0),first);
  for(int i=1;i<5;i++)queue.put(new UUID(0,i),new Probe());assertTrue(first.closed);assertEquals(4,queue.size());
  var old=queue.entries().get(new UUID(0,4));queue.put(new UUID(0,4),new Probe());assertTrue(old.closed);
  var rest=queue.entries();queue.clear();assertTrue(rest.values().stream().allMatch(p->p.closed));
 }
 @Test void explicitReplyCanStopAutomaticSpeechWithoutStoppingOtherExplicitSpeech(){
  var queue=new NpcSpeechPlaybackQueue<Probe>(4);var automatic=new Probe();var explicit=new Probe();
  var a=new UUID(0,1);var b=new UUID(0,2);queue.put(a,automatic);queue.put(b,explicit);
  queue.stopMatching(p->p==automatic);
  assertTrue(automatic.closed);assertFalse(explicit.closed);assertEquals(1,queue.size());
  assertSame(explicit,queue.entries().get(b));
 }
 private static final class Probe implements AutoCloseable{boolean closed;public void close(){closed=true;}}
}
