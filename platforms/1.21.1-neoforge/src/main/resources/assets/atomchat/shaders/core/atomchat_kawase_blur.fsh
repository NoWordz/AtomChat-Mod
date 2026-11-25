#version 150

uniform sampler2D Sampler0;
uniform vec2 u_Spacing;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 texel = 1.0 / vec2(textureSize(Sampler0, 0));
    vec2 offset = texel * u_Spacing;

    vec4 sum = texture(Sampler0, texCoord);
    sum += texture(Sampler0, texCoord + vec2( offset.x,  offset.y));
    sum += texture(Sampler0, texCoord + vec2(-offset.x,  offset.y));
    sum += texture(Sampler0, texCoord + vec2( offset.x, -offset.y));
    sum += texture(Sampler0, texCoord + vec2(-offset.x, -offset.y));

    fragColor = vec4(sum.rgb / 5.0, 1.0);
}
