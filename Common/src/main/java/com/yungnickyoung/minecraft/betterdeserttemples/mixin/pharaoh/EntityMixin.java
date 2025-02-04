package com.yungnickyoung.minecraft.betterdeserttemples.mixin.pharaoh;

import com.yungnickyoung.minecraft.betterdeserttemples.util.PharaohUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Update's a temple's "cleared" status when its pharaoh mob is discarded for any reason, such as despawning.
 * This status update is immediately stored to file, so that the temple remains "cleared" after a server restart.
 * For more information on this persistence, see {@link com.yungnickyoung.minecraft.betterdeserttemples.world.state.TempleStateCache}
 * and {@link com.yungnickyoung.minecraft.betterdeserttemples.world.state.TempleStateRegion}.
 */
@Mixin(Entity.class)
public abstract class EntityMixin {
    @Shadow
    private Level level;

    @Inject(method = "discard", at = @At("HEAD"))
    private void betterdeserttemples_clearTempleOnPharaohDiscard(CallbackInfo ci) {
        if (!(this.level instanceof ServerLevel serverLevel)) return;
        if (!PharaohUtil.isPharaoh(this)) return;

        PharaohUtil.onKillOrDiscardPharaoh((Entity) (Object) this, serverLevel, null);
    }
}
