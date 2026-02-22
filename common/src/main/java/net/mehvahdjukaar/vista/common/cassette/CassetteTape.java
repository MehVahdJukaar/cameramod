package net.mehvahdjukaar.vista.common.cassette;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.mehvahdjukaar.moonlight.api.fluids.SoftFluid;
import net.mehvahdjukaar.moonlight.api.util.math.ColorUtils;
import net.mehvahdjukaar.vista.VistaMod;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.RegistryFileCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.ExtraCodecs;

import java.util.Optional;

public record CassetteTape(ResourceLocation assetId, int color, Optional<Holder<SoundEvent>> soundEvent, Optional<Integer> soundDuration) {

    public static final ResourceKey<Registry<CassetteTape>> REGISTRY_KEY = ResourceKey.createRegistryKey(VistaMod.res("cassette_tape"));

    public static final Codec<CassetteTape> DIRECT_CODEC = RecordCodecBuilder.<CassetteTape>create((instance) -> instance.group(
            ResourceLocation.CODEC.fieldOf("asset_id").forGetter(CassetteTape::assetId),
            ColorUtils.CODEC.fieldOf("color").forGetter(CassetteTape::color),
            SoundEvent.CODEC.optionalFieldOf("sound").forGetter(CassetteTape::soundEvent),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("sound_duration").forGetter(CassetteTape::soundDuration)

    ).apply(instance, CassetteTape::new));


    public static final Codec<Holder<CassetteTape>> CODEC = RegistryFileCodec.create(REGISTRY_KEY, DIRECT_CODEC);
}
