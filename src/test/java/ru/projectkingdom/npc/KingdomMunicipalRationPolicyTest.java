package ru.projectkingdom.npc;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Public rations are explicit, physical and only for real civic citizens. */
class KingdomMunicipalRationPolicyTest {
    @Test void defaultDoesNotOfferTownFoodToRandomWorkers(){
        var policy=new KingdomMunicipalRationPolicy();
        assertFalse(policy.enabled(UUID.randomUUID()));
        assertFalse(KingdomMunicipalCanteen.eligible(false,true,false,false,true,true,false));
        assertFalse(KingdomMunicipalCanteen.eligible(true,false,false,false,true,true,false));
        assertFalse(KingdomMunicipalCanteen.eligible(true,true,true,false,true,true,false));
        assertFalse(KingdomMunicipalCanteen.eligible(true,true,false,true,true,true,false));
        assertFalse(KingdomMunicipalCanteen.eligible(true,true,false,false,false,true,false));
        assertFalse(KingdomMunicipalCanteen.eligible(true,true,false,false,true,false,false));
        assertFalse(KingdomMunicipalCanteen.eligible(true,true,false,false,true,true,true));
        assertTrue(KingdomMunicipalCanteen.eligible(true,true,false,false,true,true,false));
    }
    @Test void savedAuthorizationIsOnlyAFlagAndDoesNotCreateFood(){
        UUID town=UUID.randomUUID();
        CompoundTag root=new CompoundTag();
        CompoundTag row=new CompoundTag();row.putUUID("Town",town);
        ListTag list=new ListTag();list.add(row);root.put("Towns",list);
        var loaded=KingdomMunicipalRationPolicy.load(root);
        assertTrue(loaded.enabled(town));
        assertFalse(loaded.enabled(UUID.randomUUID()));
        var copy=KingdomMunicipalRationPolicy.load(loaded.save(new CompoundTag()));
        assertTrue(copy.enabled(town));
        assertFalse(copy.enabled(UUID.randomUUID()));
    }
}
