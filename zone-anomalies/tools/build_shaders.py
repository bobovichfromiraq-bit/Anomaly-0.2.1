#!/usr/bin/env python3
"""Генерирует шейдер искажения мира (assets/anomalies/shaders). N слотов аномалий — константа."""
import json
import os

N = 8
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "anomalies", "shaders")
os.makedirs(os.path.join(OUT, "post"), exist_ok=True)
os.makedirs(os.path.join(OUT, "program"), exist_ok=True)
names = ["X", "Y", "R", "S", "D", "T"]   # центр x,y (uv), радиус (uv по высоте), сила, дистанция (блоки), тип

uniforms = [
    {"name": "ProjMat", "type": "matrix4x4", "count": 16, "values": [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1]},
    {"name": "OutSize", "type": "float", "count": 2, "values": [1.0, 1.0]},
] + [{"name": n, "type": "float", "count": 1, "values": [v]} for n, v in (("Count", 0.0), ("Aspect", 1.0), ("Far", 256.0), ("Clock", 0.0))]
for i in range(N):
    for n in names:
        uniforms.append({"name": f"A{i}{n}", "type": "float", "count": 1, "values": [0.0]})

json.dump({"blend": {"func": "add", "srcrgb": "one", "dstrgb": "zero"},
           "vertex": "anomalies:distort", "fragment": "anomalies:distort", "attributes": ["Position"],
           "samplers": [{"name": "DiffuseSampler"}, {"name": "DiffuseDepthSampler"}],
           "uniforms": uniforms}, open(os.path.join(OUT, "program", "distort.json"), "w"), indent=2)
json.dump({"targets": ["swap"], "passes": [
    {"name": "anomalies:distort", "intarget": "minecraft:main", "outtarget": "swap",
     "auxtargets": [{"name": "DiffuseDepthSampler", "id": "minecraft:main:depth"}]},
    {"name": "blit", "intarget": "swap", "outtarget": "minecraft:main"}]},
    open(os.path.join(OUT, "post", "distortion.json"), "w"), indent=2)

open(os.path.join(OUT, "program", "distort.vsh"), "w").write("""#version 150

in vec4 Position;

uniform mat4 ProjMat;
uniform vec2 OutSize;

out vec2 texCoord;

void main() {
    vec4 outPos = ProjMat * vec4(Position.xy, 0.0, 1.0);
    gl_Position = vec4(outPos.xy, 0.2, 1.0);
    texCoord = Position.xy / OutSize;
}
""")

decl = "\n".join("uniform float " + ", ".join(f"A{i}{n}" for n in names) + ";" for i in range(N))
arr = lambda n: f"float {n}s[{N}] = float[{N}](" + ", ".join(f"A{i}{n}" for i in range(N)) + ");"
frag = f"""#version 150
// Zone Anomalies: world distortion (gravity swirl/lens, electric heat ripple), depth aware.

uniform sampler2D DiffuseSampler;
uniform sampler2D DiffuseDepthSampler;

uniform float Count, Aspect, Far, Clock;
{decl}

in vec2 texCoord;
out vec4 fragColor;

const float NEAR = 0.05;

float linearDepth(float z) {{
    float ndc = z * 2.0 - 1.0;
    return 2.0 * NEAR * Far / (Far + NEAR - ndc * (Far - NEAR));
}}

float hash(vec2 p) {{
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}}

void main() {{
    {arr("X")}
    {arr("Y")}
    {arr("R")}
    {arr("S")}
    {arr("D")}
    {arr("T")}

    float sceneDepth = linearDepth(texture(DiffuseDepthSampler, texCoord).r);
    vec2 total = vec2(0.0);
    vec3 glow = vec3(0.0);

    for (int i = 0; i < {N}; i++) {{
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
        if (T < 0.5) {{
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
        }} else {{
            // Electric: outgoing ripples, jitter, blue glow
            vec2 nd = d / max(len, 1.0e-4);
            float wave = sin(r * 38.0 - Clock * 14.0);
            off = nd * wave * 0.007 * S * f;
            off.x /= Aspect;
            float j = hash(floor(texCoord * vec2(90.0, 50.0)) + floor(Clock * 24.0));
            off += (vec2(j, hash(vec2(j, Clock))) - 0.5) * 0.005 * S * f;
            glow += vec3(0.20, 0.45, 1.0) * f * f * f * 0.40 * S * (0.75 + 0.25 * sin(Clock * 40.0));
        }}

        // do not pull in pixels that lie in front of the anomaly
        vec2 tuv = clamp(texCoord + off, 0.001, 0.999);
        if (linearDepth(texture(DiffuseDepthSampler, tuv).r) >= D - 0.6) {{
            total += off;
        }}
    }}

    // slight chromatic aberration on distorted areas
    vec2 uvR = clamp(texCoord + total * 1.18, 0.001, 0.999);
    vec2 uvG = clamp(texCoord + total, 0.001, 0.999);
    vec2 uvB = clamp(texCoord + total * 0.82, 0.001, 0.999);
    vec3 col = vec3(texture(DiffuseSampler, uvR).r, texture(DiffuseSampler, uvG).g, texture(DiffuseSampler, uvB).b);
    fragColor = vec4(col + glow, 1.0);
}}
"""
open(os.path.join(OUT, "program", "distort.fsh"), "w").write(frag)
print("shader ok, slots:", N)
