
package com.noxcrew.noxesium.feature.ui.render;

import com.mojang.blaze3d.buffers.BufferType;
import com.mojang.blaze3d.buffers.BufferUsage;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuFence;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.CompiledShaderProgram;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL30C;

import java.io.Closeable;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A wrapper around a RenderTarget used for capturing rendered UI elements
 * and re-using them.
 * <p>
 * Inspired by <a href="https://github.com/tr7zw/Exordium">Exordium</a>
 */
public class ElementBuffer implements Closeable {

    /**
     * We add an extra buffer that stores a PBO which we use to take
     * snapshots of the current frame.
     */
    private GpuBuffer pbo;
    private RenderTarget target;
    private GpuFence fence;
    private byte[] lastSnapshot = null;

    private final AtomicBoolean configuring = new AtomicBoolean(false);

    /**
     * Processes the contents of the PBO if it's available.
     */
    public Optional<Boolean> process() {
        if (pbo == null) return Optional.empty();
        if (fence == null) return Optional.empty();

        // Wait for actual data to be available
        if (fence.awaitCompletion(0L)) {
            var result = false;

            pbo.bind();

            // Bind the buffer and get its contents
            var byteBuffer = GlStateManager._glMapBufferRange(GL30.GL_PIXEL_PACK_BUFFER, 0, pbo.size, GL30C.GL_MAP_READ_BIT);
            if (byteBuffer != null) {
                var newSnapshot = new byte[byteBuffer.remaining()];
                byteBuffer.get(newSnapshot, 0, newSnapshot.length);

                // Unbind the buffer after we are done with it
                GlStateManager._glUnmapBuffer(GL30.GL_PIXEL_PACK_BUFFER);

                // Compare the two frames to determine if it is unchanged
                result = Arrays.equals(lastSnapshot, newSnapshot);
                lastSnapshot = newSnapshot;
            } else {
                GlStateManager._glUnmapBuffer(GL30.GL_PIXEL_PACK_BUFFER);
            }
            fence = null;
            return Optional.of(result);
        }
        return Optional.empty();
    }

    /**
     * Snapshots the current buffer contents to a PBO.
     */
    public void snapshot() {
        if (pbo == null) return;
        if (fence != null) return;

        // Read the contents of the buffer to the PBO
        var window = Minecraft.getInstance().getWindow();
        var width = window.getWidth();
        var height = window.getHeight();

        // Bind the PBO to tell the GPU to read the pixels into it
        pbo.bind();
        GL11.glReadPixels(
                0, 0,
                width, height,
                GL11.GL_RGBA,
                GL11.GL_UNSIGNED_BYTE,
                0
        );

        // Start waiting for the GPU to return the data
        fence = new GpuFence();
    }

    /**
     * Binds this buffer to the render target, replacing any previous target.
     */
    public boolean bind(GuiGraphics guiGraphics) {
        RenderSystem.assertOnRenderThread();

        // Flush the gui graphics to finish drawing to whatever it was on
        guiGraphics.flush();

        // Before binding we want to resize this buffer if necessary
        resize();

        // If the buffer has been properly created we can bind it
        // and clear it.
        if (isValid()) {
            // Bind the render target for writing
            target.bindWrite(true);

            // Clear the contents of the render target while keeping it bound
            GlStateManager._clearColor(0, 0, 0, 0);
            GlStateManager._clearDepth(1.0);
            GlStateManager._clear(16384 | 256);
            return true;
        }
        return false;
    }

    /**
     * Resizes this buffer to fit the game window.
     */
    private void resize() {
        var window = Minecraft.getInstance().getWindow();
        var width = window.getWidth();
        var height = window.getHeight();

        if (target == null || target.width != width || target.height != height) {
            if (configuring.compareAndSet(false, true)) {
                try {
                    // Create the PBO / re-size an existing buffer instance
                    if (pbo == null) {
                        pbo = new GpuBuffer(BufferType.PIXEL_PACK, BufferUsage.DYNAMIC_READ, 0);
                    }
                    pbo.resize(width * height * 4);

                    if (target == null) {
                        // This constructor internally runs resize! True indicates that we want
                        // a depth buffer to be created as well.
                        target = new TextureTarget(width, height, true);
                    } else {
                        target.resize(width, height);
                    }
                } finally {
                    configuring.set(false);
                }
            }
        }
    }

    /**
     * Draws this buffer directly and immediately.
     */
    protected void draw(CompiledShaderProgram shader, SharedVertexBuffer buffer) {
        buffer.draw(shader, target.getColorTextureId());
    }

    /**
     * Returns whether the buffer is valid and has been prepared.
     */
    public boolean isValid() {
        return target != null;
    }

    @Override
    public void close() {
        if (pbo != null) {
            pbo.close();
            pbo = null;
        }
        if (fence != null) {
            fence.close();
            fence = null;
        }
        if (target != null) {
            target.destroyBuffers();
            target = null;
        }
    }
}
