package net.mehvahdjukaar.vista.mixins;

import net.mehvahdjukaar.vista.VistaModClient;
import net.mehvahdjukaar.vista.common.chunk_tracking.ILevelRendererExt;
import net.mehvahdjukaar.vista.common.chunk_tracking.IPinnableRenderSection;
import net.mehvahdjukaar.vista.common.chunk_tracking.IViewAreaExt;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Mixin(ViewArea.class)
public class ViewAreaMixin implements IViewAreaExt {

    @Final
    @Shadow protected Level level;
    @Shadow protected int sectionGridSizeY;
    @Shadow public SectionRenderDispatcher.RenderSection[] sections;

    // kept so new sections can be built outside the constructor
    @Unique private SectionRenderDispatcher vista$dispatcher;

    // where the pinned sections start, i.e. where the normal torus grid ends
    @Unique private int vista$normalSectionCount = -1;

    // The torus array is floorMod'd and can't be indexed by true coordinates, so dirtying a far zone
    // chunk resolves its section through here instead.
    @Unique
    private final Map<Long, SectionRenderDispatcher.RenderSection> vista$pinnedBySection = new HashMap<>();

    @Inject(method = "createSections", at = @At("HEAD"))
    private void vista$captureDispatcher(SectionRenderDispatcher dispatcher, CallbackInfo ci) {
        this.vista$dispatcher = dispatcher;
    }

    // Appends a Y-column of sections per registered extra chunk, past the end of the torus index
    // range so repositionCamera never moves them.
    @Inject(method = "createSections", at = @At("TAIL"))
    private void vista$appendPinnedSections(SectionRenderDispatcher dispatcher, CallbackInfo ci) {
        this.vista$normalSectionCount = this.sections.length;
        vista$buildPinnedSections(dispatcher);
    }

    // Swaps the pinned slots without touching compiled geometry elsewhere, so zone data changes don't
    // need a full allChanged().
    @Override
    public void vista$rebuildPinnedSections() {
        if (vista$dispatcher == null || vista$normalSectionCount < 0) return;

        for (int i = vista$normalSectionCount; i < this.sections.length; i++) {
            if (this.sections[i] != null) {
                this.sections[i].releaseBuffers();
            }
        }

        this.sections = Arrays.copyOf(this.sections, vista$normalSectionCount);
        vista$buildPinnedSections(vista$dispatcher);
    }

    @Override
    public void vista$setPinnedSectionDirty(int secX, int secY, int secZ, boolean reRenderOnMainThread) {
        SectionRenderDispatcher.RenderSection section =
                this.vista$pinnedBySection.get(SectionPos.asLong(secX, secY, secZ));
        if (section != null) {
            section.setDirty(reRenderOnMainThread);
        }
    }

    @Override
    public boolean vista$isPinnedSectionCompiled(int secX, int secY, int secZ) {
        SectionRenderDispatcher.RenderSection section =
                this.vista$pinnedBySection.get(SectionPos.asLong(secX, secY, secZ));
        return section != null
                && section.getCompiled() != SectionRenderDispatcher.CompiledSection.UNCOMPILED;
    }

    @Unique
    private void vista$buildPinnedSections(SectionRenderDispatcher dispatcher) {
        this.vista$pinnedBySection.clear();
        Set<ChunkPos> extraChunks = VistaModClient.CLIENT_EXTRA_CHUNK_VIEW_DATA.getAllChunks();
        if (extraChunks.isEmpty()) return;

        int base = this.sections.length;
        int extraSlots = extraChunks.size() * this.sectionGridSizeY;
        SectionRenderDispatcher.RenderSection[] extended = Arrays.copyOf(this.sections, base + extraSlots);

        int slotOffset = 0;
        for (ChunkPos chunkPos : extraChunks) {
            int blockX = chunkPos.getMinBlockX();
            int blockZ = chunkPos.getMinBlockZ();
            for (int yIndex = 0; yIndex < this.sectionGridSizeY; yIndex++) {
                int newIndex = base + slotOffset;
                int yOrigin = this.level.getMinBuildHeight() + yIndex * 16;
                SectionRenderDispatcher.RenderSection section = dispatcher.new RenderSection(newIndex, blockX, yOrigin, blockZ);
                extended[newIndex] = section;
                if (section instanceof IPinnableRenderSection ps) {
                    ps.vista$setPinned(true);
                }
                this.vista$pinnedBySection.put(
                        SectionPos.asLong(chunkPos.x, SectionPos.blockToSectionCoord(yOrigin), chunkPos.z),
                        section);
                slotOffset++;
            }
        }

        this.sections = extended;
    }


}
