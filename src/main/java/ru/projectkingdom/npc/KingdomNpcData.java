package ru.projectkingdom.npc;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Global economy and contracts, persistent independently of loaded chunks. */
public final class KingdomNpcData extends SavedData {
    public static final String DATA_NAME = "kingdom_npc_v1";
    public final Map<Integer, Worker> workers = new LinkedHashMap<>();
    public final Map<UUID, Long> accounts = new LinkedHashMap<>();
    public int nextId = 1;

    public static KingdomNpcData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(KingdomNpcData::load, KingdomNpcData::new, DATA_NAME);
    }

    public static KingdomNpcData load(CompoundTag root) {
        KingdomNpcData data = new KingdomNpcData();
        data.nextId = Math.max(1, root.getInt("NextId"));
        ListTag list = root.getList("Workers", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            Worker worker = Worker.load(entry);
            data.workers.put(worker.id, worker);
        }
        ListTag accounts = root.getList("Accounts", Tag.TAG_COMPOUND);
        for (int i = 0; i < accounts.size(); i++) {
            CompoundTag entry = accounts.getCompound(i);
            if (entry.hasUUID("Player")) data.accounts.put(entry.getUUID("Player"), Math.max(0, entry.getLong("Balance")));
        }
        return data;
    }

    @Override public CompoundTag save(CompoundTag root) {
        root.putInt("NextId", nextId);
        ListTag list = new ListTag();
        for (Worker worker : workers.values()) list.add(worker.save());
        root.put("Workers", list);
        ListTag balances = new ListTag();
        for (var entry : accounts.entrySet()) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Player", entry.getKey());
            tag.putLong("Balance", entry.getValue());
            balances.add(tag);
        }
        root.put("Accounts", balances);
        return root;
    }

    public Worker add(UUID entityId, String name, String dimension, long gameDay) {
        Worker worker = new Worker(nextId++, entityId, name, dimension, gameDay);
        workers.put(worker.id, worker);
        setDirty();
        return worker;
    }

    public long balance(UUID playerId) { return accounts.getOrDefault(playerId, 0L); }

    public void deposit(UUID playerId, long count) {
        if (count <= 0 || count > 2304) throw new IllegalArgumentException("Deposit range 1..2304");
        accounts.put(playerId, Math.addExact(balance(playerId), count));
        setDirty();
    }

    public Collection<Worker> all() { return workers.values(); }

    public static final class Worker {
        public final int id;
        public final UUID entityId;
        public final String name;
        public final String dimension;
        public UUID employer;
        public String job = "free";
        public int dailyWage = 2;
        public long earned = 0;
        public long lastPaidDay;
        public int unpaidDays = 0;
        public boolean contractPaused = false;
        // Saved independently of loaded chunks; defaults preserve 0.1.0 worlds.
        public String order = "wander";
        public long workUnits = 0;
        public long lastWorkedDay;
        public int homeX, homeY, homeZ;
        public boolean hasHome = false;
        public long produced = 0; // Virtual test-resource ledger, not items or money.
        public int skillXp = 0;
        // Life simulation values 0..100 (persistent, server authoritative).
        public int hunger = 80;
        public int energy = 80;
        public int morale = 70;
        public int safety = 70;
        public String settlement = "wilderness";
        public String familyName = "";
        public long lastLifeDay;
        public int lifeSeed = 0;
        public int personalStock = 0; // test provisions, cannot be traded for currency

        // Identity fields survive unloaded chunks and server restarts.
        public String gender = "unspecified";
        public int ageYears = 25;
        public long ageAnchorDay;
        public int ageDaysAccumulated = 0;
        public String originMod = "minecraft"; // Optional external origin; not an integration claim.
        public String workplaceMod = "kingdomnpc";
        public String workplaceId = "";
        // Real physical logistics: one small carried stack; persists on every save.
        public boolean hasSource=false, hasDestination=false;
        public int sourceX,sourceY,sourceZ,destX,destY,destZ;
        public ItemStack cargo=ItemStack.EMPTY;
        // Optional original AstikorCarts entity, identified by UUID; no hard mod dependency.
        public UUID courierCart;
        // Only items in the actual cart inventory are counted here; never a virtual stack.
        public UUID cartCargoOrder;
        public int cartCargoCount;
        // Persistent courier diagnostics and non-destructive navigation recovery.
        public String courierStatus="IDLE";
        public long courierLastProgressTick;
        public int courierLastX, courierLastY, courierLastZ;
        public int courierStallAttempts;
        public java.util.List<ItemStack> fieldInventory=new java.util.ArrayList<>();
        /** NPC personal physical coin purse (distinct from goods/courier cargo). */
        public java.util.List<ItemStack> wagePurse=new java.util.ArrayList<>();
        public ItemStack fieldTool=ItemStack.EMPTY;
        public long fieldHarvested=0;
        public long deliveredItems=0;
        public long bakedBread=0;
        // Server-side planning; not fake production. No actual mod recipe without adapter.
        // NPC supply inquiry is an intent, never a paid order until physical cash is transferred.
        public String supplyItem="";
        public int supplyCount=0;
        public long supplyNextAttempt=0;
        public String supplyStatus="IDLE";
        public long supplyQuoteCopper=0;
        public String supplyOrderId="";
        public int supplyDeskX,supplyDeskY,supplyDeskZ;
        public String autonomousIntent="IDLE";
        public boolean hasWorkstation=false;
        public int workX,workY,workZ;

        Worker(int id, UUID entityId, String name, String dimension, long day) {
            this.id = id; this.entityId = entityId; this.name = name;
            this.dimension = dimension; this.lastPaidDay = day; this.lastWorkedDay = day; this.ageAnchorDay = day; this.lastLifeDay = day;
        }
        static Worker load(CompoundTag t) {
            Worker w = new Worker(t.getInt("Id"), t.getUUID("Entity"), t.getString("Name"), t.getString("Dimension"), t.getLong("LastPaid"));
            if (t.hasUUID("Employer")) w.employer = t.getUUID("Employer");
            w.job = t.getString("Job");
            w.dailyWage = Math.max(1, t.getInt("Wage"));
            w.earned = Math.max(0, t.getLong("Earned"));
            w.unpaidDays = Math.max(0, t.getInt("Unpaid"));
            w.contractPaused = t.getBoolean("Paused");
            w.order = t.contains("Order", Tag.TAG_STRING) ? t.getString("Order") : "wander";
            w.workUnits = Math.max(0L, t.getLong("WorkUnits"));
            w.produced = Math.max(0L, t.getLong("Produced"));
            w.lastWorkedDay = t.contains("LastWorked", Tag.TAG_LONG) ? t.getLong("LastWorked") : w.lastPaidDay;
            w.skillXp = Math.max(0, t.getInt("SkillXp"));
            w.hunger = t.contains("Hunger", Tag.TAG_INT) ? NpcLife.clamp(t.getInt("Hunger")) : 80;
            w.energy = t.contains("Energy", Tag.TAG_INT) ? NpcLife.clamp(t.getInt("Energy")) : 80;
            w.morale = t.contains("Morale", Tag.TAG_INT) ? NpcLife.clamp(t.getInt("Morale")) : 70;
            w.safety = t.contains("Safety", Tag.TAG_INT) ? NpcLife.clamp(t.getInt("Safety")) : 70;
            w.settlement = t.contains("Settlement", Tag.TAG_STRING) ? t.getString("Settlement") : "wilderness";
            w.familyName = t.contains("FamilyName", Tag.TAG_STRING) ? t.getString("FamilyName") : "";
            w.lastLifeDay = t.contains("LastLifeDay", Tag.TAG_LONG) ? t.getLong("LastLifeDay") : w.lastPaidDay;
            w.personalStock = Math.max(0,t.getInt("PersonalStock"));
            w.gender = t.contains("Gender", Tag.TAG_STRING) ? NpcIdentity.gender(t.getString("Gender")) : "unspecified";
            w.ageYears = t.contains("AgeYears", Tag.TAG_INT) ? NpcIdentity.validAge(t.getInt("AgeYears")) : 25;
            w.ageAnchorDay = t.contains("AgeAnchorDay", Tag.TAG_LONG) ? t.getLong("AgeAnchorDay") : w.lastPaidDay;
            w.ageDaysAccumulated = t.contains("AgeDaysAccumulated", Tag.TAG_INT) ? Math.max(0, t.getInt("AgeDaysAccumulated")) % NpcIdentity.DAYS_PER_YEAR : 0;
            w.originMod = t.contains("OriginMod", Tag.TAG_STRING) ? t.getString("OriginMod") : "minecraft";
            w.workplaceMod = t.contains("WorkplaceMod", Tag.TAG_STRING) ? t.getString("WorkplaceMod") : "kingdomnpc";
            w.workplaceId = t.contains("WorkplaceId", Tag.TAG_STRING) ? t.getString("WorkplaceId") : "";
            w.hasSource=t.getBoolean("HasSource"); w.hasDestination=t.getBoolean("HasDestination");
            w.sourceX=t.getInt("SourceX");w.sourceY=t.getInt("SourceY");w.sourceZ=t.getInt("SourceZ");
            w.destX=t.getInt("DestX");w.destY=t.getInt("DestY");w.destZ=t.getInt("DestZ");
            if(t.contains("Cargo",Tag.TAG_COMPOUND)) w.cargo=ItemStack.of(t.getCompound("Cargo"));
            if(t.hasUUID("CourierCart")) w.courierCart=t.getUUID("CourierCart");
            if(t.hasUUID("CartCargoOrder")) w.cartCargoOrder=t.getUUID("CartCargoOrder");
            w.cartCargoCount=Math.max(0,t.getInt("CartCargoCount"));
            w.courierStatus=t.contains("CourierStatus",Tag.TAG_STRING)?t.getString("CourierStatus"):"IDLE";
            w.courierLastProgressTick=Math.max(0,t.getLong("CourierLastProgressTick"));
            w.courierLastX=t.getInt("CourierLastX");w.courierLastY=t.getInt("CourierLastY");w.courierLastZ=t.getInt("CourierLastZ");
            w.courierStallAttempts=Math.max(0,t.getInt("CourierStallAttempts"));
            if(t.contains("FieldTool",Tag.TAG_COMPOUND)) w.fieldTool=ItemStack.of(t.getCompound("FieldTool"));
            w.fieldHarvested=Math.max(0L,t.getLong("FieldHarvested"));
            ListTag fieldItems=t.getList("FieldInventory",Tag.TAG_COMPOUND);
            for(int i=0;i<fieldItems.size()&&i<36;i++){ItemStack stack=ItemStack.of(fieldItems.getCompound(i));if(!stack.isEmpty())w.fieldInventory.add(stack);}
            ListTag wages=t.getList("WagePurse",Tag.TAG_COMPOUND);
            for(int i=0;i<wages.size()&&i<36;i++){ItemStack coin=ItemStack.of(wages.getCompound(i));
                if(!coin.isEmpty() && KingdomCurrency.value(coin)>0)w.wagePurse.add(coin);}

            w.deliveredItems=Math.max(0L,t.getLong("DeliveredItems"));
            w.bakedBread=Math.max(0L,t.getLong("BakedBread"));
            w.supplyQuoteCopper=Math.max(0,t.getLong("SupplyQuoteCopper"));
            w.supplyOrderId=t.getString("SupplyOrderId");
            w.supplyItem=t.getString("SupplyItem");
            w.supplyCount=Math.max(0,Math.min(2304,t.getInt("SupplyCount")));
            w.supplyNextAttempt=Math.max(0,t.getLong("SupplyNextAttempt"));
            w.supplyStatus=t.contains("SupplyStatus",Tag.TAG_STRING)?t.getString("SupplyStatus"):"IDLE";
            w.supplyDeskX=t.getInt("SupplyDeskX");w.supplyDeskY=t.getInt("SupplyDeskY");w.supplyDeskZ=t.getInt("SupplyDeskZ");
            w.autonomousIntent=t.contains("AutonomousIntent",Tag.TAG_STRING)?t.getString("AutonomousIntent"):"IDLE";
            w.hasWorkstation=t.getBoolean("HasWorkstation");
            w.workX=t.getInt("WorkX");w.workY=t.getInt("WorkY");w.workZ=t.getInt("WorkZ");
            w.hasHome = t.getBoolean("HasHome");
            if (w.hasHome) { w.homeX=t.getInt("HomeX"); w.homeY=t.getInt("HomeY"); w.homeZ=t.getInt("HomeZ"); }
            return w;
        }
        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putInt("Id", id); t.putUUID("Entity", entityId); t.putString("Name", name);
            t.putString("Dimension", dimension); t.putLong("LastPaid", lastPaidDay);
            if (employer != null) t.putUUID("Employer", employer);
            t.putString("Job", job); t.putInt("Wage", dailyWage);
            t.putLong("Earned", earned); t.putInt("Unpaid", unpaidDays);
            t.putBoolean("Paused", contractPaused);
            t.putString("Order", order); t.putLong("WorkUnits", workUnits);
            t.putLong("Produced", produced); t.putLong("LastWorked", lastWorkedDay);
            t.putInt("SkillXp", skillXp); t.putBoolean("HasHome", hasHome);
            t.putInt("Hunger",hunger);t.putInt("Energy",energy);t.putInt("Morale",morale);t.putInt("Safety",safety);
            t.putString("Settlement",settlement);t.putString("FamilyName",familyName);
            t.putLong("LastLifeDay",lastLifeDay);t.putInt("PersonalStock",personalStock);
            t.putString("Gender", gender); t.putInt("AgeYears", ageYears);
            t.putLong("AgeAnchorDay", ageAnchorDay); t.putInt("AgeDaysAccumulated", ageDaysAccumulated);
            t.putString("OriginMod", originMod); t.putString("WorkplaceMod", workplaceMod);
            t.putString("WorkplaceId", workplaceId);
            t.putBoolean("HasSource",hasSource);t.putBoolean("HasDestination",hasDestination);
            t.putInt("SourceX",sourceX);t.putInt("SourceY",sourceY);t.putInt("SourceZ",sourceZ);
            t.putInt("DestX",destX);t.putInt("DestY",destY);t.putInt("DestZ",destZ);
            if(!cargo.isEmpty())t.put("Cargo",cargo.save(new CompoundTag()));
            if(courierCart!=null)t.putUUID("CourierCart",courierCart);
            if(cartCargoOrder!=null)t.putUUID("CartCargoOrder",cartCargoOrder);
            t.putInt("CartCargoCount",cartCargoCount);
            t.putString("CourierStatus",courierStatus);
            t.putLong("CourierLastProgressTick",courierLastProgressTick);
            t.putInt("CourierLastX",courierLastX);t.putInt("CourierLastY",courierLastY);t.putInt("CourierLastZ",courierLastZ);
            t.putInt("CourierStallAttempts",courierStallAttempts);
            if(!fieldTool.isEmpty())t.put("FieldTool",fieldTool.save(new CompoundTag()));
            t.putLong("FieldHarvested",fieldHarvested);
            ListTag fieldItems=new ListTag();for(ItemStack stack:fieldInventory)if(!stack.isEmpty())fieldItems.add(stack.save(new CompoundTag()));
            t.put("FieldInventory",fieldItems);
            ListTag wages=new ListTag();for(ItemStack coin:wagePurse)
                if(!coin.isEmpty() && KingdomCurrency.value(coin)>0)wages.add(coin.save(new CompoundTag()));
            t.put("WagePurse",wages);
            t.putLong("DeliveredItems",deliveredItems);
            t.putLong("BakedBread",bakedBread);
            t.putLong("SupplyQuoteCopper",supplyQuoteCopper);t.putString("SupplyOrderId",supplyOrderId);
            t.putString("SupplyItem",supplyItem);t.putInt("SupplyCount",supplyCount);
            t.putLong("SupplyNextAttempt",supplyNextAttempt);t.putString("SupplyStatus",supplyStatus);
            t.putInt("SupplyDeskX",supplyDeskX);t.putInt("SupplyDeskY",supplyDeskY);t.putInt("SupplyDeskZ",supplyDeskZ);
            t.putString("AutonomousIntent", autonomousIntent);
            t.putBoolean("HasWorkstation",hasWorkstation);
            t.putInt("WorkX",workX);t.putInt("WorkY",workY);t.putInt("WorkZ",workZ);
            if (hasHome) {t.putInt("HomeX", homeX);t.putInt("HomeY", homeY);t.putInt("HomeZ", homeZ);}
            return t;
        }
    }
}
