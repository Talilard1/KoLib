"""KoLib's opening sound, made from scratch and timed to the animation (see Opening.kt)."""
import numpy as np
import wave
import sys

SR = 48000
LENGTH = 5.6
N = int(SR * LENGTH)
t = np.arange(N) / SR
rng = np.random.default_rng(11)

# Times from the animation.
SWIRL_START, SWIRL_END = 0.25, 2.3
DRAW_START, DRAW_END = 1.9, 2.7
FLASH = 2.8
SHIMMER_START, SHIMMER_END = 3.35, 4.0
ZOOM_START, END = 4.25, 5.0


def env(points):
    """A piecewise-linear envelope from (time, level) points."""
    ts, vs = zip(*points)
    return np.interp(t, ts, vs, left=vs[0], right=vs[-1])


def smooth(a, b, x):
    v = np.clip((x - a) / (b - a), 0, 1)
    return v * v * (3 - 2 * v)


def onepole_lp(x, cutoff):
    """One-pole low-pass; cutoff may change over time (an array)."""
    cutoff = np.broadcast_to(cutoff, x.shape)
    a = np.exp(-2 * np.pi * cutoff / SR)
    y = np.empty_like(x)
    acc = 0.0
    for i in range(len(x)):
        acc = (1 - a[i]) * x[i] + a[i] * acc
        y[i] = acc
    return y


def bandpass(x, centre, q):
    """A state-variable band-pass with a centre frequency that can sweep."""
    centre = np.broadcast_to(centre, x.shape)
    y = np.empty_like(x)
    low = band = 0.0
    damp = 1.0 / q
    for i in range(len(x)):
        f = 2 * np.sin(np.pi * min(centre[i], SR / 6) / SR)
        high = x[i] - low - damp * band
        band += f * high
        low += f * band
        y[i] = band
    return y


def pan(mono, p):
    """Equal-power pan, p from -1 (left) to 1 (right)."""
    p = np.broadcast_to(p, mono.shape)
    a = (p + 1) * np.pi / 4
    return np.stack([mono * np.cos(a), mono * np.sin(a)], axis=1)


out = np.zeros((N, 2))

# 1. A low rumble, swelling as the threads come in.
noise = rng.standard_normal(N)
rumble = onepole_lp(onepole_lp(noise, 140.0), 140.0) * 6.0
rumble += 0.5 * np.sin(2 * np.pi * np.cumsum(48 + 16 * smooth(SWIRL_START, SWIRL_END, t)) / SR)
rumble *= env([(0.15, 0), (0.8, 0.18), (1.8, 0.55), (2.4, 0.35), (3.0, 0.0)])
out += pan(rumble, 0.0)

# 2. The swirl: air rushing round, higher and faster as it closes in, going round the ears with the threads.
swirl_noise = onepole_lp(rng.standard_normal(N), 5000.0)
centre = 320 * (2600 / 320) ** smooth(0.5, 2.35, t)
swirl = bandpass(swirl_noise, centre, 2.2) * 1.5 * 0.22
swirl *= env([(0.45, 0), (1.1, 0.18), (2.05, 0.62), (2.3, 0.55), (2.62, 0.0)])
spin_rate = 0.4 + 2.6 * smooth(0.5, 2.25, t)
spin = np.sin(2 * np.pi * np.cumsum(spin_rate) / SR)
out += pan(swirl, 0.75 * spin)

# 3. Pages riffling in the swirl: quick soft ticks, faster and faster.
riffle = np.zeros(N)
rate_at = lambda x: 9 + 15 * smooth(1.0, 2.35, x)
x = 1.0
while x < 2.45:
    i = int(x * SR)
    burst = rng.standard_normal(int(0.014 * SR)) * np.hanning(int(0.014 * SR))
    riffle[i:i + len(burst)] += burst[: max(0, N - i)]
    x += 1.0 / rate_at(x) * (0.8 + 0.4 * rng.random())
riffle = bandpass(riffle, 2600.0, 1.2)
riffle *= env([(0.95, 0), (1.3, 0.07), (2.2, 0.12), (2.45, 0.0)]) * 0.6
out += pan(riffle, -0.6 * spin)

# 4. A rising hush into the flash.
rise_noise = rng.standard_normal(N)
rise = bandpass(rise_noise, 1200 * (4000 / 1200) ** smooth(FLASH - 0.5, FLASH, t), 1.5)
rise *= np.where(t < FLASH, np.clip((t - (FLASH - 0.5)) / 0.5, 0, 1) ** 3, 0) * 0.2
out += pan(rise, 0.0)

# 5. The light drawing the book: a faint glassy glide.
glide_f = 1320 * (1760 / 1320) ** smooth(DRAW_START, DRAW_END, t)
glide = np.sin(2 * np.pi * np.cumsum(glide_f + 6 * np.sin(2 * np.pi * 5.5 * t)) / SR)
glide *= env([(DRAW_START, 0), (DRAW_START + 0.15, 0.035), (DRAW_END, 0.05), (FLASH + 0.05, 0.0)])
out += pan(glide, 0.0)

# 6. The flash: a soft low hit...
k = t - FLASH
hit = np.where(k >= 0, np.sin(2 * np.pi * np.cumsum(np.where(k >= 0, 42 + 50 * np.exp(-k * 14), 0)) / SR) * np.exp(-np.maximum(k, 0) * 7.5), 0)
click_noise = rng.standard_normal(N)
click = onepole_lp(click_noise, 1800.0) * np.where((k >= 0) & (k < 0.03), np.exp(-np.maximum(k, 0) * 120), 0) * 2.5
out += pan(hit * 0.85 + click * 0.25, 0.0)

# ...and a warm bell chord, struck a note at a time.
def bell(freq, start, level, side, decay=2.6):
    k = t - start
    on = k >= 0
    kk = np.maximum(k, 0)
    attack = np.clip(kk / 0.004, 0, 1)
    tone = np.zeros(N)
    for ratio, amp, dec in [(1.0, 1.0, decay), (2.0, 0.42, decay * 0.55), (3.01, 0.16, decay * 0.35), (4.17, 0.1, decay * 0.22), (5.43, 0.06, decay * 0.15)]:
        for cents in (-1.6, 1.6):
            f = freq * ratio * 2 ** (cents / 1200)
            tone += amp * 0.5 * np.sin(2 * np.pi * f * kk) * np.exp(-kk / dec)
    tone *= attack * on * level
    return pan(tone, side)

chord = [(293.66, 0.000, 0.22, -0.35), (440.00, 0.014, 0.18, 0.3), (587.33, 0.026, 0.16, -0.15),
         (739.99, 0.040, 0.12, 0.45), (880.00, 0.052, 0.10, -0.5), (1318.51, 0.070, 0.06, 0.6)]
for f, d, lv, sd in chord:
    out += bell(f, FLASH + d, lv * 0.55, sd)

# 7. Sparkles after the flash, then a run up the scale as the shine crosses the book, left to right.
notes = [1174.66, 1318.51, 1479.98, 1760.00, 1975.53, 2349.32]
for _ in range(16):
    start = FLASH + 0.04 + rng.random() ** 1.8 * 1.3
    out += bell(rng.choice(notes), start, 0.01 + 0.009 * rng.random(), rng.uniform(-0.9, 0.9), decay=0.35 + 0.3 * rng.random())
for i, f in enumerate(notes):
    start = SHIMMER_START + 0.06 + i * (SHIMMER_END - SHIMMER_START - 0.12) / (len(notes) - 1)
    out += bell(f, start, 0.017, -0.8 + 1.6 * i / (len(notes) - 1), decay=0.55)

for _ in range(10):
    out += bell(rng.choice(notes), 1.0 + rng.random() * 1.2, 0.008, rng.uniform(-0.8, 0.8), decay=0.3)

# 7b. Chimes from the start (with their own random numbers, so everything after the flash is as it was).
rng2 = np.random.default_rng(29)
# A first twinkle as the light appears...
out += bell(1760.00, 0.10, 0.075, 0.3, decay=1.1)
out += bell(2349.32, 0.19, 0.05, -0.35, decay=0.9)
out += bell(1318.51, 0.27, 0.045, 0.1, decay=1.0)
# ...then glittering notes climbing with the swirl, faster, higher and brighter as it closes in, going
# round the ears with it, up to the top of the scale as the book is drawn.
scale = [587.33, 659.25, 739.99, 880.00, 987.77, 1174.66, 1318.51, 1479.98, 1760.00, 1975.53, 2349.32, 2637.02]
spin_at = lambda x: np.sin(2 * np.pi * np.sum(spin_rate[: int(x * SR)]) / SR)
run = [0, 2, 1, 3, 2, 4, 3, 5, 4, 6, 5, 7, 6, 8, 7, 9, 8, 10, 9, 10]
gaps = np.array([0.19 - 0.13 * (k / (len(run) - 1)) ** 0.8 for k in range(len(run) - 1)])
gaps *= (2.62 - 0.45) / gaps.sum()
x = 0.45
for k, i in enumerate(run):
    p = k / (len(run) - 1)
    level = (0.055 + 0.035 * p) * (0.85 + 0.3 * rng2.random())
    out += bell(scale[i], x, level, 0.7 * spin_at(x), decay=1.0 - 0.55 * p)
    if k < len(gaps):
        x += gaps[k] * (0.94 + 0.12 * rng2.random())

# 7c. Under the chimes, a soft glassy hum on the chord the flash strikes, swelling as the swirl gathers.
hum = np.zeros(N)
for f, lv in [(293.66, 0.45), (587.33, 1.0), (880.00, 0.6), (1174.66, 0.3)]:
    for cents in (-3.5, 3.5):
        hum += lv * np.sin(2 * np.pi * f * 2 ** (cents / 1200) * t + rng2.random() * 2 * np.pi)
hum *= 1 + 0.22 * np.sin(2 * np.pi * 3.1 * t)
hum *= env([(0.06, 0), (0.7, 0.009), (1.7, 0.012), (2.45, 0.015), (2.76, 0.0)])
out += pan(hum, 0.0)

# 8. A low warm pad under it all while the name shows.
pad = np.zeros(N)
for f in (146.83, 220.0, 293.66, 369.99):
    for cents in (-7, 0, 7):
        ff = f * 2 ** (cents / 1200)
        phase = (ff * t + rng.random()) % 1.0
        pad += (2 * phase - 1) / 3
pad = onepole_lp(onepole_lp(pad, 900.0), 900.0)
pad *= env([(FLASH, 0), (FLASH + 0.45, 0.07), (ZOOM_START - 0.2, 0.06), (ZOOM_START + 0.5, 0.0)])
out += np.stack([pad * 0.9, pad], axis=1)

# 9. Flying through into the app: a rising rush, landing with a soft low "whump".
zoom_noise = onepole_lp(rng.standard_normal(N), 7000.0)
zc = 260 * (3200 / 260) ** smooth(ZOOM_START, END - 0.05, t)
rush = bandpass(zoom_noise, zc, 1.6) * 1.3
rush *= np.where(t < END - 0.06, np.clip((t - ZOOM_START) / (END - 0.06 - ZOOM_START), 0, 1) ** 2.4, 0) * 0.55
rush += np.where(t >= END - 0.06, 1, 0) * 0.55 * np.exp(-np.maximum(t - (END - 0.06), 0) * 18) * bandpass(zoom_noise, 3200.0, 1.6) * 1.3
out += pan(rush, 0.0) * 1.0
k = t - (END - 0.06)
whump = np.where(k >= 0, np.sin(2 * np.pi * np.cumsum(np.where(k >= 0, 38 + 40 * np.exp(-k * 10), 0)) / SR) * np.exp(-np.maximum(k, 0) * 9), 0)
out += pan(whump * 0.5, 0.0)

# A room: a stereo reverb tail, darker as it fades.
ir_len = int(1.7 * SR)
it = np.arange(ir_len) / SR
ir = rng.standard_normal((ir_len, 2)) * np.exp(-it / 0.42)[:, None]
for ch in range(2):
    ir[:, ch] = onepole_lp(ir[:, ch], 4200.0 * np.exp(-it * 1.8) + 300.0)
ir /= np.sqrt((ir ** 2).sum(axis=0))
wet = np.zeros_like(out)
L = N + ir_len
nfft = 1 << (L - 1).bit_length()
for ch in range(2):
    wet[:, ch] = np.fft.irfft(np.fft.rfft(out[:, ch], nfft) * np.fft.rfft(ir[:, ch], nfft), nfft)[:N]
mix = out * 0.82 + wet * 0.38

# Gentle limiting, then a fade at the very end.
mix = np.tanh(mix * 1.15) / np.tanh(1.15)
mix *= env([(0, 1), (LENGTH - 0.45, 1), (LENGTH, 0)])[:, None]
# As loud after the flash as before the chimes from the start were added (that part was matched to the
# reference): measured from the flash to the shine's end.
active = mix[int(2.85 * SR):int(4.2 * SR)]
rms_db = 20 * np.log10(np.sqrt((active ** 2).mean()) + 1e-12)
gain_db = -22.75 - rms_db
mix *= 10 ** (gain_db / 20)
peak = np.abs(mix).max()
if peak > 0.89:
    mix *= 0.89 / peak
print(f"rms before {rms_db:.1f} dB, gain {gain_db:.1f} dB, peak {20*np.log10(np.abs(mix).max()):.1f} dBFS")

pcm = (np.clip(mix, -1, 1) * 32767).astype(np.int16)
with wave.open(sys.argv[1], "wb") as w:
    w.setnchannels(2)
    w.setsampwidth(2)
    w.setframerate(SR)
    w.writeframes(pcm.tobytes())
