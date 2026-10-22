package ru.projectkingdom.npc;
import java.util.*;
public class KingdomNpcData {public static final java.util.Map<net.minecraft.server.MinecraftServer,KingdomNpcData> INST=new java.util.HashMap<>();public static KingdomNpcData get(net.minecraft.server.MinecraftServer s){return INST.computeIfAbsent(s,k->new KingdomNpcData());}public void setDirty(){}
    public static class Worker {
        public UUID entityId, employer;public String dimension="overworld";public java.util.List<net.minecraft.world.item.ItemStack> wagePurse=new java.util.ArrayList<>();
        public int ageYears=25,dailyWage=10, hunger=80,energy=80,skillXp; public long workUnits,produced; public String order="work",job="smith",workplaceId=""; public boolean contractPaused,hasWorkstation=true,hasSource=true,hasDestination=true;public int workX,workY,workZ,sourceX,sourceY,sourceZ,destX,destY,destZ;
        public Worker(UUID id, UUID employer){this.entityId=id;this.employer=employer;}
    }
    private final List<Worker> workers=new ArrayList<>();
    public Collection<Worker> all(){return workers;}
    public void add(Worker w){workers.add(w);}
}
