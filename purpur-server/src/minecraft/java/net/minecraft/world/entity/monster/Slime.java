package net.minecraft.world.entity.monster;

import com.google.common.annotations.VisibleForTesting;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.ConversionType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import org.jspecify.annotations.Nullable;

public class Slime extends Mob implements Enemy {
    private static final EntityDataAccessor<Integer> ID_SIZE = SynchedEntityData.defineId(Slime.class, EntityDataSerializers.INT);
    public static final int MIN_SIZE = 1;
    public static final int MAX_SIZE = 127;
    public static final int MAX_NATURAL_SIZE = 4;
    private static final boolean DEFAULT_WAS_ON_GROUND = false;
    public float targetSquish;
    public float squish;
    public float oSquish;
    private boolean wasOnGround = false;
    private boolean canWander = true; // Paper - Slime pathfinder events
    protected boolean actualJump; // Purpur - Ridables

    public Slime(final EntityType<? extends Slime> type, final Level level) {
        super(type, level);
        this.fixupDimensions();
        this.moveControl = new Slime.SlimeMoveControl(this);
    }

    // Purpur start - Ridables
    @Override
    public boolean isRidable() {
        return level().purpurConfig.slimeRidable;
    }

    @Override
    public boolean dismountsUnderwater() {
        return level().purpurConfig.useDismountsUnderwaterTag ? super.dismountsUnderwater() : !level().purpurConfig.slimeRidableInWater;
    }

    @Override
    public boolean isControllable() {
        return level().purpurConfig.slimeControllable;
    }

    @Override
    public float getJumpPower() {
        float height = super.getJumpPower();
        return getRider() != null && this.isControllable() && actualJump ? height * 1.5F : height;
    }

    @Override
    public boolean onSpacebar() {
        if (onGround && getRider() != null && this.isControllable()) {
            actualJump = true;
            if (getRider().getForwardMot() == 0 || getRider().getStrafeMot() == 0) {
                jumpFromGround(); // jump() here if not moving
            }
        }
        return true; // do not jump() in wasd controller, let vanilla controller handle
    }
    // Purpur end - Ridables

    // Purpur start - Configurable entity base attributes
    protected String getMaxHealthEquation() {
        return level().purpurConfig.slimeMaxHealth;
    }

    protected String getAttackDamageEquation() {
        return level().purpurConfig.slimeAttackDamage;
    }

    protected java.util.Map<Integer, Double> getMaxHealthCache() {
        return level().purpurConfig.slimeMaxHealthCache;
    }

    protected java.util.Map<Integer, Double> getAttackDamageCache() {
        return level().purpurConfig.slimeAttackDamageCache;
    }

    protected double getFromCache(java.util.function.Supplier<String> equation, java.util.function.Supplier<java.util.Map<Integer, Double>> cache, java.util.function.Supplier<Double> defaultValue) {
        int size = getSize();
        Double value = cache.get().get(size);
        if (value == null) {
            try {
                value = ((Number) scriptEngine.eval("let size = " + size + "; " + equation.get())).doubleValue();
            } catch (javax.script.ScriptException e) {
                e.printStackTrace();
                value = defaultValue.get();
            }
            cache.get().put(size, value);
        }
        return value;
    }
    // Purpur end - Configurable entity base attributes

    // Purpur start - Toggle for water sensitive mob damage
    @Override
    public boolean isSensitiveToWater() {
        return this.level().purpurConfig.slimeTakeDamageFromWater;
    }
    // Purpur end - Toggle for water sensitive mob damage

    // Purpur start - Mobs always drop experience
    @Override
    protected boolean isAlwaysExperienceDropper() {
        return this.level().purpurConfig.slimeAlwaysDropExp;
    }
    // Purpur end - Mobs always drop experience

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new org.purpurmc.purpur.entity.ai.HasRider(this)); // Purpur - Ridables
        this.goalSelector.addGoal(1, new Slime.SlimeFloatGoal(this));
        this.goalSelector.addGoal(2, new Slime.SlimeAttackGoal(this));
        this.goalSelector.addGoal(3, new Slime.SlimeRandomDirectionGoal(this));
        this.goalSelector.addGoal(5, new Slime.SlimeKeepOnJumpingGoal(this));
        this.targetSelector.addGoal(0, new org.purpurmc.purpur.entity.ai.HasRider(this)); // Purpur - Ridables
        this.targetSelector
            .addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, (target, level) -> Math.abs(target.getY() - this.getY()) <= 4.0));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, IronGolem.class, true));
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.HOSTILE;
    }

    @Override
    protected void defineSynchedData(final SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(ID_SIZE, 1);
    }

    @VisibleForTesting
    public void setSize(final int size, final boolean updateHealth) {
        int actualSize = Mth.clamp(size, 1, 127);
        this.entityData.set(ID_SIZE, actualSize);
        this.reapplyPosition();
        this.refreshDimensions();
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(getFromCache(this::getMaxHealthEquation, this::getMaxHealthCache, () -> (double) (size * size))); // Purpur - Configurable entity base attributes
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.2F + 0.1F * actualSize);
        this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(getFromCache(this::getAttackDamageEquation, this::getAttackDamageCache, () -> (double) actualSize)); // Purpur - Configurable entity base attributes
        if (updateHealth) {
            this.setHealth(this.getMaxHealth());
        }

        this.xpReward = actualSize;
    }

    public int getSize() {
        return this.entityData.get(ID_SIZE);
    }

    @Override
    protected void addAdditionalSaveData(final ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("Size", this.getSize() - 1);
        output.putBoolean("wasOnGround", this.wasOnGround);
        output.putBoolean("Paper.canWander", this.canWander); // Paper
    }

    @Override
    protected void readAdditionalSaveData(final ValueInput input) {
        this.setSize(input.getIntOr("Size", 0) + 1, false);
        super.readAdditionalSaveData(input);
        this.wasOnGround = input.getBooleanOr("wasOnGround", false);
        this.canWander = input.getBooleanOr("Paper.canWander", true); // Paper
    }

    public boolean isTiny() {
        return this.getSize() <= 1;
    }

    protected ParticleOptions getParticleType() {
        return ParticleTypes.ITEM_SLIME;
    }

    @Override
    public void tick() {
        this.oSquish = this.squish;
        this.squish = this.squish + (this.targetSquish - this.squish) * 0.5F;
        super.tick();
        if (this.onGround() && !this.wasOnGround) {
            float size = this.getDimensions(this.getPose()).width() * 2.0F;
            float radius = size / 2.0F;

            for (int i = 0; i < size * 16.0F; i++) {
                float dir = this.random.nextFloat() * (float) (Math.PI * 2);
                float d = this.random.nextFloat() * 0.5F + 0.5F;
                float xd = Mth.sin(dir) * radius * d;
                float zd = Mth.cos(dir) * radius * d;
                this.level().addParticle(this.getParticleType(), this.getX() + xd, this.getY(), this.getZ() + zd, 0.0, 0.0, 0.0);
            }

            this.playSound(this.getSquishSound(), this.getSoundVolume(), ((this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F) / 0.8F);
            this.targetSquish = -0.5F;
        } else if (!this.onGround() && this.wasOnGround) {
            this.targetSquish = 1.0F;
        }

        this.wasOnGround = this.onGround();
        this.decreaseSquish();
    }

    protected void decreaseSquish() {
        this.targetSquish *= 0.6F;
    }

    protected int getJumpDelay() {
        return this.random.nextInt(20) + 10;
    }

    @Override
    public void refreshDimensions() {
        double oldX = this.getX();
        double oldY = this.getY();
        double oldZ = this.getZ();
        super.refreshDimensions();
        this.setPos(oldX, oldY, oldZ);
    }

    @Override
    public void onSyncedDataUpdated(final EntityDataAccessor<?> accessor) {
        if (ID_SIZE.equals(accessor)) {
            this.refreshDimensions();
            this.setYRot(this.yHeadRot);
            this.yBodyRot = this.yHeadRot;
            if (this.isInWater() && this.random.nextInt(20) == 0) {
                this.doWaterSplashEffect();
            }
        }

        super.onSyncedDataUpdated(accessor);
    }

    @Override
    public EntityType<? extends Slime> getType() {
        return (EntityType<? extends Slime>)super.getType();
    }

    @Override
    public void remove(final Entity.RemovalReason reason, org.bukkit.event.entity.EntityRemoveEvent.@Nullable Cause eventCause) { // CraftBukkit - add Bukkit remove cause
        int size = this.getSize();
        if (!this.level().isClientSide() && size > 1 && this.isDeadOrDying()) {
            float width = this.getDimensions(this.getPose()).width();
            float xzSlimeSpawnOffset = width / 2.0F;
            int halfSize = size / 2;
            int count = 2 + this.random.nextInt(3);
            PlayerTeam team = this.getTeam();
            // CraftBukkit start
            org.bukkit.event.entity.SlimeSplitEvent event = new org.bukkit.event.entity.SlimeSplitEvent((org.bukkit.entity.Slime) this.getBukkitEntity(), count);
            if (event.callEvent() && event.getCount() > 0) {
                count = event.getCount();
            } else {
                super.remove(reason, eventCause); // CraftBukkit - add Bukkit remove cause
                return;
            }

            java.util.List<LivingEntity> slimes = new java.util.ArrayList<>(count);
            // CraftBukkit end

            for (int i = 0; i < count; i++) {
                float xd = (i % 2 - 0.5F) * xzSlimeSpawnOffset;
                float zd = (i / 2 - 0.5F) * xzSlimeSpawnOffset;
                Slime converted = this.convertTo(this.getType(), new ConversionParams(ConversionType.SPLIT_ON_DEATH, false, false, team), EntitySpawnReason.TRIGGERED, slime -> { // CraftBukkit
                    slime.setSize(halfSize, true);
                    slime.snapTo(this.getX() + xd, this.getY() + 0.5, this.getZ() + zd, this.random.nextFloat() * 360.0F, 0.0F);
                // CraftBukkit start
                }, null, null);
                if (converted != null) {
                    slimes.add(converted);
                }
                // CraftBukkit end
            }
            // CraftBukkit start
            if (!slimes.isEmpty() && org.bukkit.craftbukkit.event.CraftEventFactory.callEntityTransformEvent(this, slimes, org.bukkit.event.entity.EntityTransformEvent.TransformReason.SPLIT).isCancelled()) { // check for empty converted entities or cancel event
                super.remove(reason, eventCause); // add Bukkit remove cause
                return;
            }
            for (LivingEntity living : slimes) {
                this.level().addFreshEntity(living, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.SLIME_SPLIT);
            }
            // CraftBukkit end
        }

        super.remove(reason, eventCause); // CraftBukkit - add Bukkit remove cause
    }

    @Override
    public void push(final Entity entity) {
        super.push(entity);
        if (entity instanceof IronGolem && this.isDealsDamage()) {
            this.dealDamage((LivingEntity)entity);
        }
    }

    @Override
    public void playerTouch(final Player player) {
        if (this.isDealsDamage()) {
            this.dealDamage(player);
        }
    }

    protected void dealDamage(final LivingEntity target) {
        if (this.level() instanceof ServerLevel level && this.isAlive() && this.isWithinMeleeAttackRange(target) && this.hasLineOfSight(target)) {
            DamageSource damageSource = this.damageSources().mobAttack(this);
            if (target.hurtServer(level, damageSource, this.getAttackDamage())) {
                this.playSound(SoundEvents.SLIME_ATTACK, 1.0F, (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
                EnchantmentHelper.doPostAttackEffects(level, target, damageSource);
            }
        }
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(final Entity passenger, final EntityDimensions dimensions, final float scale) {
        return new Vec3(0.0, dimensions.height() - 0.015625 * this.getSize() * scale, 0.0);
    }

    protected boolean isDealsDamage() {
        return !this.isTiny() && this.isEffectiveAi();
    }

    protected float getAttackDamage() {
        return (float)this.getAttributeValue(Attributes.ATTACK_DAMAGE);
    }

    @Override
    public SoundEvent getHurtSound(final DamageSource source) {
        return this.isTiny() ? SoundEvents.SLIME_HURT_SMALL : SoundEvents.SLIME_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return this.isTiny() ? SoundEvents.SLIME_DEATH_SMALL : SoundEvents.SLIME_DEATH;
    }

    protected SoundEvent getSquishSound() {
        return this.isTiny() ? SoundEvents.SLIME_SQUISH_SMALL : SoundEvents.SLIME_SQUISH;
    }

    public static boolean checkSlimeSpawnRules(
        final EntityType<Slime> type, final LevelAccessor level, final EntitySpawnReason spawnReason, final BlockPos pos, final RandomSource random
    ) {
        // Purpur start - Config to disable hostile mob spawn on ice
        if (net.minecraft.world.entity.monster.Monster.canSpawnInBlueAndPackedIce(level, pos)) {
            return false;
        }
        // Purpur end - Config to disable hostile mob spawn on ice
        if (level.getDifficulty() != Difficulty.PEACEFUL) {
            if (EntitySpawnReason.isSpawner(spawnReason)) {
                return checkMobSpawnRules(type, level, spawnReason, pos, random);
            }

            // Paper start - Replace rules for Height in Swamp Biomes
            final double maxHeightSwamp = level.getMinecraftWorld().paperConfig().entities.spawning.slimeSpawnHeight.surfaceBiome.maximum;
            final double minHeightSwamp = level.getMinecraftWorld().paperConfig().entities.spawning.slimeSpawnHeight.surfaceBiome.minimum;
            // Paper end - Replace rules for Height in Swamp Biomes
            if (level.getBiome(pos).is(BiomeTags.ALLOWS_SURFACE_SLIME_SPAWNS) && pos.getY() > minHeightSwamp && pos.getY() < maxHeightSwamp) { // Paper - Replace rules for Height in Swamp Biomes
                float surfaceSlimeSpawnChance = level.environmentAttributes().getValue(EnvironmentAttributes.SURFACE_SLIME_SPAWN_CHANCE, pos);
                if (random.nextFloat() < surfaceSlimeSpawnChance && level.getMaxLocalRawBrightness(pos) <= random.nextInt(8)) {
                    return checkMobSpawnRules(type, level, spawnReason, pos, random);
                }
            }

            if (!(level instanceof WorldGenLevel)) {
                return false;
            }

            ChunkPos chunkPos = ChunkPos.containing(pos);
            boolean slimeChunk = level.getMinecraftWorld().paperConfig().entities.spawning.allChunksAreSlimeChunks || WorldgenRandom.seedSlimeChunk(chunkPos.x(), chunkPos.z(), ((WorldGenLevel) level).getSeed(), level.getMinecraftWorld().spigotConfig.slimeSeed).nextInt(10) == 0; // Paper
                // Paper start - Replace rules for Height in Slime Chunks
                final double maxHeightSlimeChunk = level.getMinecraftWorld().paperConfig().entities.spawning.slimeSpawnHeight.slimeChunk.maximum;
                if (random.nextInt(10) == 0 && slimeChunk && pos.getY() < maxHeightSlimeChunk) {
                // Paper end - Replace rules for Height in Slime Chunks
                return checkMobSpawnRules(type, level, spawnReason, pos, random);
            }
        }

        return false;
    }

    @Override
    public float getSoundVolume() {
        return 0.4F * this.getSize();
    }

    @Override
    public int getMaxHeadXRot() {
        return 0;
    }

    protected boolean doPlayJumpSound() {
        return this.getSize() > 0;
    }

    @Override
    public void jumpFromGround() {
        Vec3 movement = this.getDeltaMovement();
        this.setDeltaMovement(movement.x, this.getJumpPower(), movement.z);
        this.needsSync = true;
        this.actualJump = false; // Purpur - Ridables
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(
        final ServerLevelAccessor level, final DifficultyInstance difficulty, final EntitySpawnReason spawnReason, final @Nullable SpawnGroupData groupData
    ) {
        RandomSource random = level.getRandom();
        int sizeScale = random.nextInt(3);
        if (sizeScale < 2 && random.nextFloat() < 0.5F * difficulty.getSpecialMultiplier()) {
            sizeScale++;
        }

        int size = 1 << sizeScale;
        this.setSize(size, true);
        return super.finalizeSpawn(level, difficulty, spawnReason, groupData);
    }

    private float getSoundPitch() {
        float pitchAdjuster = this.isTiny() ? 1.4F : 0.8F;
        return ((this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F) * pitchAdjuster;
    }

    protected SoundEvent getJumpSound() {
        return this.isTiny() ? SoundEvents.SLIME_JUMP_SMALL : SoundEvents.SLIME_JUMP;
    }

    @Override
    public EntityDimensions getDefaultDimensions(final Pose pose) {
        return super.getDefaultDimensions(pose).scale(this.getSize());
    }

    // Paper start - Slime pathfinder events
    public boolean canWander() {
        return this.canWander;
    }

    public void setWander(boolean canWander) {
        this.canWander = canWander;
    }
    // Paper end - Slime pathfinder events

    private static class SlimeAttackGoal extends Goal {
        private final Slime slime;
        private int growTiredTimer;

        public SlimeAttackGoal(final Slime slime) {
            this.slime = slime;
            this.setFlags(EnumSet.of(Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = this.slime.getTarget();

            // Paper start - Slime pathfinder events
            if (target == null || !target.isAlive()) {
                return false;
            }
            if (!this.slime.canAttack(target)) {
                return false;
            }
            return this.slime.getMoveControl() instanceof Slime.SlimeMoveControl && this.slime.canWander && new com.destroystokyo.paper.event.entity.SlimeTargetLivingEntityEvent((org.bukkit.entity.Slime) this.slime.getBukkitEntity(), (org.bukkit.entity.LivingEntity) target.getBukkitEntity()).callEvent();
            // Paper end - Slime pathfinder events
        }

        @Override
        public void start() {
            this.growTiredTimer = reducedTickDelay(300);
            super.start();
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity target = this.slime.getTarget();

            // Paper start - Slime pathfinder events
            if (target == null || !target.isAlive()) {
                return false;
            }
            if (!this.slime.canAttack(target)) {
                return false;
            }
            return --this.growTiredTimer > 0 && this.slime.canWander && new com.destroystokyo.paper.event.entity.SlimeTargetLivingEntityEvent((org.bukkit.entity.Slime) this.slime.getBukkitEntity(), (org.bukkit.entity.LivingEntity) target.getBukkitEntity()).callEvent();
            // Paper end - Slime pathfinder events
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = this.slime.getTarget();
            if (target != null) {
                this.slime.lookAt(target, 10.0F, 10.0F);
            }

            if (this.slime.getMoveControl() instanceof Slime.SlimeMoveControl slimeMoveControl) {
                slimeMoveControl.setDirection(this.slime.getYRot(), this.slime.isDealsDamage());
            }
        }

        // Paper start - Slime pathfinder events; clear timer and target when goal resets
        public void stop() {
            this.growTiredTimer = 0;
            this.slime.setTarget(null);
        }
        // Paper end - Slime pathfinder events
    }

    private static class SlimeFloatGoal extends Goal {
        private final Slime slime;

        public SlimeFloatGoal(final Slime mob) {
            this.slime = mob;
            this.setFlags(EnumSet.of(Goal.Flag.JUMP, Goal.Flag.MOVE));
            mob.getNavigation().setCanFloat(true);
        }

        @Override
        public boolean canUse() {
            return (this.slime.isInWater() || this.slime.isInLava()) && this.slime.getMoveControl() instanceof Slime.SlimeMoveControl && this.slime.canWander && new com.destroystokyo.paper.event.entity.SlimeSwimEvent((org.bukkit.entity.Slime) this.slime.getBukkitEntity()).callEvent(); // Paper - Slime pathfinder events
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (this.slime.getRandom().nextFloat() < 0.8F) {
                this.slime.getJumpControl().jump();
            }

            if (this.slime.getMoveControl() instanceof Slime.SlimeMoveControl slimeMoveControl) {
                slimeMoveControl.setWantedMovement(1.2);
            }
        }
    }

    private static class SlimeKeepOnJumpingGoal extends Goal {
        private final Slime slime;

        public SlimeKeepOnJumpingGoal(final Slime mob) {
            this.slime = mob;
            this.setFlags(EnumSet.of(Goal.Flag.JUMP, Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return !this.slime.isPassenger() && this.slime.canWander && new com.destroystokyo.paper.event.entity.SlimeWanderEvent((org.bukkit.entity.Slime) this.slime.getBukkitEntity()).callEvent(); // Paper - Slime pathfinder events
        }

        @Override
        public void tick() {
            if (this.slime.getMoveControl() instanceof Slime.SlimeMoveControl slimeMoveControl) {
                slimeMoveControl.setWantedMovement(1.0);
            }
        }
    }

    private static class SlimeMoveControl extends org.purpurmc.purpur.controller.MoveControllerWASD { // Purpur - Ridables
        private float yRot;
        private int jumpDelay;
        private final Slime slime;
        private boolean isAggressive;

        public SlimeMoveControl(final Slime slime) {
            super(slime);
            this.slime = slime;
            this.yRot = 180.0F * slime.getYRot() / Mth.PI;
        }

        public void setDirection(final float yRot, final boolean isAggressive) {
            this.yRot = yRot;
            this.isAggressive = isAggressive;
        }

        public void setWantedMovement(final double speedModifier) {
            this.setSpeedModifier(speedModifier); // Purpur - Ridables
            this.operation = MoveControl.Operation.MOVE_TO;
        }

        @Override
        public void tick() {
            // Purpur start - Ridables
            if (slime.getRider() != null && slime.isControllable()) {
                purpurTick(slime.getRider());
                if (slime.getForwardMot() != 0 || slime.getStrafeMot() != 0) {
                    if (jumpDelay > 10) {
                        jumpDelay = 6;
                    }
                } else {
                    jumpDelay = 20;
                }
            } else {
            // Purpur end - Ridables
            this.mob.setYRot(this.rotlerp(this.mob.getYRot(), this.yRot, 90.0F));
            this.mob.yHeadRot = this.mob.getYRot();
            this.mob.yBodyRot = this.mob.getYRot();
            } if ((slime.getRider() == null || !slime.isControllable()) && this.operation != MoveControl.Operation.MOVE_TO) { // Purpur - Ridables
                this.mob.setZza(0.0F);
            } else {
                this.operation = MoveControl.Operation.WAIT;
                if (this.mob.onGround()) {
                    this.mob.setSpeed((float)(this.getSpeedModifier() * this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED) * (slime.getRider() != null && slime.isControllable() && (slime.getRider().getForwardMot() != 0 || slime.getRider().getStrafeMot() != 0) ? 2.0D : 1.0D))); // Purpur - Ridables
                    if (this.jumpDelay-- <= 0) {
                        this.jumpDelay = this.slime.getJumpDelay();
                        if (this.isAggressive) {
                            this.jumpDelay /= 3;
                        }

                        this.slime.getJumpControl().jump();
                        if (this.slime.doPlayJumpSound()) {
                            this.slime.playSound(this.slime.getJumpSound(), this.slime.getSoundVolume(), this.slime.getSoundPitch());
                        }
                    } else {
                        this.slime.xxa = 0.0F;
                        this.slime.zza = 0.0F;
                        this.mob.setSpeed(0.0F);
                    }
                } else {
                    this.mob.setSpeed((float)(this.getSpeedModifier() * this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED) * (slime.getRider() != null && slime.isControllable() && (slime.getRider().getForwardMot() != 0 || slime.getRider().getStrafeMot() != 0) ? 2.0D : 1.0D))); // Purpur - Ridables
                }
            }
        }
    }

    private static class SlimeRandomDirectionGoal extends Goal {
        private final Slime slime;
        private float chosenDegrees;
        private int nextRandomizeTime;

        public SlimeRandomDirectionGoal(final Slime slime) {
            this.slime = slime;
            this.setFlags(EnumSet.of(Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return this.slime.getTarget() == null && this.slime.canWander // Paper - Slime pathfinder events
                && (this.slime.onGround() || this.slime.isInWater() || this.slime.isInLava() || this.slime.hasEffect(MobEffects.LEVITATION))
                && this.slime.getMoveControl() instanceof Slime.SlimeMoveControl;
        }

        @Override
        public void tick() {
            if (--this.nextRandomizeTime <= 0) {
                this.nextRandomizeTime = this.adjustedTickDelay(40 + this.slime.getRandom().nextInt(60));
                this.chosenDegrees = this.slime.getRandom().nextInt(360);
                // Paper start - Slime pathfinder events
                com.destroystokyo.paper.event.entity.SlimeChangeDirectionEvent event = new com.destroystokyo.paper.event.entity.SlimeChangeDirectionEvent((org.bukkit.entity.Slime) this.slime.getBukkitEntity(), this.chosenDegrees);
                if (!this.slime.canWander || !event.callEvent()) return;
                this.chosenDegrees = event.getNewYaw();
                // Paper end - Slime pathfinder events
            }

            if (this.slime.getMoveControl() instanceof Slime.SlimeMoveControl slimeMoveControl) {
                slimeMoveControl.setDirection(this.chosenDegrees, false);
            }
        }
    }
}
