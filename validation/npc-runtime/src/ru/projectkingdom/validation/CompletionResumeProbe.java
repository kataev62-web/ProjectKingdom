package ru.projectkingdom.validation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import ru.projectkingdom.npc.*;

/** Continue an actual completed rock scenario; no fixture reconstruction or executor calls. */
public final class CompletionResumeProbe {
    private static final KingdomMineSettings.Shaft SHAFT =
            new KingdomMineSettings.Shaft(36,42,36,42,200,"minecraft:overworld");
    private static MinecraftServer server;
    private static ServerLevel level;
    private static Path root;
    private static Properties checkpoint;
    private static Villager resident;
    private static KingdomNpcData.Worker worker;
    private static Container finished;
    private static int ticks, phase, initialCuts, restCuts, restTick, initialDelivered;
    private static long time = 3000;
    private static boolean done;

    public static void boot(Object value) {
        server = (MinecraftServer) value;
        server.execute(() -> {
            try {
                root = server.getWorldPath(LevelResource.ROOT).toRealPath();
                require(root.equals(Path.of(System.getProperty("npc.runtime.allowedWorld")).toRealPath()), "isolated copied world");
                require(Files.readString(root.resolve(".npc-runtime-validation-world")).trim()
                        .equals("disposable-npc-runtime-validation-v1"), "disposable world token");
                require(Files.isRegularFile(root.resolve("npc-autonomous-PASS")), "completed actual rock acceptance world required");
                checkpoint = new Properties();
                try (var input = Files.newInputStream(root.resolve("npc-autonomous-checkpoint.properties"))) { checkpoint.load(input); }
                MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false,
                        TickEvent.ServerTickEvent.class, CompletionResumeProbe::before);
                MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false,
                        TickEvent.ServerTickEvent.class, CompletionResumeProbe::observe);
            } catch (Throwable failure) { fail(failure); }
        });
    }

    private static void before(TickEvent.ServerTickEvent event) {
        if (!done && event.phase == TickEvent.Phase.START) TfcClock.set(server,time);
    }

    private static void observe(TickEvent.ServerTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END) return;
        try {
            require(++ticks <= 8000, "continued production timed out phase=" + phase
                    + (worker == null ? "" : " status=" + worker.mineStatus + " energy=" + worker.energy));
            if (resident == null) { load(); return; }
            require(resident.isAlive() && KingdomMcaAdapter.isResident(resident), "same real MCA miner alive");
            require("work".equals(worker.order) && "miner".equals(worker.job)
                    && KingdomNpcWorkday.employed(server, worker), "standing work order and real payroll contract retained");
            int cuts = removed(2);
            if (phase == 0 && cuts >= initialCuts + 2) {
                require(worker.lastLifeDay < 1, "dawn recovery fixture starts before next life day");
                worker.energy = 24;
                ((net.minecraft.world.level.saveddata.SavedData) KingdomNpcData.get(server)).setDirty();
                restCuts = cuts;
                restTick = ticks;
                time = 24000;
                phase = 1;
                pass("new gameplay JAR continued completed world with two additional native granite cuts");
            } else if (phase == 1 && ticks - restTick >= 240) {
                require(worker.lastLifeDay >= 1 && worker.energy > 25,
                        "actual 200-tick life handler recovered exhausted miner at next dawn with work order retained");
                require(removed(2) == restCuts, "dawn REST did not mine more rock");
                time = 26000;
                phase = 2;
                pass("next-day dawn actual lifecycle recovered energy=" + worker.energy + "; opening next shift");
            } else if (phase == 2 && cuts > restCuts && delivered() >= initialDelivered) {
                require(worker.physicalBag && KingdomMcaAdapter.personal(resident).getContainerSize() == 27,
                        "continued work retains actual physical MCA bag");
                Files.writeString(root.resolve("npc-completion-resume-PASS"),
                        "PASS first=" + removed(1) + " second=" + cuts + " delivered=" + delivered()
                                + " energy=" + worker.energy + " resident=" + resident.getUUID() + "\n");
                pass("same completed world: further native cuts, preserved delivered stock, next-day energy recovery and next-shift production resumed");
                done = true;
                server.halt(false);
            }
            if (ticks % 400 == 0) System.out.println("[NPC-RESUME] PROGRESS phase=" + phase + " cuts=" + cuts
                    + " delivered=" + delivered() + " energy=" + worker.energy + " status=" + worker.mineStatus);
        } catch (Throwable failure) { fail(failure); }
    }

    private static void load() {
        level = server.overworld();
        for (int cx=1;cx<=3;cx++) for(int cz=1;cz<=3;cz++) {
            level.setChunkForced(cx,cz,true);
            level.getChunkAt(new BlockPos(cx*16,200,cz*16));
        }
        var entity=level.getEntity(UUID.fromString(checkpoint.getProperty("resident")));
        if (entity == null && ticks < 150) return;
        require(entity instanceof Villager, "persisted actual miner UUID loaded");
        resident=(Villager)entity;
        worker=KingdomNpcData.get(server).workers.get(Integer.parseInt(checkpoint.getProperty("worker")));
        require(worker!=null && worker.entityId.equals(resident.getUUID())
                && worker.workplaceId.equals(checkpoint.getProperty("workplace")), "original job/enterprise restored");
        finished=(Container)level.getBlockEntity(new BlockPos(41,200,45));
        require(finished!=null && removed(1)==49 && removed(2)>=16 && delivered()>=64,
                "existing complete soil49/granite16 and real warehouse64 fixture");
        require(KingdomResidentInventory.bind(worker,resident), "restored physical inventory bound");
        require(worker.fieldTool.is(Items.DIAMOND_PICKAXE) && worker.fieldTool.getDamageValue()>0
                || worker.fieldInventory.stream().anyMatch(s -> s.is(Items.DIAMOND_PICKAXE) && s.getDamageValue()>0),
                "existing physically worn native pickaxe retained");
        var contract=KingdomEnterprisePayrollData.get(server).contract(UUID.fromString(worker.workplaceId),resident.getUUID());
        require(contract!=null && contract.active() && contract.owed()+contract.dailyCopper()
                < KingdomEnterprisePayrollData.arrearsLimit(contract.dailyCopper()), "one-day recovery remains below genuine payroll debt cutoff");
        initialCuts=removed(2);
        initialDelivered=delivered();
        pass("copied completed rock world loaded: same miner/tools/enterprise, first49 second="+initialCuts+" delivered="+initialDelivered);
    }

    private static int removed(int depth) {
        int count=0;
        String expected=depth==1?"tfc:dirt/loam":"tfc:rock/raw/granite";
        for(var cell:KingdomMineCentralShaftPlan.layer(SHAFT,depth))
            if(!BuiltInRegistries.BLOCK.getKey(level.getBlockState(cell.target()).getBlock()).toString().equals(expected))count++;
        return count;
    }
    private static int delivered() {
        int count=0;
        for(int slot=0;slot<finished.getContainerSize();slot++)count+=finished.getItem(slot).getCount();
        return count;
    }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static void pass(String message){System.out.println("[NPC-RESUME] PASS "+message);}
    private static void fail(Throwable failure){done=true;System.err.println("[NPC-RESUME] FAIL tick="+ticks);failure.printStackTrace();Runtime.getRuntime().halt(34);}
}
