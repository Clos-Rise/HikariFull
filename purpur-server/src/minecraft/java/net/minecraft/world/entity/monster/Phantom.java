package net.minecraft.world.entity.monster;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class Phantom extends Mob implements Enemy {
    public static final float FLAP_DEGREES_PER_TICK = 7.448451F;
    public static final int TICKS_PER_FLAP = Mth.ceil(24.166098F);
    private static final EntityDataAccessor<Integer> ID_SIZE = SynchedEntityData.defineId(Phantom.class, EntityDataSerializers.INT);
    private Vec3 moveTargetPoint = Vec3.ZERO;
    public @Nullable BlockPos anchorPoint;
    private Phantom.AttackPhase attackPhase = Phantom.AttackPhase.CIRCLE;
    Vec3 crystalPosition; // Purpur - Phantoms attracted to crystals and crystals shoot phantoms
    // Paper start
    public java.util.@Nullable UUID spawningEntity;
    //public boolean shouldBurnInDay = true; // Purpur - API for any mob to burn daylight
    // Paper end
    private static final net.minecraft.world.item.crafting.Ingredient TORCH = net.minecraft.world.item.crafting.Ingredient.of(net.minecraft.world.item.Items.TORCH, net.minecraft.world.item.Items.SOUL_TORCH); // Purpur - Phantoms burn in light

    public Phantom(final EntityType<? extends Phantom> type, final Level level) {
        super(type, level);
        this.xpReward = 5;
        this.moveControl = new Phantom.PhantomMoveControl(this);
        this.lookControl = new Phantom.PhantomLookControl(this);
        this.setShouldBurnInDay(true); // Purpur - API for any mob to burn daylight
    }

    // Purpur start - Ridables
    @Override
    public boolean isRidable() {
        return level().purpurConfig.phantomRidable;
    }

    @Override
    public boolean dismountsUnderwater() {
        return level().purpurConfig.useDismountsUnderwaterTag ? super.dismountsUnderwater() : !level().purpurConfig.phantomRidableInWater;
    }

    @Override
    public boolean isControllable() {
        return level().purpurConfig.phantomControllable;
    }

    @Override
    public double getMaxY() {
        return level().purpurConfig.phantomMaxY;
    }

    public static net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.FLYING_SPEED, 3.0D);
    }

    @Override
    public boolean onSpacebar() {
        if (getRider() != null && getRider().getBukkitEntity().hasPermission("allow.special.phantom")) {
            shoot();
        }
        return false;
    }

    public boolean shoot() {
        org.bukkit.Location loc = ((org.bukkit.entity.LivingEntity) getBukkitEntity()).getEyeLocation();
        loc.setPitch(-loc.getPitch());
        org.bukkit.util.Vector target = loc.getDirection().normalize().multiply(100).add(loc.toVector());

        org.purpurmc.purpur.entity.projectile.PhantomFlames flames = new org.purpurmc.purpur.entity.projectile.PhantomFlames(level(), this);
        flames.canGrief = level().purpurConfig.phantomAllowGriefing;
        flames.shoot(target.getX() - getX(), target.getY() - getY(), target.getZ() - getZ(), 1.0F, 5.0F);
        level().addFreshEntity(flames);
        return true;
    }
    // Purpur end - Ridables

    // Purpur start - Phantoms attracted to crystals and crystals shoot phantoms
    @Override
    protected void dropFromLootTable(ServerLevel world, DamageSource damageSource, boolean causedByPlayer) {
        boolean dropped = false;
        if (lastHurtByPlayer == null && damageSource.getEntity() instanceof net.minecraft.world.entity.boss.enderdragon.EndCrystal) {
            if (random.nextInt(5) < 1) {
                dropped = spawnAtLocation(world, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PHANTOM_MEMBRANE)) != null;
            }
        }
        if (!dropped) {
            super.dropFromLootTable(world, damageSource, causedByPlayer);
        }
    }

    public boolean isCirclingCrystal() {
        return crystalPosition != null;
    }
    // Purpur end - Phantoms attracted to crystals and crystals shoot phantoms

    // Purpur start - Toggle for water sensitive mob damage
    @Override
    public boolean isSensitiveToWater() {
        return this.level().purpurConfig.phantomTakeDamageFromWater;
    }
    // Purpur end - Toggle for water sensitive mob damage

    // Purpur start - Mobs always drop experience
    @Override
    protected boolean isAlwaysExperienceDropper() {
        return this.level().purpurConfig.phantomAlwaysDropExp;
    }
    // Purpur end - Mobs always drop experience

    @Override
    public boolean isFlapping() {
        return (this.getUniqueFlapTickOffset() + this.tickCount) % TICKS_PER_FLAP == 0;
    }

    @Override
    protected BodyRotationControl createBodyControl() {
        return new Phantom.PhantomBodyRotationControl(this);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new org.purpurmc.purpur.entity.ai.HasRider(this)); // Purpur - Ridables
        // Purpur start - Phantoms attracted to crystals and crystals shoot phantoms
        if (level().purpurConfig.phantomOrbitCrystalRadius > 0) {
            this.goalSelector.addGoal(1, new PhantomFindCrystalGoal(this));
            this.goalSelector.addGoal(2, new PhantomOrbitCrystalGoal(this));
        }
        this.goalSelector.addGoal(3, new Phantom.PhantomAttackStrategyGoal());
        this.goalSelector.addGoal(4, new Phantom.PhantomSweepAttackGoal());
        this.goalSelector.addGoal(5, new Phantom.PhantomCircleAroundAnchorGoal());
        // Purpur end - Phantoms attracted to crystals and crystals shoot phantoms
        this.targetSelector.addGoal(0, new org.purpurmc.purpur.entity.ai.HasRider(this)); // Purpur - Ridables
        this.targetSelector.addGoal(1, new Phantom.PhantomAttackPlayerTargetGoal());
    }

    @Override
    protected void defineSynchedData(final SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(ID_SIZE, 0);
    }

    public void setPhantomSize(final int size) {
        this.entityData.set(ID_SIZE, Mth.clamp(size, 0, 64));
    }

    private void updatePhantomSizeInfo() {
        this.refreshDimensions();
        if (level().purpurConfig.phantomFlamesOnSwoop && attackPhase == AttackPhase.SWOOP) shoot(); // Purpur - Ridables - Phantom flames on swoop
        // Purpur start - Configurable entity base attributes
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(getFromCache(() -> this.level().purpurConfig.phantomMaxHealth, () -> this.level().purpurConfig.phantomMaxHealthCache, () -> 20.0D));
        this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(getFromCache(() -> this.level().purpurConfig.phantomAttackDamage, () -> this.level().purpurConfig.phantomAttackDamageCache, () -> (double) (6 + this.getPhantomSize())));
        // Purpur end - Configurable entity base attributes
    }

    public int getPhantomSize() {
        return this.entityData.get(ID_SIZE);
    }

    @Override
    public void onSyncedDataUpdated(final EntityDataAccessor<?> accessor) {
        if (ID_SIZE.equals(accessor)) {
            this.updatePhantomSizeInfo();
        }

        super.onSyncedDataUpdated(accessor);
    }

    public int getUniqueFlapTickOffset() {
        return this.getId() * 3;
    }

    // Purpur start - Configurable entity base attributes
    private double getFromCache(java.util.function.Supplier<String> equation, java.util.function.Supplier<java.util.Map<Integer, Double>> cache, java.util.function.Supplier<Double> defaultValue) {
        int size = getPhantomSize();
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

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            float anim = Mth.cos((this.getUniqueFlapTickOffset() + this.tickCount) * 7.448451F * Mth.DEG_TO_RAD + Mth.PI);
            float nextAnim = Mth.cos((this.getUniqueFlapTickOffset() + this.tickCount + 1) * 7.448451F * Mth.DEG_TO_RAD + Mth.PI);
            if (anim > 0.0F && nextAnim <= 0.0F) {
                this.level()
                    .playLocalSound(
                        this.getX(),
                        this.getY(),
                        this.getZ(),
                        SoundEvents.PHANTOM_FLAP,
                        this.getSoundSource(),
                        0.95F + this.random.nextFloat() * 0.05F,
                        0.95F + this.random.nextFloat() * 0.05F,
                        false
                    );
            }

            float width = this.getBbWidth() * 1.48F;
            float c = Mth.cos(this.getYRot() * Mth.DEG_TO_RAD) * width;
            float s = Mth.sin(this.getYRot() * Mth.DEG_TO_RAD) * width;
            float h = (0.3F + anim * 0.45F) * this.getBbHeight() * 2.5F;
            this.level().addParticle(ParticleTypes.MYCELIUM, this.getX() + c, this.getY() + h, this.getZ() + s, 0.0, 0.0, 0.0);
            this.level().addParticle(ParticleTypes.MYCELIUM, this.getX() - c, this.getY() + h, this.getZ() - s, 0.0, 0.0, 0.0);
        }
    }

    // Paper start
    @Override
    public boolean isSunBurnTick() {
        // Purpur start - API for any mob to burn daylight
        boolean burnFromDaylight = this.shouldBurnInDay && super.isSunBurnTick() && this.level().purpurConfig.phantomBurnInDaylight;
        boolean burnFromLightSource = this.level().purpurConfig.phantomBurnInLight > 0 && this.level().getMaxLocalRawBrightness(blockPosition()) >= this.level().purpurConfig.phantomBurnInLight;
        return burnFromDaylight || burnFromLightSource;
        // Purpur end - API for any mob to burn daylight
    }
    // Paper end

    @Override
    protected void checkFallDamage(final double ya, final boolean onGround, final BlockState onState, final BlockPos pos) {
    }

    @Override
    public boolean onClimbable() {
        return false;
    }

    @Override
    public void travel(final Vec3 input) {
        this.travelFlying(input, 0.2F);
        // Purpur start - Ridables
        if (this.getRider() != null && this.isControllable() && !this.onGround) {
            float speed = (float) this.getAttributeValue(Attributes.FLYING_SPEED);
            this.setSpeed(speed);
            Vec3 mot = this.getDeltaMovement();
            this.move(net.minecraft.world.entity.MoverType.SELF, mot.multiply(speed, speed, speed));
            this.setDeltaMovement(mot.scale(0.9D));
        }
        // Purpur end - Ridables
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(
        final ServerLevelAccessor level, final DifficultyInstance difficulty, final EntitySpawnReason spawnReason, final @Nullable SpawnGroupData groupData
    ) {
        this.anchorPoint = this.blockPosition().above(5);
        // Purpur start - Configurable phantom size
        int min = level.getLevel().purpurConfig.phantomMinSize;
        int max = level.getLevel().purpurConfig.phantomMaxSize;
        this.setPhantomSize(min == max ? min : level.getRandom().nextInt(max + 1 - min) + min);
        // Purpur end - Configurable phantom size
        return super.finalizeSpawn(level, difficulty, spawnReason, groupData);
    }

    @Override
    protected void readAdditionalSaveData(final ValueInput input) {
        super.readAdditionalSaveData(input);
        this.anchorPoint = input.read("anchor_pos", BlockPos.CODEC).orElse(null);
        this.setPhantomSize(input.getIntOr("size", 0));
        // Paper start
        this.spawningEntity = input.read("Paper.SpawningEntity", net.minecraft.core.UUIDUtil.CODEC).orElse(null);
        //this.shouldBurnInDay = input.getBooleanOr("Paper.ShouldBurnInDay", true); // Purpur - implemented in LivingEntity - API for any mob to burn daylight
        // Paper end
    }

    @Override
    protected void addAdditionalSaveData(final ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.storeNullable("anchor_pos", BlockPos.CODEC, this.anchorPoint);
        output.putInt("size", this.getPhantomSize());
        // Paper start
        output.storeNullable("Paper.SpawningEntity", net.minecraft.core.UUIDUtil.CODEC, this.spawningEntity);
        //output.putBoolean("Paper.ShouldBurnInDay", this.shouldBurnInDay); // Purpur - implemented in LivingEntity - API for any mob to burn daylight
        // Paper end
    }

    @Override
    public boolean shouldRenderAtSqrDistance(final double distance) {
        return true;
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.HOSTILE;
    }

    @Override
    public SoundEvent getAmbientSound() {
        return SoundEvents.PHANTOM_AMBIENT;
    }

    @Override
    public SoundEvent getHurtSound(final DamageSource source) {
        return SoundEvents.PHANTOM_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.PHANTOM_DEATH;
    }

    @Override
    public EntityDimensions getDefaultDimensions(final Pose pose) {
        int size = this.getPhantomSize();
        EntityDimensions originalDimensions = super.getDefaultDimensions(pose);
        return originalDimensions.scale(1.0F + 0.15F * size);
    }

    private boolean canAttack(final ServerLevel level, final LivingEntity target, final TargetingConditions targetConditions) {
        return targetConditions.test(level, this, target);
    }

    private enum AttackPhase {
        CIRCLE,
        SWOOP;
    }

    private class PhantomAttackPlayerTargetGoal extends Goal {
        private final TargetingConditions attackTargeting = TargetingConditions.forCombat().range(64.0);
        private int nextScanTick = reducedTickDelay(20);

        @Override
        public boolean canUse() {
            if (this.nextScanTick > 0) {
                this.nextScanTick--;
                return false;
            }

            this.nextScanTick = reducedTickDelay(60);
            ServerLevel level = getServerLevel(Phantom.this.level());
            List<Player> players = level.getNearbyPlayers(this.attackTargeting, Phantom.this, Phantom.this.getBoundingBox().inflate(16.0, 64.0, 16.0));
            if (level().purpurConfig.phantomIgnorePlayersWithTorch) players.removeIf(human -> TORCH.test(human.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND)) || TORCH.test(human.getItemInHand(net.minecraft.world.InteractionHand.OFF_HAND))); // Purpur - Phantoms burn in light
                if (!players.isEmpty()) {
                players.sort(Comparator.<Player, Double>comparing(Entity::getY).reversed());

                for (Player player : players) {
                    if (Phantom.this.canAttack(level, player, TargetingConditions.DEFAULT)) {
                        if (!level().paperConfig().entities.behavior.phantomsOnlyAttackInsomniacs || EntitySelector.IS_INSOMNIAC.test(player)) { // Paper - Add phantom creative and insomniac controls
                        Phantom.this.setTarget(player, org.bukkit.event.entity.EntityTargetEvent.TargetReason.CLOSEST_PLAYER); // CraftBukkit - reason
                        return true;
                        } // Paper - Add phantom creative and insomniac controls
                    }
                }
            }

            return false;
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity target = Phantom.this.getTarget();
            return target != null && Phantom.this.canAttack(getServerLevel(Phantom.this.level()), target, TargetingConditions.DEFAULT);
        }
    }

    private class PhantomAttackStrategyGoal extends Goal {
        private int nextSweepTick;

        @Override
        public boolean canUse() {
            LivingEntity target = Phantom.this.getTarget();
            return target != null && Phantom.this.canAttack(getServerLevel(Phantom.this.level()), target, TargetingConditions.DEFAULT);
        }

        @Override
        public void start() {
            this.nextSweepTick = this.adjustedTickDelay(10);
            Phantom.this.attackPhase = Phantom.AttackPhase.CIRCLE;
            this.setAnchorAboveTarget();
        }

        @Override
        public void stop() {
            if (Phantom.this.anchorPoint != null) {
                Phantom.this.anchorPoint = Phantom.this.level()
                    .getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, Phantom.this.anchorPoint)
                    .above(10 + Phantom.this.random.nextInt(20));
            }
        }

        @Override
        public void tick() {
            if (Phantom.this.attackPhase == Phantom.AttackPhase.CIRCLE) {
                this.nextSweepTick--;
                if (this.nextSweepTick <= 0) {
                    Phantom.this.attackPhase = Phantom.AttackPhase.SWOOP;
                    this.setAnchorAboveTarget();
                    this.nextSweepTick = this.adjustedTickDelay((8 + Phantom.this.random.nextInt(4)) * 20);
                    Phantom.this.playSound(SoundEvents.PHANTOM_SWOOP, 10.0F, 0.95F + Phantom.this.random.nextFloat() * 0.1F);
                }
            }
        }

        private void setAnchorAboveTarget() {
            if (Phantom.this.anchorPoint != null) {
                Phantom.this.anchorPoint = Phantom.this.getTarget().blockPosition().above(20 + Phantom.this.random.nextInt(20));
                if (Phantom.this.anchorPoint.getY() < Phantom.this.level().getSeaLevel()) {
                    Phantom.this.anchorPoint = new BlockPos(
                        Phantom.this.anchorPoint.getX(), Phantom.this.level().getSeaLevel() + 1, Phantom.this.anchorPoint.getZ()
                    );
                }
            }
        }
    }

    private class PhantomBodyRotationControl extends BodyRotationControl {
        public PhantomBodyRotationControl(final Mob mob) {
            super(mob);
        }

        @Override
        public void clientTick() {
            Phantom.this.yHeadRot = Phantom.this.yBodyRot;
            Phantom.this.yBodyRot = Phantom.this.getYRot();
        }
    }

    private class PhantomCircleAroundAnchorGoal extends Phantom.PhantomMoveTargetGoal {
        private float angle;
        private float distance;
        private float height;
        private float clockwise;

        @Override
        public boolean canUse() {
            return Phantom.this.getTarget() == null || Phantom.this.attackPhase == Phantom.AttackPhase.CIRCLE;
        }

        @Override
        public void start() {
            this.distance = 5.0F + Phantom.this.random.nextFloat() * 10.0F;
            this.height = -4.0F + Phantom.this.random.nextFloat() * 9.0F;
            this.clockwise = Phantom.this.random.nextBoolean() ? 1.0F : -1.0F;
            this.selectNext();
        }

        @Override
        public void tick() {
            if (Phantom.this.random.nextInt(this.adjustedTickDelay(350)) == 0) {
                this.height = -4.0F + Phantom.this.random.nextFloat() * 9.0F;
            }

            if (Phantom.this.random.nextInt(this.adjustedTickDelay(250)) == 0) {
                this.distance++;
                if (this.distance > 15.0F) {
                    this.distance = 5.0F;
                    this.clockwise = -this.clockwise;
                }
            }

            if (Phantom.this.random.nextInt(this.adjustedTickDelay(450)) == 0) {
                this.angle = Phantom.this.random.nextFloat() * 2.0F * (float) Math.PI;
                this.selectNext();
            }

            if (this.touchingTarget()) {
                this.selectNext();
            }

            if (Phantom.this.moveTargetPoint.y < Phantom.this.getY() && !Phantom.this.level().isEmptyBlock(Phantom.this.blockPosition().below(1))) {
                this.height = Math.max(1.0F, this.height);
                this.selectNext();
            }

            if (Phantom.this.moveTargetPoint.y > Phantom.this.getY() && !Phantom.this.level().isEmptyBlock(Phantom.this.blockPosition().above(1))) {
                this.height = Math.min(-1.0F, this.height);
                this.selectNext();
            }
        }

        private void selectNext() {
            if (Phantom.this.anchorPoint == null) {
                Phantom.this.anchorPoint = Phantom.this.blockPosition();
            }

            this.angle = this.angle + this.clockwise * 15.0F * Mth.DEG_TO_RAD;
            Phantom.this.moveTargetPoint = Vec3.atLowerCornerOf(Phantom.this.anchorPoint)
                .add(this.distance * Mth.cos(this.angle), -4.0F + this.height, this.distance * Mth.sin(this.angle));
        }
    }

    private static class PhantomLookControl extends org.purpurmc.purpur.controller.LookControllerWASD { // Purpur - Ridables
        public PhantomLookControl(final Mob mob) {
            super(mob);
        }

        // Purpur start - Ridables
        public void purpurTick(Player rider) {
            setYawPitch(rider.getYRot(), -rider.xRotO * 0.75F);
        }
        // Purpur end - Ridables

        @Override
        public void vanillaTick() { // Purpur - Ridables
        }
    }

    // Purpur start - Phantoms attracted to crystals and crystals shoot phantoms
    class PhantomFindCrystalGoal extends Goal {
        private final Phantom phantom;
        private net.minecraft.world.entity.boss.enderdragon.EndCrystal crystal;
        private Comparator<net.minecraft.world.entity.boss.enderdragon.EndCrystal> comparator;

        PhantomFindCrystalGoal(Phantom phantom) {
            this.phantom = phantom;
            this.comparator = Comparator.comparingDouble(phantom::distanceToSqr);
            this.setFlags(EnumSet.of(Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            double range = maxTargetRange();
            List<net.minecraft.world.entity.boss.enderdragon.EndCrystal> crystals = level().getEntitiesOfClass(net.minecraft.world.entity.boss.enderdragon.EndCrystal.class, phantom.getBoundingBox().inflate(range));
            if (crystals.isEmpty()) {
                return false;
            }
            crystals.sort(comparator);
            crystal = crystals.get(0);
            if (phantom.distanceToSqr(crystal) > range * range) {
                crystal = null;
                return false;
            }
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            if (crystal == null || !crystal.isAlive()) {
                return false;
            }
            double range = maxTargetRange();
            return phantom.distanceToSqr(crystal) <= (range * range) * 2;
        }

        @Override
        public void start() {
            phantom.crystalPosition = new Vec3(crystal.getX(), crystal.getY() + (phantom.random.nextInt(10) + 10), crystal.getZ());
        }

        @Override
        public void stop() {
            crystal = null;
            phantom.crystalPosition = null;
            super.stop();
        }

        private double maxTargetRange() {
            return phantom.level().purpurConfig.phantomOrbitCrystalRadius;
        }
    }

    class PhantomOrbitCrystalGoal extends Goal {
        private final Phantom phantom;
        private float offset;
        private float radius;
        private float verticalChange;
        private float direction;

        PhantomOrbitCrystalGoal(Phantom phantom) {
            this.phantom = phantom;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return phantom.isCirclingCrystal();
        }

        @Override
        public void start() {
            this.radius = 5.0F + phantom.random.nextFloat() * 10.0F;
            this.verticalChange = -4.0F + phantom.random.nextFloat() * 9.0F;
            this.direction = phantom.random.nextBoolean() ? 1.0F : -1.0F;
            updateOffset();
        }

        @Override
        public void tick() {
            if (phantom.random.nextInt(350) == 0) {
                this.verticalChange = -4.0F + phantom.random.nextFloat() * 9.0F;
            }
            if (phantom.random.nextInt(250) == 0) {
                ++this.radius;
                if (this.radius > 15.0F) {
                    this.radius = 5.0F;
                    this.direction = -this.direction;
                }
            }
            if (phantom.random.nextInt(450) == 0) {
                this.offset = phantom.random.nextFloat() * 2.0F * 3.1415927F;
                updateOffset();
            }
            if (phantom.moveTargetPoint.distanceToSqr(phantom.getX(), phantom.getY(), phantom.getZ()) < 4.0D) {
                updateOffset();
            }
            if (phantom.moveTargetPoint.y < phantom.getY() && !phantom.level().isEmptyBlock(new BlockPos(phantom).below(1))) {
                this.verticalChange = Math.max(1.0F, this.verticalChange);
                updateOffset();
            }
            if (phantom.moveTargetPoint.y > phantom.getY() && !phantom.level().isEmptyBlock(new BlockPos(phantom).above(1))) {
                this.verticalChange = Math.min(-1.0F, this.verticalChange);
                updateOffset();
            }
        }

        private void updateOffset() {
            this.offset += this.direction * 15.0F * 0.017453292F;
            phantom.moveTargetPoint = phantom.crystalPosition.add(
                this.radius * Mth.cos(this.offset),
                -4.0F + this.verticalChange,
                this.radius * Mth.sin(this.offset));
        }
    }
    // Purpur end - Phantoms attracted to crystals and crystals shoot phantoms

    private class PhantomMoveControl extends org.purpurmc.purpur.controller.FlyingMoveControllerWASD { // Purpur - Ridables
        private float speed = 0.1F;

        public PhantomMoveControl(final Mob mob) {
            super(mob);
        }

        // Purpur start - Ridables
        public void purpurTick(Player rider) {
            if (!Phantom.this.onGround) {
                // phantom is always in motion when flying
                // TODO - FIX THIS
                // rider.setForward(1.0F);
            }
            super.purpurTick(rider);
        }
        // Purpur end - Ridables

        @Override
        public void vanillaTick() { // Purpur - Ridables
            if (Phantom.this.horizontalCollision) {
                Phantom.this.setYRot(Phantom.this.getYRot() + 180.0F);
                this.speed = 0.1F;
            }

            double tdx = Phantom.this.moveTargetPoint.x - Phantom.this.getX();
            double tdy = Phantom.this.moveTargetPoint.y - Phantom.this.getY();
            double tdz = Phantom.this.moveTargetPoint.z - Phantom.this.getZ();
            double sd = Math.sqrt(tdx * tdx + tdz * tdz);
            if (Math.abs(sd) > 1.0E-5F) {
                double yRelativeScale = 1.0 - Math.abs(tdy * 0.7F) / sd;
                tdx *= yRelativeScale;
                tdz *= yRelativeScale;
                sd = Math.sqrt(tdx * tdx + tdz * tdz);
                double sd2 = Math.sqrt(tdx * tdx + tdz * tdz + tdy * tdy);
                float prev = Phantom.this.getYRot();
                float angle = (float)Mth.atan2(tdz, tdx);
                float a = Mth.wrapDegrees(Phantom.this.getYRot() + 90.0F);
                float b = Mth.wrapDegrees(angle * Mth.RAD_TO_DEG);
                Phantom.this.setYRot(Mth.approachDegrees(a, b, 4.0F) - 90.0F);
                Phantom.this.yBodyRot = Phantom.this.getYRot();
                if (Mth.degreesDifferenceAbs(prev, Phantom.this.getYRot()) < 3.0F) {
                    this.speed = Mth.approach(this.speed, 1.8F, 0.005F * (1.8F / this.speed));
                } else {
                    this.speed = Mth.approach(this.speed, 0.2F, 0.025F);
                }

                float xRotD = (float)(-(Mth.atan2(-tdy, sd) * 180.0F / (float)Math.PI));
                Phantom.this.setXRot(xRotD);
                float moveAngle = Phantom.this.getYRot() + 90.0F;
                double txd = this.speed * Mth.cos(moveAngle * Mth.DEG_TO_RAD) * Math.abs(tdx / sd2);
                double tzd = this.speed * Mth.sin(moveAngle * Mth.DEG_TO_RAD) * Math.abs(tdz / sd2);
                double tyd = this.speed * Mth.sin(xRotD * Mth.DEG_TO_RAD) * Math.abs(tdy / sd2);
                Vec3 movement = Phantom.this.getDeltaMovement();
                Phantom.this.setDeltaMovement(movement.add(new Vec3(txd, tyd, tzd).subtract(movement).scale(0.2)));
            }
        }
    }

    private abstract class PhantomMoveTargetGoal extends Goal {
        public PhantomMoveTargetGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        protected boolean touchingTarget() {
            return Phantom.this.moveTargetPoint.distanceToSqr(Phantom.this.getX(), Phantom.this.getY(), Phantom.this.getZ()) < 4.0;
        }
    }

    private class PhantomSweepAttackGoal extends Phantom.PhantomMoveTargetGoal {
        private static final int CAT_SEARCH_TICK_DELAY = 20;
        private boolean isScaredOfCat;
        private int catSearchTick;

        @Override
        public boolean canUse() {
            return Phantom.this.getTarget() != null && Phantom.this.attackPhase == Phantom.AttackPhase.SWOOP;
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity target = Phantom.this.getTarget();
            if (target == null) {
                return false;
            } else if (!target.isAlive()) {
                return false;
            // Purpur start - Phantoms burn in light
            } else if (level().purpurConfig.phantomBurnInLight > 0 && level().getLightEmission(new BlockPos(Phantom.this)) >= level().purpurConfig.phantomBurnInLight) {
                return false;
            } else if (level().purpurConfig.phantomIgnorePlayersWithTorch && (TORCH.test(target.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND)) || TORCH.test(target.getItemInHand(net.minecraft.world.InteractionHand.OFF_HAND)))) {
                return false;
            // Purpur end - Phantoms burn in light
            } else if (target instanceof Player player && (target.isSpectator() || player.isCreative())) {
                return false;
            } else {
                if (!this.canUse()) {
                    return false;
                }

                if (Phantom.this.tickCount > this.catSearchTick) {
                    this.catSearchTick = Phantom.this.tickCount + 20;
                    List<Cat> cats = Phantom.this.level()
                        .getEntitiesOfClass(Cat.class, Phantom.this.getBoundingBox().inflate(16.0), EntitySelector.ENTITY_STILL_ALIVE);

                    for (Cat cat : cats) {
                        cat.hiss();
                    }

                    this.isScaredOfCat = !cats.isEmpty();
                }

                return !this.isScaredOfCat;
            }
        }

        @Override
        public void stop() {
            Phantom.this.setTarget(null);
            Phantom.this.attackPhase = Phantom.AttackPhase.CIRCLE;
        }

        @Override
        public void tick() {
            LivingEntity target = Phantom.this.getTarget();
            if (target != null) {
                Phantom.this.moveTargetPoint = new Vec3(target.getX(), target.getY(0.5), target.getZ());
                if (Phantom.this.getBoundingBox().inflate(0.2F).intersects(target.getBoundingBox())) {
                    Phantom.this.doHurtTarget(getServerLevel(Phantom.this.level()), target);
                    Phantom.this.attackPhase = Phantom.AttackPhase.CIRCLE;
                    if (!Phantom.this.isSilent()) {
                        Phantom.this.level().levelEvent(LevelEvent.SOUND_PHANTOM_BITE, Phantom.this.blockPosition(), 0);
                    }
                } else if (Phantom.this.horizontalCollision || Phantom.this.hurtTime > 0) {
                    Phantom.this.attackPhase = Phantom.AttackPhase.CIRCLE;
                }
            }
        }
    }
}
