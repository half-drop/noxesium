#version 150

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform sampler2D Sampler3;
uniform sampler2D Sampler4;
uniform sampler2D Sampler5;
uniform sampler2D Sampler6;
uniform sampler2D Sampler7;
uniform int SamplerCount;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    if (SamplerCount == 1) {
        fragColor.rgb = texture(Sampler0, texCoord).rgb;
    } else if (SamplerCount == 2) {
        fragColor.rgb = (1.0/2.0) * (
            texture(Sampler0, texCoord).rgb +
            texture(Sampler1, texCoord).rgb
        );
    } else if (SamplerCount == 3) {
        fragColor.rgb = (1.0/3.0) * (
            texture(Sampler0, texCoord).rgb +
            texture(Sampler1, texCoord).rgb +
            texture(Sampler2, texCoord).rgb
        );
    } else if (SamplerCount == 4) {
        fragColor.rgb = (1.0/4.0) * (
            texture(Sampler0, texCoord).rgb +
            texture(Sampler1, texCoord).rgb +
            texture(Sampler2, texCoord).rgb +
            texture(Sampler3, texCoord).rgb
        );
    } else if (SamplerCount == 5) {
        fragColor.rgb = (1.0/5.0) * (
            texture(Sampler0, texCoord).rgb +
            texture(Sampler1, texCoord).rgb +
            texture(Sampler2, texCoord).rgb +
            texture(Sampler3, texCoord).rgb +
            texture(Sampler4, texCoord).rgb
        );
    } else if (SamplerCount == 6) {
        fragColor.rgb = (1.0/6.0) * (
            texture(Sampler0, texCoord).rgb +
            texture(Sampler1, texCoord).rgb +
            texture(Sampler2, texCoord).rgb +
            texture(Sampler3, texCoord).rgb +
            texture(Sampler4, texCoord).rgb +
            texture(Sampler5, texCoord).rgb
        );
    } else if (SamplerCount == 7) {
        fragColor.rgb = (1.0/7.0) * (
            texture(Sampler0, texCoord).rgb +
            texture(Sampler1, texCoord).rgb +
            texture(Sampler2, texCoord).rgb +
            texture(Sampler3, texCoord).rgb +
            texture(Sampler4, texCoord).rgb +
            texture(Sampler5, texCoord).rgb +
            texture(Sampler6, texCoord).rgb
        );
    } else if (SamplerCount == 8) {
        fragColor.rgb = (1.0/8.0) * (
            texture(Sampler0, texCoord).rgb +
            texture(Sampler1, texCoord).rgb +
            texture(Sampler2, texCoord).rgb +
            texture(Sampler3, texCoord).rgb +
            texture(Sampler4, texCoord).rgb +
            texture(Sampler4, texCoord).rgb +
            texture(Sampler6, texCoord).rgb +
            texture(Sampler7, texCoord).rgb
        );
    }
}
