package net.mehvahdjukaar.vista.client.chunk_tracking;

import net.mehvahdjukaar.vista.VistaModClient;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class PinnedSections {

    private final SectionRenderDispatcher renderDispatcher;
    private final Level level;
    private final int sectionGridSizeY;
    private final int normalSectionCount;
    private final Map<Long, SectionRenderDispatcher.RenderSection> sectionsByPos = new HashMap<>();

    public PinnedSections(SectionRenderDispatcher dispatcher, Level level, int sectionGridSizeY, int normalSectionCount) {
        this.renderDispatcher = dispatcher;
        this.level = level;
        this.sectionGridSizeY = sectionGridSizeY;
        this.normalSectionCount = normalSectionCount;
    }

    public SectionRenderDispatcher.RenderSection[] appendTo(SectionRenderDispatcher.RenderSection[] sections) {
        sectionsByPos.clear();
        Set<ChunkPos> zoneChunks = VistaModClient.CLIENT_EXTRA_CHUNK_VIEW_DATA.getAllChunks();
        if (zoneChunks.isEmpty()) return sections;

        var sectionsWithPinned = Arrays.copyOf(sections, sections.length + zoneChunks.size() * sectionGridSizeY);
        int index = sections.length;
        for (ChunkPos chunkPos : zoneChunks) {
            for (int yIndex = 0; yIndex < sectionGridSizeY; yIndex++) {
                int blockY = level.getMinBuildHeight() + (yIndex * 16);
                var section = renderDispatcher.new RenderSection(index, chunkPos.getMinBlockX(), blockY, chunkPos.getMinBlockZ());
                ((IPinnableRenderSection) section).vista$setPinned(true);
                sectionsWithPinned[index] = section;
                sectionsByPos.put(SectionPos.asLong(chunkPos.x, SectionPos.blockToSectionCoord(blockY), chunkPos.z), section);
                index++;
            }
        }
        return sectionsWithPinned;
    }

    public SectionRenderDispatcher.RenderSection[] removeFrom(SectionRenderDispatcher.RenderSection[] sections) {
        for (int i = normalSectionCount; i < sections.length; i++) {
            if (sections[i] != null) sections[i].releaseBuffers();
        }
        return Arrays.copyOf(sections, normalSectionCount);
    }

    @Nullable
    public SectionRenderDispatcher.RenderSection get(int secX, int secY, int secZ) {
        return sectionsByPos.get(SectionPos.asLong(secX, secY, secZ));
    }
}
