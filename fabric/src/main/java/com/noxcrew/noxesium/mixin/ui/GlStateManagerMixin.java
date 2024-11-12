package com.noxcrew.noxesium.mixin.ui;

import com.mojang.blaze3d.platform.GlStateManager;
import com.noxcrew.noxesium.feature.ui.render.DynamicElement;
import com.noxcrew.noxesium.feature.ui.render.SharedVertexBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Overrides the blending state to enforce blending to be on for some part of the code.
 */
@Mixin(value = GlStateManager.class, remap = false)
public abstract class GlStateManagerMixin {

    @Inject(method = "_enableBlend", at = @At("HEAD"), cancellable = true)
    private static void preventnableBlend(CallbackInfo ci) {
        if (SharedVertexBuffer.allowBlendChanges) return;
        ci.cancel();
    }

    @Inject(method = "_disableBlend", at = @At("HEAD"), cancellable = true)
    private static void preventDisableBlend(CallbackInfo ci) {
        if (SharedVertexBuffer.allowBlendChanges) return;
        ci.cancel();
    }

    @Inject(method = "_blendFunc", at = @At("HEAD"), cancellable = true)
    private static void preventBlendFunc(CallbackInfo ci) {
        if (SharedVertexBuffer.allowBlendChanges) return;
        ci.cancel();
    }

    @Inject(method = "_blendFuncSeparate", at = @At("HEAD"), cancellable = true)
    private static void preventBlendFuncSeparate(CallbackInfo ci) {
        if (SharedVertexBuffer.allowBlendChanges) return;
        ci.cancel();
    }

    @Inject(method = "_glBindFramebuffer", at = @At("HEAD"), cancellable = true)
    private static void preventBindFrameBuffer(int i, int j, CallbackInfo ci) {
        if (SharedVertexBuffer.allowRebindingTarget) return;
        ci.cancel();
    }

    @Inject(method = "_viewport", at = @At("HEAD"), cancellable = true)
    private static void preventViewport(CallbackInfo ci) {
        if (SharedVertexBuffer.allowRebindingTarget) return;
        ci.cancel();
    }

    @Inject(method = "_drawElements", at = @At("HEAD"))
    private static void onDrawElements(CallbackInfo ci) {
        // Note that some elements were drawn!
        DynamicElement.elementsWereDrawn = true;
    }
}
