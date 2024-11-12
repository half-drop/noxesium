package com.noxcrew.noxesium.feature.ui.render;

import com.noxcrew.noxesium.feature.ui.layer.NoxesiumLayeredDraw;
import com.noxcrew.noxesium.feature.ui.render.api.NoxesiumRenderState;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Stores the entire render state of the current UI.
 */
public class NoxesiumUiRenderState implements NoxesiumRenderState {

    private final List<ElementBufferGroup> groups = new CopyOnWriteArrayList<>();
    private int lastSize = 0;

    /**
     * Returns all groups in this render state.
     */
    public List<ElementBufferGroup> groups() {
        return groups;
    }

    /**
     * Renders the given layered draw object to the screen.
     */
    public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker, NoxesiumLayeredDraw layeredDraw) {
        var nanoTime = System.nanoTime();

        // Update which groups exist
        if (lastSize != layeredDraw.size()) {
            lastSize = layeredDraw.size();
            resetGroups();

            // Determine all layers ordered and flattened, then
            // split them up into
            var flattened = layeredDraw.flatten();

            // Start by splitting into 4 partitions
            var chunked = chunked(flattened, flattened.size() / 4);
            for (var chunk : chunked) {
                var group = new ElementBufferGroup();
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
            // Determine if the group has recently changed their
            // visibility state, if so request an immediate redraw!
            for (var layer : group.layers()) {
                if (layer.group() != null && layer.group().hasChangedRecently()) {
                    group.dynamic().redraw();
                    break;
                }
            }

            // Update the dynamic element of the group
            if (group.dynamic().update(nanoTime, guiGraphics, () -> {
                for (var layer : group.layers()) {
                    if (layer.group() == null || layer.group().test()) {
                        group.renderLayer(guiGraphics, deltaTracker, layer.layer());
                    }
                }
            })) {
                bound = true;
            }
        }
        if (bound) {
            SharedVertexBuffer.rebindMainRenderTarget();
        }

        // Draw the groups in order
        var ids = new ArrayList<Integer>();
        for (var group : groups) {
            // If the buffer is broken we have to early exit and draw
            // directly before going back to the buffers!
            if (group.dynamic().isInvalid()) {
                SharedVertexBuffer.draw(ids);
                ids.clear();
                group.drawDirectly(guiGraphics, deltaTracker);
                continue;
            }

            // If the buffer is valid we use it to draw
            var id = group.dynamic().getTextureId();
            if (id != -1) ids.add(id);
        }

        // Call draw on any remaining ids
        SharedVertexBuffer.draw(ids);
    }

    /**
     * Destroys all previous groups.
     */
    private void resetGroups() {
        for (var group : groups) {
            group.close();
        }
        groups.clear();
    }

    @Override
    public void tick() {
        for (var group : groups) {
            group.dynamic().tick();
        }
    }

    @Override
    public void close() {
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
