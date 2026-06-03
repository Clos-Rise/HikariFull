package net.minecraft.world.entity.projectile.throwableitemprojectile;

import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

public class Snowball extends ThrowableItemProjectile {
    public Snowball(final EntityType<? extends Snowball> type, final Level level) {
        super(type, level);
    }

    public Snowball(final Level level, final LivingEntity mob, final ItemStack itemStack) {
        super(EntityType.SNOWBALL, mob, level, itemStack);
    }

    public Snowball(final Level level, final double x, final double y, final double z, final ItemStack itemStack) {
        super(EntityType.SNOWBALL, x, y, z, level, itemStack);
    }

    @Override
    public Item getDefaultItem() {
        return Items.SNOWBALL;
    }

    private ParticleOptions getParticle() {
        ItemStack item = this.getItem();
        return item.isEmpty() ? ParticleTypes.ITEM_SNOWBALL : new ItemParticleOption(ParticleTypes.ITEM, ItemStackTemplate.fromNonEmptyStack(item));
    }

    @Override
    public void handleEntityEvent(final byte id) {
        if (id == EntityEvent.DEATH) {
            ParticleOptions particle = this.getParticle();

            for (int i = 0; i < 8; i++) {
                this.level().addParticle(particle, this.getX(), this.getY(), this.getZ(), 0.0, 0.0, 0.0);
            }
        }
    }

    @Override
    protected void onHitEntity(final EntityHitResult hitResult) {
        super.onHitEntity(hitResult);
        Entity entity = hitResult.getEntity();
        int damage = entity.level().purpurConfig.snowballDamage >= 0 ? entity.level().purpurConfig.snowballDamage : entity instanceof Blaze ? 3 : 0; // Purpur - Add configurable snowball damage
        entity.hurt(this.damageSources().thrown(this, this.getOwner()), damage);
    }

    // Purpur start - options to extinguish fire blocks with snowballs - borrowed and modified code from ThrownPotion#onHitBlock and ThrownPotion#dowseFire
    @Override
    protected void onHitBlock(net.minecraft.world.phys.BlockHitResult blockHitResult) {
        super.onHitBlock(blockHitResult);

        if (!this.level().isClientSide()) {
            net.minecraft.core.BlockPos pos = blockHitResult.getBlockPos();
            net.minecraft.core.BlockPos relativePos = pos.relative(blockHitResult.getDirection());

            net.minecraft.world.level.block.state.BlockState blockState = this.level().getBlockState(pos);

            if (this.level().purpurConfig.snowballExtinguishesFire && this.level().getBlockState(relativePos).is(net.minecraft.world.level.block.Blocks.FIRE)) {
                if (org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(this, relativePos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState())) {
                    this.level().removeBlock(relativePos, false);
                }
            } else if (this.level().purpurConfig.snowballExtinguishesCandles && net.minecraft.world.level.block.AbstractCandleBlock.isLit(blockState)) {
                if (org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(this, pos, blockState.setValue(net.minecraft.world.level.block.AbstractCandleBlock.LIT, false))) {
                    net.minecraft.world.level.block.AbstractCandleBlock.extinguish(null, blockState, this.level(), pos);
                }
            } else if (this.level().purpurConfig.snowballExtinguishesCampfires && net.minecraft.world.level.block.CampfireBlock.isLitCampfire(blockState)) {
                if (org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(this, pos, blockState.setValue(net.minecraft.world.level.block.CampfireBlock.LIT, false))) {
                    this.level().levelEvent(null, 1009, pos, 0);
                    net.minecraft.world.level.block.CampfireBlock.dowse(this.getOwner(), this.level(), pos, blockState);
                    this.level().setBlockAndUpdate(pos, blockState.setValue(net.minecraft.world.level.block.CampfireBlock.LIT, false));
                }
            }
        }
    }
    // Purpur end - options to extinguish fire blocks with snowballs

    @Override
    protected void onHit(final HitResult hitResult) {
        super.onHit(hitResult);
        if (!this.level().isClientSide()) {
            this.level().broadcastEntityEvent(this, EntityEvent.DEATH);
            this.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.HIT); // CraftBukkit - add Bukkit remove cause
        }
    }
}
