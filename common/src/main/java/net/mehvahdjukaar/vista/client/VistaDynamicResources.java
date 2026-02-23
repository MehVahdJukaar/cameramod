package net.mehvahdjukaar.vista.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.mehvahdjukaar.moonlight.api.resources.ResType;
import net.mehvahdjukaar.moonlight.api.resources.pack.DynClientResourcesGenerator;
import net.mehvahdjukaar.moonlight.api.resources.pack.DynamicTexturePack;
import net.mehvahdjukaar.moonlight.api.resources.pack.ResourceGenTask;
import net.mehvahdjukaar.vista.VistaMod;
import net.minecraft.world.item.DyeColor;
import org.apache.logging.log4j.Logger;

import java.util.function.Consumer;

public class VistaDynamicResources extends DynClientResourcesGenerator {

    public VistaDynamicResources() {
        super(new DynamicTexturePack(VistaMod.res("color_shaders")));
    }


    @Override
    public Logger getLogger() {
        return VistaMod.LOGGER;
    }

    @Override
    public void regenerateDynamicAssets(Consumer<ResourceGenTask> consumer) {
        consumer.accept((resourceManager, resourceSink) -> {
            for (var c : DyeColor.values()) {
                int intValue = c.getTextColor(); // assumed 0xRRGGBB
                // unpack sRGB 0..1
                float sr = ((intValue >> 16) & 0xFF) / 255f;
                float sg = ((intValue >> 8) & 0xFF) / 255f;
                float sb = (intValue & 0xFF) / 255f;

                // sRGB -> linear (use for luminance and some calculations)
                float lr = sr <= 0.04045f ? sr / 12.92f : (float) Math.pow((sr + 0.055f) / 1.055f, 2.4);
                float lg = sg <= 0.04045f ? sg / 12.92f : (float) Math.pow((sg + 0.055f) / 1.055f, 2.4);
                float lb = sb <= 0.04045f ? sb / 12.92f : (float) Math.pow((sb + 0.055f) / 1.055f, 2.4);

                // perceptual luminance (linear)
                float lum = 0.2126f * lr + 0.7152f * lg + 0.0722f * lb;

                // HSV from sRGB (for saturation-driven choices)
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

                // ---------- algorithmic mapping (tweaks are intentionally conservative) ----------
                // Strength of the tint: how strongly the grade pushes toward the dye color
                final float TINT_STRENGTH = 0.70f; // 0..1, increase to make stronger grade

                // Mul: a per-channel multiplicative that biases toward the tint while preserving highlights.
                // Math: mul = 1 - (1 - linearTint) * strength
                float mulR = 1f - (1f - lr) * TINT_STRENGTH;
                float mulG = 1f - (1f - lg) * TINT_STRENGTH;
                float mulB = 1f - (1f - lb) * TINT_STRENGTH;

                // Slight nonlinear smoothing to avoid overly harsh low-channel crushing
                // apply a very mild gamma-like lift to mul (optional, keeps midtones nicer)
                final float MUL_SMOOTH = 0.98f;
                mulR = (float) Math.pow(mulR, MUL_SMOOTH);
                mulG = (float) Math.pow(mulG, MUL_SMOOTH);
                mulB = (float) Math.pow(mulB, MUL_SMOOTH);

                // Add: tiny per-channel lift to open shadows and give the grade a "film-like" subtle tint in darks.
                // The lift scales with (1 - luminance) so darker tints give slightly more shadow lift,
                // but we keep it tiny so it remains plausible and not emissive.
                float addBase = 0.008f;               // minimum lift
                float addStrength = 0.045f;           // max additional lift for very dark dyes
                float addScale = addBase + addStrength * (1f - lum); // scalar 0..~0.053

                // Bias the add toward the tint hue, but reduce it where tint is already bright
                float addR = sr * addScale * 0.9f + 0.001f;
                float addG = sg * addScale * 0.9f + 0.001f;
                float addB = sb * addScale * 0.9f + 0.001f;

                // ensure add stays tiny and non-negative
                addR = Math.max(0f, Math.min(0.12f, addR));
                addG = Math.max(0f, Math.min(0.12f, addG));
                addB = Math.max(0f, Math.min(0.12f, addB));

                // Saturation: base slightly desaturated by default, increase modestly with dye saturation
                // Sat range chosen to be visually subtle: [0.85 .. 1.05]
                float saturation = 0.90f + 0.15f * sat; // if tint is very saturated, restore some saturation

                // Contrast: small tweak based on tint luminance and hue.
                // Darker dyes usually look better with slightly increased contrast; very light dyes slightly reduce contrast.
                // range ~ [0.92 .. 1.18]
                float contrast = 1.00f + 0.30f * (0.5f - lum); // dark lum -> >1.0, bright lum -> <1.0
                contrast = Math.max(0.85f, Math.min(1.25f, contrast));

                // Optionally nudge parameters for specific hue families (subtle)
                // e.g., cyan/teal tends to look better with slightly more contrast and slightly muted saturation
                if (hue >= 160f && hue <= 200f) { // cyan/teal band
                    contrast = Math.min(contrast + 0.05f, 1.30f);
                    saturation -= 0.03f;
                }
                // warm yellows/oranges: slightly more add in reds
                if (hue >= 30f && hue <= 60f) {
                    addR += 0.005f;
                }

                // ---------- build JSON ----------

                JsonObject json = new JsonObject();
                JsonArray targets = new JsonArray();
                targets.add(new JsonPrimitive("swap"));
                json.add("targets", targets);

                JsonArray passes = new JsonArray();

                JsonObject pass = new JsonObject();
                pass.addProperty("name", "minecraft:vista_color_grade");
                pass.addProperty("intarget", "minecraft:main");
                pass.addProperty("outtarget", "swap");

                JsonArray uniforms = new JsonArray();

                // Mul uniform
                JsonObject uMul = new JsonObject();
                uMul.addProperty("name", "Mul");
                JsonArray mulArr = new JsonArray();
                mulArr.add(new JsonPrimitive(round4(mulR)));
                mulArr.add(new JsonPrimitive(round4(mulG)));
                mulArr.add(new JsonPrimitive(round4(mulB)));
                uMul.add("values", mulArr);
                uniforms.add(uMul);

                // Add uniform
                JsonObject uAdd = new JsonObject();
                uAdd.addProperty("name", "Add");
                JsonArray addArr = new JsonArray();
                addArr.add(new JsonPrimitive(round4(addR)));
                addArr.add(new JsonPrimitive(round4(addG)));
                addArr.add(new JsonPrimitive(round4(addB)));
                uAdd.add("values", addArr);
                uniforms.add(uAdd);

                // Contrast uniform (single value)
                JsonObject uContrast = new JsonObject();
                uContrast.addProperty("name", "Contrast");
                JsonArray contrastArr = new JsonArray();
                contrastArr.add(new JsonPrimitive(round4(contrast)));
                uContrast.add("values", contrastArr);
                uniforms.add(uContrast);

                // Saturation uniform (single value)
                JsonObject uSat = new JsonObject();
                uSat.addProperty("name", "Saturation");
                JsonArray satArr = new JsonArray();
                satArr.add(new JsonPrimitive(round4(saturation)));
                uSat.add("values", satArr);
                uniforms.add(uSat);

                pass.add("uniforms", uniforms);
                passes.add(pass);

                // blit pass
                JsonObject blit = new JsonObject();
                blit.addProperty("name", "blit");
                blit.addProperty("intarget", "swap");
                blit.addProperty("outtarget", "minecraft:main");
                passes.add(blit);

                json.add("passes", passes);

                // add to resource sink
                resourceSink.addJson(
                        VistaMod.res("shaders/post/" + c.getSerializedName() + "_tint.json"),
                        json,
                        ResType.GENERIC
                );
            }


        });
    }

    /**
     * helper: round to 4 decimal places for nicer JSON numbers
     */
    private static float round4(float v) {
        return Math.round(v * 10000f) / 10000f;
    }

}
