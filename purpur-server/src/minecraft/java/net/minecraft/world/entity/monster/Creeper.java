package net.minecraft.world.entity.monster;

import java.util.Collection;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.SwellGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.feline.Ocelot;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import org.jspecify.annotations.Nullable;

public class Creeper extends Monster {
    private static final EntityDataAccessor<Integer> DATA_SWELL_DIR = SynchedEntityData.defineId(Creeper.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_IS_POWERED = SynchedEntityData.defineId(Creeper.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_IS_IGNITED = SynchedEntityData.defineId(Creeper.class, EntityDataSerializers.BOOLEAN);
    private static final boolean DEFAULT_IGNITED = false;
    private static final boolean DEFAULT_POWERED = false;
    private static final short DEFAULT_MAX_SWELL = 30;
    private static final byte DEFAULT_EXPLOSION_RADIUS = 3;
    private int oldSwell;
    public int swell;
    public int maxSwell = 30;
    public int explosionRadius = 3;
    public boolean droppedSkulls;
    public @Nullable Entity entityIgniter; // CraftBukkit
    private boolean exploding = false; // Purpur - Config to make Creepers explode on death
    // Purpur start - Ridables
    private int spacebarCharge = 0;
    private int prevSpacebarCharge = 0;
    private int powerToggleDelay = 0;
    // Purpur end - Ridables

    public Creeper(final EntityType<? extends Creeper> type, final Level level) {
        super(type, level);
    }

    // Purpur start - Ridables
    @Override
    public boolean isRidable() {
        return level().purpurConfig.creeperRidable;
    }

    @Override
    public boolean dismountsUnderwater() {
        return level().purpurConfig.useDismountsUnderwaterTag ? super.dismountsUnderwater() : !level().purpurConfig.creeperRidableInWater;
    }

    @Override
    public boolean isControllable() {
        return level().purpurConfig.creeperControllable;
    }

    @Override
    protected void customServerAiStep(ServerLevel world) {
        if (powerToggleDelay > 0) {
            powerToggleDelay--;
        }
        if (getRider() != null && this.isControllable()) {
            if (getRider().getForwardMot() != 0 || getRider().getStrafeMot() != 0) {
                spacebarCharge = 0;
                setIgnited(false);
                setSwellDir(-1);
            }
            if (spacebarCharge == prevSpacebarCharge) {
                spacebarCharge = 0;
            }
            prevSpacebarCharge = spacebarCharge;
        }
        super.customServerAiStep(world);
    }

    @Override
    public void onMount(Player rider) {
        super.onMount(rider);
        setIgnited(false);
        setSwellDir(-1);
    }

    @Override
    public boolean onSpacebar() {
        if (powerToggleDelay > 0) {
            return true; // just toggled power, do not jump or ignite
        }
        spacebarCharge++;
        if (spacebarCharge > maxSwell - 2) {
            spacebarCharge = 0;
            if (getRider() != null && getRider().getBukkitEntity().hasPermission("allow.powered.creeper")) {
                powerToggleDelay = 20;
                setPowered(!isPowered());
                setIgnited(false);
                setSwellDir(-1);
                return true;
            }
        }
        if (!isIgnited()) {
            if (getRider() != null && getRider().getForwardMot() == 0 && getRider().getStrafeMot() == 0 &&
                    getRider().getBukkitEntity().hasPermission("allow.special.creeper")) {
                setIgnited(true);
                setSwellDir(1);
                return true;
            }
        }
        return getForwardMot() == 0 && getStrafeMot() == 0; // do not jump if standing still
    }
    // Purpur end - Ridables

    // Purpur start - Configurable entity base attributes
    @Override
    public void initAttributes() {
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(this.level().purpurConfig.creeperMaxHealth);
        this.getAttribute(Attributes.SCALE).setBaseValue(this.level().purpurConfig.creeperScale);
    }
    // Purpur end - Configurable entity base attributes

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, new SwellGoal(this));
        this.goalSelector.addGoal(3, new org.purpurmc.purpur.entity.ai.HasRider(this)); // Purpur - Ridables
        this.goalSelector.addGoal(3, new AvoidEntityGoal<>(this, Ocelot.class, 6.0F, 1.0, 1.2));
        this.goalSelector.addGoal(3, new AvoidEntityGoal<>(this, Cat.class, 6.0F, 1.0, 1.2));
        this.goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.0, false));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(0, new org.purpurmc.purpur.entity.ai.HasRider(this)); // Purpur - Ridables
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(2, new HurtByTargetGoal(this));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MOVEMENT_SPEED, 0.25);
    }

    @Override
    public int getMaxFallDistance() {
        return this.getTarget() == null ? this.getComfortableFallDistance(0.0F) : this.getComfortableFallDistance(this.getHealth() - 1.0F);
    }

    @Override
    public boolean causeFallDamage(final double fallDistance, final float damageModifier, final DamageSource damageSource) {
        boolean damaged = super.causeFallDamage(fallDistance, damageModifier, damageSource);
        this.swell += (int)(fallDistance * 1.5);
        if (this.swell > this.maxSwell - 5) {
            this.swell = this.maxSwell - 5;
        }

        return damaged;
    }

    @Override
    protected void defineSynchedData(final SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(DATA_SWELL_DIR, -1);
        entityData.define(DATA_IS_POWERED, false);
        entityData.define(DATA_IS_IGNITED, false);
    }

    @Override
    protected void addAdditionalSaveData(final ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("powered", this.isPowered());
        output.putShort("Fuse", (short)this.maxSwell);
        output.putByte("ExplosionRadius", (byte)this.explosionRadius);
        output.putBoolean("ignited", this.isIgnited());
    }

    @Override
    protected void readAdditionalSaveData(final ValueInput input) {
        super.readAdditionalSaveData(input);
        this.entityData.set(DATA_IS_POWERED, input.getBooleanOr("powered", false));
        this.maxSwell = input.getShortOr("Fuse", (short)30);
        this.explosionRadius = input.getByteOr("ExplosionRadius", (byte)3);
        if (input.getBooleanOr("ignited", false)) {
            this.entityData.set(DATA_IS_IGNITED, true); // Paper - set directly to avoid firing event
        }
    }

    @Override
    public void tick() {
        if (this.isAlive()) {
            this.oldSwell = this.swell;
            if (this.isIgnited()) {
                this.setSwellDir(1);
            }

            int swellDir = this.getSwellDir();
            if (swellDir > 0 && this.swell == 0) {
                this.playSound(SoundEvents.CREEPER_PRIMED, 1.0F, 0.5F);
                this.gameEvent(GameEvent.PRIME_FUSE);
            }

            this.swell += swellDir;
            if (this.swell < 0) {
                this.swell = 0;
            }

            if (this.swell >= this.maxSwell) {
                this.swell = this.maxSwell;
                this.explodeCreeper();
            }
        }

        super.tick();
    }

    @Override
    public boolean setTarget(final @Nullable LivingEntity target, org.bukkit.event.entity.EntityTargetEvent.@Nullable TargetReason reason) { // CraftBukkit
        if (!(target instanceof Goat)) {
            return super.setTarget(target, reason); // CraftBukkit
        }
        return false; // CraftBukkit
    }

    // Purpur start - Special mobs naturally spawn
    @Override
    public net.minecraft.world.entity.SpawnGroupData finalizeSpawn(net.minecraft.world.level.ServerLevelAccessor world, net.minecraft.world.DifficultyInstance difficulty, net.minecraft.world.entity.EntitySpawnReason spawnReason, net.minecraft.world.entity.@Nullable SpawnGroupData entityData) {
        double chance = world.getLevel().purpurConfig.creeperChargedChance;
        if (chance > 0D && random.nextDouble() <= chance) {
            setPowered(true);
        }
        return super.finalizeSpawn(world, difficulty, spawnReason, entityData);
    }
    // Purpur end - Special mobs naturally spawn

    // Purpur start - Config to make Creepers explode on death
    @Override
    protected org.bukkit.event.entity.EntityDeathEvent dropAllDeathLoot(ServerLevel world, DamageSource damageSource) {
        if (!this.exploding && this.level().purpurConfig.creeperExplodeWhenKilled && damageSource.getEntity() instanceof net.minecraft.server.level.ServerPlayer) {
            this.explodeCreeper();
        }
        return super.dropAllDeathLoot(world, damageSource);
    }
    // Purpur end - Config to make Creepers explode on death

    // Purpur start - Toggle for water sensitive mob damage
    @Override
    public boolean isSensitiveToWater() {
        return this.level().purpurConfig.creeperTakeDamageFromWater;
    }
    // Purpur end - Toggle for water sensitive mob damage

    // Purpur start - Mobs always drop experience
    @Override
    protected boolean isAlwaysExperienceDropper() {
        return this.level().purpurConfig.creeperAlwaysDropExp;
    }
    // Purpur end - Mobs always drop experience

    @Override
    public SoundEvent getHurtSound(final DamageSource source) {
        return SoundEvents.CREEPER_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.CREEPER_DEATH;
    }

    @Override
    public boolean killedEntity(final ServerLevel level, final LivingEntity entity, final DamageSource source) {
        if (this.shouldDropLoot(level) && this.isPowered() && !this.droppedSkulls) {
            entity.dropFromLootTable(level, source, false, BuiltInLootTables.CHARGED_CREEPER, itemStack -> {
                entity.spawnAtLocation(level, itemStack);
                this.droppedSkulls = true;
            });
        }

        return super.killedEntity(level, entity, source);
    }

    @Override
    public boolean doHurtTarget(final ServerLevel level, final Entity target) {
        return true;
    }

    public boolean isPowered() {
        return this.entityData.get(DATA_IS_POWERED);
    }

    public float getSwelling(final float a) {
        return Mth.lerp(a, this.oldSwell, this.swell) / (this.maxSwell - 2);
    }

    public int getSwellDir() {
        return this.entityData.get(DATA_SWELL_DIR);
    }

    public void setSwellDir(final int dir) {
        this.entityData.set(DATA_SWELL_DIR, dir);
    }

    @Override
    public void thunderHit(final ServerLevel level, final LightningBolt lightningBolt) {
        super.thunderHit(level, lightningBolt);
        // CraftBukkit start
        if (org.bukkit.craftbukkit.event.CraftEventFactory.callCreeperPowerEvent(this, lightningBolt, org.bukkit.event.entity.CreeperPowerEvent.PowerCause.LIGHTNING).isCancelled()) {
            return;
        }
        // CraftBukkit end
        this.entityData.set(DATA_IS_POWERED, true);
    }

    // CraftBukkit start
    public void setPowered(boolean powered) {
        this.entityData.set(DATA_IS_POWERED, powered);
    }
    // CraftBukkit end

    @Override
    protected InteractionResult mobInteract(final Player player, final InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);
        if (itemStack.is(ItemTags.CREEPER_IGNITERS)) {
            SoundEvent soundEvent = itemStack.is(Items.FIRE_CHARGE) ? SoundEvents.FIRECHARGE_USE : SoundEvents.FLINTANDSTEEL_USE;
            this.level()
                .playSound(player, this.getX(), this.getY(), this.getZ(), soundEvent, this.getSoundSource(), 1.0F, this.random.nextFloat() * 0.4F + 0.8F);
            if (!this.level().isClientSide()) {
                this.entityIgniter = player; // CraftBukkit
                this.ignite();
                if (itemStack.getMaxDamage() == 0) { // CraftBukkit - fix MC-264285: unbreakable flint and steels are completely consumed when igniting a creeper
                    itemStack.shrink(1);
                } else {
                    itemStack.hurtAndBreak(1, player, hand.asEquipmentSlot());
                }
            }

            return InteractionResult.SUCCESS;
        } else {
            return super.mobInteract(player, hand);
        }
    }

    public void explodeCreeper() {
        this.exploding = true; // Purpur - Config to make Creepers explode on death
        if (this.level() instanceof ServerLevel level) {
            float explosionMultiplier = this.isPowered() ? 2.0F : 1.0F;
            float multiplier = level.purpurConfig.creeperHealthRadius ? this.getHealth() / this.getMaxHealth() : 1; // Purpur - Config for health to impact Creeper explosion radius
            // CraftBukkit start
            org.bukkit.event.entity.ExplosionPrimeEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callExplosionPrimeEvent(this, (this.explosionRadius * explosionMultiplier) * multiplier, false); // Purpur - Config for health to impact Creeper explosion radius
            if (!event.isCancelled()) {
            // CraftBukkit end
            this.dead = true;
            level.explode(this, this.getX(), this.getY(), this.getZ(), event.getRadius(), event.getFire(), level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.MOB_GRIEFING) && level().purpurConfig.creeperAllowGriefing ? Level.ExplosionInteraction.MOB : Level.ExplosionInteraction.NONE); // CraftBukkit // Paper - fix DamageSource API (revert to vanilla, no, just no, don't change this) // Purpur - Add enderman and creeper griefing controls
            this.spawnLingeringCloud();
            this.triggerOnDeathMobEffects(level, Entity.RemovalReason.KILLED);
            this.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.EXPLODE); // CraftBukkit - add Bukkit remove cause
            // CraftBukkit start
            } else {
                this.swell = 0;
                this.entityData.set(DATA_IS_IGNITED, false); // Paper
            }
            // CraftBukkit end
        }
        this.exploding = false; // Purpur - Config to make Creepers explode on death
    }

    private void spawnLingeringCloud() {
        Collection<MobEffectInstance> activeEffects = this.getActiveEffects();
        if (!activeEffects.isEmpty() && !this.level().paperConfig().entities.behavior.disableCreeperLingeringEffect) { // Paper - Option to disable creeper lingering effect
            AreaEffectCloud cloud = new AreaEffectCloud(this.level(), this.getX(), this.getY(), this.getZ());
            cloud.setOwner(this); // CraftBukkit
            cloud.setRadius(2.5F);
            cloud.setRadiusOnUse(-0.5F);
            cloud.setWaitTime(10);
            cloud.setDuration(300);
            cloud.setPotionDurationScale(0.25F);
            cloud.setRadiusPerTick(-cloud.getRadius() / cloud.getDuration());

            for (MobEffectInstance mobEffect : activeEffects) {
                cloud.addEffect(new MobEffectInstance(mobEffect));
            }

            this.level().addFreshEntity(cloud, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.EXPLOSION); // CraftBukkit
        }
    }

    // Paper start - Call CreeperIgniteEvent
    public void setIgnited(boolean ignited) {
        if (isIgnited() != ignited) {
            com.destroystokyo.paper.event.entity.CreeperIgniteEvent event = new com.destroystokyo.paper.event.entity.CreeperIgniteEvent((org.bukkit.entity.Creeper) getBukkitEntity(), ignited);
            if (event.callEvent()) {
                this.entityData.set(DATA_IS_IGNITED, event.isIgnited());
                if (!event.isIgnited()) setSwellDir(-1); // Purpur - Ridables
            }
        }
    }
    // Paper end - Call CreeperIgniteEvent

    public boolean isIgnited() {
        return this.entityData.get(DATA_IS_IGNITED);
    }

    public void ignite() {
        setIgnited(true); // Paper - Call CreeperIgniteEvent
    }
}
