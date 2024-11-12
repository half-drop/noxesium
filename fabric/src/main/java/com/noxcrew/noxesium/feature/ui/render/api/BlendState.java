package com.noxcrew.noxesium.feature.ui.render.api;

import com.mojang.blaze3d.platform.GlStateManager;
import org.lwjgl.opengl.GL11;

/**
 * Holds a snapshot of the current blending state.
 */
public class BlendState {

    /**
     * Returns the initial blend state before UI rendering starts in vanilla.
     */
    public static BlendState initial() {
        var state = new BlendState();
        state.blend = false;
        state.srcRgb = GL11.GL_SRC_ALPHA;
        state.dstRgb = GL11.GL_ONE_MINUS_SRC_ALPHA;
        state.srcAlpha = GL11.GL_ONE;
        state.dstAlpha = GL11.GL_ZERO;
        return state;
    }

    /**
     * Returns the standard blend state when rendering UI elements.
     */
    public static BlendState standard() {
        var state = new BlendState();
        state.blend = true;
        state.srcRgb = GL11.GL_SRC_ALPHA;
        state.dstRgb = GL11.GL_ONE_MINUS_SRC_ALPHA;
        state.srcAlpha = GL11.GL_ONE;
        state.dstAlpha = GL11.GL_ONE_MINUS_SRC_ALPHA;
        return state;
    }

    /**
     * Takes a snapshot of the current blend state
     * intended by vanilla.
     */
    public static BlendState snapshot() {
        var state = new BlendState();
        state.blend = GlStateManager.BLEND.mode.enabled;
        state.srcRgb = GlStateManager.BLEND.srcRgb;
        state.dstRgb = GlStateManager.BLEND.dstRgb;
        state.srcAlpha = GlStateManager.BLEND.srcAlpha;
        state.dstAlpha = GlStateManager.BLEND.dstAlpha;
        return state;
    }

    private boolean blend;
    private int srcRgb, dstRgb, srcAlpha, dstAlpha;

    /**
     * Applies this blend state.
     */
    public void apply() {
        if (blend) {
            GlStateManager._enableBlend();
        } else {
            GlStateManager._disableBlend();
        }

        GlStateManager._blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
    }

    /**
     * Returns whether blending is enabled.
     */
    public boolean enabled() {
        return blend;
    }

    /**
     * Returns the srcRgb value.
     */
    public int srcRgb() {
        return srcRgb;
    }

    /**
     * Returns the dstRgb value.
     */
    public int dstRgb() {
        return dstRgb;
    }

    /**
     * Returns the srcAlpha value.
     */
    public int srcAlpha() {
        return srcAlpha;
    }

    /**
     * Returns the dstAlpha value.
     */
    public int dstAlpha() {
        return dstAlpha;
    }
}
