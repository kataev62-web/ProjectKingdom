package ru.projectkingdom.npc;
public class NpcLifeTest {
 public static void main(String[] args) {
  assert NpcLife.clamp(-1)==0;
  assert NpcLife.clamp(101)==100;
  assert NpcLife.stepHunger(80,2)==68;
  assert NpcLife.stepEnergy(40,2,false,true)==60;
  assert NpcLife.feed(50,2)==90;
  assert !NpcLife.ableToWork(16,80,80,false);
  assert !NpcLife.ableToWork(25,10,80,false);
  assert NpcLife.ableToWork(25,80,80,false);
  System.out.println("NpcLifeTest PASS");
 }
}
