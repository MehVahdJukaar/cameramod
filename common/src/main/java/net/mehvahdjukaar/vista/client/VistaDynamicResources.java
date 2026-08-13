package net.mehvahdjukaar.vista.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.mehvahdjukaar.moonlight.api.events.AfterLanguageLoadEvent;
import net.mehvahdjukaar.moonlight.api.resources.ResType;
import net.mehvahdjukaar.moonlight.api.resources.pack.DynamicClientResourceProvider;
import net.mehvahdjukaar.moonlight.api.resources.pack.PackGenerationStrategy;
import net.mehvahdjukaar.moonlight.api.resources.pack.ResourceGenTask;
import net.mehvahdjukaar.moonlight.api.resources.pack.ResourceSink;
import net.mehvahdjukaar.vista.VistaMod;
import net.mehvahdjukaar.vista.configs.CommonConfigs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.DyeColor;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

public class VistaDynamicResources extends DynamicClientResourceProvider {

    public VistaDynamicResources() {
        super(VistaMod.res("color_shaders"), PackGenerationStrategy.REGEN_ON_EVERY_RELOAD); //leave like this since they are config dependant
    }

    @Override
    protected void regenerateDynamicAssets(Consumer<ResourceGenTask> consumer) {
        consumer.accept((resourceManager, resourceSink) -> {
            for (var c : DyeColor.values()) {
                int intValue = c.getTextColor(); // 0xRRGGBB
                float sr = ((intValue >> 16) & 0xFF) / 255f;
                float sg = ((intValue >> 8) & 0xFF) / 255f;
                float sb = (intValue & 0xFF) / 255f;

                // sRGB to linear
                float lr = sr <= 0.04045f ? sr / 12.92f : (float)Math.pow((sr + 0.055f) / 1.055f, 2.4);
                float lg = sg <= 0.04045f ? sg / 12.92f : (float)Math.pow((sg + 0.055f) / 1.055f, 2.4);
                float lb = sb <= 0.04045f ? sb / 12.92f : (float)Math.pow((sb + 0.055f) / 1.055f, 2.4);

                float lum = 0.2126f * lr + 0.7152f * lg + 0.0722f * lb;

                // HSV, for the saturation and hue driven tweaks further down
                float max = Math.max(sr, Math.max(sg, sb));
                float min = Math.min(sr, Math.min(sg, sb));
                float delta = max - min;
                float hue = 0f;
                if (delta > 0f) {
                    if (max == sr) hue = ((sg - sb) / delta) % 6f;
                    else if (max == sg) hue = ((sb - sr) / delta) + 2f;
                    else hue = ((sr - sg) / delta) + 4f;
                    hue *= 60f;
                    if (hue < 0) hue += 360f;
                }
                float sat = max == 0f ? 0f : delta / max; // 0..1
                float val = max;

                // everything below is hand tuned to stay subtle
                final float TINT_STRENGTH = 0.70f;

                // per-channel multiplier biasing toward the tint while preserving highlights
                float mulR = 1f - (1f - lr) * TINT_STRENGTH;
                float mulG = 1f - (1f - lg) * TINT_STRENGTH;
                float mulB = 1f - (1f - lb) * TINT_STRENGTH;

                // mild gamma lift, keeps low channels from crushing and midtones nicer
                final float MUL_SMOOTH = 0.98f;
                mulR = (float)Math.pow(mulR, MUL_SMOOTH);
                mulG = (float)Math.pow(mulG, MUL_SMOOTH);
                mulB = (float)Math.pow(mulB, MUL_SMOOTH);

                // Tiny shadow lift so darks pick up the tint instead of going flat black. Scales with
                // 1-luminance, and stays small enough not to read as emissive.
                float addBase = 0.008f;
                float addStrength = 0.045f;
                float addScale = addBase + addStrength * (1f - lum);

                float addR = sr * addScale * 0.9f + 0.001f;
                float addG = sg * addScale * 0.9f + 0.001f;
                float addB = sb * addScale * 0.9f + 0.001f;

                addR = Math.clamp(addR, 0f, 0.12f);
                addG = Math.clamp(addG, 0f, 0.12f);
                addB = Math.clamp(addB, 0f, 0.12f);

                // slightly desaturate by default, walking back up for already saturated dyes
                float saturation = 0.90f + 0.15f * sat;

                // dark dyes want a bit more contrast, light ones a bit less
                float contrast = 1.00f + 0.30f * (0.5f - lum);
                contrast = Math.clamp(contrast, 0.85f, 1.25f);

                if (hue >= 160f && hue <= 200f) { // cyan and teal read better punchier and less saturated
                    contrast = Math.min(contrast + 0.05f, 1.30f);
                    saturation -= 0.03f;
                }
                if (hue >= 30f && hue <= 60f) { // warm yellows and oranges want more red in the lift
                    addR += 0.005f;
                }

                JsonObject json = new JsonObject();
                JsonArray targets = new JsonArray();
                targets.add(new JsonPrimitive("swap"));
                json.add("targets", targets);

                JsonArray passes = new JsonArray();

                JsonObject pass = new JsonObject();
                pass.addProperty("name", "vista:color_grade");
                pass.addProperty("intarget", "minecraft:main");
                pass.addProperty("outtarget", "swap");

                JsonArray uniforms = new JsonArray();

                JsonObject uMul = new JsonObject();
                uMul.addProperty("name", "Mul");
                JsonArray mulArr = new JsonArray();
                mulArr.add(new JsonPrimitive(round4(mulR)));
                mulArr.add(new JsonPrimitive(round4(mulG)));
                mulArr.add(new JsonPrimitive(round4(mulB)));
                uMul.add("values", mulArr);
                uniforms.add(uMul);

                JsonObject uAdd = new JsonObject();
                uAdd.addProperty("name", "Add");
                JsonArray addArr = new JsonArray();
                addArr.add(new JsonPrimitive(round4(addR)));
                addArr.add(new JsonPrimitive(round4(addG)));
                addArr.add(new JsonPrimitive(round4(addB)));
                uAdd.add("values", addArr);
                uniforms.add(uAdd);

                JsonObject uContrast = new JsonObject();
                uContrast.addProperty("name", "Contrast");
                JsonArray contrastArr = new JsonArray();
                contrastArr.add(new JsonPrimitive(round4(contrast)));
                uContrast.add("values", contrastArr);
                uniforms.add(uContrast);

                JsonObject uSat = new JsonObject();
                uSat.addProperty("name", "Saturation");
                JsonArray satArr = new JsonArray();
                satArr.add(new JsonPrimitive(round4(saturation)));
                uSat.add("values", satArr);
                uniforms.add(uSat);

                pass.add("uniforms", uniforms);
                passes.add(pass);

                JsonObject blit = new JsonObject();
                blit.addProperty("name", "blit");
                blit.addProperty("intarget", "swap");
                blit.addProperty("outtarget", "minecraft:main");
                passes.add(blit);

                json.add("passes", passes);

                resourceSink.addJson(
                        VistaMod.res("shaders/post/" + c.getSerializedName() + "_tint.json"),
                        json,
                        ResType.GENERIC
                );
            }


        });
    }

    // keeps the generated JSON readable
    private static float round4(float v) {
        return Math.round(v * 10000f) / 10000f;
    }

    @Override
    protected Collection<String> gatherSupportedNamespaces() {
        return List.of();
    }

    @Override
    protected void addDynamicTranslations(AfterLanguageLoadEvent afterLanguageLoadEvent) {
    }
}
