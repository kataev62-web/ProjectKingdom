package ru.projectkingdom.npc.voice;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class NpcSpeechTextTest {
 @Test void legacyNamesAreBoundedWithoutSplittingSupplementaryCharacters(){
  String name=NpcSpeechText.limit("😀".repeat(70),64,"Житель");
  assertEquals(64,name.length());assertEquals("😀".repeat(32),name);
  assertEquals("А".repeat(63),NpcSpeechText.limit("А".repeat(63)+"😀",64,"Житель"));
 }
 @Test void emptyAndNullNamesHaveValidFallbackAndDetailsMayBeEmpty(){
  assertEquals("Житель",NpcSpeechText.limit("   ",64,"Житель"));
  assertEquals("Житель",NpcSpeechText.limit(null,64,"Житель"));
  assertEquals("",NpcSpeechText.limit(null,512,""));
  assertEquals("Анна",NpcSpeechText.limit(" Анна ",64,"Житель"));
 }
}
