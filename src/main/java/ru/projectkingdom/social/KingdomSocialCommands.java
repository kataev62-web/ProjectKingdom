package ru.projectkingdom.social;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.UUID;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;

/** Restricted QA-only utility until the town-hall promotion UI is ready. */
public final class KingdomSocialCommands {
    private KingdomSocialCommands() {}
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("kingdomstatus")
            .executes(ctx -> {
                var p=ctx.getSource().getPlayerOrException();
                var rank=KingdomSocialData.get(p.getServer()).rank(p.getUUID());
                p.sendSystemMessage(Component.literal("Социальный статус: "+rank.displayName()
                        +" (уровень "+rank.level()+")"));
                return 1;
            })
            .then(Commands.literal("grant").requires(source -> source.hasPermission(2))
                .then(Commands.argument("person",StringArgumentType.word())
                    .then(Commands.argument("level",IntegerArgumentType.integer(2,5))
                        .executes(ctx -> {
                            UUID person;
                            try { person=UUID.fromString(StringArgumentType.getString(ctx,"person")); }
                            catch(IllegalArgumentException ex) { return 0; }
                            KingdomSocialRank target=KingdomSocialRank.fromLevel(
                                    IntegerArgumentType.getInteger(ctx,"level"));
                            boolean changed=KingdomSocialData.get(ctx.getSource().getServer())
                                    .grantVerified(person,target);
                            ctx.getSource().sendSuccess(()->Component.literal(changed
                                    ?"Уровень выдан для тестирования.":"Повышение не выполнено."),false);
                            return changed?1:0;
                        })))));
    }
}
