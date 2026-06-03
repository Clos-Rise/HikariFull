package net.minecraft.world.entity.ai.goal;

import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import org.jspecify.annotations.Nullable;

public class SwellGoal extends Goal {
    private final Creeper creeper;
    private @Nullable LivingEntity target;

    public SwellGoal(final Creeper creeper) {
        this.creeper = creeper;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.creeper.getTarget();
        return this.creeper.getSwellDir() > 0 || target != null && !target.isDeadOrDying() && this.creeper.distanceToSqr(target) < 9.0;
    }


    @Override
    public void start() {
        this.creeper.getNavigation().stop();
        this.target = this.creeper.getTarget();
    }

    @Override
    public void stop() {
        this.target = null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (this.target != null && !this.target.isDeadOrDying()) {
            if (this.creeper.distanceToSqr(this.target) > 49.0 || !net.minecraft.world.entity.EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(this.target)) { // Paper - Fix MC-179072 - consider creative / spectator players as out of reach
                this.creeper.setSwellDir(-1);
            } else if (!this.creeper.getSensing().hasLineOfSight(this.target)) {
                this.creeper.setSwellDir(-1);
            } else {
                this.creeper.setSwellDir(1);
                // Purpur start - option to allow creeper to encircle target when fusing
                if (this.creeper.level().purpurConfig.creeperEncircleTarget) {
                    net.minecraft.world.phys.Vec3 relative = this.creeper.position().subtract(this.target.position());
                    relative = relative.yRot((float) Math.PI / 3).normalize().multiply(2, 2, 2);
                    net.minecraft.world.phys.Vec3 destination = this.target.position().add(relative);
                    this.creeper.getNavigation().moveTo(destination.x, destination.y, destination.z, 1);
                }
                // Purpur end - option to allow creeper to encircle target when fusing
            }
        } else {
            this.creeper.setSwellDir(-1);
        }
    }
}
