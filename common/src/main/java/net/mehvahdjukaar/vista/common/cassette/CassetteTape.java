package net.mehvahdjukaar.vista.common.cassette;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.mehvahdjukaar.moonlight.api.util.math.ColorUtils;
import net.mehvahdjukaar.vista.VistaMod;
import net.minecraft.core.Holder;
import net.minecraft.resources.RegistryFileCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.ExtraCodecs;

import java.util.Optional;

public record CassetteTape(ResourceLocation assetId, int color, Optional<Holder<SoundEvent>> soundEvent, Optional<Integer> soundDuration) {


    public static final Codec<CassetteTape> DIRECT_CODEC = RecordCodecBuilder.<CassetteTape>create((instance) -> instance.group(
                    ResourceLocation.CODEC.fieldOf("asset_id").forGetter(CassetteTape::assetId),
                    ColorUtils.CODEC.fieldOf("color").forGetter(CassetteTape::color),
                    SoundEvent.CODEC.optionalFieldOf("sound").forGetter(CassetteTape::soundEvent),
                    ExtraCodecs.POSITIVE_INT.optionalFieldOf("sound_duration").forGetter(CassetteTape::soundDuration)

            ).apply(instance, CassetteTape::new));


    public static final StreamCodec<RegistryFriendlyByteBuf, CassetteTape> DIRECT_STREAM_CODEC =
            StreamCodec.composite(ResourceLocation.STREAM_CODEC, CassetteTape::assetId,
                    ByteBufCodecs.VAR_INT, CassetteTape::color,
                    ByteBufCodecs.optional(SoundEvent.STREAM_CODEC), CassetteTape::soundEvent,
                    ByteBufCodecs.optional(ByteBufCodecs.VAR_INT), CassetteTape::soundDuration,
                    CassetteTape::new);


    public static final Codec<Holder<CassetteTape>> CODEC = RegistryFileCodec.create(VistaMod.CASSETTE_TAPE_REGISTRY_KEY, DIRECT_CODEC);

    public static final StreamCodec<RegistryFriendlyByteBuf, Holder<CassetteTape>> STREAM_CODEC = ByteBufCodecs.holder(
            VistaMod.CASSETTE_TAPE_REGISTRY_KEY, DIRECT_STREAM_CODEC);
}
