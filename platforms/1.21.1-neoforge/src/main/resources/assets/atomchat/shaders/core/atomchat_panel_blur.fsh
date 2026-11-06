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
    vec2 q = abs(p) - u_Rect.zw + u_Radius;
    float dist = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - u_Radius;

    float aa = fwidth(dist);
    float shapeAlpha = 1.0 - smoothstep(-aa, aa, dist);

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
