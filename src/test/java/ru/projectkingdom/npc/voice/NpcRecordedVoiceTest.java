package ru.projectkingdom.npc.voice;
import java.util.*;
import org.junit.jupiter.api.Test;
import ru.projectkingdom.npc.NpcDialogueEngine;
import static org.junit.jupiter.api.Assertions.*;
final class NpcRecordedVoiceTest {
 @Test void stableVoicesCoverEveryGenderAndExtremeIdentity(){
  var male=new HashSet<NpcVoiceBank>();var female=new HashSet<NpcVoiceBank>();
  for(int i=0;i<1000;i++){
   UUID id=new UUID(Long.MIN_VALUE+i,Long.MAX_VALUE-i*17L);
   var m=NpcVoiceBank.forNpc(id,"male");var f=NpcVoiceBank.forNpc(id,"female");
   assertEquals(m,NpcVoiceBank.forNpc(UUID.fromString(id.toString()),"male"));
   assertTrue(m.id().startsWith("male_"));assertTrue(f.id().startsWith("female_"));male.add(m);female.add(f);
  }
  assertEquals(3,male.size());assertEquals(3,female.size());
  assertThrows(IllegalArgumentException.class,()->NpcVoiceBank.fromId("../file"));
 }
 @Test void everyIntentHasARecordingAndKeepsChangingDetailsSeparate(){
  var s=state(90,90,90,true,false,true,true);
  var identity=NpcDialogueEngine.reply("Кто ты?",s);
  assertEquals(NpcVoiceLine.IDENTITY,identity.line());assertTrue(identity.text().contains("Анна"));
  assertFalse(identity.line().text().contains("Анна"));
  for(String phrase:new String[]{"Привет","Как дела?","Чем помочь?","Вот хлеб","Хочу торговать","Хочу тебя нанять","Уволь","Наши отношения","Открой меню","???"}){
   var r=NpcDialogueEngine.reply(phrase,s);assertNotNull(r.line());assertEquals(r.line(),NpcVoiceLine.fromId(r.line().id()));
  }
 }
 @Test void recordingsFollowActualNeedsAndContractState(){
  assertEquals(NpcVoiceLine.DANGER,NpcDialogueEngine.reply("Как дела?",state(10,10,10,false,false,false,false)).line());
  assertEquals(NpcVoiceLine.HUNGRY,NpcDialogueEngine.reply("Как дела?",state(10,90,90,false,false,true,false)).line());
  assertEquals(NpcVoiceLine.TIRED,NpcDialogueEngine.reply("Как дела?",state(90,10,90,false,false,true,false)).line());
  assertEquals(NpcVoiceLine.WAGES,NpcDialogueEngine.reply("Как работа?",state(90,90,90,true,true,true,true)).line());
  assertEquals(NpcVoiceLine.WORKSTATION,NpcDialogueEngine.reply("Как работа?",state(90,90,90,true,false,true,false)).line());
 }
 @Test void catalogContainsTwentySevenBoundedUniqueLines(){
  assertEquals(27,NpcVoiceLine.values().length);var ids=new HashSet<String>();
  for(var l:NpcVoiceLine.values()){assertTrue(ids.add(l.id()));assertTrue(l.id().matches("[a-z_]+"));assertFalse(l.text().isBlank());assertTrue(l.text().length()<64);}
  assertThrows(IllegalArgumentException.class,()->NpcVoiceLine.fromId("../file"));assertNotEquals(NpcVoiceLine.BREAD_THANKS,NpcVoiceLine.BREAD_MISSING);
 }
 private static NpcDialogueEngine.Snapshot state(int h,int e,int safety,boolean employed,boolean paused,boolean home,boolean station){
  return new NpcDialogueEngine.Snapshot("Анна Тестова","farmer",h,e,90,safety,employed,paused,home,station,"haul",10);
 }
}
