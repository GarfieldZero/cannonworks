package com.cannonworks.command;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import com.cannonworks.CannonWorks;
import com.cannonworks.ballistics.Ballistics;
import com.cannonworks.ballistics.Trajectory;
import com.cannonworks.block.CannonBlockEntity;
import com.cannonworks.entity.ShellManager;
import com.cannonworks.integration.WaystoneTargets;
import com.cannonworks.payload.CannonPayload;
import com.cannonworks.payload.FireMode;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
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
 * /cannon testfire &lt;距离&gt; [俯仰] [airburst]                  空包弹
 * /cannon testfire cargo &lt;waystone|tnt|pearl|warpplate&gt; ...  只带货物
 * /cannon testfire fill &lt;方块&gt; &lt;数量&gt; ...                     只带填充
 * /cannon testfire full &lt;方块&gt; &lt;数量&gt; &lt;货物&gt; ...              填充 + 货物
 * /cannon testfire at &lt;x&gt; &lt;y&gt; &lt;z&gt; [俯仰]
 * /cannon block load|fire ...                                  给方块装填/开火（无头测试）
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
                .then(Commands.literal("waystones").executes(CannonCommand::listWaystones))
                .then(Commands.literal("nearest").executes(CannonCommand::nearestWaystone))
                .then(Commands.literal("tp")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(CannonCommand::teleportTo)))
                .then(Commands.literal("debug").executes(CannonCommand::debugFlights))
                .then(Commands.literal("probe")
                        .then(Commands.argument("from", Vec3Argument.vec3())
                                .then(Commands.argument("to", Vec3Argument.vec3())
                                        .executes(CannonCommand::probeClip))))
                .then(blockDebugNode(event.getBuildContext()))
                .then(testfireNode(event.getBuildContext())));
    }

    // ---- testfire ----

    private static LiteralArgumentBuilder<CommandSourceStack> testfireNode(CommandBuildContext buildContext) {
        return Commands.literal("testfire")
                .then(launchBranch(context -> CannonPayload.EMPTY))
                .then(Commands.literal("at")
                        .then(Commands.argument("target", Vec3Argument.vec3())
                                .executes(context -> fireAt(context, 45.0))
                                .then(pitchArgument().executes(context -> fireAt(context,
                                        DoubleArgumentType.getDouble(context, "pitch"))))))
                .then(Commands.literal("cargo")
                        .then(Commands.literal("waystone")
                                .then(launchBranch(context -> CannonPayload.cargo(
                                        CannonPayload.Cargo.WAYSTONE, null, null))))
                        .then(Commands.literal("tnt")
                                .then(launchBranch(context -> CannonPayload.cargo(
                                        CannonPayload.Cargo.TNT, null, null))))
                        .then(Commands.literal("pearl")
                                .then(launchBranch(context -> CannonPayload.cargo(
                                        CannonPayload.Cargo.ENDER_PEARL, null, null))))
                        .then(Commands.literal("warpplate")
                                .then(launchBranch(context -> CannonPayload.cargo(
                                        CannonPayload.Cargo.WARP_PLATE, null, null))))
                        .then(Commands.literal("water")
                                .then(launchBranch(context -> CannonPayload.cargo(
                                        CannonPayload.Cargo.WATER, null, null))))
                        .then(Commands.literal("lava")
                                .then(launchBranch(context -> CannonPayload.cargo(
                                        CannonPayload.Cargo.LAVA, null, null)))))
                .then(Commands.literal("fill")
                        .then(Commands.argument("block", BlockStateArgument.block(buildContext))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                        .then(launchBranch(context -> CannonPayload.filler(
                                                BlockStateArgument.getBlock(context, "block").getState(),
                                                IntegerArgumentType.getInteger(context, "count")))))))
                .then(Commands.literal("full")
                        .then(Commands.argument("block", BlockStateArgument.block(buildContext))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                        .then(Commands.literal("waystone")
                                                .then(launchBranch(context -> payloadWithCargo(context,
                                                        CannonPayload.Cargo.WAYSTONE))))
                                        .then(Commands.literal("tnt")
                                                .then(launchBranch(context -> payloadWithCargo(context,
                                                        CannonPayload.Cargo.TNT))))
                                        .then(Commands.literal("pearl")
                                                .then(launchBranch(context -> payloadWithCargo(context,
                                                        CannonPayload.Cargo.ENDER_PEARL))))
                                        .then(Commands.literal("warpplate")
                                                .then(launchBranch(context -> payloadWithCargo(context,
                                                        CannonPayload.Cargo.WARP_PLATE)))))));
    }

    private static CannonPayload payloadWithCargo(CommandContext<CommandSourceStack> context,
                                                  CannonPayload.Cargo cargo) {
        BlockState state = BlockStateArgument.getBlock(context, "block").getState();
        int count = IntegerArgumentType.getInteger(context, "count");
        return CannonPayload.of(state, count, cargo, null, null);
    }

    /** `<距离> [俯仰] [airburst]`，四种模式共用。 */
    private static ArgumentBuilder<CommandSourceStack, ?> launchBranch(
            Function<CommandContext<CommandSourceStack>, CannonPayload> payloadFactory) {
        return Commands.argument("distance", DoubleArgumentType.doubleArg(4.0, Ballistics.MAX_RANGE))
                .executes(context -> fireRelative(context, 45.0, payloadFactory.apply(context), FireMode.CONTACT))
                .then(pitchArgument()
                        .executes(context -> fireRelative(context, DoubleArgumentType.getDouble(context, "pitch"),
                                payloadFactory.apply(context), FireMode.CONTACT))
                        .then(Commands.literal("airburst")
                                .executes(context -> fireRelative(context,
                                        DoubleArgumentType.getDouble(context, "pitch"),
                                        payloadFactory.apply(context), FireMode.AIRBURST))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, Double> pitchArgument() {
        return Commands.argument("pitch", DoubleArgumentType.doubleArg(Ballistics.MIN_PITCH, Ballistics.MAX_PITCH));
    }

    private static int fireRelative(CommandContext<CommandSourceStack> context, double pitchDeg,
                                    CannonPayload payload, FireMode mode) {
        CommandSourceStack source = context.getSource();
        double distance = DoubleArgumentType.getDouble(context, "distance");
        Vec3 origin = source.getPosition().add(0.0, 1.5, 0.0);
        Vec3 facing = Vec3.directionFromRotation(0.0f, source.getRotation().y);
        Vec3 horizontal = new Vec3(facing.x, 0.0, facing.z);
        if (horizontal.lengthSqr() < 1.0E-6) {
            horizontal = new Vec3(0.0, 0.0, 1.0);
        }
        Vec3 target = origin.add(horizontal.normalize().scale(distance));
        return launch(source, origin, target, pitchDeg, payload, mode);
    }

    private static int fireAt(CommandContext<CommandSourceStack> context, double pitchDeg) {
        CommandSourceStack source = context.getSource();
        Vec3 target = Vec3Argument.getVec3(context, "target");
        Vec3 origin = source.getPosition().add(0.0, 1.5, 0.0);
        return launch(source, origin, target, pitchDeg, CannonPayload.EMPTY, FireMode.CONTACT);
    }

    private static int launch(CommandSourceStack source, Vec3 origin, Vec3 target, double pitchDeg,
                              CannonPayload payload, FireMode mode) {
        Trajectory trajectory = Ballistics.create(origin, target, pitchDeg);
        if (trajectory.range() > Ballistics.MAX_RANGE) {
            source.sendFailure(Component.literal(String.format(
                    "射程 %.0f 格超出单发上限 %.0f 格", trajectory.range(), Ballistics.MAX_RANGE)));
            return 0;
        }
        UUID owner = source.getEntity() instanceof ServerPlayer player ? player.getUUID() : null;
        ShellManager.launch(source.getLevel(), trajectory, owner, payload, mode);

        source.sendSuccess(() -> Component.literal(String.format(
                "试射[%s]：射程 %.1f 格，俯仰 %.0f°，顶点 %.0f 格，飞行 %.1f 秒，负载[%s]",
                mode.label(), trajectory.range(), pitchDeg, trajectory.apex(), trajectory.flightTicks() / 20.0,
                payload.describe())), false);
        return 1;
    }

    private static int probeClip(CommandContext<CommandSourceStack> context) {
        Vec3 from = Vec3Argument.getVec3(context, "from");
        Vec3 to = Vec3Argument.getVec3(context, "to");
        net.minecraft.world.phys.BlockHitResult hit = context.getSource().getLevel().clip(
                new net.minecraft.world.level.ClipContext(from, to,
                        net.minecraft.world.level.ClipContext.Block.COLLIDER,
                        net.minecraft.world.level.ClipContext.Fluid.NONE, null));
        context.getSource().sendSuccess(() -> Component.literal(
                "clip=" + hit.getType() + " @ " + hit.getBlockPos().toShortString() + " loc=" + hit.getLocation()), false);
        return 1;
    }

    // ---- 方块调试（无头测试用） ----

    private static LiteralArgumentBuilder<CommandSourceStack> blockDebugNode(CommandBuildContext buildContext) {
        return Commands.literal("block")
                .then(Commands.literal("fire")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(CannonCommand::blockFire)))
                .then(Commands.literal("load")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(Commands.argument("slot", IntegerArgumentType.integer(0, 2))
                                        .then(Commands.argument("item", ItemArgument.item(buildContext))
                                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                                        .executes(CannonCommand::blockLoad))))))
                .then(Commands.literal("name")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(CannonCommand::blockName))))
                // greedyString 会把引号原样吞进去，传不了空串，清空只好单开一个子命令
                .then(Commands.literal("clearname")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(CannonCommand::blockClearName)));
    }

    /** 无头验收用：清掉石碑的自定义名字，等价于界面命名框里删空再回车。 */
    private static int blockClearName(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
        if (!(source.getLevel().getBlockEntity(pos) instanceof CannonBlockEntity cannon)) {
            source.sendFailure(Component.literal("那里不是大炮"));
            return 0;
        }
        if (!cannon.renameCargo("")) {
            source.sendFailure(Component.literal("货物槽里没有可命名的石碑"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("已清空名字"), false);
        return 1;
    }

    /**
     * 无头验收用：给货物槽里的石碑改名，等价于界面的命名框。
     * 名字走 {@code renameCargo}，也就是玩家点 GUI 时走的那条路。
     */
    private static int blockName(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
        if (!(source.getLevel().getBlockEntity(pos) instanceof CannonBlockEntity cannon)) {
            source.sendFailure(Component.literal("那里不是大炮"));
            return 0;
        }
        String name = StringArgumentType.getString(context, "name");
        if (!cannon.renameCargo(name)) {
            source.sendFailure(Component.literal("货物槽里没有可命名的石碑"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("已命名为「" + name + "」"), false);
        return 1;
    }

    private static int blockFire(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
        BlockEntity blockEntity = source.getLevel().getBlockEntity(pos);
        if (!(blockEntity instanceof CannonBlockEntity cannon)) {
            source.sendFailure(Component.literal("那里不是大炮"));
            return 0;
        }
        boolean fired = cannon.fire(null);
        source.sendSuccess(() -> Component.literal(fired ? "已开火" : "开火失败（冷却中 / 没火药 / 装药为 0）"), false);
        return fired ? 1 : 0;
    }

    private static int blockLoad(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
        BlockEntity blockEntity = source.getLevel().getBlockEntity(pos);
        if (!(blockEntity instanceof CannonBlockEntity cannon)) {
            source.sendFailure(Component.literal("那里不是大炮"));
            return 0;
        }
        int slot = IntegerArgumentType.getInteger(context, "slot");
        int count = IntegerArgumentType.getInteger(context, "count");
        ItemStack stack = new ItemStack(ItemArgument.getItem(context, "item").getItem(), count);
        cannon.getInventory().setStackInSlot(slot, stack);
        source.sendSuccess(() -> Component.literal("已放入 " + stack.getHoverName().getString() + " ×" + count), false);
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

    // ---- 传送石碑联动 ----

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
