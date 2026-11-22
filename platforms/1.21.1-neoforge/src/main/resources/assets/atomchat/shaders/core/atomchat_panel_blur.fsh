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

// Coverage of the pixel square on the inside of the local contour tangent.
float tangentArea(float field, vec2 gradient) {
    float large = max(gradient.x, gradient.y);
    float small = min(gradient.x, gradient.y);
    float tail = max(0.0, 0.5 * (large + small) - abs(field));
    float amount = tail < small
        ? tail * tail / (2.0 * large * small)
        : (tail - 0.5 * small) / max(large, 0.000001);
    return field > 0.0 ? amount : 1.0 - amount;
}

void main() {
    vec2 derivative = vec2(dFdx(guiPos.x), dFdy(guiPos.y));
    vec2 pixel = max(abs(derivative), vec2(0.000001));
    vec2 p = guiPos - u_Rect.xy;
    // The quad extends one pixel beyond the mask. Clamp its UV only in that
    // extension, so the padded fragments never sample uncaptured texels.
    vec2 beyond = p - clamp(p, -u_Rect.zw, u_Rect.zw);
    vec2 texSlope = vec2(dFdx(texCoord0.x), dFdy(texCoord0.y)) * sign(derivative) / pixel;
    vec2 coord = texCoord0 - beyond * texSlope;
    vec2 uv = vec2(coord.x, mix(coord.y, 1.0 - coord.y, u_FlipV));
    vec4 texColor = texture(Sampler0, uv);

    float radius = abs(u_Radius);
    vec2 q = abs(p) - u_Rect.zw + radius;
    vec2 edge = max(q, 0.0);
    float shapeAlpha;
    if (radius == 0.0 || (u_Radius < 0.0 && min(q.x, q.y) <= 0.0)) {
        vec2 side = clamp(0.5 - (q - radius) / pixel, 0.0, 1.0);
        shapeAlpha = side.x * side.y;
    } else if (u_Radius < 0.0) {
        // Invert the same monotonic half-cubic as CornerGeometry and RoundedCoverage.
        const float cut = 0.224;
        const float diagonal = 1.0 - cut;
        const float first = 1.0 - 0.727;
        const float second = 1.0 - 2.0 * cut;
        const float a = 3.0 * first;
        const float b = 3.0 * (second - 2.0 * first);
        const float c = diagonal - a - b;
        vec2 u = q / radius;
        float lesser = min(u.x, u.y);
        float along = min(lesser, diagonal);
        float t = along / diagonal;
        for (int i = 0; i < 3; ++i) {
            float value = t * (a + t * (b + t * c));
            t -= (value - along) / (a + t * (2.0 * b + t * 3.0 * c));
        }
        float slope = 3.0 * cut * t * t / (a + t * (2.0 * b + t * 3.0 * c));
        float field = max(u.x, u.y) - (1.0 - cut * t * t * t) + (lesser - along) * slope;
        vec2 gradient = u.x < u.y ? vec2(slope, 1.0) : vec2(1.0, slope);
        shapeAlpha = tangentArea(field, gradient * pixel / radius);
    } else {
        float dist = length(edge) + min(max(q.x, q.y), 0.0) - radius;
        float len = length(edge);
        vec2 gradient = len > 0.0 ? edge / len : (q.x > q.y ? vec2(1.0, 0.0) : vec2(0.0, 1.0));
        shapeAlpha = tangentArea(dist, gradient * pixel);
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
