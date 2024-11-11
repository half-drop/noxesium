package com.noxcrew.noxesium.feature.ui.render;

import com.noxcrew.noxesium.NoxesiumMod;
import com.noxcrew.noxesium.feature.ui.layer.NoxesiumLayeredDraw;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Stores the entire render state of the current UI.
 */
public class NoxesiumUiRenderState implements Closeable {

    private final List<ElementBufferGroup> groups = new ArrayList<>();
    private final SharedVertexBuffer sharedBuffer = new SharedVertexBuffer();
    private int lastSize = 0;

    /**
     * Returns all groups in this render state.
     */
    public List<ElementBufferGroup> groups() {
        return groups;
    }

    /**
     * Ticks each group to process snapshots.
     */
    public void tick() {
        for (var group : groups) {
            group.tick();
        }
    }

    /**
     * Renders the given layered draw object to the screen.
     */
    public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker, NoxesiumLayeredDraw layeredDraw) {
        var nanoTime = System.nanoTime();

        // TODO Also apply optimizations to GameRenderer#this.minecraft.screen.renderWithTooltip!
        // TODO Merge together neighboring buffers that are on the same cycle

        // Update the vertex buffer
        sharedBuffer.create();

        // Update which groups exist
        if (lastSize != layeredDraw.size()) {
            try {
                resetGroups();
            } catch (IOException e) {
                NoxesiumMod.getInstance().getLogger().error("Failed to reset buffer groups", e);
            }

            // Determine all layers ordered and flattened, then
            // split them up into
            var flattened = layeredDraw.flatten();
            lastSize = flattened.size();

            // Start by splitting into 4 partitions
            var chunked = chunked(flattened, lastSize / 4);
            var random = new Random();
            for (var chunk : chunked) {
                var group = new ElementBufferGroup(random);
                group.addLayers(chunk);
                groups.add(group);
            }
        }

        // Update for each group what the condition is
        for (var group : layeredDraw.subgroups()) {
            group.update();
        }

        // Tick the groups, possibly redrawing the buffer contents, if any buffers got drawn to
        // we want to unbind the buffer afterwards
        var bound = false;
        for (var group : groups) {
            if (group.update(nanoTime, guiGraphics, deltaTracker)) {
                bound = true;
            }
        }
        if (bound) {
            BufferHelper.unbind();
        }

        // Draw the groups
        for (var group : groups) {
            var buffer = group.buffer();
            if (group.shouldUseBuffer()) {
                // If the buffer is valid we use it to draw
                buffer.draw(BufferHelper.prepare(sharedBuffer), sharedBuffer);
            } else {
                // If the buffer is invalid we draw directly
                BufferHelper.unprepare();
                group.drawDirectly(guiGraphics, deltaTracker);
            }
        }

        // Always break down the buffer state after we are done!
        BufferHelper.unprepare();
    }

    /**
     * Destroys all previous groups.
     */
    private void resetGroups() throws IOException {
        for (var group : groups) {
            group.close();
        }
        groups.clear();
    }

    @Override
    public void close() throws IOException {
        resetGroups();
    }

    /**
     * Returns a chunked version of the input list where each chunk is at most [amount] large.
     */
    private <T> List<List<T>> chunked(List<T> input, int amount) {
        var result = new ArrayList<List<T>>();
        var index = 0;
        var total = input.size();
        while (index < total) {
            var group = Math.min(amount, total - index);
            result.add(input.subList(index, index + group));
            index += group;
        }
        return result;
    }
}
