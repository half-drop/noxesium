
package com.noxcrew.noxesium.feature.ui.render;

import com.google.common.base.Preconditions;
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
import org.lwjgl.opengl.GL44;

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

    private final boolean useDepth;
    private int currentIndex = 0;
    private int validPbos = 0;
    private boolean pboReady = false;
    private GpuBuffer[] pbos;
    private ByteBuffer[] buffers;
    private RenderTarget target;
    private GpuFence fence;

    private final AtomicBoolean configuring = new AtomicBoolean(false);

    public ElementBuffer(boolean useDepth) {
        this.useDepth = useDepth;
    }

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
        if (fence == null || pbos == null || buffers == null) return;
        if (fence.awaitCompletion(0L)) {
            // Mark down that we've taken an PBO and prevent taking
            // another snapshot for now and we're ready to have them
            // compared.
            validPbos++;
            fence = null;
            pboReady = true;
        }
    }

    /**
     * Snapshots the current buffer contents to a PBO.
     */
    public void snapshot() {
        if (fence != null || pbos == null) return;

        // Flip which buffer we are drawing into
        if (currentIndex == 1) currentIndex = 0;
        else currentIndex = 1;

        // Bind the PBO to tell the GPU to read the frame buffer's
        // texture into it directly
        pbos[currentIndex].bind();
        GL11.glGetTexImage(
                GL11.GL_TEXTURE_2D,
                0,
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
                    if (pbos == null) {
                        pbos = new GpuBuffer[2];
                    }
                    if (buffers == null) {
                        buffers = new ByteBuffer[2];
                    }
                    for (var i = 0; i < 2; i++) {
                        if (pbos[i] == null) {
                            pbos[i] = new GpuBuffer(BufferType.PIXEL_PACK, BufferUsage.STREAM_READ, 0);
                        }
                        pbos[i].resize(width * height * 4);

                        if (buffers[i] == null) {
                            // Configure the buffer to have a persistent size so we can keep it bound permanently
                            var flags = GL30C.GL_MAP_READ_BIT | GL44.GL_MAP_PERSISTENT_BIT | GL44.GL_MAP_COHERENT_BIT;
                            GL44.glBufferStorage(GL30.GL_PIXEL_PACK_BUFFER, pbos[i].size, flags);

                            // Create a persistent buffer to the PBOs contents
                            buffers[i] = Preconditions.checkNotNull(GL30.glMapBufferRange(GL30.GL_PIXEL_PACK_BUFFER, 0, pbos[i].size, flags));
                        }
                    }

                    if (target == null) {
                        // This constructor internally runs resize! True indicates that we want
                        // a depth buffer to be created as well.
                        target = new TextureTarget(width, height, useDepth);
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
    public void draw(CompiledShaderProgram shader) {
        SharedVertexBuffer.draw(shader, target.getColorTextureId());
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
    public ByteBuffer[] snapshots() {
        return pboReady ? buffers : null;
    }

    /**
     * Marks down that a new PBO should be updated.
     */
    public void requestNewPBO() {
        pboReady = false;
        validPbos--;
    }

    @Override
    public void close() {
        buffers = null;

        if (pbos != null) {
            for (var pbo : pbos) {
                pbo.close();
            }
            pbos = null;
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
