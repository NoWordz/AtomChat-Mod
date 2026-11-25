package com.atom.chat.render;

import com.atom.chat.config.GraphicsQuality;
import io.github.humbleui.skija.BlendMode;
import io.github.humbleui.skija.Data;
import io.github.humbleui.skija.FilterTileMode;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.RuntimeEffect;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.skija.Shader;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.types.Rect;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Physical-pixel pass spacing shared by the panel and card backdrops. */
final class KawaseBlur {
    static final int PASSES = 5;

    private static final String CARD_SHADER = """
            uniform shader sampleImage;
            uniform float2 spacing;
            half4 main(float2 position) {
                half4 sum = sampleImage.eval(position);
                sum += sampleImage.eval(position + spacing);
                sum += sampleImage.eval(position - spacing);
                float2 opposite = float2(spacing.x, -spacing.y);
                sum += sampleImage.eval(position + opposite);
                sum += sampleImage.eval(position - opposite);
                return half4(sum.rgb * 0.2, 1.0);
            }
            """;
    private static RuntimeEffect cardEffect;

    private KawaseBlur() {}

    static float spacing(int pass, float strength) {
        return (2F + pass * 1.5F) * strength;
    }

    static int sampleDivisor(int ultraDivisor, float strength, GraphicsQuality quality) {
        // The resampling filter can dominate a weak blur. Keep its original resolution below 100%.
        return quality == GraphicsQuality.BALANCED && strength >= 1F ? ultraDivisor * 2 : ultraDivisor;
    }

    static int sampleSize(int deviceSize, int divisor) {
        return Math.max(1, (deviceSize + divisor - 1) / divisor);
    }

    static Image blur(Surface input, Surface temporary, Rect deviceBounds, float strength) {
        if (cardEffect == null) cardEffect = RuntimeEffect.makeForShader(CARD_SHADER);
        float xScale = input.getWidth() / deviceBounds.getWidth();
        float yScale = input.getHeight() / deviceBounds.getHeight();
        var bounds = Rect.makeWH(input.getWidth(), input.getHeight());
        var uniform = ByteBuffer.allocate(2 * Float.BYTES).order(ByteOrder.nativeOrder());
        try (var paint = new Paint().setBlendMode(BlendMode.SRC)) {
            for (int pass = 0; pass < PASSES; pass++) {
                float spacing = spacing(pass, strength);
                uniform.putFloat(0, spacing * xScale);
                uniform.putFloat(Float.BYTES, spacing * yScale);
                Surface from = pass % 2 == 0 ? input : temporary;
                Surface to = pass % 2 == 0 ? temporary : input;
                try (Image image = from.makeImageSnapshot();
                     Shader child = image.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, SamplingMode.LINEAR, null);
                     Data offsets = Data.makeFromBytes(uniform.array());
                     Shader shader = cardEffect.makeShader(offsets, new Shader[]{child})) {
                    // The input buffer's canvas starts out in world coordinates.
                    var canvas = to.getCanvas();
                    int save = canvas.save();
                    try {
                        canvas.resetMatrix();
                        canvas.drawRect(bounds, paint.setShader(shader));
                    } finally {
                        canvas.restoreToCount(save);
                    }
                    paint.setShader(null);
                }
            }
        }
        return (PASSES % 2 == 0 ? input : temporary).makeImageSnapshot();
    }

}
