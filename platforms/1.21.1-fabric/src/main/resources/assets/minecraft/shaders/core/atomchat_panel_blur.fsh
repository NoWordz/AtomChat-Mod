#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform vec4 u_Rect;
uniform float u_Radius;
uniform float u_FlipV;
uniform float u_MaxLuma;

in vec2 guiPos;
in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec2 uv = vec2(texCoord0.x, mix(texCoord0.y, 1.0 - texCoord0.y, u_FlipV));
    vec4 texColor = texture(Sampler0, uv);

    vec2 p = guiPos - u_Rect.xy;
    vec2 q = abs(p) - u_Rect.zw + u_Radius;
    float dist = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - u_Radius;

    float aa = fwidth(dist);
    float shapeAlpha = 1.0 - smoothstep(-aa, aa, dist);

    // The framebuffer copy's alpha channel is not meaningful (MC's main target
    // is often alpha=0), so the output alpha must come from the panel fade and
    // the rounded mask only. RGB is the actual blurred world color.
    vec3 col = texColor.rgb * vertexColor.rgb * ColorModulator.rgb;
    // Luma ceiling (the DangoMusic glass adoption): a bright backdrop (snow,
    // desert noon) blurred to near-white drowns white panel text; scaling RGB
    // down to the ceiling keeps it readable. RGB only — the display path
    // treats framebuffer alpha as color, so the output alpha below stays
    // mask/fade-only. u_MaxLuma 1.0 (the json default) is an exact no-op for
    // a texture already clamped to [0,1].
    float luma = dot(col, vec3(0.2126, 0.7152, 0.0722));
    if (luma > u_MaxLuma) col *= u_MaxLuma / luma;
    vec4 color = vec4(col, 1.0);
    color.a = vertexColor.a * ColorModulator.a * shapeAlpha;
    if (color.a < 0.002) {
        discard;
    }
    fragColor = color;
}
