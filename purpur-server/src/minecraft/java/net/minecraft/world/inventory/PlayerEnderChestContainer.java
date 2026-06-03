package net.minecraft.world.inventory;

import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class PlayerEnderChestContainer extends SimpleContainer {
    private @Nullable EnderChestBlockEntity activeChest;
    // CraftBukkit start
    private final Player owner;

    @Override
    public org.bukkit.inventory.InventoryHolder getOwner() {
        return this.owner.getBukkitEntity();
    }

    @Override
    public org.bukkit.@Nullable Location getLocation() {
        return this.activeChest != null ? org.bukkit.craftbukkit.util.CraftLocation.toBukkit(this.activeChest.getBlockPos(), this.activeChest.getLevel()) : null;
    }

    public PlayerEnderChestContainer(Player owner) {
        super(org.purpurmc.purpur.PurpurConfig.enderChestSixRows ? 54 : 27); // Purpur - Barrels and enderchests 6 rows
        this.owner = owner;
        // CraftBukkit end
    }

    // Purpur start - Barrels and enderchests 6 rows
    @Override
    public int getContainerSize() {
        return owner == null || owner.sixRowEnderchestSlotCount < 0 ? super.getContainerSize() : owner.sixRowEnderchestSlotCount;
    }
    // Purpur end - Barrels and enderchests 6 rows

    public void setActiveChest(final EnderChestBlockEntity activeChest) {
        this.activeChest = activeChest;
    }

    public boolean isActiveChest(final EnderChestBlockEntity chest) {
        return this.activeChest == chest;
    }

    public void fromSlots(final ValueInput.TypedInputList<ItemStackWithSlot> list) {
        // Purpur start - Barrels and enderchests 6 rows
        int storageSlotCount = org.purpurmc.purpur.PurpurConfig.enderChestSixRows && org.purpurmc.purpur.PurpurConfig.enderChestPersistHiddenRows ? 54 : this.getContainerSize();
        for (int i = 0; i < storageSlotCount; i++) {
        // Purpur end - Barrels and enderchests 6 rows
            this.setItem(i, ItemStack.EMPTY);
        }

        for (ItemStackWithSlot item : list) {
            if (item.isValidInContainer(storageSlotCount)) { // Purpur - Barrels and enderchests 6 rows
                this.setItem(item.slot(), item.stack());
            }
        }
    }

    public void storeAsSlots(final ValueOutput.TypedOutputList<ItemStackWithSlot> output) {
        // Purpur start - Barrels and enderchests 6 rows
        int storageSlotCount = org.purpurmc.purpur.PurpurConfig.enderChestSixRows && org.purpurmc.purpur.PurpurConfig.enderChestPersistHiddenRows ? 54 : this.getContainerSize(); // Purpur - Barrels and enderchests 6 rows
        for (int i = 0; i < storageSlotCount; i++) {
        // Purpur end - Barrels and enderchests 6 rows
            ItemStack itemStack = this.getItem(i);
            if (!itemStack.isEmpty()) {
                output.add(new ItemStackWithSlot(i, itemStack));
            }
        }
    }

    @Override
    public boolean stillValid(final Player player) {
        return (this.activeChest == null || this.activeChest.stillValid(player)) && super.stillValid(player);
    }

    @Override
    public void startOpen(final ContainerUser containerUser) {
        if (this.activeChest != null) {
            this.activeChest.startOpen(containerUser);
        }

        super.startOpen(containerUser);
    }

    @Override
    public void stopOpen(final ContainerUser containerUser) {
        if (this.activeChest != null) {
            this.activeChest.stopOpen(containerUser);
        }

        super.stopOpen(containerUser);
        this.activeChest = null;
    }
}
