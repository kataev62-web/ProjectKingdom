package ru.projectkingdom.npc;
/** Plain Java smoke check for the canonical bread recipe arithmetic. */
public class WorkshopRecipeTest {
    public static void main(String[] args) {
        int stock = 8, bread = 0;
        while(stock >= 3) { stock -= 3; bread++; }
        if(stock != 2 || bread != 2) throw new AssertionError("Bread recipe arithmetic failed");
        System.out.println("Bread recipe arithmetic OK");
    }
}
