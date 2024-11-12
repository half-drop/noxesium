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
        fragColor = texture(Sampler0, texCoord);
    } else if (SamplerCount == 2) {
        fragColor = (1.0/2.0) * (
            texture(Sampler0, texCoord) +
            texture(Sampler1, texCoord)
        );
    } else if (SamplerCount == 3) {
        fragColor = (1.0/3.0) * (
            texture(Sampler0, texCoord) +
            texture(Sampler1, texCoord) +
            texture(Sampler2, texCoord)
        );
    } else if (SamplerCount == 4) {
        fragColor = (1.0/4.0) * (
            texture(Sampler0, texCoord) +
            texture(Sampler1, texCoord) +
            texture(Sampler2, texCoord) +
            texture(Sampler3, texCoord)
        );
    } else if (SamplerCount == 5) {
        fragColor = (1.0/5.0) * (
            texture(Sampler0, texCoord) +
            texture(Sampler1, texCoord) +
            texture(Sampler2, texCoord) +
            texture(Sampler3, texCoord) +
            texture(Sampler4, texCoord)
        );
    } else if (SamplerCount == 6) {
        fragColor = (1.0/6.0) * (
            texture(Sampler0, texCoord) +
            texture(Sampler1, texCoord) +
            texture(Sampler2, texCoord) +
            texture(Sampler3, texCoord) +
            texture(Sampler4, texCoord) +
            texture(Sampler5, texCoord)
        );
    } else if (SamplerCount == 7) {
        fragColor = (1.0/7.0) * (
            texture(Sampler0, texCoord) +
            texture(Sampler1, texCoord) +
            texture(Sampler2, texCoord) +
            texture(Sampler3, texCoord) +
            texture(Sampler4, texCoord) +
            texture(Sampler5, texCoord) +
            texture(Sampler6, texCoord)
        );
    } else if (SamplerCount == 8) {
        fragColor = (1.0/8.0) * (
            texture(Sampler0, texCoord) +
            texture(Sampler1, texCoord) +
            texture(Sampler2, texCoord) +
            texture(Sampler3, texCoord) +
            texture(Sampler4, texCoord) +
            texture(Sampler4, texCoord) +
            texture(Sampler6, texCoord) +
            texture(Sampler7, texCoord)
        );
    }
}
