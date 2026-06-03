package net.minecraft.world.entity.animal.cow;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public abstract class AbstractCow extends Animal {
    private static final EntityDimensions BABY_DIMENSIONS = EntityType.COW.getDimensions().scale(0.5F).withEyeHeight(0.665F);

    public AbstractCow(final EntityType<? extends AbstractCow> type, final Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(0, new org.purpurmc.purpur.entity.ai.HasRider(this)); // Purpur - Ridables
        this.goalSelector.addGoal(1, new PanicGoal(this, 2.0));
        this.goalSelector.addGoal(2, new BreedGoal(this, 1.0));
        this.goalSelector.addGoal(3, new TemptGoal(this, 1.25, i -> level().purpurConfig.cowFeedMushrooms > 0 && (i.is(net.minecraft.world.level.block.Blocks.RED_MUSHROOM.asItem()) || i.is(net.minecraft.world.level.block.Blocks.BROWN_MUSHROOM.asItem())) || i.is(ItemTags.COW_FOOD), false)); // Purpur - Cows eat mushrooms
        this.goalSelector.addGoal(4, new FollowParentGoal(this, 1.25));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 1.0));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    @Override
    public boolean isFood(final ItemStack itemStack) {
        return itemStack.is(ItemTags.COW_FOOD);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createAnimalAttributes().add(Attributes.MAX_HEALTH, 10.0).add(Attributes.MOVEMENT_SPEED, 0.2F);
    }

    @Override
    public SoundEvent getAmbientSound() {
        return this.getSoundSet().ambientSound().value();
    }

    @Override
    public SoundEvent getHurtSound(final DamageSource source) {
        return this.getSoundSet().hurtSound().value();
    }

    @Override
    public SoundEvent getDeathSound() {
        return this.getSoundSet().deathSound().value();
    }

    @Override
    protected void playStepSound(final BlockPos pos, final BlockState blockState) {
        this.playSound(this.getSoundSet().stepSound().value(), 0.15F, 1.0F);
    }

    @Override
    public float getSoundVolume() {
        return 0.4F;
    }

    protected CowSoundVariant getSoundSet() {
        return SoundEvents.COW_SOUNDS.get(CowSoundVariants.SoundSet.CLASSIC);
    }

    @Override
    public InteractionResult mobInteract(final Player player, final InteractionHand hand) {
        if (getRider() != null) return InteractionResult.PASS; // Purpur - Ridables
        ItemStack itemStack = player.getItemInHand(hand);
        if (itemStack.is(Items.BUCKET) && !this.isBaby()) {
            // CraftBukkit start - Got milk?
            org.bukkit.event.player.PlayerBucketFillEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerBucketFillEvent(player.level(), player, this.blockPosition(), this.blockPosition(), null, itemStack, Items.MILK_BUCKET, hand);
            if (event.isCancelled()) {
                player.containerMenu.sendAllDataToRemote(); // Paper - Fix inventory desync
                return tryRide(player, hand); // Purpur - Ridables
            }
            // CraftBukkit end
            player.playSound(SoundEvents.COW_MILK, 1.0F, 1.0F);
            ItemStack bucketOrMilkBucket = ItemUtils.createFilledResult(itemStack, player, org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(event.getItemStack())); // CraftBukkit
            player.setItemInHand(hand, bucketOrMilkBucket);
            return InteractionResult.SUCCESS;
        // Purpur start - Cows eat mushrooms - feed mushroom to change to mooshroom
        } else if (level().purpurConfig.cowFeedMushrooms > 0 && this.getType() != EntityType.MOOSHROOM && isMushroom(itemStack)) {
            return this.feedMushroom(player, itemStack);
        // Purpur end - Cows eat mushrooms
        } else {
            return super.mobInteract(player, hand);
        }
    }

    @Override
    public EntityDimensions getDefaultDimensions(final Pose pose) {
        return this.isBaby() ? BABY_DIMENSIONS : super.getDefaultDimensions(pose);
    }

    // Purpur start - Cows eat mushrooms - feed mushroom to change to mooshroom
    private int redMushroomsFed = 0;
    private int brownMushroomsFed = 0;

    private boolean isMushroom(ItemStack stack) {
        return stack.getItem() == net.minecraft.world.level.block.Blocks.RED_MUSHROOM.asItem() || stack.getItem() == net.minecraft.world.level.block.Blocks.BROWN_MUSHROOM.asItem();
    }

    private int incrementFeedCount(ItemStack stack) {
        if (stack.getItem() == net.minecraft.world.level.block.Blocks.RED_MUSHROOM.asItem()) {
            return ++redMushroomsFed;
        } else {
            return ++brownMushroomsFed;
        }
    }

    private InteractionResult feedMushroom(Player player, ItemStack stack) {
        level().broadcastEntityEvent(this, (byte) 18); // hearts
        playSound(SoundEvents.COW_MILK, 1.0F, 1.0F);
        if (incrementFeedCount(stack) < level().purpurConfig.cowFeedMushrooms) {
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            return InteractionResult.CONSUME; // require 5 mushrooms to transform (prevents mushroom duping)
        }
        MushroomCow mooshroom = EntityType.MOOSHROOM.create(level(), net.minecraft.world.entity.EntitySpawnReason.CONVERSION);
        if (mooshroom == null) {
            return InteractionResult.PASS;
        }
        if (stack.getItem() == net.minecraft.world.level.block.Blocks.BROWN_MUSHROOM.asItem()) {
            mooshroom.setVariant(MushroomCow.Variant.BROWN);
        } else {
            mooshroom.setVariant(MushroomCow.Variant.RED);
        }
        mooshroom.snapTo(this.getX(), this.getY(), this.getZ(), this.getYRot(), this.getXRot());
        mooshroom.setHealth(this.getHealth());
        mooshroom.setAge(getAge());
        mooshroom.copyPosition(this);
        mooshroom.setYBodyRot(this.yBodyRot);
        mooshroom.setYHeadRot(this.getYHeadRot());
        mooshroom.yRotO = this.yRotO;
        mooshroom.xRotO = this.xRotO;
        if (this.hasCustomName()) {
            mooshroom.setCustomName(this.getCustomName());
        }
        if (org.bukkit.craftbukkit.event.CraftEventFactory.callEntityTransformEvent(this, mooshroom, org.bukkit.event.entity.EntityTransformEvent.TransformReason.INFECTION).isCancelled()) {
            return InteractionResult.PASS;
        }
        this.level().addFreshEntity(mooshroom);
        this.remove(RemovalReason.DISCARDED, org.bukkit.event.entity.EntityRemoveEvent.Cause.DISCARD);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        for (int i = 0; i < 15; ++i) {
            ((net.minecraft.server.level.ServerLevel) level()).sendParticlesSource(((net.minecraft.server.level.ServerLevel) level()).players(), null, net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
                    false, true,
                    getX() + random.nextFloat(), getY() + (random.nextFloat() * 2), getZ() + random.nextFloat(), 1,
                    random.nextGaussian() * 0.05D, random.nextGaussian() * 0.05D, random.nextGaussian() * 0.05D, 0);
        }
        return InteractionResult.SUCCESS;
    }
    // Purpur end - Cows eat mushrooms
}
