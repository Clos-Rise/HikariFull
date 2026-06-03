package net.minecraft.world.entity.ambient;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class Bat extends AmbientCreature {
    public static final float FLAP_LENGTH_SECONDS = 0.5F;
    public static final float TICKS_PER_FLAP = 10.0F;
    private static final EntityDataAccessor<Byte> DATA_ID_FLAGS = SynchedEntityData.defineId(Bat.class, EntityDataSerializers.BYTE);
    private static final int FLAG_RESTING = 1;
    private static final TargetingConditions BAT_RESTING_TARGETING = TargetingConditions.forNonCombat().range(4.0);
    private static final byte DEFAULT_FLAGS = 0;
    public final AnimationState flyAnimationState = new AnimationState();
    public final AnimationState restAnimationState = new AnimationState();
    public @Nullable BlockPos targetPosition;

    public Bat(final EntityType<? extends Bat> type, final Level level) {
        super(type, level);
        this.moveControl = new org.purpurmc.purpur.controller.FlyingWithSpacebarMoveControllerWASD(this, 0.075F); // Purpur - Ridables
        if (!level.isClientSide()) {
            this.setResting(true);
        }
    }

    // Purpur start - Ridables
    @Override
    public boolean shouldSendAttribute(net.minecraft.world.entity.ai.attributes.Attribute attribute) { return attribute != Attributes.FLYING_SPEED.value(); } // Fixes log spam on clients

    @Override
    public boolean isRidable() {
        return level().purpurConfig.batRidable;
    }

    @Override
    public boolean dismountsUnderwater() {
        return level().purpurConfig.useDismountsUnderwaterTag ? super.dismountsUnderwater() : !level().purpurConfig.batRidableInWater;
    }

    @Override
    public boolean isControllable() {
        return level().purpurConfig.batControllable;
    }

    @Override
    public double getMaxY() {
        return level().purpurConfig.batMaxY;
    }

    @Override
    public void onMount(net.minecraft.world.entity.player.Player rider) {
        super.onMount(rider);
        if (isResting()) {
            setResting(false);
            level().levelEvent(null, 1025, new BlockPos(this).above(), 0);
        }
    }

    @Override
    public void travel(Vec3 vec3) {
        super.travel(vec3);
        if (getRider() != null && this.isControllable() && !onGround) {
            float speed = (float) getAttributeValue(Attributes.FLYING_SPEED) * 2;
            setSpeed(speed);
            Vec3 mot = getDeltaMovement();
            move(net.minecraft.world.entity.MoverType.SELF, mot.multiply(speed, 0.25, speed));
            setDeltaMovement(mot.scale(0.9D));
        }
    }
    // Purpur end - Ridables

    // Purpur start - Configurable entity base attributes
    @Override
    public void initAttributes() {
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(this.level().purpurConfig.batMaxHealth);
        this.getAttribute(Attributes.SCALE).setBaseValue(this.level().purpurConfig.batScale);
        this.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(this.level().purpurConfig.batFollowRange);
        this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(this.level().purpurConfig.batKnockbackResistance);
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(this.level().purpurConfig.batMovementSpeed);
        this.getAttribute(Attributes.FLYING_SPEED).setBaseValue(this.level().purpurConfig.batFlyingSpeed);
        this.getAttribute(Attributes.ARMOR).setBaseValue(this.level().purpurConfig.batArmor);
        this.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(this.level().purpurConfig.batArmorToughness);
        this.getAttribute(Attributes.ATTACK_KNOCKBACK).setBaseValue(this.level().purpurConfig.batAttackKnockback);
    }
    // Purpur end - Configurable entity base attributes

    // Purpur start - Toggle for water sensitive mob damage
    @Override
    public boolean isSensitiveToWater() {
        return this.level().purpurConfig.batTakeDamageFromWater;
    }
    // Purpur end - Toggle for water sensitive mob damage

    // Purpur start - Mobs always drop experience
    @Override
    protected boolean isAlwaysExperienceDropper() {
        return this.level().purpurConfig.batAlwaysDropExp;
    }
    // Purpur end - Mobs always drop experience

    @Override
    public boolean isFlapping() {
        return !this.isResting() && this.tickCount % 10.0F == 0.0F;
    }

    @Override
    protected void defineSynchedData(final SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(DATA_ID_FLAGS, (byte)0);
    }

    @Override
    public float getSoundVolume() {
        return 0.1F;
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 0.95F;
    }

    @Override
    public @Nullable SoundEvent getAmbientSound() {
        return this.isResting() && this.random.nextInt(4) != 0 ? null : SoundEvents.BAT_AMBIENT;
    }

    @Override
    public SoundEvent getHurtSound(final DamageSource source) {
        return SoundEvents.BAT_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.BAT_DEATH;
    }

    @Override
    public boolean isCollidable(boolean ignoreClimbing) { // Paper - Climbing should not bypass cramming gamerule
        return false;
    }

    @Override
    protected void doPush(final Entity entity) {
    }

    @Override
    protected void pushEntities() {
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 6.0).add(Attributes.FLYING_SPEED, 0.6D); // Purpur - Ridables
    }

    public boolean isResting() {
        return (this.entityData.get(DATA_ID_FLAGS) & 1) != 0;
    }

    public void setResting(final boolean value) {
        byte current = this.entityData.get(DATA_ID_FLAGS);
        if (value) {
            this.entityData.set(DATA_ID_FLAGS, (byte)(current | 1));
        } else {
            this.entityData.set(DATA_ID_FLAGS, (byte)(current & -2));
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.isResting()) {
            this.setDeltaMovement(Vec3.ZERO);
            this.setPosRaw(this.getX(), Mth.floor(this.getY()) + 1.0 - this.getBbHeight(), this.getZ());
        } else {
            this.setDeltaMovement(this.getDeltaMovement().multiply(1.0, 0.6, 1.0));
        }

        this.setupAnimationStates();
    }

    @Override
    protected void customServerAiStep(final ServerLevel level) {
        // Purpur start - Ridables
        if (getRider() != null && this.isControllable()) {
            Vec3 mot = getDeltaMovement();
            setDeltaMovement(mot.x(), mot.y() + (getVerticalMot() > 0 ? 0.07D : 0.0D), mot.z());
            return;
        }
        // Purpur end - Ridables
        super.customServerAiStep(level);
        BlockPos pos = this.blockPosition();
        BlockPos above = pos.above();
        if (this.isResting()) {
            boolean isSilent = this.isSilent();
            if (level.getBlockState(above).isRedstoneConductor(level, pos)) {
                if (this.random.nextInt(200) == 0) {
                    this.yHeadRot = this.random.nextInt(360);
                }

                if (level.getNearestPlayer(BAT_RESTING_TARGETING, this) != null && org.bukkit.craftbukkit.event.CraftEventFactory.handleBatToggleSleepEvent(this, true)) { // CraftBukkit - Call BatToggleSleepEvent
                    this.setResting(false);
                    if (!isSilent) {
                        level.levelEvent(null, LevelEvent.SOUND_BAT_LIFTOFF, pos, 0);
                    }
                }
            } else if (org.bukkit.craftbukkit.event.CraftEventFactory.handleBatToggleSleepEvent(this, true)) { // CraftBukkit - Call BatToggleSleepEvent
                this.setResting(false);
                if (!isSilent) {
                    level.levelEvent(null, LevelEvent.SOUND_BAT_LIFTOFF, pos, 0);
                }
            }
        } else {
            if (this.targetPosition != null && (!level.isEmptyBlock(this.targetPosition) || this.targetPosition.getY() <= level.getMinY())) {
                this.targetPosition = null;
            }

            if (this.targetPosition == null || this.random.nextInt(30) == 0 || this.targetPosition.closerToCenterThan(this.position(), 2.0)) {
                this.targetPosition = BlockPos.containing(
                    this.getX() + this.random.nextInt(7) - this.random.nextInt(7),
                    this.getY() + this.random.nextInt(6) - 2.0,
                    this.getZ() + this.random.nextInt(7) - this.random.nextInt(7)
                );
            }

            double dx = this.targetPosition.getX() + 0.5 - this.getX();
            double dy = this.targetPosition.getY() + 0.1 - this.getY();
            double dz = this.targetPosition.getZ() + 0.5 - this.getZ();
            Vec3 movement = this.getDeltaMovement();
            Vec3 newMovement = movement.add(
                (Math.signum(dx) * 0.5 - movement.x) * 0.1F, (Math.signum(dy) * 0.7F - movement.y) * 0.1F, (Math.signum(dz) * 0.5 - movement.z) * 0.1F
            );
            this.setDeltaMovement(newMovement);
            float yRotD = (float)(Mth.atan2(newMovement.z, newMovement.x) * 180.0F / (float)Math.PI) - 90.0F;
            float rotDiff = Mth.wrapDegrees(yRotD - this.getYRot());
            this.zza = 0.5F;
            this.setYRot(this.getYRot() + rotDiff);
            if (this.random.nextInt(100) == 0 && level.getBlockState(above).isRedstoneConductor(level, above) && org.bukkit.craftbukkit.event.CraftEventFactory.handleBatToggleSleepEvent(this, false)) { // CraftBukkit - Call BatToggleSleepEvent
                this.setResting(true);
            }
        }
    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.EVENTS;
    }

    @Override
    protected void checkFallDamage(final double ya, final boolean onGround, final BlockState onState, final BlockPos pos) {
    }

    @Override
    public boolean isIgnoringBlockTriggers() {
        return true;
    }

    @Override
    public boolean hurtServer(final ServerLevel level, final DamageSource source, final float damage) {
        if (this.isInvulnerableTo(level, source)) {
            return false;
        }

        if (this.isResting() && org.bukkit.craftbukkit.event.CraftEventFactory.handleBatToggleSleepEvent(this, true)) { // CraftBukkit - Call BatToggleSleepEvent
            this.setResting(false);
        }

        return super.hurtServer(level, source, damage);
    }

    @Override
    protected void readAdditionalSaveData(final ValueInput input) {
        super.readAdditionalSaveData(input);
        this.entityData.set(DATA_ID_FLAGS, input.getByteOr("BatFlags", (byte)0));
    }

    @Override
    protected void addAdditionalSaveData(final ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putByte("BatFlags", this.entityData.get(DATA_ID_FLAGS));
    }

    public static boolean checkBatSpawnRules(
        final EntityType<Bat> type, final LevelAccessor level, final EntitySpawnReason spawnReason, final BlockPos pos, final RandomSource random
    ) {
        return pos.getY() < level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, pos).getY()
            && !random.nextBoolean()
            && level.getMaxLocalRawBrightness(pos) <= random.nextInt(4)
            && level.getBlockState(pos.below()).is(BlockTags.BATS_SPAWNABLE_ON)
            && checkMobSpawnRules(type, level, spawnReason, pos, random);
    }

    private void setupAnimationStates() {
        if (this.isResting()) {
            this.flyAnimationState.stop();
            this.restAnimationState.startIfStopped(this.tickCount);
        } else {
            this.restAnimationState.stop();
            this.flyAnimationState.startIfStopped(this.tickCount);
        }
    }
}
