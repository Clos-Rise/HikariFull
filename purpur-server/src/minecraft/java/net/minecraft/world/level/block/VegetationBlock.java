package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;

public abstract class VegetationBlock extends Block {
    protected VegetationBlock(final BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected abstract MapCodec<? extends VegetationBlock> codec();

    protected boolean mayPlaceOn(final BlockState state, final BlockGetter level, final BlockPos pos) {
        return state.is(BlockTags.SUPPORTS_VEGETATION);
    }

    @Override
    protected BlockState updateShape(
        final BlockState state,
        final LevelReader level,
        final ScheduledTickAccess ticks,
        final BlockPos pos,
        final Direction directionToNeighbour,
        final BlockPos neighbourPos,
        final BlockState neighbourState,
        final RandomSource random
    ) {
        // CraftBukkit start
        if (!state.canSurvive(level, pos)) {
            // Suppress during worldgen
            if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel && serverLevel.hasPhysicsEvent) || !org.bukkit.craftbukkit.event.CraftEventFactory.callBlockPhysicsEvent(serverLevel, pos).isCancelled()) { // Paper
                return Blocks.AIR.defaultBlockState();
            }
        }
        return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
        // CraftBukkit end
    }

    @Override
    protected boolean canSurvive(final BlockState state, final LevelReader level, final BlockPos pos) {
        BlockPos below = pos.below();
        return this.mayPlaceOn(level.getBlockState(below), level, below);
    }

    @Override
    protected boolean propagatesSkylightDown(final BlockState state) {
        return state.getFluidState().isEmpty();
    }

    @Override
    protected boolean isPathfindable(final BlockState state, final PathComputationType type) {
        return type == PathComputationType.AIR && !this.hasCollision || super.isPathfindable(state, type);
    }

    // Purpur start - Ability for hoe to replant crops
    public void playerDestroyAndReplant(net.minecraft.world.level.Level world, net.minecraft.world.entity.player.Player player, BlockPos pos, BlockState state, @javax.annotation.Nullable net.minecraft.world.level.block.entity.BlockEntity blockEntity, net.minecraft.world.item.ItemStack itemInHand, net.minecraft.world.level.ItemLike itemToReplant) {
        player.awardStat(net.minecraft.stats.Stats.BLOCK_MINED.get(this));
        player.causeFoodExhaustion(0.005F, org.bukkit.event.entity.EntityExhaustionEvent.ExhaustionReason.BLOCK_MINED);
        java.util.List<net.minecraft.world.item.ItemStack> dropList = Block.getDrops(state, (net.minecraft.server.level.ServerLevel) world, pos, blockEntity, player, itemInHand);

        boolean planted = false;
        for (net.minecraft.world.item.ItemStack itemToDrop : dropList) {
            if (!planted && itemToDrop.getItem() == itemToReplant) {
                world.setBlock(pos, defaultBlockState(), 3);
                itemToDrop.setCount(itemToDrop.getCount() - 1);
                planted = true;
            }
            Block.popResource(world, pos, itemToDrop);
        }

        state.spawnAfterBreak((net.minecraft.server.level.ServerLevel) world, pos, itemInHand, true);
    }
    // Purpur end - Ability for hoe to replant crops
}
