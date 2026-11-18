package com.carter.visuals.mixin;

import com.carter.visuals.Config;
import com.carter.visuals.SkyFx;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientWorld.class)
public abstract class ClientWorldMixin {
    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
    private void carter$sky(Vec3d cameraPos, float tickDelta, CallbackInfoReturnable<Vec3d> cir) {
        Config c = Config.get();
        if (!c.enabled || c.sky <= 0f) return;
        ClientWorld w = (ClientWorld) (Object) this;
        if (w.getRegistryKey() != World.OVERWORLD) return;
        cir.setReturnValue(SkyFx.apply(w, cir.getReturnValue(), tickDelta, c.sky));
    }
}
