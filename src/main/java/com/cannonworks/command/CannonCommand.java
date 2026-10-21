package com.cannonworks.command;

import java.util.List;
import java.util.Optional;

import com.cannonworks.CannonWorks;
import com.cannonworks.integration.WaystoneTargets;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/**
 * 开发期调试命令，用来验证「大炮 ↔ 传送石碑」这条链路是否打通。
 *
 * <pre>
 * /cannon waystones   列出玩家已激活的石碑
 * /cannon nearest     最近的一座石碑
 * /cannon tp &lt;名字&gt;   强制传送到同名石碑
 * </pre>
 *
 * <p>注意：本类不 import 任何 Waystones 类型，只有 {@link WaystoneTargets}
 * 里才有；Waystones 缺席时这里只发一条提示，不会触发类加载。</p>
 */
@Mod.EventBusSubscriber(modid = CannonWorks.MOD_ID)
public final class CannonCommand {
    private CannonCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("cannon")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("waystones")
                        .executes(CannonCommand::listWaystones))
                .then(Commands.literal("nearest")
                        .executes(CannonCommand::nearestWaystone))
                .then(Commands.literal("tp")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(CannonCommand::teleportTo))));
    }

    private static int listWaystones(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        if (!hasWaystones(source)) {
            return 0;
        }
        ServerPlayer player = source.getPlayerOrException();
        List<String> lines = WaystoneTargets.describeActivated(player);
        source.sendSuccess(() -> Component.literal("已激活的传送石碑：" + lines.size() + " 座"), false);
        for (String line : lines) {
            source.sendSuccess(() -> Component.literal(" - " + line), false);
        }
        return Math.max(1, lines.size());
    }

    private static int nearestWaystone(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        if (!hasWaystones(source)) {
            return 0;
        }
        ServerPlayer player = source.getPlayerOrException();
        Optional<String> nearest = WaystoneTargets.describeNearest(player);
        if (nearest.isEmpty()) {
            source.sendFailure(Component.literal("附近没有传送石碑"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("最近的传送石碑：" + nearest.get()), false);
        return 1;
    }

    private static int teleportTo(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        if (!hasWaystones(source)) {
            return 0;
        }
        ServerPlayer player = source.getPlayerOrException();
        String name = StringArgumentType.getString(context, "name");

        Optional<Component> failure = WaystoneTargets.teleport(player, name);
        if (failure.isPresent()) {
            source.sendFailure(failure.get());
            return 0;
        }
        source.sendSuccess(() -> Component.literal("已传送至 " + name), true);
        return 1;
    }

    private static boolean hasWaystones(CommandSourceStack source) {
        if (ModList.get().isLoaded("waystones")) {
            return true;
        }
        source.sendFailure(Component.literal("未检测到「传送石碑」(Waystones) 模组，联动功能不可用"));
        return false;
    }
}
