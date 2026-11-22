#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform vec4 u_Rect;
uniform float u_Radius;
uniform float u_FlipV;

in vec2 guiPos;
in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec2 uv = vec2(texCoord0.x, mix(texCoord0.y, 1.0 - texCoord0.y, u_FlipV));
    vec4 texColor = texture(Sampler0, uv);

    vec2 p = guiPos - u_Rect.xy;
    float radius = abs(u_Radius);
    vec2 q = abs(p) - u_Rect.zw + radius;
    vec2 edge = max(q, 0.0);
    float shapeAlpha;
    if (u_Radius < 0.0) {
        // Signed cubic field with the same corner reach as the vector contour.
        vec2 s = edge / radius;
        vec2 cubes = s * s * s;
        float f = cubes.x + cubes.y - 1.0;
        shapeAlpha = clamp(0.5 - f / max(fwidth(f), 0.00001), 0.0, 1.0);
    } else {
        float dist = length(edge) + min(max(q.x, q.y), 0.0) - radius;
        float aa = max(fwidth(dist), 0.00001);
        shapeAlpha = 1.0 - smoothstep(-aa, aa, dist);
    }

    // The framebuffer copy's alpha channel is not meaningful (MC's main target
    // is often alpha=0), so the output alpha must come from the panel fade and
    // the rounded mask only. RGB is the actual blurred world color.
    vec4 color = vec4(texColor.rgb * vertexColor.rgb * ColorModulator.rgb, 1.0);
    color.a = vertexColor.a * ColorModulator.a * shapeAlpha;
    if (color.a < 0.002) {
        discard;
    }
    fragColor = color;
}
