package net.minecraft.world.entity.monster.skeleton;

import com.google.common.annotations.VisibleForTesting;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class Skeleton extends AbstractSkeleton {
    private static final int TOTAL_CONVERSION_TIME = 300;
    public static final EntityDataAccessor<Boolean> DATA_STRAY_CONVERSION_ID = SynchedEntityData.defineId(Skeleton.class, EntityDataSerializers.BOOLEAN);
    public static final String CONVERSION_TAG = "StrayConversionTime";
    private static final int NOT_CONVERTING = -1;
    public int inPowderSnowTime;
    public int conversionTime;

    public Skeleton(final EntityType<? extends Skeleton> type, final Level level) {
        super(type, level);
    }

    // Purpur start - Ridables
    @Override
    public boolean isRidable() {
        return level().purpurConfig.skeletonRidable;
    }

    @Override
    public boolean dismountsUnderwater() {
        return level().purpurConfig.useDismountsUnderwaterTag ? super.dismountsUnderwater() : !level().purpurConfig.skeletonRidableInWater;
    }

    @Override
    public boolean isControllable() {
        return level().purpurConfig.skeletonControllable;
    }
    // Purpur end - Ridables

    // Purpur start - Configurable entity base attributes
    @Override
    public void initAttributes() {
        this.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(this.level().purpurConfig.skeletonMaxHealth);
    }
    // Purpur end - Configurable entity base attributes

    // Purpur start - Toggle for water sensitive mob damage
    @Override
    public boolean isSensitiveToWater() {
        return this.level().purpurConfig.skeletonTakeDamageFromWater;
    }
    // Purpur end - Toggle for water sensitive mob damage

    // Purpur start - Mobs always drop experience
    @Override
    protected boolean isAlwaysExperienceDropper() {
        return this.level().purpurConfig.skeletonAlwaysDropExp;
    }
    // Purpur end - Mobs always drop experience

    // Purpur start - Check mobGriefing Overrides
    @Override
    protected Boolean checkEntityPickUpLootOverride() {
        return this.level().purpurConfig.skeletonCanPickUpLoot;
    }
    // Purpur end - Check mobGriefing Overrides

    @Override
    protected void defineSynchedData(final SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(DATA_STRAY_CONVERSION_ID, false);
    }

    public boolean isFreezeConverting() {
        return this.getEntityData().get(DATA_STRAY_CONVERSION_ID);
    }

    public void setFreezeConverting(final boolean isConverting) {
        this.entityData.set(DATA_STRAY_CONVERSION_ID, isConverting);
    }

    @Override
    public boolean isShaking() {
        return this.isFreezeConverting();
    }

    @Override
    public void tick() {
        if (!this.level().isClientSide() && this.isAlive() && !this.isNoAi()) {
            if (this.isInPowderSnow) {
                if (this.isFreezeConverting()) {
                    this.conversionTime--;
                    if (this.conversionTime < 0) {
                        this.doFreezeConversion();
                    }
                } else {
                    this.inPowderSnowTime++;
                    if (this.inPowderSnowTime >= 140) {
                        this.startFreezeConversion(300);
                    }
                }
            } else {
                this.inPowderSnowTime = -1;
                this.setFreezeConverting(false);
            }
        }

        super.tick();
    }

    @Override
    protected void addAdditionalSaveData(final ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("StrayConversionTime", this.isFreezeConverting() ? this.conversionTime : -1);
    }

    @Override
    protected void readAdditionalSaveData(final ValueInput input) {
        super.readAdditionalSaveData(input);
        int conversionTime = input.getIntOr("StrayConversionTime", -1);
        if (conversionTime != -1) {
            this.startFreezeConversion(conversionTime);
        } else {
            this.setFreezeConverting(false);
        }
    }

    @VisibleForTesting
    public void startFreezeConversion(final int time) {
        this.conversionTime = time;
        this.setFreezeConverting(true);
    }

    protected void doFreezeConversion() {
        final Stray entity = this.convertTo(EntityType.STRAY, ConversionParams.single(this, true, true), stray -> { // Paper - Fix issues with mob conversion; reset conversion time to prevent event spam
            if (!this.isSilent()) {
                this.level().levelEvent(null, LevelEvent.SOUND_SKELETON_TO_STRAY, this.blockPosition(), 0);
            }
        // Paper start - add spawn and transform reasons
        }, org.bukkit.event.entity.EntityTransformEvent.TransformReason.FROZEN, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.FROZEN);
        if (entity == null) {
            // Reset conversion time to prevent event spam
            this.conversionTime = 300;
        }
        // Paper end - add spawn and transform reasons
    }

    @Override
    public boolean canFreeze() {
        return false;
    }

    @Override
    public SoundEvent getAmbientSound() {
        return SoundEvents.SKELETON_AMBIENT;
    }

    @Override
    public SoundEvent getHurtSound(final DamageSource source) {
        return SoundEvents.SKELETON_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.SKELETON_DEATH;
    }

    @Override
    SoundEvent getStepSound() {
        return SoundEvents.SKELETON_STEP;
    }

    // Purpur start - Skeletons eat wither roses
    private int witherRosesFed = 0;

    @Override
    public net.minecraft.world.InteractionResult mobInteract(net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand) {
        net.minecraft.world.item.ItemStack stack = player.getItemInHand(hand);

        if (level().purpurConfig.skeletonFeedWitherRoses > 0 && this.getType() != EntityType.WITHER_SKELETON && stack.getItem() == net.minecraft.world.level.block.Blocks.WITHER_ROSE.asItem()) {
            return this.feedWitherRose(player, stack);
        }

        return super.mobInteract(player, hand);
    }

    private net.minecraft.world.InteractionResult feedWitherRose(net.minecraft.world.entity.player.Player player, net.minecraft.world.item.ItemStack stack) {
        if (++witherRosesFed < level().purpurConfig.skeletonFeedWitherRoses) {
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            return net.minecraft.world.InteractionResult.CONSUME;
        }

        WitherSkeleton skeleton = EntityType.WITHER_SKELETON.create(level(), net.minecraft.world.entity.EntitySpawnReason.CONVERSION);
        if (skeleton == null) {
            return net.minecraft.world.InteractionResult.PASS;
        }

        skeleton.snapTo(this.getX(), this.getY(), this.getZ(), this.getYRot(), this.getXRot());
        skeleton.setHealth(this.getHealth());
        skeleton.setAggressive(this.isAggressive());
        skeleton.copyPosition(this);
        skeleton.setYBodyRot(this.yBodyRot);
        skeleton.setYHeadRot(this.getYHeadRot());
        skeleton.yRotO = this.yRotO;
        skeleton.xRotO = this.xRotO;

        if (this.hasCustomName()) {
            skeleton.setCustomName(this.getCustomName());
        }

        if (org.bukkit.craftbukkit.event.CraftEventFactory.callEntityTransformEvent(this, skeleton, org.bukkit.event.entity.EntityTransformEvent.TransformReason.INFECTION).isCancelled()) {
            return net.minecraft.world.InteractionResult.PASS;
        }

        this.level().addFreshEntity(skeleton);
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
        return net.minecraft.world.InteractionResult.SUCCESS;
    }
    // Purpur end - Skeletons eat wither roses
}
