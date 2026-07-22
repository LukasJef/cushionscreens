package dev.atrixx.cushionscreens.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.Cushion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={Entity.class})
public class CushionRenderDistanceMixin {
    @Inject(method={"shouldRenderAtSqrDistance(D)Z"}, at={@At(value="HEAD")}, cancellable=true)
    private void cushionscreens$renderCushionsFar(double distanceSq, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Cushion) {
            cir.setReturnValue(true);
        }
    }
}
