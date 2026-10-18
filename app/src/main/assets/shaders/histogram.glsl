#version 310 es
layout(local_size_x = 16, local_size_y = 16) in;

layout(binding = 0) uniform sampler2D inputTexture;

layout(std430, binding = 1) buffer HistogramBuffer {
    uint bins[256 * 4]; // R, G, B, Luminance
};

void main() {
    ivec2 texelPos = ivec2(gl_GlobalInvocationID.xy);
    vec3 color = texelFetch(inputTexture, texelPos, 0).rgb;

    // R, G, B bins
    atomicAdd(bins[uint(color.r * 255.0)], 1u);
    atomicAdd(bins[256u + uint(color.g * 255.0)], 1u);
    atomicAdd(bins[512u + uint(color.b * 255.0)], 1u);

    // Luminance bin (ITU-R BT.709)
    float lum = dot(color, vec3(0.2126, 0.7152, 0.0722));
    atomicAdd(bins[768u + uint(lum * 255.0)], 1u);
}