package com.noxcrew.noxesium.mixin.debug;

import com.mojang.blaze3d.platform.InputConstants;
import com.noxcrew.noxesium.CompatibilityReferences;
import com.noxcrew.noxesium.NoxesiumConfig;
import net.minecraft.Util;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Objects;

/**
 * Adds various debug hotkeys for Noxesium.
 */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {

    @Shadow
    private long debugCrashKeyTime;

    @Shadow
    protected abstract void debugFeedbackTranslated(String string, Object... objects);

    @Redirect(method = "handleDebugKeys", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent;addMessage(Lnet/minecraft/network/chat/Component;)V"))
    public void redirect(ChatComponent instance, Component component) {
        if (component.getContents() instanceof TranslatableContents translatableContents) {
            if (translatableContents.getKey().equals("debug.pause.help")) {
                if (NoxesiumConfig.hasConfiguredPerformancePatches()) {
                    instance.addMessage(Component.translatable("debug.experimental_patches.help"));
                }
                if (!CompatibilityReferences.isUsingClothConfig()) {
                    instance.addMessage(Component.translatable("debug.fps_overlay.help"));
                }
            }
        }
        instance.addMessage(component);
    }

    @Inject(method = "handleDebugKeys", at = @At("TAIL"), cancellable = true)
    public void injected(int keyCode, CallbackInfoReturnable<Boolean> cir) {
        if (this.debugCrashKeyTime > 0L && this.debugCrashKeyTime < Util.getMillis() - 100L) {
            return;
        }

        if (keyCode == InputConstants.KEY_W && NoxesiumConfig.hasConfiguredPerformancePatches()) {
            cir.setReturnValue(true);

            if (Objects.equals(NoxesiumConfig.enableExperimentalPatches, false)) {
                NoxesiumConfig.enableExperimentalPatches = true;
                this.debugFeedbackTranslated("debug.experimental_patches.enabled");
            } else {
                NoxesiumConfig.enableExperimentalPatches = false;
                this.debugFeedbackTranslated("debug.experimental_patches.disabled");
            }
        }
        if (keyCode == InputConstants.KEY_Y && !CompatibilityReferences.isUsingClothConfig()) {
            cir.setReturnValue(true);

            if (NoxesiumConfig.fpsOverlay) {
                NoxesiumConfig.fpsOverlay = false;
                this.debugFeedbackTranslated("debug.fps_overlay.disabled");
            } else {
                NoxesiumConfig.fpsOverlay = true;
                this.debugFeedbackTranslated("debug.fps_overlay.enabled");
            }
        }
    }
}
