package net.minecraft.world.item;

import com.google.common.collect.ImmutableMap.Builder;
import java.util.Map;
import java.util.Optional;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.gameevent.GameEvent;
import org.jspecify.annotations.Nullable;

public class AxeItem extends Item {
    protected static final Map<Block, Block> STRIPPABLES = new Builder<Block, Block>()
        .put(Blocks.OAK_WOOD, Blocks.STRIPPED_OAK_WOOD)
        .put(Blocks.OAK_LOG, Blocks.STRIPPED_OAK_LOG)
        .put(Blocks.DARK_OAK_WOOD, Blocks.STRIPPED_DARK_OAK_WOOD)
        .put(Blocks.DARK_OAK_LOG, Blocks.STRIPPED_DARK_OAK_LOG)
        .put(Blocks.PALE_OAK_WOOD, Blocks.STRIPPED_PALE_OAK_WOOD)
        .put(Blocks.PALE_OAK_LOG, Blocks.STRIPPED_PALE_OAK_LOG)
        .put(Blocks.ACACIA_WOOD, Blocks.STRIPPED_ACACIA_WOOD)
        .put(Blocks.ACACIA_LOG, Blocks.STRIPPED_ACACIA_LOG)
        .put(Blocks.CHERRY_WOOD, Blocks.STRIPPED_CHERRY_WOOD)
        .put(Blocks.CHERRY_LOG, Blocks.STRIPPED_CHERRY_LOG)
        .put(Blocks.BIRCH_WOOD, Blocks.STRIPPED_BIRCH_WOOD)
        .put(Blocks.BIRCH_LOG, Blocks.STRIPPED_BIRCH_LOG)
        .put(Blocks.JUNGLE_WOOD, Blocks.STRIPPED_JUNGLE_WOOD)
        .put(Blocks.JUNGLE_LOG, Blocks.STRIPPED_JUNGLE_LOG)
        .put(Blocks.SPRUCE_WOOD, Blocks.STRIPPED_SPRUCE_WOOD)
        .put(Blocks.SPRUCE_LOG, Blocks.STRIPPED_SPRUCE_LOG)
        .put(Blocks.WARPED_STEM, Blocks.STRIPPED_WARPED_STEM)
        .put(Blocks.WARPED_HYPHAE, Blocks.STRIPPED_WARPED_HYPHAE)
        .put(Blocks.CRIMSON_STEM, Blocks.STRIPPED_CRIMSON_STEM)
        .put(Blocks.CRIMSON_HYPHAE, Blocks.STRIPPED_CRIMSON_HYPHAE)
        .put(Blocks.MANGROVE_WOOD, Blocks.STRIPPED_MANGROVE_WOOD)
        .put(Blocks.MANGROVE_LOG, Blocks.STRIPPED_MANGROVE_LOG)
        .put(Blocks.BAMBOO_BLOCK, Blocks.STRIPPED_BAMBOO_BLOCK)
        .build();

    public AxeItem(final ToolMaterial material, final float attackDamageBaseline, final float attackSpeedBaseline, final Item.Properties properties) {
        super(properties.axe(material, attackDamageBaseline, attackSpeedBaseline));
    }

    @Override
    public InteractionResult useOn(final UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        if (playerHasBlockingItemUseIntent(context)) {
            return InteractionResult.PASS;
        }

        Optional<org.purpurmc.purpur.tool.Actionable> newBlock = this.evaluateActionable(level, pos, player, level.getBlockState(pos)); // Purpur - Tool actionable options
        if (newBlock.isEmpty()) {
            return InteractionResult.PASS;
        }

        org.purpurmc.purpur.tool.Actionable actionable = newBlock.get(); // Purpur - Tool actionable options
        BlockState state = actionable.into().withPropertiesOf(level.getBlockState(pos)); // Purpur - Tool actionable options
        ItemStack itemInHand = context.getItemInHand();
        // Paper start - EntityChangeBlockEvent
        if (!org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(player, pos, state)) { // Purpur - Tool actionable options
            return InteractionResult.PASS;
        }
        // Paper end
        if (player instanceof ServerPlayer) {
            CriteriaTriggers.ITEM_USED_ON_BLOCK.trigger((ServerPlayer)player, pos, itemInHand);
        }

        // Purpur start - Tool actionable options
        level.setBlock(pos, state, Block.UPDATE_ALL_IMMEDIATE);
        actionable.drops().forEach((drop, chance) -> {
            if (level.getRandom().nextDouble() < chance) {
                Block.popResourceFromFace(level, pos, context.getClickedFace(), new ItemStack(drop));
            }
        });
        level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, state));
        // Purpur end - Tool actionable options
        if (player != null) {
            itemInHand.hurtAndBreak(1, player, context.getHand().asEquipmentSlot());
        }

        return InteractionResult.SUCCESS;
    }

    private static boolean playerHasBlockingItemUseIntent(final UseOnContext context) {
        Player player = context.getPlayer();
        return context.getHand().equals(InteractionHand.MAIN_HAND)
            && player.getOffhandItem().has(DataComponents.BLOCKS_ATTACKS)
            && !player.isSecondaryUseActive();
    }

    private Optional<org.purpurmc.purpur.tool.Actionable> evaluateActionable(final Level level, final BlockPos pos, final @Nullable Player player, final BlockState oldState) { // Purpur - Tool actionable options
        Optional<org.purpurmc.purpur.tool.Actionable> stripped = Optional.ofNullable(level.purpurConfig.axeStrippables.get(oldState.getBlock())); // Purpur - Tool actionable options
        if (stripped.isPresent()) {
            level.playSound(STRIPPABLES.containsKey(oldState.getBlock()) ? player : null, pos, SoundEvents.AXE_STRIP, SoundSource.BLOCKS, 1.0F, 1.0F); // Purpur - force sound
            return stripped;
        } else {
            Optional<org.purpurmc.purpur.tool.Actionable> scrapedBlock = Optional.ofNullable(level.purpurConfig.axeWeatherables.get(oldState.getBlock())); // Purpur - Tool actionable options
            if (scrapedBlock.isPresent()) {
                spawnSoundAndParticle(level, pos, WeatheringCopper.getPrevious(oldState).isPresent() ? player : null, oldState, SoundEvents.AXE_SCRAPE, LevelEvent.PARTICLES_SCRAPE); // Purpur - Tool actionable options - force sound
                return scrapedBlock;
            } else {
                // Purpur start - Tool actionable options
                Optional<org.purpurmc.purpur.tool.Actionable> waxoffBlock = Optional.ofNullable(level.purpurConfig.axeWaxables.get(oldState.getBlock()));
                //    .map(b -> b.withPropertiesOf(oldState));
                // Purpur end - Tool actionable options
                if (waxoffBlock.isPresent()) {
                    spawnSoundAndParticle(level, pos, HoneycombItem.WAX_OFF_BY_BLOCK.get().containsKey(oldState.getBlock()) ? player : null, oldState, SoundEvents.AXE_WAX_OFF, LevelEvent.PARTICLES_WAX_OFF); // Purpur - Tool actionable options - force sound
                    return waxoffBlock;
                } else {
                    return Optional.empty();
                }
            }
        }
    }

    private static void spawnSoundAndParticle(
        final Level level, final BlockPos pos, final @Nullable Player player, final BlockState oldState, final SoundEvent soundEvent, final int particle
    ) {
        level.playSound(player, pos, soundEvent, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.levelEvent(player, particle, pos, 0);
        if (oldState.getBlock() instanceof ChestBlock && oldState.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            BlockPos neighborPos = ChestBlock.getConnectedBlockPos(pos, oldState);
            level.gameEvent(GameEvent.BLOCK_CHANGE, neighborPos, GameEvent.Context.of(player, level.getBlockState(neighborPos)));
            level.levelEvent(player, particle, neighborPos, 0);
        }
    }

    private Optional<BlockState> getStripped(final BlockState state) {
        return Optional.ofNullable(STRIPPABLES.get(state.getBlock()))
            .map(block -> block.defaultBlockState().setValue(RotatedPillarBlock.AXIS, state.getValue(RotatedPillarBlock.AXIS)));
    }
}
