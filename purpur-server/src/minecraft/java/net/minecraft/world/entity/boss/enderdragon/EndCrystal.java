package net.minecraft.world.entity.boss.enderdragon;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.dimension.end.EnderDragonFight;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class EndCrystal extends Entity {
    private static final EntityDataAccessor<Optional<BlockPos>> DATA_BEAM_TARGET = SynchedEntityData.defineId(
        EndCrystal.class, EntityDataSerializers.OPTIONAL_BLOCK_POS
    );
    private static final EntityDataAccessor<Boolean> DATA_SHOW_BOTTOM = SynchedEntityData.defineId(EndCrystal.class, EntityDataSerializers.BOOLEAN);
    private static final boolean DEFAULT_SHOW_BOTTOM = true;
    public int time;
    public boolean generatedByDragonFight = false; // Paper - Fix invulnerable end crystals
    // Purpur start - Phantoms attracted to crystals and crystals shoot phantoms
    private net.minecraft.world.entity.monster.Phantom targetPhantom;
    private int phantomBeamTicks = 0;
    private int phantomDamageCooldown = 0;
    private int idleCooldown = 0;
    // Purpur end - Phantoms attracted to crystals and crystals shoot phantoms

    public EndCrystal(final EntityType<? extends EndCrystal> type, final Level level) {
        super(type, level);
        this.blocksBuilding = true;
        this.time = this.random.nextInt(100000);
    }

    public EndCrystal(final Level level, final double x, final double y, final double z) {
        this(EntityType.END_CRYSTAL, level);
        this.setPos(x, y, z);
    }

    // Purpur start - End crystal explosion options
    public boolean shouldExplode() {
        return showsBottom() ? level().purpurConfig.basedEndCrystalExplode : level().purpurConfig.baselessEndCrystalExplode;
    }

    public float getExplosionPower() {
        return (float) (showsBottom() ? level().purpurConfig.basedEndCrystalExplosionPower : level().purpurConfig.baselessEndCrystalExplosionPower);
    }

    public boolean hasExplosionFire() {
        return showsBottom() ? level().purpurConfig.basedEndCrystalExplosionFire : level().purpurConfig.baselessEndCrystalExplosionFire;
    }

    public Level.ExplosionInteraction getExplosionEffect() {
        return showsBottom() ? level().purpurConfig.basedEndCrystalExplosionEffect : level().purpurConfig.baselessEndCrystalExplosionEffect;
    }
    // Purpur end - End crystal explosion options

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.NONE;
    }

    @Override
    protected void defineSynchedData(final SynchedEntityData.Builder entityData) {
        entityData.define(DATA_BEAM_TARGET, Optional.empty());
        entityData.define(DATA_SHOW_BOTTOM, true);
    }

    @Override
    public void tick() {
        this.time++;
        this.applyEffectsFromBlocks();
        this.handlePortal();
        if (this.level() instanceof ServerLevel) {
            BlockPos pos = this.blockPosition();
            if (((ServerLevel)this.level()).getDragonFight() != null && this.level().getBlockState(pos).isAir()) {
                if (!org.bukkit.craftbukkit.event.CraftEventFactory.callBlockIgniteEvent(this.level(), pos, this).isCancelled()) { // Paper
                this.level().setBlockAndUpdate(pos, BaseFireBlock.getState(this.level(), pos));
                } // Paper
            }
        }

        // Paper start - Fix invulnerable end crystals
        if (this.level().paperConfig().unsupportedSettings.fixInvulnerableEndCrystalExploit && this.generatedByDragonFight && this.isInvulnerable()) {
            if (!java.util.Objects.equals(((ServerLevel) this.level()).uuid, this.originWorld)
                || ((ServerLevel) this.level()).getDragonFight() == null
                || ((ServerLevel) this.level()).getDragonFight().respawnStage == null
                || ((ServerLevel) this.level()).getDragonFight().respawnStage.ordinal() > net.minecraft.world.level.dimension.end.DragonRespawnStage.SUMMONING_DRAGON.ordinal()) {
                this.setInvulnerable(false);
                this.setBeamTarget(null);
            }
        }
        // Paper end - Fix invulnerable end crystals
        if (this.level().purpurConfig.endCrystalCramming > 0 && this.level().getEntitiesOfClass(EndCrystal.class, getBoundingBox()).size() > this.level().purpurConfig.endCrystalCramming) this.hurt(this.damageSources().cramming(), 6.0F); // Purpur - End Crystal Cramming

        // Purpur start - Phantoms attracted to crystals and crystals shoot phantoms
        if (level().purpurConfig.phantomAttackedByCrystalRadius <= 0 || --idleCooldown > 0) {
            return; // on cooldown
        }

        if (targetPhantom == null) {
            for (net.minecraft.world.entity.monster.Phantom phantom : level().getEntitiesOfClass(net.minecraft.world.entity.monster.Phantom.class, getBoundingBox().inflate(level().purpurConfig.phantomAttackedByCrystalRadius))) {
                if (phantom.hasLineOfSight(this)) {
                    attackPhantom(phantom);
                    break;
                }
            }
        } else {
            setBeamTarget(new BlockPos(targetPhantom).offset(0, -2, 0));
            if (--phantomBeamTicks > 0 && targetPhantom.isAlive()) {
                phantomDamageCooldown--;
                if (targetPhantom.hasLineOfSight(this)) {
                    if (phantomDamageCooldown <= 0) {
                        phantomDamageCooldown = 20;
                        targetPhantom.hurt(targetPhantom.damageSources().indirectMagic(this, this), level().purpurConfig.phantomAttackedByCrystalDamage);
                    }
                } else {
                    forgetPhantom(); // no longer in sight
                }
            } else {
                forgetPhantom(); // attacked long enough
            }
        }
    }

    private void attackPhantom(net.minecraft.world.entity.monster.Phantom phantom) {
        phantomDamageCooldown = 0;
        phantomBeamTicks = 60;
        targetPhantom = phantom;
    }

    private void forgetPhantom() {
        targetPhantom = null;
        setBeamTarget(null);
        phantomBeamTicks = 0;
        phantomDamageCooldown = 0;
        idleCooldown = 60;
        // Purpur end - Phantoms attracted to crystals and crystals shoot phantoms
    }

    @Override
    protected void addAdditionalSaveData(final ValueOutput output) {
        output.storeNullable("beam_target", BlockPos.CODEC, this.getBeamTarget());
        output.putBoolean("ShowBottom", this.showsBottom());
        output.putBoolean("Paper.GeneratedByDragonFight", this.generatedByDragonFight); // Paper - Fix invulnerable end crystals
    }

    @Override
    protected void readAdditionalSaveData(final ValueInput input) {
        this.setBeamTarget(input.read("beam_target", BlockPos.CODEC).orElse(null));
        this.setShowBottom(input.getBooleanOr("ShowBottom", true));
        this.generatedByDragonFight = input.getBooleanOr("Paper.GeneratedByDragonFight", false); // Paper - Fix invulnerable end crystals
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public final boolean hurtClient(final DamageSource source) {
        return !this.isInvulnerableToBase(source) && !(source.getEntity() instanceof EnderDragon);
    }

    @Override
    public final boolean hurtServer(final ServerLevel level, final DamageSource source, final float damage) {
        if (this.isInvulnerableToBase(source)) {
            return false;
        }

        if (source.getEntity() instanceof EnderDragon) {
            return false;
        }

        if (!this.isRemoved()) {
            // CraftBukkit start - All non-living entities need this
            if (org.bukkit.craftbukkit.event.CraftEventFactory.handleNonLivingEntityDamageEvent(this, source, damage, false)) {
                return false;
            }
            // CraftBukkit end
            if (!source.is(DamageTypeTags.IS_EXPLOSION)) {
                if (shouldExplode()) {// Purpur - End crystal explosion options
                DamageSource damageSource = source.getEntity() != null ? this.damageSources().explosion(this, source.getEntity()) : null;
                // CraftBukkit start
                org.bukkit.event.entity.ExplosionPrimeEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callExplosionPrimeEvent(this, getExplosionPower(), hasExplosionFire()); // Purpur - End crystal explosion options
                if (event.isCancelled()) {
                    return false;
                }

                this.remove(Entity.RemovalReason.KILLED, org.bukkit.event.entity.EntityRemoveEvent.Cause.EXPLODE); // Paper - add Bukkit remove cause
                level.explode(this, damageSource, null, this.getX(), this.getY(), this.getZ(), event.getRadius(), event.getFire(), getExplosionEffect()); // Purpur - End crystal explosion options
                } else this.unsetRemoved(); // Purpur - End crystal explosion options
            } else {
                this.remove(Entity.RemovalReason.KILLED, org.bukkit.event.entity.EntityRemoveEvent.Cause.DEATH); // Paper - add Bukkit remove cause
                // CraftBukkit end
            }

            this.onDestroyedBy(level, source);
        }

        return true;
    }

    @Override
    public void kill(final ServerLevel level) {
        this.onDestroyedBy(level, this.damageSources().generic());
        super.kill(level);
    }

    private void onDestroyedBy(final ServerLevel level, final DamageSource source) {
        EnderDragonFight fight = level.getDragonFight();
        if (fight != null) {
            fight.onCrystalDestroyed(this, source);
        }
    }

    public void setBeamTarget(final @Nullable BlockPos target) {
        this.getEntityData().set(DATA_BEAM_TARGET, Optional.ofNullable(target));
    }

    public @Nullable BlockPos getBeamTarget() {
        return this.getEntityData().get(DATA_BEAM_TARGET).orElse(null);
    }

    public void setShowBottom(final boolean showBottom) {
        this.getEntityData().set(DATA_SHOW_BOTTOM, showBottom);
    }

    public boolean showsBottom() {
        return this.getEntityData().get(DATA_SHOW_BOTTOM);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(final double distance) {
        return super.shouldRenderAtSqrDistance(distance) || this.getBeamTarget() != null;
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(Items.END_CRYSTAL);
    }
}
