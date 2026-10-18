package net.rizkiepratama.hako.engine

/** The same detector is used for preview and encoder; only the enable uniforms differ. */
object MonitorAssistShader {
    val vertex = """
        attribute vec4 position;
        attribute vec4 inputTextureCoordinate;
        varying vec2 textureCoordinate;
        uniform mat4 textureTransform;
        void main() {
            gl_Position = position;
            textureCoordinate = (textureTransform * inputTextureCoordinate).xy;
        }
    """.trimIndent()

    // The 2D variant allows known signal fixtures to exercise the real shader in GPU tests.
    fun fragment(externalTexture: Boolean): String = """
        ${if (externalTexture) "#extension GL_OES_EGL_image_external : require" else ""}
        precision highp float;
        varying vec2 textureCoordinate;
        uniform ${if (externalTexture) "samplerExternalOES" else "sampler2D"} videoTexture;
        uniform vec2 texelSize;
        uniform int zebraEnabled;
        uniform int peakingEnabled;
        uniform float zebraThreshold;
        uniform float peakingThreshold;
        uniform vec3 peakingColor;

        float luminance(vec2 uv) {
            return dot(texture2D(videoTexture, uv).rgb, vec3(0.2126, 0.7152, 0.0722));
        }

        void main() {
            vec4 source = texture2D(videoTexture, textureCoordinate);
            vec3 result = source.rgb;
            float y = dot(source.rgb, vec3(0.2126, 0.7152, 0.0722));
            if (zebraEnabled == 1 && y >= zebraThreshold) {
                // Source-relative stripes keep their pattern consistent across both outputs.
                float stripe = step(0.5, fract(dot(textureCoordinate, vec2(320.0, 180.0)) / 6.0));
                result = mix(result, vec3(stripe), 0.65);
            }
            if (peakingEnabled == 1) {
                vec2 d = texelSize;
                float tl = luminance(textureCoordinate + vec2(-d.x, d.y));
                float t  = luminance(textureCoordinate + vec2(0.0, d.y));
                float tr = luminance(textureCoordinate + d);
                float l  = luminance(textureCoordinate + vec2(-d.x, 0.0));
                float r  = luminance(textureCoordinate + vec2(d.x, 0.0));
                float bl = luminance(textureCoordinate - d);
                float b  = luminance(textureCoordinate + vec2(0.0, -d.y));
                float br = luminance(textureCoordinate + vec2(d.x, -d.y));
                float gx = -tl - 2.0*l - bl + tr + 2.0*r + br;
                float gy = -bl - 2.0*b - br + tl + 2.0*t + tr;
                float strength = length(vec2(gx, gy)) * 0.25;
                float peak = smoothstep(peakingThreshold, peakingThreshold * 1.5, strength);
                result = mix(result, peakingColor, peak);
            }
            gl_FragColor = vec4(result, source.a);
        }
    """.trimIndent()
}
