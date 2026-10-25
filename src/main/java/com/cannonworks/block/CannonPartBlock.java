package com.cannonworks.block;

import net.minecraft.core.BlockPos;import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;

/**
 * 大炮底座的不可见占位块。
 *
 * <p>它承担两件事：给底座提供真实碰撞；右键/破坏时把操作转发给控制器。
 * 没有 BlockEntity，控制器靠附近的 {@link CannonBlockEntity} 扫描定位。</p>
 */
public class CannonPartBlock extends Block {

    private static final VoxelShape SHAPE = Shapes.block();

    public CannonPartBlock(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            BlockPos master = CannonMultiblock.findMaster(level, pos);
            if (master != null && level.getBlockEntity(master) instanceof CannonBlockEntity cannon) {
                NetworkHooks.openScreen(serverPlayer, cannon, master);
                return InteractionResult.CONSUME;
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide && !CannonMultiblock.isRemoving()) {
            BlockPos master = CannonMultiblock.findMaster(level, pos);
            if (master != null) {
                CannonMultiblock.breakFromPart((ServerLevel) level, master);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public boolean isCollisionShapeFullBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }
}
