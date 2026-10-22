package ru.projectkingdom.npc;

/** Deterministic low-frequency server-side survival model, no free items or money. */
public final class NpcLife {
    private NpcLife() {}
    public static int clamp(int v) { return Math.max(0,Math.min(100,v)); }
    public static int stepHunger(int hunger, long days) { return clamp(hunger - (int)(Math.min(30,days)*6)); }
    public static int stepEnergy(int energy, long days, boolean working, boolean housed) {
        return clamp(energy + (int)Math.min(30,days)*(working ? -8 : (housed ? 10 : 4)));
    }
    public static int stepMorale(int morale, int hunger, int energy, int safety, boolean paid, long days) {
        int delta=(hunger<25?-6:1)+(energy<25?-4:1)+(safety<25?-4:1)+(paid?1:-2);
        return clamp(morale+(int)Math.min(30,days)*delta);
    }
    public static boolean ableToWork(int age, int hunger, int energy, boolean paused) {
        return age>=18 && hunger>=20 && energy>=15 && !paused;
    }
    public static int feed(int current, int foodUnits) {
        if(foodUnits<0) throw new IllegalArgumentException("Negative food");
        return clamp(current+Math.multiplyExact(foodUnits,20));
    }
}
