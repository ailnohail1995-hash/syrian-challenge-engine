// محرّك صوتي كامل بالـ Web Audio API — لا يحتاج أي ملفات أو إنترنت
type Osc = OscillatorType;

class SoundEngine {
  private ctx: AudioContext | null = null;
  private master: GainNode | null = null;
  private musicGain: GainNode | null = null;
  private musicNodes: { stop: () => void } | null = null;
  muted = false;

  private ensure(): AudioContext | null {
    if (typeof window === "undefined") return null;
    if (!this.ctx) {
      const AC =
        window.AudioContext ||
        (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
      if (!AC) return null;
      this.ctx = new AC();
      this.master = this.ctx.createGain();
      this.master.gain.value = 0.9;
      this.master.connect(this.ctx.destination);
      this.musicGain = this.ctx.createGain();
      this.musicGain.gain.value = 0.0;
      this.musicGain.connect(this.master);
    }
    if (this.ctx.state === "suspended") void this.ctx.resume();
    return this.ctx;
  }

  unlock() {
    this.ensure();
  }

  setMuted(m: boolean) {
    this.muted = m;
    const ctx = this.ensure();
    if (ctx && this.master) {
      this.master.gain.cancelScheduledValues(ctx.currentTime);
      this.master.gain.linearRampToValueAtTime(m ? 0 : 0.9, ctx.currentTime + 0.15);
    }
  }

  private tone(
    freq: number,
    dur: number,
    opts: { type?: Osc; gain?: number; delay?: number; slideTo?: number; dest?: AudioNode } = {},
  ) {
    const ctx = this.ensure();
    if (!ctx || !this.master) return;
    const t0 = ctx.currentTime + (opts.delay ?? 0);
    const osc = ctx.createOscillator();
    const g = ctx.createGain();
    osc.type = opts.type ?? "sine";
    osc.frequency.setValueAtTime(freq, t0);
    if (opts.slideTo) osc.frequency.exponentialRampToValueAtTime(opts.slideTo, t0 + dur);
    const peak = opts.gain ?? 0.2;
    g.gain.setValueAtTime(0.0001, t0);
    g.gain.exponentialRampToValueAtTime(peak, t0 + 0.012);
    g.gain.exponentialRampToValueAtTime(0.0001, t0 + dur);
    osc.connect(g);
    g.connect(opts.dest ?? this.master);
    osc.start(t0);
    osc.stop(t0 + dur + 0.05);
  }

  private noise(dur: number, gain = 0.15, freq = 1200, q = 1) {
    const ctx = this.ensure();
    if (!ctx || !this.master) return;
    const len = Math.floor(ctx.sampleRate * dur);
    const buf = ctx.createBuffer(1, len, ctx.sampleRate);
    const d = buf.getChannelData(0);
    for (let i = 0; i < len; i++) d[i] = (Math.random() * 2 - 1) * (1 - i / len);
    const src = ctx.createBufferSource();
    src.buffer = buf;
    const filt = ctx.createBiquadFilter();
    filt.type = "bandpass";
    filt.frequency.value = freq;
    filt.Q.value = q;
    const g = ctx.createGain();
    g.gain.value = gain;
    src.connect(filt);
    filt.connect(g);
    g.connect(this.master);
    src.start();
  }

  hover() {
    this.tone(880, 0.06, { type: "triangle", gain: 0.05 });
  }

  click() {
    this.tone(520, 0.08, { type: "square", gain: 0.07 });
    this.tone(1040, 0.06, { type: "sine", gain: 0.04, delay: 0.02 });
  }

  select() {
    this.tone(392, 0.12, { type: "triangle", gain: 0.12, slideTo: 784 });
    this.noise(0.12, 0.05, 2400, 2);
  }

  whoosh() {
    this.noise(0.45, 0.12, 700, 0.7);
    this.tone(180, 0.4, { type: "sine", gain: 0.08, slideTo: 60 });
  }

  tick(urgent = false) {
    this.tone(urgent ? 1400 : 900, 0.05, { type: "square", gain: urgent ? 0.11 : 0.05 });
  }

  correct() {
    // آرپيج شرقي صاعد
    [523.25, 659.25, 783.99, 1046.5].forEach((f, i) =>
      this.tone(f, 0.5, { type: "triangle", gain: 0.16, delay: i * 0.075 }),
    );
    this.tone(1567.98, 0.7, { type: "sine", gain: 0.08, delay: 0.3 });
    this.noise(0.35, 0.05, 5200, 1.2);
  }

  wrong() {
    this.tone(196, 0.5, { type: "sawtooth", gain: 0.11, slideTo: 92 });
    this.tone(146.83, 0.55, { type: "square", gain: 0.06, delay: 0.04 });
    this.noise(0.25, 0.06, 320, 0.8);
  }

  timeout() {
    this.tone(300, 0.25, { type: "square", gain: 0.1, slideTo: 150 });
    this.tone(150, 0.5, { type: "sawtooth", gain: 0.08, delay: 0.2, slideTo: 70 });
  }

  streak(level: number) {
    const base = 523.25 * Math.pow(1.05946, Math.min(level, 8) * 2);
    [0, 4, 7, 12].forEach((s, i) =>
      this.tone(base * Math.pow(1.05946, s), 0.35, {
        type: "sine",
        gain: 0.12,
        delay: i * 0.05,
      }),
    );
  }

  legend() {
    // دخول قاعة التحدي — رهبة
    this.tone(65.41, 2.2, { type: "sawtooth", gain: 0.09 });
    this.tone(98, 2.0, { type: "sine", gain: 0.07, delay: 0.1 });
    this.noise(1.2, 0.07, 300, 0.5);
    [261.63, 311.13, 392, 466.16].forEach((f, i) =>
      this.tone(f, 1.4, { type: "triangle", gain: 0.07, delay: 0.35 + i * 0.13 }),
    );
  }

  countdown(n: number) {
    if (n > 0) this.tone(440 + (3 - n) * 110, 0.25, { type: "triangle", gain: 0.16 });
    else {
      this.tone(880, 0.6, { type: "triangle", gain: 0.2, slideTo: 1760 });
      this.noise(0.5, 0.12, 3000, 0.8);
    }
  }

  victory() {
    const mel = [523.25, 659.25, 783.99, 1046.5, 987.77, 1046.5, 1318.51];
    mel.forEach((f, i) =>
      this.tone(f, 0.8, { type: "triangle", gain: 0.15, delay: i * 0.13 }),
    );
    [130.81, 196, 261.63].forEach((f) => this.tone(f, 2.6, { type: "sine", gain: 0.08 }));
    this.noise(1.0, 0.05, 4200, 1.0);
  }

  clash() {
    this.tone(110, 0.8, { type: "sawtooth", gain: 0.12, slideTo: 440 });
    this.noise(0.6, 0.13, 900, 0.6);
    this.tone(1760, 0.3, { type: "square", gain: 0.06, delay: 0.35 });
  }

  // خلفية موسيقية شرقية هادئة (drone + حجاز)
  startMusic() {
    const ctx = this.ensure();
    if (!ctx || !this.musicGain || this.musicNodes) return;
    const g = this.musicGain;
    g.gain.cancelScheduledValues(ctx.currentTime);
    g.gain.linearRampToValueAtTime(0.16, ctx.currentTime + 2.5);

    const drone = ctx.createOscillator();
    drone.type = "sine";
    drone.frequency.value = 98;
    const dg = ctx.createGain();
    dg.gain.value = 0.35;
    drone.connect(dg);
    dg.connect(g);
    drone.start();

    const fifth = ctx.createOscillator();
    fifth.type = "triangle";
    fifth.frequency.value = 147;
    const fg = ctx.createGain();
    fg.gain.value = 0.12;
    fifth.connect(fg);
    fg.connect(g);
    fifth.start();

    // مقام حجاز
    const scale = [293.66, 311.13, 392, 415.3, 466.16, 523.25, 622.25];
    let i = 0;
    const timer = window.setInterval(() => {
      if (this.muted) return;
      const f = scale[Math.floor(Math.random() * scale.length)] ?? 392;
      this.tone(f, 2.4, { type: "sine", gain: 0.05, dest: g });
      i++;
      if (i % 4 === 0) this.tone(f / 2, 3.2, { type: "triangle", gain: 0.035, dest: g });
    }, 1900);

    this.musicNodes = {
      stop: () => {
        window.clearInterval(timer);
        try {
          drone.stop();
          fifth.stop();
        } catch {
          /* noop */
        }
      },
    };
  }

  stopMusic() {
    const ctx = this.ensure();
    if (this.musicGain && ctx) this.musicGain.gain.linearRampToValueAtTime(0, ctx.currentTime + 0.6);
    const n = this.musicNodes;
    this.musicNodes = null;
    if (n) window.setTimeout(() => n.stop(), 800);
  }
}

export const sfx = new SoundEngine();
