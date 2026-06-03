package net.minecraft.world.level.block.entity;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class BarrelBlockEntity extends RandomizableContainerBlockEntity {
    // CraftBukkit start - add fields and methods
    public java.util.List<org.bukkit.entity.HumanEntity> transaction = new java.util.ArrayList<>();
    private int maxStack = MAX_STACK;

    @Override
    public java.util.List<ItemStack> getContents() {
        return this.items;
    }

    @Override
    public void onOpen(org.bukkit.craftbukkit.entity.CraftHumanEntity player) {
        this.transaction.add(player);
    }

    @Override
    public void onClose(org.bukkit.craftbukkit.entity.CraftHumanEntity player) {
        this.transaction.remove(player);
    }

    @Override
    public java.util.List<org.bukkit.entity.HumanEntity> getViewers() {
        return this.transaction;
    }

    @Override
    public int getMaxStackSize() {
        return this.maxStack;
    }

    @Override
    public void setMaxStackSize(int size) {
        this.maxStack = size;
    }
    // CraftBukkit end
    private static final Component DEFAULT_NAME = Component.translatable("container.barrel");
    // Purpur start - Barrels and enderchests 6 rows
    private NonNullList<ItemStack> items = NonNullList.withSize(switch (org.purpurmc.purpur.PurpurConfig.barrelRows) {
        case 6 -> 54;
        case 5 -> 45;
        case 4 -> 36;
        case 2 -> 18;
        case 1 -> 9;
        default -> 27;
    }, ItemStack.EMPTY);
    // Purpur end - Barrels and enderchests 6 rows
    public final ContainerOpenersCounter openersCounter = new ContainerOpenersCounter() {
        // Paper start - delay open/close callbacks
        @Override
        public boolean delayCallbacks() {
            return true;
        }
        // Paper end - delay open/close callbacks

        @Override
        protected void onOpen(final Level level, final BlockPos pos, final BlockState state) {
            BarrelBlockEntity.this.playSound(state, SoundEvents.BARREL_OPEN);
            BarrelBlockEntity.this.updateBlockState(state, true);
        }

        @Override
        protected void onClose(final Level level, final BlockPos pos, final BlockState state) {
            BarrelBlockEntity.this.playSound(state, SoundEvents.BARREL_CLOSE);
            BarrelBlockEntity.this.updateBlockState(state, false);
        }

        @Override
        protected void openerCountChanged(final Level level, final BlockPos pos, final BlockState blockState, final int previous, final int current) {
        }

        @Override
        public boolean isOwnContainer(final Player player) {
            if (player.containerMenu instanceof ChestMenu) {
                Container container = ((ChestMenu)player.containerMenu).getContainer();
                return container == BarrelBlockEntity.this;
            } else {
                return false;
            }
        }
    };

    public BarrelBlockEntity(final BlockPos worldPosition, final BlockState blockState) {
        super(BlockEntityType.BARREL, worldPosition, blockState);
    }

    @Override
    protected void saveAdditional(final ValueOutput output) {
        super.saveAdditional(output);
        if (!this.trySaveLootTable(output)) {
            ContainerHelper.saveAllItems(output, this.items);
        }
    }

    @Override
    protected void loadAdditional(final ValueInput input) {
        super.loadAdditional(input);
        this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
        if (!this.tryLoadLootTable(input)) {
            ContainerHelper.loadAllItems(input, this.items);
        }
    }

    @Override
    public int getContainerSize() {
        // Purpur start - Barrels and enderchests 6 rows
        return switch (org.purpurmc.purpur.PurpurConfig.barrelRows) {
            case 6 -> 54;
            case 5 -> 45;
            case 4 -> 36;
            case 2 -> 18;
            case 1 -> 9;
            default -> 27;
        };
        // Purpur end - Barrels and enderchests 6 rows
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    protected void setItems(final NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    protected Component getDefaultName() {
        return DEFAULT_NAME;
    }

    @Override
    protected AbstractContainerMenu createMenu(final int containerId, final Inventory inventory) {
        // Purpur start - Barrels and enderchests 6 rows
        return switch (org.purpurmc.purpur.PurpurConfig.barrelRows) {
            case 6 -> ChestMenu.sixRows(containerId, inventory, this);
            case 5 -> ChestMenu.fiveRows(containerId, inventory, this);
            case 4 -> ChestMenu.fourRows(containerId, inventory, this);
            case 2 -> ChestMenu.twoRows(containerId, inventory, this);
            case 1 -> ChestMenu.oneRow(containerId, inventory, this);
            default -> ChestMenu.threeRows(containerId, inventory, this);
        };
        // Purpur end - Barrels and enderchests 6 rows
    }

    @Override
    public void startOpen(final ContainerUser containerUser) {
        if (!this.remove && !containerUser.getLivingEntity().isSpectator()) {
            this.openersCounter
                .incrementOpeners(
                    containerUser.getLivingEntity(), this.getLevel(), this.getBlockPos(), this.getBlockState(), containerUser.getContainerInteractionRange()
                );
        }
    }

    @Override
    public void stopOpen(final ContainerUser containerUser) {
        if (!this.remove && !containerUser.getLivingEntity().isSpectator()) {
            this.openersCounter.decrementOpeners(containerUser.getLivingEntity(), this.getLevel(), this.getBlockPos(), this.getBlockState());
        }
    }

    @Override
    public List<ContainerUser> getEntitiesWithContainerOpen() {
        return this.openersCounter.getEntitiesWithContainerOpen(this.getLevel(), this.getBlockPos());
    }

    public void recheckOpen() {
        if (!this.remove) {
            this.openersCounter.recheckOpeners(this.getLevel(), this.getBlockPos(), this.getBlockState());
        }
    }

    public void updateBlockState(final BlockState state, final boolean isOpen) {
        this.level.setBlock(this.getBlockPos(), state.setValue(BarrelBlock.OPEN, isOpen), Block.UPDATE_ALL);
    }

    public void playSound(final BlockState state, final SoundEvent event) {
        Vec3i direction = state.getValue(BarrelBlock.FACING).getUnitVec3i();
        double x = this.worldPosition.getX() + 0.5 + direction.getX() / 2.0;
        double y = this.worldPosition.getY() + 0.5 + direction.getY() / 2.0;
        double z = this.worldPosition.getZ() + 0.5 + direction.getZ() / 2.0;
        this.level.playSound(null, x, y, z, event, SoundSource.BLOCKS, 0.5F, this.level.getRandom().nextFloat() * 0.1F + 0.9F);
    }
}
