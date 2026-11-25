#version 150
// Zone Anomalies: world distortion (gravity swirl/lens, electric heat ripple), depth aware.

uniform sampler2D DiffuseSampler;
uniform sampler2D DiffuseDepthSampler;

uniform float Count, Aspect, Far, Clock;
uniform float A0X, A0Y, A0R, A0S, A0D, A0T;
uniform float A1X, A1Y, A1R, A1S, A1D, A1T;
uniform float A2X, A2Y, A2R, A2S, A2D, A2T;
uniform float A3X, A3Y, A3R, A3S, A3D, A3T;
uniform float A4X, A4Y, A4R, A4S, A4D, A4T;
uniform float A5X, A5Y, A5R, A5S, A5D, A5T;
uniform float A6X, A6Y, A6R, A6S, A6D, A6T;
uniform float A7X, A7Y, A7R, A7S, A7D, A7T;

in vec2 texCoord;
out vec4 fragColor;

const float NEAR = 0.05;

float linearDepth(float z) {
    float ndc = z * 2.0 - 1.0;
    return 2.0 * NEAR * Far / (Far + NEAR - ndc * (Far - NEAR));
}

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

void main() {
    float Xs[8] = float[8](A0X, A1X, A2X, A3X, A4X, A5X, A6X, A7X);
    float Ys[8] = float[8](A0Y, A1Y, A2Y, A3Y, A4Y, A5Y, A6Y, A7Y);
    float Rs[8] = float[8](A0R, A1R, A2R, A3R, A4R, A5R, A6R, A7R);
    float Ss[8] = float[8](A0S, A1S, A2S, A3S, A4S, A5S, A6S, A7S);
    float Ds[8] = float[8](A0D, A1D, A2D, A3D, A4D, A5D, A6D, A7D);
    float Ts[8] = float[8](A0T, A1T, A2T, A3T, A4T, A5T, A6T, A7T);

    float sceneDepth = linearDepth(texture(DiffuseDepthSampler, texCoord).r);
    vec2 total = vec2(0.0);
    vec3 glow = vec3(0.0);

    for (int i = 0; i < 8; i++) {
        if (float(i) >= Count) break;
        vec2 c = vec2(Xs[i], Ys[i]);
        float R = Rs[i], S = Ss[i], D = Ds[i], T = Ts[i];
        vec2 d = texCoord - c;
        d.x *= Aspect;
        float len = length(d);
        float r = len / R;
        if (r >= 1.0) continue;
        if (sceneDepth < D - 0.6) continue;            // pixel is in front of the anomaly: leave it untouched

        float f = 1.0 - r;
        f = f * f * (3.0 - 2.0 * f);
        vec2 off;
        if (T < 0.5) {
            // Gravity: swirl + pinch towards the centre + air shimmer + bright rim of the lens
            float ang = S * 1.7 * f * f;
            float cs = cos(ang), sn = sin(ang);
            vec2 rd = vec2(cs * d.x - sn * d.y, sn * d.x + cs * d.y);
            vec2 pd = rd * (1.0 - 0.38 * S * f);
            off = pd - d;
            off.x /= Aspect;
            off += vec2(sin(texCoord.y * 70.0 + Clock * 9.0), cos(texCoord.x * 60.0 + Clock * 7.0)) * 0.0022 * S * f;
            float rim = exp(-pow((r - 0.86) / 0.05, 2.0));
            glow += vec3(0.50, 0.70, 1.0) * rim * 0.16 * S;
        } else {
            // Electric: outgoing ripples, jitter, blue glow
            vec2 nd = d / max(len, 1.0e-4);
            float wave = sin(r * 38.0 - Clock * 14.0);
            off = nd * wave * 0.007 * S * f;
            off.x /= Aspect;
            float j = hash(floor(texCoord * vec2(90.0, 50.0)) + floor(Clock * 24.0));
            off += (vec2(j, hash(vec2(j, Clock))) - 0.5) * 0.005 * S * f;
            glow += vec3(0.20, 0.45, 1.0) * f * f * f * 0.40 * S * (0.75 + 0.25 * sin(Clock * 40.0));
        }

        // do not pull in pixels that lie in front of the anomaly
        vec2 tuv = clamp(texCoord + off, 0.001, 0.999);
        if (linearDepth(texture(DiffuseDepthSampler, tuv).r) >= D - 0.6) {
            total += off;
        }
    }

    // slight chromatic aberration on distorted areas
    vec2 uvR = clamp(texCoord + total * 1.18, 0.001, 0.999);
    vec2 uvG = clamp(texCoord + total, 0.001, 0.999);
    vec2 uvB = clamp(texCoord + total * 0.82, 0.001, 0.999);
    vec3 col = vec3(texture(DiffuseSampler, uvR).r, texture(DiffuseSampler, uvG).g, texture(DiffuseSampler, uvB).b);
    fragColor = vec4(col + glow, 1.0);
}
