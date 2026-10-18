#version 310 es
layout(local_size_x = 16, local_size_y = 16) in;

layout(binding = 0) uniform sampler2D inputTexture;
layout(rgba8, binding = 1) uniform writeonly image2D outputImage;

void main() {
    ivec2 texelPos = ivec2(gl_GlobalInvocationID.xy);
    vec3 color = texelFetch(inputTexture, texelPos, 0).rgb;

    // RGB to YUV (BT.709)
    float u = -0.09991 * color.r - 0.33609 * color.g + 0.436 * color.b;
    float v = 0.615 * color.r - 0.55861 * color.g - 0.05639 * color.b;

    // Map U, V to output image coordinates (e.g., 256x256)
    // U, V are in range approx [-0.5, 0.5]
    ivec2 outputPos = ivec2((u + 0.5) * 255.0, (v + 0.5) * 255.0);

    // Increment density (this is a simplified approach, usually requires atomic on a buffer)
    // For now, let's just mark the spot to verify the logic.
    imageStore(outputImage, outputPos, vec4(0.0, 1.0, 0.0, 0.1));
}