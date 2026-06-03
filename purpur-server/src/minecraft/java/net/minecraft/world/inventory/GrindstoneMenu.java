package net.minecraft.world.inventory;

import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.phys.Vec3;

public class GrindstoneMenu extends AbstractContainerMenu {
    // CraftBukkit start
    private org.bukkit.craftbukkit.inventory.@org.jspecify.annotations.Nullable CraftInventoryView view = null;
    private final org.bukkit.entity.Player player;

    @Override
    public org.bukkit.craftbukkit.inventory.CraftInventoryView getBukkitView() {
        if (this.view != null) {
            return this.view;
        }

        org.bukkit.craftbukkit.inventory.CraftInventoryGrindstone inventory = new org.bukkit.craftbukkit.inventory.CraftInventoryGrindstone(this.repairSlots, this.resultSlots);
        this.view = new org.bukkit.craftbukkit.inventory.CraftInventoryView(this.player, inventory, this);
        return this.view;
    }
    // CraftBukkit end
    public static final int MAX_NAME_LENGTH = 35;
    public static final int INPUT_SLOT = 0;
    public static final int ADDITIONAL_SLOT = 1;
    public static final int RESULT_SLOT = 2;
    private static final int INV_SLOT_START = 3;
    private static final int INV_SLOT_END = 30;
    private static final int USE_ROW_SLOT_START = 30;
    private static final int USE_ROW_SLOT_END = 39;
    private final Container resultSlots; // Paper - Add missing InventoryHolders - move down
    final Container repairSlots; // Paper - Add missing InventoryHolders - move down
    private final ContainerLevelAccess access;

    public GrindstoneMenu(final int containerId, final Inventory inventory) {
        this(containerId, inventory, ContainerLevelAccess.NULL);
    }

    public GrindstoneMenu(final int containerId, final Inventory inventory, final ContainerLevelAccess access) {
        super(MenuType.GRINDSTONE, containerId);
        // Paper start - Add missing InventoryHolders
        this.resultSlots = new ResultContainer(this.createBlockHolder(access)); // Paper - Add missing InventoryHolders
        this.repairSlots = new SimpleContainer(this.createBlockHolder(access), 2) { // Paper - Add missing InventoryHolders
            @Override
            public void setChanged() {
                super.setChanged();
                GrindstoneMenu.this.slotsChanged(this);
            }
            // CraftBukkit start
            @Override
            public org.bukkit.Location getLocation() {
                return access.getLocation();
            }
            // CraftBukkit end
        };
        // Paper end - Add missing InventoryHolders
        this.access = access;
        this.addSlot(new Slot(this.repairSlots, 0, 49, 19) {
            @Override
            public boolean mayPlace(final ItemStack itemStack) {
                return itemStack.isDamageableItem() || EnchantmentHelper.hasAnyEnchantments(itemStack);
            }
        });
        this.addSlot(new Slot(this.repairSlots, 1, 49, 40) {
            @Override
            public boolean mayPlace(final ItemStack itemStack) {
                return itemStack.isDamageableItem() || EnchantmentHelper.hasAnyEnchantments(itemStack);
            }
        });
        this.addSlot(new Slot(this.resultSlots, 2, 129, 34) {
            @Override
            public boolean mayPlace(final ItemStack itemStack) {
                return false;
            }

            @Override
            public void onTake(final Player player, final ItemStack carried) {
                access.execute((level, pos) -> {
                    ItemStack itemstack = activeQuickItem == null ? carried : activeQuickItem; // Purpur - Grindstone API
                    if (level instanceof ServerLevel) {
                        // Paper start - Fire BlockExpEvent on grindstone use
                        org.bukkit.event.block.BlockExpEvent event = new org.bukkit.event.block.BlockExpEvent(org.bukkit.craftbukkit.block.CraftBlock.at(level, pos), this.getExperienceAmount(level));
                        event.callEvent();
                        org.purpurmc.purpur.event.inventory.GrindstoneTakeResultEvent grindstoneTakeResultEvent = new org.purpurmc.purpur.event.inventory.GrindstoneTakeResultEvent(player.getBukkitEntity(), getBukkitView(), org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(itemstack), event.getExpToDrop()); grindstoneTakeResultEvent.callEvent(); // Purpur - Grindstone API
                        ExperienceOrb.awardWithDirection((ServerLevel) level, Vec3.atCenterOf(pos), Vec3.ZERO, grindstoneTakeResultEvent.getExperienceAmount(), org.bukkit.entity.ExperienceOrb.SpawnReason.GRINDSTONE, player, null); // Purpur - Grindstone API
                        // Paper end - Fire BlockExpEvent on grindstone use
                    }

                    level.levelEvent(LevelEvent.SOUND_GRINDSTONE_USED, pos, 0);
                });
                GrindstoneMenu.this.repairSlots.setItem(0, ItemStack.EMPTY);
                GrindstoneMenu.this.repairSlots.setItem(1, ItemStack.EMPTY);
            }

            private int getExperienceAmount(final Level level) {
                int amount = 0;
                amount += this.getExperienceFromItem(GrindstoneMenu.this.repairSlots.getItem(0));
                amount += this.getExperienceFromItem(GrindstoneMenu.this.repairSlots.getItem(1));
                if (amount > 0) {
                    int halfAmount = (int)Math.ceil(amount / 2.0);
                    return halfAmount + level.getRandom().nextInt(halfAmount);
                } else {
                    return 0;
                }
            }

            private int getExperienceFromItem(final ItemStack item) {
                int amount = 0;
                ItemEnchantments enchantments = EnchantmentHelper.getEnchantmentsForCrafting(item);

                for (Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
                    Holder<Enchantment> enchant = entry.getKey();
                    int lvl = entry.getIntValue();
                    if (!org.purpurmc.purpur.PurpurConfig.grindstoneIgnoredEnchants.contains(enchant.value())) { // Purpur - Config for grindstones
                        amount += enchant.value().getMinCost(lvl);
                    }
                }

                return amount;
            }
        });
        this.addStandardInventorySlots(inventory, 8, 84);
        this.player = (org.bukkit.entity.Player) inventory.player.getBukkitEntity(); // CraftBukkit
    }

    @Override
    public void slotsChanged(final Container container) {
        super.slotsChanged(container);
        if (container == this.repairSlots) {
            this.createResult();
            org.bukkit.craftbukkit.event.CraftEventFactory.callPrepareResultEvent(this, RESULT_SLOT); // Paper - Add PrepareResultEvent
        }
    }

    private void createResult() {
        org.bukkit.craftbukkit.event.CraftEventFactory.callPrepareGrindstoneEvent(this.getBukkitView(), this.computeResult(this.repairSlots.getItem(0), this.repairSlots.getItem(1))); // CraftBukkit
        this.sendAllDataToRemote(); // CraftBukkit - SPIGOT-6686: Always send completed inventory to stay in sync with client
        this.broadcastChanges();
    }

    private ItemStack computeResult(final ItemStack input, final ItemStack additional) {
        boolean hasAnItem = !input.isEmpty() || !additional.isEmpty();
        if (!hasAnItem) {
            return ItemStack.EMPTY;
        }

        if (input.getCount() <= 1 && additional.getCount() <= 1) {
            boolean hasBothItems = !input.isEmpty() && !additional.isEmpty();
            if (!hasBothItems) {
                ItemStack item = !input.isEmpty() ? input : additional;
                return !EnchantmentHelper.hasAnyEnchantments(item) ? ItemStack.EMPTY : this.removeNonCursesFrom(item.copy());
            } else {
                return this.mergeItems(input, additional);
            }
        } else {
            return ItemStack.EMPTY;
        }
    }

    private ItemStack mergeItems(final ItemStack input, final ItemStack additional) {
        if (!input.is(additional.getItem())) {
            return ItemStack.EMPTY;
        }

        int durability = Math.max(input.getMaxDamage(), additional.getMaxDamage());
        int remaining1 = input.getMaxDamage() - input.getDamageValue();
        int remaining2 = additional.getMaxDamage() - additional.getDamageValue();
        int remaining = remaining1 + remaining2 + durability * 5 / 100;
        int count = 1;
        if (!input.isDamageableItem()) {
            if (input.getMaxStackSize() < 2 || !ItemStack.matches(input, additional)) {
                return ItemStack.EMPTY;
            }

            count = 2;
        }

        ItemStack newItem = input.copyWithCount(count);
        if (newItem.isDamageableItem()) {
            newItem.set(DataComponents.MAX_DAMAGE, durability);
            newItem.setDamageValue(Math.max(durability - remaining, 0));
        }

        this.mergeEnchantsFrom(newItem, additional);
        return this.removeNonCursesFrom(newItem);
    }

    private void mergeEnchantsFrom(final ItemStack target, final ItemStack source) {
        EnchantmentHelper.updateEnchantments(target, newEnchantments -> {
            ItemEnchantments enchantments = EnchantmentHelper.getEnchantmentsForCrafting(source);

            for (Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
                Holder<Enchantment> enchant = entry.getKey();
                if (!org.purpurmc.purpur.PurpurConfig.grindstoneIgnoredEnchants.contains(enchant.value()) || newEnchantments.getLevel(enchant) == 0) { // Purpur - Config for grindstones
                    newEnchantments.upgrade(enchant, entry.getIntValue());
                }
            }
        });
    }

    // Purpur start - Config for grindstones
    private java.util.List<net.minecraft.core.component.DataComponentType<?>> GRINDSTONE_REMOVE_ATTRIBUTES_REMOVAL_LIST = java.util.List.of(
        // DataComponents.MAX_STACK_SIZE,
        // DataComponents.DAMAGE,
        // DataComponents.BLOCK_STATE,
        DataComponents.CUSTOM_DATA,
        // DataComponents.MAX_DAMAGE,
        // DataComponents.UNBREAKABLE,
        // DataComponents.CUSTOM_NAME,
        // DataComponents.ITEM_NAME,
        // DataComponents.LORE,
        // DataComponents.RARITY,
        // DataComponents.ENCHANTMENTS,
        // DataComponents.CAN_PLACE_ON,
        // DataComponents.CAN_BREAK,
        DataComponents.ATTRIBUTE_MODIFIERS,
        DataComponents.CUSTOM_MODEL_DATA,
        // DataComponents.HIDE_ADDITIONAL_TOOLTIP,
        // DataComponents.HIDE_TOOLTIP,
        // DataComponents.REPAIR_COST,
        // DataComponents.CREATIVE_SLOT_LOCK,
        // DataComponents.ENCHANTMENT_GLINT_OVERRIDE,
        // DataComponents.INTANGIBLE_PROJECTILE,
        // DataComponents.FOOD,
        // DataComponents.FIRE_RESISTANT,
        // DataComponents.TOOL,
        // DataComponents.STORED_ENCHANTMENTS,
        DataComponents.DYED_COLOR,
        // DataComponents.MAP_COLOR,
        // DataComponents.MAP_ID,
        // DataComponents.MAP_DECORATIONS,
        // DataComponents.MAP_POST_PROCESSING,
        // DataComponents.CHARGED_PROJECTILES,
        // DataComponents.BUNDLE_CONTENTS,
        // DataComponents.POTION_CONTENTS,
        DataComponents.SUSPICIOUS_STEW_EFFECTS
        // DataComponents.WRITABLE_BOOK_CONTENT,
        // DataComponents.WRITTEN_BOOK_CONTENT,
        // DataComponents.TRIM,
        // DataComponents.DEBUG_STICK_STATE,
        // DataComponents.ENTITY_DATA,
        // DataComponents.BUCKET_ENTITY_DATA,
        // DataComponents.BLOCK_ENTITY_DATA,
        // DataComponents.INSTRUMENT,
        // DataComponents.OMINOUS_BOTTLE_AMPLIFIER,
        // DataComponents.RECIPES,
        // DataComponents.LODESTONE_TRACKER,
        // DataComponents.FIREWORK_EXPLOSION,
        // DataComponents.FIREWORKS,
        // DataComponents.PROFILE,
        // DataComponents.NOTE_BLOCK_SOUND,
        // DataComponents.BANNER_PATTERNS,
        // DataComponents.BASE_COLOR,
        // DataComponents.POT_DECORATIONS,
        // DataComponents.CONTAINER,
        // DataComponents.BEES,
        // DataComponents.LOCK,
        // DataComponents.CONTAINER_LOOT,
    );
    // Purpur end - Config for grindstones
    private ItemStack removeNonCursesFrom(ItemStack item) {
        ItemEnchantments newEnchantments = EnchantmentHelper.updateEnchantments(
            item, enchantments -> enchantments.removeIf(enchantment -> !org.purpurmc.purpur.PurpurConfig.grindstoneIgnoredEnchants.contains(enchantment.value())) // Purpur - Config for grindstones
        );
        if (item.is(Items.ENCHANTED_BOOK) && newEnchantments.isEmpty()) {
            item = item.transmuteCopy(Items.BOOK);
        }

        int repairCost = 0;

        for (int i = 0; i < newEnchantments.size(); i++) {
            repairCost = AnvilMenu.calculateIncreasedRepairCost(repairCost);
        }

        item.set(DataComponents.REPAIR_COST, repairCost);

        // Purpur start - Config for grindstones
        net.minecraft.core.component.DataComponentPatch.Builder builder = net.minecraft.core.component.DataComponentPatch.builder();
        if (org.purpurmc.purpur.PurpurConfig.grindstoneRemoveAttributes) {
            item.getComponents().forEach(typedDataComponent -> {
                if (GRINDSTONE_REMOVE_ATTRIBUTES_REMOVAL_LIST.contains(typedDataComponent.type())) {
                    builder.remove(typedDataComponent.type());
                }
            });
        }
        if (org.purpurmc.purpur.PurpurConfig.grindstoneRemoveDisplay) {
            builder.remove(DataComponents.CUSTOM_NAME);
            builder.remove(DataComponents.LORE);
        }
        item.applyComponents(builder.build());
        // Purpur end - Config for grindstones
        return item;
    }

    @Override
    public void removed(final Player player) {
        super.removed(player);
        this.access.execute((level, pos) -> this.clearContainer(player, this.repairSlots));
    }

    @Override
    public boolean stillValid(final Player player) {
        if (!this.checkReachable) return true; // CraftBukkit
        return stillValid(this.access, player, Blocks.GRINDSTONE);
    }

    @Override
    public ItemStack quickMoveStack(final Player player, final int slotIndex) {
        ItemStack clicked = ItemStack.EMPTY;
        Slot slot = this.slots.get(slotIndex);
        if (slot != null && slot.hasItem()) {
            ItemStack item = slot.getItem();
            clicked = item.copy();
            ItemStack input = this.repairSlots.getItem(0);
            ItemStack additional = this.repairSlots.getItem(1);
            if (slotIndex == 2) {
                if (!this.moveItemStackTo(item, 3, 39, true)) {
                    return ItemStack.EMPTY;
                }

                slot.onQuickCraft(item, clicked);
            } else if (slotIndex != 0 && slotIndex != 1) {
                if (!input.isEmpty() && !additional.isEmpty()) {
                    if (slotIndex >= 3 && slotIndex < 30) {
                        if (!this.moveItemStackTo(item, 30, 39, false)) {
                            return ItemStack.EMPTY;
                        }
                    } else if (slotIndex >= 30 && slotIndex < 39 && !this.moveItemStackTo(item, 3, 30, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (!this.moveItemStackTo(item, 0, 2, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(item, 3, 39, false)) {
                return ItemStack.EMPTY;
            }

            if (item.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            if (item.getCount() == clicked.getCount()) {
                return ItemStack.EMPTY;
            }

            this.activeQuickItem = clicked; // Purpur - Grindstone API
            slot.onTake(player, item);
            this.activeQuickItem = null; // Purpur - Grindstone API
        }

        return clicked;
    }
}
