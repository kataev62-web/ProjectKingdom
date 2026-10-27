package ru.projectkingdom.map;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import ru.projectkingdom.network.KingdomNetwork;
import ru.projectkingdom.network.ToggleCityBordersPacket;
import ru.projectkingdom.npc.KingdomNpcMod;

/** Remappable keyboard shortcut for showing/hiding nearby city borders. */
public final class KingdomBorderKey {
    private static final KeyMapping KEY=new KeyMapping("key.kingdomnpc.borders",
            InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_G,"key.categories.kingdomnpc");
    private KingdomBorderKey(){}
    @Mod.EventBusSubscriber(modid=KingdomNpcMod.MOD_ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration(){}
        @SubscribeEvent public static void register(RegisterKeyMappingsEvent event){event.register(KEY);}
    }
    @Mod.EventBusSubscriber(modid=KingdomNpcMod.MOD_ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.FORGE)
    public static final class Runtime {
        private Runtime(){}
        @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
            if(event.phase!=TickEvent.Phase.END)return;
            var mc=Minecraft.getInstance();
            while(KEY.consumeClick()){
                if(mc.player!=null&&mc.level!=null&&mc.screen==null)
                    KingdomNetwork.CHANNEL.sendToServer(new ToggleCityBordersPacket());
            }
        }
    }
}
