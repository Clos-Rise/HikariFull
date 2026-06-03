package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;
import org.jspecify.annotations.Nullable;

public class HarvestFarmland extends Behavior<Villager> {
    private static final int HARVEST_DURATION = 200;
    public static final float SPEED_MODIFIER = 0.5F;
    private @Nullable BlockPos aboveFarmlandPos;
    private long nextOkStartTime;
    private int timeWorkedSoFar;
    private final List<BlockPos> validFarmlandAroundVillager = Lists.newArrayList();
    private boolean clericWartFarmer = false; // Purpur - Option for Villager Clerics to farm Nether Wart

    public HarvestFarmland() {
        super(
            ImmutableMap.of(
                MemoryModuleType.LOOK_TARGET,
                MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.WALK_TARGET,
                MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.SECONDARY_JOB_SITE,
                MemoryStatus.VALUE_PRESENT
            )
        );
    }

    @Override
    protected boolean checkExtraStartConditions(final ServerLevel level, final Villager body) {
        if (!level.getGameRules().get(GameRules.MOB_GRIEFING, level.purpurConfig.villagerMobGriefingOverride)) { // Purpur - Add mobGriefing override to everything affected
            return false;
        }

        if (!body.getVillagerData().profession().is(VillagerProfession.FARMER) && !(level.purpurConfig.villagerClericsFarmWarts && body.getVillagerData().profession().is(VillagerProfession.CLERIC))) { // Purpur - Option for Villager Clerics to farm Nether Wart
            return false;
        }

        if (!this.clericWartFarmer && body.getVillagerData().profession().is(VillagerProfession.CLERIC)) this.clericWartFarmer = true; // Purpur - Option for Villager Clerics to farm Nether Wart
        BlockPos.MutableBlockPos mutPos = body.blockPosition().mutable();
        this.validFarmlandAroundVillager.clear();

        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    mutPos.set(body.getX() + x, body.getY() + y, body.getZ() + z);
                    if (this.validPos(mutPos, level)) {
                        this.validFarmlandAroundVillager.add(new BlockPos(mutPos));
                    }
                }
            }
        }

        this.aboveFarmlandPos = this.getValidFarmland(level);
        return this.aboveFarmlandPos != null;
    }

    private @Nullable BlockPos getValidFarmland(final ServerLevel level) {
        return this.validFarmlandAroundVillager.isEmpty()
            ? null
            : this.validFarmlandAroundVillager.get(level.getRandom().nextInt(this.validFarmlandAroundVillager.size()));
    }

    private boolean validPos(final BlockPos blockPos, final ServerLevel level) {
        BlockState state = level.getBlockState(blockPos);
        Block block = state.getBlock();
        Block blockBelow = level.getBlockState(blockPos.below()).getBlock();
        if (this.clericWartFarmer) return block == net.minecraft.world.level.block.Blocks.NETHER_WART && state.getValue(net.minecraft.world.level.block.NetherWartBlock.AGE) == 3 || state.isAir() && block == net.minecraft.world.level.block.Blocks.SOUL_SAND; // Purpur - Option for Villager Clerics to farm Nether Wart
        return block instanceof CropBlock && ((CropBlock)block).isMaxAge(state) || state.isAir() && blockBelow instanceof FarmlandBlock;
    }

    @Override
    protected void start(final ServerLevel level, final Villager body, final long timestamp) {
        if (timestamp > this.nextOkStartTime && this.aboveFarmlandPos != null) {
            body.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(this.aboveFarmlandPos));
            body.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new BlockPosTracker(this.aboveFarmlandPos), SPEED_MODIFIER, 1));
        }
    }

    @Override
    protected void stop(final ServerLevel level, final Villager body, final long timestamp) {
        body.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        body.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        this.timeWorkedSoFar = 0;
        this.nextOkStartTime = timestamp + 40L;
    }

    @Override
    protected void tick(final ServerLevel level, final Villager body, final long timestamp) {
        if (this.aboveFarmlandPos == null || this.aboveFarmlandPos.closerToCenterThan(body.position(), 1.0)) {
            if (this.aboveFarmlandPos != null && timestamp > this.nextOkStartTime) {
                BlockState blockState = level.getBlockState(this.aboveFarmlandPos);
                Block block = blockState.getBlock();
                Block blockBelow = level.getBlockState(this.aboveFarmlandPos.below()).getBlock();
                if (block instanceof CropBlock && ((CropBlock)block).isMaxAge(blockState) && !this.clericWartFarmer || this.clericWartFarmer && block == net.minecraft.world.level.block.Blocks.NETHER_WART && blockState.getValue(net.minecraft.world.level.block.NetherWartBlock.AGE) == 3) { // Purpur - Option for Villager Clerics to farm Nether Wart
                    if (org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(body, this.aboveFarmlandPos, blockState.getFluidState().createLegacyBlock())) { // CraftBukkit // Paper - fix wrong block state
                    level.destroyBlock(this.aboveFarmlandPos, true, body);
                    } // CraftBukkit
                }

                if (blockState.isAir() && blockBelow instanceof FarmlandBlock &&  !this.clericWartFarmer || this.clericWartFarmer && blockBelow == net.minecraft.world.level.block.Blocks.SOUL_SAND && body.hasFarmSeeds()) { // Purpur - Option for Villager Clerics to farm Nether Wart
                    SimpleContainer inventory = body.getInventory();

                    for (int i = 0; i < inventory.getContainerSize(); i++) {
                        ItemStack itemStack = inventory.getItem(i);
                        boolean ok = false;
                        if (!itemStack.isEmpty() && (itemStack.is(ItemTags.VILLAGER_PLANTABLE_SEEDS) || this.clericWartFarmer && itemStack.getItem() == net.minecraft.world.item.Items.NETHER_WART) && itemStack.getItem() instanceof BlockItem blockItem) { // Purpur - Option for Villager Clerics to farm Nether Wart
                            BlockState place = blockItem.getBlock().defaultBlockState();
                            if (org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(body, this.aboveFarmlandPos, place)) { // CraftBukkit
                            level.setBlockAndUpdate(this.aboveFarmlandPos, place);
                            level.gameEvent(GameEvent.BLOCK_PLACE, this.aboveFarmlandPos, GameEvent.Context.of(body, place));
                            ok = true;
                            } // CraftBukkit
                        }

                        if (ok) {
                            level.playSound(
                                null,
                                this.aboveFarmlandPos.getX(),
                                this.aboveFarmlandPos.getY(),
                                this.aboveFarmlandPos.getZ(),
                                this.clericWartFarmer ? SoundEvents.NETHER_WART_PLANTED : SoundEvents.CROP_PLANTED, // Purpur - Option for Villager Clerics to farm Nether Wart
                                SoundSource.BLOCKS,
                                1.0F,
                                1.0F
                            );
                            itemStack.shrink(1);
                            if (itemStack.isEmpty()) {
                                inventory.setItem(i, ItemStack.EMPTY);
                            }
                            break;
                        }
                    }
                }

                if (block instanceof CropBlock && !((CropBlock)block).isMaxAge(blockState)) {
                    this.validFarmlandAroundVillager.remove(this.aboveFarmlandPos);
                    this.aboveFarmlandPos = this.getValidFarmland(level);
                    if (this.aboveFarmlandPos != null) {
                        this.nextOkStartTime = timestamp + 20L;
                        body.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new BlockPosTracker(this.aboveFarmlandPos), SPEED_MODIFIER, 1));
                        body.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(this.aboveFarmlandPos));
                    }
                }
            }

            this.timeWorkedSoFar++;
        }
    }

    @Override
    protected boolean canStillUse(final ServerLevel level, final Villager body, final long timestamp) {
        return this.timeWorkedSoFar < HARVEST_DURATION;
    }
}
