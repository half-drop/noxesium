
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
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A wrapper around a RenderTarget used for capturing rendered UI elements
 * and re-using them.
 * <p>
 * Inspired by <a href="https://github.com/tr7zw/Exordium">Exordium</a>
 */
public class ElementBuffer implements Closeable {

    private int validPbos = 0;
    private GpuBuffer pbo;
    private byte[][] buffers;
    private RenderTarget target;
    private GpuFence fence;
    private ByteBuffer pboBuffer, recycledBuffer;

    private final AtomicBoolean configuring = new AtomicBoolean(false);

    /**
     * Returns whether the buffer is ready for a snapshot.
     */
    public boolean canSnapshot() {
        return fence == null && validPbos < 2;
    }

    /**
     * Awaits the contents of the PBO being available.
     */
    public void awaitFence() {
        // Wait for actual data to be available
        if (fence == null || pbo == null || buffers == null) return;
        if (fence.awaitCompletion(0L)) {
            // Re-bind the current PBO so it stays bound
            pbo.bind();

            // Bind the buffer and get its contents
            pboBuffer = GL30.glMapBufferRange(GL30.GL_PIXEL_PACK_BUFFER, 0, pbo.size, GL30C.GL_MAP_READ_BIT, pboBuffer);
            if (pboBuffer != null) {
                // Extract the contents and save them, re-use the same buffer because it's faster
                recycledBuffer.clear();
                recycledBuffer.put(pboBuffer);
                buffers[0] = buffers[1];
                buffers[1] = recycledBuffer.array();
            }

            // Unbind the buffer after we are done
            GlStateManager._glUnmapBuffer(GL30.GL_PIXEL_PACK_BUFFER);

            // Unbind the PBO so it doesn't get modified afterwards
            GlStateManager._glBindBuffer(GL30.GL_PIXEL_PACK_BUFFER, 0);

            // Mark down that we've taken an PBO and prevent taking
            // another snapshot for now.
            validPbos++;
            fence = null;
        }
    }

    /**
     * Snapshots the current buffer contents to a PBO.
     */
    public void snapshot() {
        if (fence != null || pbo == null) return;

        // Read the contents of the buffer to the PBO
        var window = Minecraft.getInstance().getWindow();
        var width = window.getWidth();
        var height = window.getHeight();

        // Bind the PBO to tell the GPU to read the pixels into it
        pbo.bind();
        GL11.glReadPixels(
                0, 0,
                width, height,
                GL30.GL_BGRA,
                GL11.GL_UNSIGNED_BYTE,
                0
        );

        // Unbind the PBO so it doesn't get modified afterwards
        GlStateManager._glBindBuffer(GL30.GL_PIXEL_PACK_BUFFER, 0);

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
        BufferHelper.allowRebindingTarget = true;
        resize();
        BufferHelper.allowRebindingTarget = false;

        // If the buffer has been properly created we can bind it
        // and clear it.
        if (isValid()) {
            // Bind the render target for writing
            BufferHelper.allowRebindingTarget = true;
            target.bindWrite(true);
            BufferHelper.allowRebindingTarget = false;

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

                    // Create a new empty buffers object
                    buffers = new byte[2][];

                    // Create a new re-usable byte buffer for extracting the PBOs
                    recycledBuffer = ByteBuffer.allocate(pbo.size);

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

    /**
     * Returns the snapshots that were taken.
     */
    public byte[][] snapshots() {
        return buffers;
    }

    /**
     * Marks down that a new PBO should be updated.
     */
    public void requestNewPBO() {
        if (buffers != null) {
            buffers[0] = null;
        }
        validPbos--;
    }

    @Override
    public void close() {
        recycledBuffer = null;

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
