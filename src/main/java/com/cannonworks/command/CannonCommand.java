package com.cannonworks.command;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import com.cannonworks.CannonWorks;
import com.cannonworks.ballistics.Ballistics;
import com.cannonworks.ballistics.Trajectory;
import com.cannonworks.entity.ShellManager;
import com.cannonworks.integration.WaystoneTargets;
import com.cannonworks.payload.CannonPayload;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/**
 * 开发期调试命令。
 *
 * <pre>
 * /cannon testfire &lt;距离&gt; [俯仰]                     空包弹（只炸坑）
 * /cannon testfire waystone &lt;距离&gt; [俯仰]            送一座传送石碑过去并激活
 * /cannon testfire buffer hay|water &lt;距离&gt; [俯仰]   缓冲物：软着陆，不炸坑
 * /cannon testfire filler &lt;方块&gt; &lt;数量&gt; &lt;距离&gt; [俯仰]  空投建材
 * /cannon testfire at &lt;x&gt; &lt;y&gt; &lt;z&gt; [俯仰]           朝绝对坐标打
 * /cannon debug                                      列出在飞的炮弹
 * </pre>
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
                                .executes(CannonCommand::teleportTo)))
                .then(Commands.literal("debug")
                        .executes(CannonCommand::debugFlights))
                .then(testfireNode(event.getBuildContext())));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> testfireNode(
            net.minecraft.commands.CommandBuildContext buildContext) {
        return Commands.literal("testfire")
                .then(distanceBranch(context -> CannonPayload.NONE))
                .then(Commands.literal("at")
                        .then(Commands.argument("target", Vec3Argument.vec3())
                                .executes(context -> fireAt(context, 45.0, CannonPayload.NONE))
                                .then(pitchArgument()
                                        .executes(context -> fireAt(context,
                                                DoubleArgumentType.getDouble(context, "pitch"), CannonPayload.NONE)))))
                .then(Commands.literal("waystone")
                        .then(distanceBranch(context -> CannonPayload.waystone())))
                .then(Commands.literal("buffer")
                        .then(Commands.literal("hay")
                                .then(distanceBranch(context -> CannonPayload.hay())))
                        .then(Commands.literal("water")
                                .then(distanceBranch(context -> CannonPayload.water()))))
                .then(Commands.literal("filler")
                        .then(Commands.argument("block", BlockStateArgument.block(buildContext))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 256))
                                        .then(distanceBranch(context -> CannonPayload.filler(
                                                BlockStateArgument.getBlock(context, "block").getState(),
                                                IntegerArgumentType.getInteger(context, "count")))))));
    }

    /** `<距离> [俯仰]` 这一段子树，被普通/石碑/缓冲/填充四种模式复用。 */
    private static ArgumentBuilder<CommandSourceStack, ?> distanceBranch(
            Function<CommandContext<CommandSourceStack>, CannonPayload> payloadFactory) {
        return Commands.argument("distance", DoubleArgumentType.doubleArg(4.0, Ballistics.MAX_RANGE))
                .executes(context -> fireRelative(context, 45.0, payloadFactory.apply(context)))
                .then(pitchArgument()
                        .executes(context -> fireRelative(context,
                                DoubleArgumentType.getDouble(context, "pitch"), payloadFactory.apply(context))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, Double> pitchArgument() {
        return Commands.argument("pitch", DoubleArgumentType.doubleArg(Ballistics.MIN_PITCH, Ballistics.MAX_PITCH));
    }

    /** 朝执行者面朝的水平方向打指定距离。 */
    private static int fireRelative(CommandContext<CommandSourceStack> context, double pitchDeg, CannonPayload payload) {
        CommandSourceStack source = context.getSource();
        double distance = DoubleArgumentType.getDouble(context, "distance");
        Vec3 origin = source.getPosition().add(0.0, 1.5, 0.0);
        Vec3 facing = Vec3.directionFromRotation(0.0f, source.getRotation().y);
        Vec3 horizontal = new Vec3(facing.x, 0.0, facing.z);
        if (horizontal.lengthSqr() < 1.0E-6) {
            horizontal = new Vec3(0.0, 0.0, 1.0);
        }
        Vec3 target = origin.add(horizontal.normalize().scale(distance));
        return launch(source, origin, target, pitchDeg, payload);
    }

    /** 朝绝对坐标打。 */
    private static int fireAt(CommandContext<CommandSourceStack> context, double pitchDeg, CannonPayload payload) {
        CommandSourceStack source = context.getSource();
        Vec3 target = Vec3Argument.getVec3(context, "target");
        Vec3 origin = source.getPosition().add(0.0, 1.5, 0.0);
        return launch(source, origin, target, pitchDeg, payload);
    }

    private static int launch(CommandSourceStack source, Vec3 origin, Vec3 target, double pitchDeg,
                              CannonPayload payload) {
        Trajectory trajectory = Ballistics.create(origin, target, pitchDeg);
        if (trajectory.range() > Ballistics.MAX_RANGE) {
            source.sendFailure(Component.literal(String.format(
                    "射程 %.0f 格超出单发上限 %.0f 格", trajectory.range(), Ballistics.MAX_RANGE)));
            return 0;
        }
        UUID owner = source.getEntity() instanceof ServerPlayer player ? player.getUUID() : null;
        ShellManager.launch(source.getLevel(), trajectory, owner, payload);

        source.sendSuccess(() -> Component.literal(String.format(
                "试射：射程 %.1f 格，俯仰 %.0f°，顶点 %.0f 格，飞行 %.1f 秒，负载[%s]",
                trajectory.range(), pitchDeg, trajectory.apex(), trajectory.flightTicks() / 20.0,
                payload.describe())), false);
        return 1;
    }

    private static int debugFlights(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        List<ShellManager.Flight> flights = ShellManager.activeFlights();
        source.sendSuccess(() -> Component.literal("在飞炮弹：" + flights.size() + " 发"), false);
        for (ShellManager.Flight flight : flights) {
            Vec3 at = flight.position();
            source.sendSuccess(() -> Component.literal(String.format(
                    " - %s  %d/%d tick  @ %.0f/%.0f/%.0f  负载[%s]  载体:%s",
                    flight.id().toString().substring(0, 8), flight.age(), flight.trajectory().flightTicks(),
                    at.x, at.y, at.z, flight.payload().describe(),
                    flight.hasEntity() ? "有" : "无(区块未加载)")), false);
        }
        return Math.max(1, flights.size());
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
