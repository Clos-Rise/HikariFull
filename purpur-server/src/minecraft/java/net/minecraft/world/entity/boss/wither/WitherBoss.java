package net.minecraft.world.entity.boss.wither;

import com.google.common.collect.ImmutableList;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class WitherBoss extends Monster implements RangedAttackMob {
    private static final EntityDataAccessor<Integer> DATA_TARGET_A = SynchedEntityData.defineId(WitherBoss.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_TARGET_B = SynchedEntityData.defineId(WitherBoss.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_TARGET_C = SynchedEntityData.defineId(WitherBoss.class, EntityDataSerializers.INT);
    private static final List<EntityDataAccessor<Integer>> DATA_TARGETS = ImmutableList.of(DATA_TARGET_A, DATA_TARGET_B, DATA_TARGET_C);
    private static final EntityDataAccessor<Integer> DATA_ID_INV = SynchedEntityData.defineId(WitherBoss.class, EntityDataSerializers.INT);
    private static final int INVULNERABLE_TICKS = 220;
    private static final int DEFAULT_INVULNERABLE_TICKS = 0;
    private final float[] xRotHeads = new float[2];
    private final float[] yRotHeads = new float[2];
    private final float[] xRotOHeads = new float[2];
    private final float[] yRotOHeads = new float[2];
    private final int[] nextHeadUpdate = new int[2];
    private final int[] idleHeadUpdates = new int[2];
    private int destroyBlocksTick;
    private int shootCooldown = 0; // Purpur - Ridables
    private boolean canPortal = false; // Paper
    public final ServerBossEvent bossEvent = Util.make(
        new ServerBossEvent(Mth.createInsecureUUID(this.random), this.getDisplayName(), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS),
        e -> e.setDarkenScreen(true)
    );
    private static final TargetingConditions.Selector LIVING_ENTITY_SELECTOR = (target, level) -> !target.is(EntityTypeTags.WITHER_FRIENDS)
        && target.attackable();
    private static final TargetingConditions TARGETING_CONDITIONS = TargetingConditions.forCombat().range(20.0).selector(LIVING_ENTITY_SELECTOR);
    private java.util.@Nullable UUID summoner; // Purpur - Summoner API
    private org.purpurmc.purpur.controller.FlyingWithSpacebarMoveControllerWASD purpurController; // Purpur - Ridables

    public WitherBoss(final EntityType<? extends WitherBoss> type, final Level level) {
        super(type, level);
        // Purpur start - Ridables
        this.purpurController = new org.purpurmc.purpur.controller.FlyingWithSpacebarMoveControllerWASD(this, 0.1F);
        this.moveControl = new FlyingMoveControl(this, 10, false) {
            @Override
            public void tick() {
                if (mob.getRider() != null && mob.isControllable()) {
                    purpurController.purpurTick(mob.getRider());
                } else {
                    super.tick();
                }
            }
        };
        // Purpur end - Ridables
        this.setHealth(this.getMaxHealth());
        this.xpReward = 50;
    }

    // Purpur start - Summoner API
    public java.util.@Nullable UUID getSummoner() {
        return summoner;
    }

    public void setSummoner(java.util.@Nullable UUID summoner) {
        this.summoner = summoner;
    }
    // Purpur end - Summoner API

    // Purpur start - Ridables
    @Override
    public boolean isRidable() {
        return level().purpurConfig.witherRidable;
    }

    @Override
    public boolean dismountsUnderwater() {
        return level().purpurConfig.useDismountsUnderwaterTag ? super.dismountsUnderwater() : !level().purpurConfig.witherRidableInWater;
    }

    @Override
    public boolean isControllable() {
        return level().purpurConfig.witherControllable;
    }

    @Override
    public double getMaxY() {
        return level().purpurConfig.witherMaxY;
    }

    @Override
    public void travel(Vec3 vec3) {
        super.travel(vec3);
        if (getRider() != null && this.isControllable() && !onGround) {
            float speed = (float) getAttributeValue(Attributes.FLYING_SPEED) * 5F;
            setSpeed(speed);
            Vec3 mot = getDeltaMovement();
            move(net.minecraft.world.entity.MoverType.SELF, mot.multiply(speed, 0.5, speed));
            setDeltaMovement(mot.scale(0.9D));
        }
    }

    @Override
    public void onMount(Player rider) {
        super.onMount(rider);
        this.entityData.set(DATA_TARGETS.get(0), 0);
        this.entityData.set(DATA_TARGETS.get(1), 0);
        this.entityData.set(DATA_TARGETS.get(2), 0);
        getNavigation().stop();
        shootCooldown = 20;
    }

    @Override
    public boolean onClick(net.minecraft.world.InteractionHand hand) {
        return shoot(getRider(), hand == net.minecraft.world.InteractionHand.MAIN_HAND ? new int[]{1} : new int[]{2});
    }

    public boolean shoot(@Nullable Player rider, int[] heads) {
        if (shootCooldown > 0) {
            return false;
        }

        shootCooldown = 20;
        if (rider == null) {
            return false;
        }

        org.bukkit.craftbukkit.entity.CraftHumanEntity player = rider.getBukkitEntity();
        if (!player.hasPermission("allow.special.wither")) {
            return false;
        }

        net.minecraft.world.phys.HitResult rayTrace = getRayTrace(120, net.minecraft.world.level.ClipContext.Fluid.NONE);
        if (rayTrace == null) {
            return false;
        }

        Vec3 loc;
        if (rayTrace.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            BlockPos pos = ((net.minecraft.world.phys.BlockHitResult) rayTrace).getBlockPos();
            loc = new Vec3(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
        } else if (rayTrace.getType() == net.minecraft.world.phys.HitResult.Type.ENTITY) {
            Entity target = ((net.minecraft.world.phys.EntityHitResult) rayTrace).getEntity();
            loc = new Vec3(target.getX(), target.getY() + (target.getEyeHeight() / 2), target.getZ());
        } else {
            org.bukkit.block.Block block = player.getTargetBlock(null, 120);
            loc = new Vec3(block.getX() + 0.5D, block.getY() + 0.5D, block.getZ() + 0.5D);
        }

        for (int head : heads) {
            shoot(head, loc.x(), loc.y(), loc.z(), rider);
        }

        return true; // handled
    }

    public void shoot(int head, double x, double y, double z, Player rider) {
        level().levelEvent(null, 1024, blockPosition(), 0);
        double headX = getHeadX(head);
        double headY = getHeadY(head);
        double headZ = getHeadZ(head);
        Vec3 vec3d = new Vec3(x - headX, y - headY, z - headZ);
        WitherSkull skull = new WitherSkull(level(), this, vec3d.normalize());
        skull.setPosRaw(headX, headY, headZ);
        level().addFreshEntity(skull);
    }
    // Purpur end - Ridables

    // Purpur start - Configurable entity base attributes
    @Override
    public void initAttributes() {
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(this.level().purpurConfig.witherMaxHealth);
        this.getAttribute(Attributes.SCALE).setBaseValue(this.level().purpurConfig.witherScale);
    }
    // Purpur end - Configurable entity base attributes

    // Purpur start - Toggle for water sensitive mob damage
    @Override
    public boolean isSensitiveToWater() {
        return this.level().purpurConfig.witherTakeDamageFromWater;
    }
    // Purpur end - Toggle for water sensitive mob damage

    // Purpur start - Mobs always drop experience
    @Override
    protected boolean isAlwaysExperienceDropper() {
        return this.level().purpurConfig.witherAlwaysDropExp;
    }
    // Purpur end - Mobs always drop experience

    @Override
    protected PathNavigation createNavigation(final Level level) {
        FlyingPathNavigation flyingPathNavigation = new FlyingPathNavigation(this, level);
        flyingPathNavigation.setCanOpenDoors(false);
        flyingPathNavigation.setCanFloat(true);
        return flyingPathNavigation;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new org.purpurmc.purpur.entity.ai.HasRider(this)); // Purpur - Ridables
        this.goalSelector.addGoal(0, new WitherBoss.WitherDoNothingGoal());
        this.goalSelector.addGoal(2, new RangedAttackGoal(this, 1.0, 40, 20.0F));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomFlyingGoal(this, 1.0));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(0, new org.purpurmc.purpur.entity.ai.HasRider(this)); // Purpur - Ridables
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 0, false, false, LIVING_ENTITY_SELECTOR));
    }

    @Override
    protected void defineSynchedData(final SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(DATA_TARGET_A, 0);
        entityData.define(DATA_TARGET_B, 0);
        entityData.define(DATA_TARGET_C, 0);
        entityData.define(DATA_ID_INV, 0);
    }

    @Override
    protected void addAdditionalSaveData(final ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("Invul", this.getInvulnerableTicks());
        output.storeNullable("Purpur.Summoner", net.minecraft.core.UUIDUtil.CODEC, getSummoner()); // Purpur - Summoner API
    }

    @Override
    protected void readAdditionalSaveData(final ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setInvulnerableTicks(input.getIntOr("Invul", 0));
        if (this.hasCustomName()) {
            this.bossEvent.setName(this.getDisplayName());
        }
        this.setSummoner(input.read("Purpur.Summoner", net.minecraft.core.UUIDUtil.CODEC).orElse(null)); // Purpur - Summoner API
    }

    @Override
    public void setCustomName(final @Nullable Component name) {
        super.setCustomName(name);
        this.bossEvent.setName(this.getDisplayName());
    }

    @Override
    public SoundEvent getAmbientSound() {
        return SoundEvents.WITHER_AMBIENT;
    }

    @Override
    public SoundEvent getHurtSound(final DamageSource source) {
        return SoundEvents.WITHER_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.WITHER_DEATH;
    }

    @Override
    public void aiStep() {
        Vec3 deltaMovement = this.getDeltaMovement().multiply(1.0, 0.6, 1.0);
        if (!this.level().isClientSide() && this.getAlternativeTarget(0) > 0) {
            Entity entity = this.level().getEntity(this.getAlternativeTarget(0));
            if (entity != null) {
                double yd = deltaMovement.y;
                if (this.getY() < entity.getY() || !this.isPowered() && this.getY() < entity.getY() + 5.0) {
                    yd = Math.max(0.0, yd);
                    yd += 0.3 - yd * 0.6F;
                }

                deltaMovement = new Vec3(deltaMovement.x, yd, deltaMovement.z);
                Vec3 delta = new Vec3(entity.getX() - this.getX(), 0.0, entity.getZ() - this.getZ());
                if (delta.horizontalDistanceSqr() > 9.0) {
                    Vec3 scale = delta.normalize();
                    deltaMovement = deltaMovement.add(scale.x * 0.3 - deltaMovement.x * 0.6, 0.0, scale.z * 0.3 - deltaMovement.z * 0.6);
                }
            }
        }

        this.setDeltaMovement(deltaMovement);
        if (deltaMovement.horizontalDistanceSqr() > 0.05) {
            this.setYRot((float)Mth.atan2(deltaMovement.z, deltaMovement.x) * Mth.RAD_TO_DEG - 90.0F);
        }

        super.aiStep();

        for (int i = 0; i < 2; i++) {
            this.yRotOHeads[i] = this.yRotHeads[i];
            this.xRotOHeads[i] = this.xRotHeads[i];
        }

        for (int i = 0; i < 2; i++) {
            int entityId = this.getAlternativeTarget(i + 1);
            Entity entity = null;
            if (entityId > 0) {
                entity = this.level().getEntity(entityId);
            }

            if (entity != null) {
                double hx = this.getHeadX(i + 1);
                double hy = this.getHeadY(i + 1);
                double hz = this.getHeadZ(i + 1);
                double xd = entity.getX() - hx;
                double yd = entity.getEyeY() - hy;
                double zd = entity.getZ() - hz;
                double sd = Math.sqrt(xd * xd + zd * zd);
                float yRotD = (float)(Mth.atan2(zd, xd) * 180.0F / (float)Math.PI) - 90.0F;
                float xRotD = (float)(-(Mth.atan2(yd, sd) * 180.0F / (float)Math.PI));
                this.xRotHeads[i] = this.rotlerp(this.xRotHeads[i], xRotD, 40.0F);
                this.yRotHeads[i] = this.rotlerp(this.yRotHeads[i], yRotD, 10.0F);
            } else {
                this.yRotHeads[i] = this.rotlerp(this.yRotHeads[i], this.yBodyRot, 10.0F);
            }
        }

        boolean isPowered = this.isPowered();

        for (int i = 0; i < 3; i++) {
            double hx = this.getHeadX(i);
            double hy = this.getHeadY(i);
            double hz = this.getHeadZ(i);
            float radius = 0.3F * this.getScale();
            this.level()
                .addParticle(
                    ParticleTypes.SMOKE,
                    hx + this.random.nextGaussian() * radius,
                    hy + this.random.nextGaussian() * radius,
                    hz + this.random.nextGaussian() * radius,
                    0.0,
                    0.0,
                    0.0
                );
            if (isPowered && this.level().getRandom().nextInt(4) == 0) {
                this.level()
                    .addParticle(
                        ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0.7F, 0.7F, 0.5F),
                        hx + this.random.nextGaussian() * radius,
                        hy + this.random.nextGaussian() * radius,
                        hz + this.random.nextGaussian() * radius,
                        0.0,
                        0.0,
                        0.0
                    );
            }
        }

        if (this.getInvulnerableTicks() > 0) {
            float height = 3.3F * this.getScale();

            for (int i = 0; i < 3; i++) {
                this.level()
                    .addParticle(
                        ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0.7F, 0.7F, 0.9F),
                        this.getX() + this.random.nextGaussian(),
                        this.getY() + this.random.nextFloat() * height,
                        this.getZ() + this.random.nextGaussian(),
                        0.0,
                        0.0,
                        0.0
                    );
            }
        }
    }

    @Override
    protected void customServerAiStep(final ServerLevel level) {
        // Purpur start - Ridables
        if (getRider() != null && this.isControllable()) {
            Vec3 mot = getDeltaMovement();
            setDeltaMovement(mot.x(), mot.y() + (getVerticalMot() > 0 ? 0.07D : 0.0D), mot.z());
        }
        if (shootCooldown > 0) {
            shootCooldown--;
        }
        // Purpur end - Ridables
        if (this.getInvulnerableTicks() > 0) {
            int newCount = this.getInvulnerableTicks() - 1;
            this.bossEvent.setProgress(1.0F - newCount / 220.0F);
            if (newCount <= 0) {
                // CraftBukkit start
                org.bukkit.event.entity.ExplosionPrimeEvent event = new org.bukkit.event.entity.ExplosionPrimeEvent(this.getBukkitEntity(), 7.0F, false);
                level.getCraftServer().getPluginManager().callEvent(event);

                if (!event.isCancelled()) {
                    level.explode(this, this.getX(), this.getEyeY(), this.getZ(), event.getRadius(), event.getFire(), Level.ExplosionInteraction.MOB);
                }
                // CraftBukkit end
                if (!this.isSilent() && level.purpurConfig.witherPlaySpawnSound) { // Purpur - Toggle for Wither's spawn sound
                    // CraftBukkit start - Use relative location for far away sounds
                    // level.globalLevelEvent(LevelEvent.SOUND_WITHER_BOSS_SPAWN, this.blockPosition(), 0);
                    int viewDistance = level.getCraftServer().getViewDistance() * 16;
                    for (ServerPlayer player : level.getPlayersForGlobalSoundGamerule()) { // Paper - respect global sound events gamerule
                        double deltaX = this.getX() - player.getX();
                        double deltaZ = this.getZ() - player.getZ();
                        double distanceSquared = Mth.square(deltaX) + Mth.square(deltaZ);
                        final double soundRadiusSquared = level.getGlobalSoundRangeSquared(config -> config.witherSpawnSoundRadius); // Paper - respect global sound events gamerule
                        if (!level.getGameRules().get(GameRules.GLOBAL_SOUND_EVENTS) && distanceSquared > soundRadiusSquared) continue; // Spigot // Paper - respect global sound events gamerule
                        if (distanceSquared > Mth.square(viewDistance)) {
                            double deltaLength = Math.sqrt(distanceSquared);
                            double relativeX = player.getX() + (deltaX / deltaLength) * viewDistance;
                            double relativeZ = player.getZ() + (deltaZ / deltaLength) * viewDistance;
                            player.connection.send(new net.minecraft.network.protocol.game.ClientboundLevelEventPacket(LevelEvent.SOUND_WITHER_BOSS_SPAWN, new BlockPos((int) relativeX, (int) this.getY(), (int) relativeZ), 0, true));
                        } else {
                            player.connection.send(new net.minecraft.network.protocol.game.ClientboundLevelEventPacket(LevelEvent.SOUND_WITHER_BOSS_SPAWN, this.blockPosition(), 0, true));
                        }
                    }
                    // CraftBukkit end
                }
            }

            this.setInvulnerableTicks(newCount);
            if (this.tickCount % 10 == 0) {
                this.heal(this.getMaxHealth() / 30, org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason.WITHER_SPAWN); // CraftBukkit // Purpur - Configurable entity base attributes
            }
        } else {
            super.customServerAiStep(level);

            for (int i = 1; i < 3; i++) {
                if (this.tickCount >= this.nextHeadUpdate[i - 1]) {
                    this.nextHeadUpdate[i - 1] = this.tickCount + 10 + this.random.nextInt(10);
                    if ((level.getDifficulty() == Difficulty.NORMAL || level.getDifficulty() == Difficulty.HARD) && this.idleHeadUpdates[i - 1]++ > 15) {
                        float hrange = 10.0F;
                        float vrange = 5.0F;
                        double xt = Mth.nextDouble(this.random, this.getX() - 10.0, this.getX() + 10.0);
                        double yt = Mth.nextDouble(this.random, this.getY() - 5.0, this.getY() + 5.0);
                        double zt = Mth.nextDouble(this.random, this.getZ() - 10.0, this.getZ() + 10.0);
                        this.performRangedAttack(i + 1, xt, yt, zt, true);
                        this.idleHeadUpdates[i - 1] = 0;
                    }

                    int headTarget = this.getAlternativeTarget(i);
                    if (headTarget > 0) {
                        LivingEntity current = (LivingEntity)level.getEntity(headTarget);
                        if (current != null && this.canAttack(current) && !(this.distanceToSqr(current) > 900.0) && this.hasLineOfSight(current)) {
                            this.performRangedAttack(i + 1, current);
                            this.nextHeadUpdate[i - 1] = this.tickCount + 40 + this.random.nextInt(20);
                            this.idleHeadUpdates[i - 1] = 0;
                        } else {
                            this.setAlternativeTarget(i, 0);
                        }
                    } else {
                        List<LivingEntity> entities = level.getNearbyEntities(
                            LivingEntity.class, TARGETING_CONDITIONS, this, this.getBoundingBox().inflate(20.0, 8.0, 20.0)
                        );
                        if (!entities.isEmpty()) {
                            LivingEntity selected = entities.get(this.random.nextInt(entities.size()));
                            if (org.bukkit.craftbukkit.event.CraftEventFactory.callEntityTargetLivingEvent(this, selected, org.bukkit.event.entity.EntityTargetEvent.TargetReason.CLOSEST_ENTITY).isCancelled()) continue; // CraftBukkit
                            this.setAlternativeTarget(i, selected.getId());
                        }
                    }
                }
            }

            if (this.getTarget() != null) {
                this.setAlternativeTarget(0, this.getTarget().getId());
            } else {
                this.setAlternativeTarget(0, 0);
            }

            if (this.destroyBlocksTick > 0) {
                this.destroyBlocksTick--;
                if (this.destroyBlocksTick == 0 && level.getGameRules().get(GameRules.MOB_GRIEFING, level.purpurConfig.witherMobGriefingOverride)) { // Purpur - Add mobGriefing override to everything affected
                    boolean destroyed = false;
                    int width = Mth.floor(this.getBbWidth() / 2.0F + 1.0F);
                    int height = Mth.floor(this.getBbHeight());

                    for (BlockPos blockPos : BlockPos.betweenClosed(
                        this.getBlockX() - width,
                        this.getBlockY(),
                        this.getBlockZ() - width,
                        this.getBlockX() + width,
                        this.getBlockY() + height,
                        this.getBlockZ() + width
                    )) {
                        BlockState state = level.getBlockState(blockPos);
                        if (canDestroy(state)) {
                            // CraftBukkit start
                            if (!org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(this, blockPos, state.getFluidState().createLegacyBlock())) { // Paper - fix wrong block state
                                continue;
                            }
                            // CraftBukkit end
                            destroyed = level.destroyBlock(blockPos, true, this) || destroyed;
                        }
                    }

                    if (destroyed) {
                        level.levelEvent(null, LevelEvent.SOUND_WITHER_BLOCK_BREAK, this.blockPosition(), 0);
                    }
                }
            }

            // Purpur start - Customizable wither health and healing - customizable heal rate and amount
            if (this.tickCount % level().purpurConfig.witherHealthRegenDelay == 0) {
                this.heal(level().purpurConfig.witherHealthRegenAmount, org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason.REGEN); // CraftBukkit
            // Purpur end - Customizable wither health and healing
            }

            this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
        }
    }

    public static boolean canDestroy(final BlockState state) {
        return !state.isAir() && !state.is(BlockTags.WITHER_IMMUNE);
    }

    public void makeInvulnerable() {
        this.setInvulnerableTicks(220);
        this.bossEvent.setProgress(0.0F);
        this.setHealth(this.getMaxHealth() / 3.0F);
    }

    @Override
    public void makeStuckInBlock(final BlockState blockState, final Vec3 speedMultiplier) {
    }

    @Override
    public void startSeenByPlayer(final ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(final ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    private double getHeadX(final int index) {
        if (index <= 0) {
            return this.getX();
        }

        float headAngle = (this.yBodyRot + 180 * (index - 1)) * Mth.DEG_TO_RAD;
        float cos = Mth.cos(headAngle);
        return this.getX() + cos * 1.3 * this.getScale();
    }

    private double getHeadY(final int index) {
        float height = index <= 0 ? 3.0F : 2.2F;
        return this.getY() + height * this.getScale();
    }

    private double getHeadZ(final int index) {
        if (index <= 0) {
            return this.getZ();
        }

        float headAngle = (this.yBodyRot + 180 * (index - 1)) * Mth.DEG_TO_RAD;
        float sin = Mth.sin(headAngle);
        return this.getZ() + sin * 1.3 * this.getScale();
    }

    private float rotlerp(final float a, final float b, final float max) {
        float diff = Mth.wrapDegrees(b - a);
        if (diff > max) {
            diff = max;
        }

        if (diff < -max) {
            diff = -max;
        }

        return a + diff;
    }

    private void performRangedAttack(final int head, final LivingEntity target) {
        this.performRangedAttack(head, target.getX(), target.getY() + target.getEyeHeight() * 0.5, target.getZ(), head == 0 && this.random.nextFloat() < 0.001F);
    }

    private void performRangedAttack(final int head, final double tx, final double ty, final double tz, final boolean dangerous) {
        if (!this.isSilent()) {
            this.level().levelEvent(null, LevelEvent.SOUND_WITHER_BOSS_SHOOT, this.blockPosition(), 0);
        }

        double hx = this.getHeadX(head);
        double hy = this.getHeadY(head);
        double hz = this.getHeadZ(head);
        double xd = tx - hx;
        double yd = ty - hy;
        double zd = tz - hz;
        Vec3 direction = new Vec3(xd, yd, zd);
        WitherSkull entity = new WitherSkull(this.level(), this, direction.normalize());
        entity.setOwner(this);
        if (dangerous) {
            entity.setDangerous(true);
        }

        entity.setPos(hx, hy, hz);
        this.level().addFreshEntity(entity);
    }

    @Override
    public void performRangedAttack(final LivingEntity target, final float power) {
        this.performRangedAttack(0, target);
    }

    @Override
    public boolean hurtServer(final ServerLevel level, final DamageSource source, final float damage) {
        if (this.isInvulnerableTo(level, source)) {
            return false;
        }

        if (source.is(DamageTypeTags.WITHER_IMMUNE_TO) || source.getEntity() instanceof WitherBoss) {
            return false;
        }

        if (this.getInvulnerableTicks() > 0 && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }

        if (this.isPowered()) {
            Entity directEntity = source.getDirectEntity();
            if (directEntity instanceof AbstractArrow || directEntity instanceof WindCharge) {
                return false;
            }
        }

        Entity sourceEntity = source.getEntity();
        if (sourceEntity != null && sourceEntity.is(EntityTypeTags.WITHER_FRIENDS)) {
            return false;
        }

        if (this.destroyBlocksTick <= 0) {
            this.destroyBlocksTick = 20;
        }

        for (int i = 0; i < this.idleHeadUpdates.length; i++) {
            this.idleHeadUpdates[i] = this.idleHeadUpdates[i] + 3;
        }

        return super.hurtServer(level, source, damage);
    }

    @Override
    protected void dropCustomDeathLoot(final ServerLevel level, final DamageSource source, final boolean killedByPlayer) {
        super.dropCustomDeathLoot(level, source, killedByPlayer);
        ItemEntity netherStar = this.spawnAtLocation(level, new net.minecraft.world.item.ItemStack(Items.NETHER_STAR), net.minecraft.world.phys.Vec3.ZERO, ItemEntity::setExtendedLifetime); // Paper - Restore vanilla drops behavior; spawnAtLocation returns null so modify the item entity with a consumer
        if (netherStar != null) {
            netherStar.setExtendedLifetime(); // Paper - diff on change
        }
    }

    @Override
    public void checkDespawn() {
        if (this.level().getDifficulty() == Difficulty.PEACEFUL && this.shouldDespawnInPeaceful()) { // Paper - allow changing despawnInPeaceful
            this.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
        } else {
            this.noActionTime = 0;
        }
    }

    @Override
    public boolean addEffect(final MobEffectInstance newEffect, final @Nullable Entity source) {
        return false;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 300.0)
            .add(Attributes.MOVEMENT_SPEED, 0.6F)
            .add(Attributes.FLYING_SPEED, 0.6F)
            .add(Attributes.FOLLOW_RANGE, 40.0)
            .add(Attributes.ARMOR, 4.0);
    }

    public float[] getHeadYRots() {
        return this.yRotHeads;
    }

    public float[] getHeadXRots() {
        return this.xRotHeads;
    }

    public int getInvulnerableTicks() {
        return this.entityData.get(DATA_ID_INV);
    }

    public void setInvulnerableTicks(final int invulnerableTicks) {
        this.entityData.set(DATA_ID_INV, invulnerableTicks);
    }

    public int getAlternativeTarget(final int headIndex) {
        return getRider() != null && this.isControllable() ? 0 : this.entityData.get(DATA_TARGETS.get(headIndex)); // Purpur - Ridables
    }

    public void setAlternativeTarget(final int headIndex, final int entityId) {
        if (getRider() == null || !this.isControllable()) this.entityData.set(DATA_TARGETS.get(headIndex), entityId); // Purpur - Ridables
    }

    public boolean isPowered() {
        return this.getHealth() <= this.getMaxHealth() / 2.0F;
    }

    @Override
    protected boolean canRide(final Entity vehicle) {
        if (this.level().purpurConfig.witherCanRideVehicles) return this.boardingCooldown <= 0; // Purpur - Configs for if Wither/Ender Dragon can ride vehicles
        return false;
    }

    @Override
    public boolean canUsePortal(final boolean ignorePassenger) {
        return this.canPortal; // Paper
    }

    // Paper start
    public void setCanTravelThroughPortals(boolean canPortal) {
        this.canPortal = canPortal;
    }
    // Paper end

    @Override
    public boolean canBeAffected(final MobEffectInstance newEffect) {
        return (!newEffect.is(MobEffects.WITHER) || !this.level().paperConfig().entities.mobEffects.immuneToWitherEffect.wither) && super.canBeAffected(newEffect);
    }

    private class WitherDoNothingGoal extends Goal {
        public WitherDoNothingGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return WitherBoss.this.getInvulnerableTicks() > 0;
        }
    }
}
