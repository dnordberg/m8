//! M8 Synth Engine — Rust native DSP for Android
//!
//! Emulates the Teensy 4.1 M8 Headless audio engine:
//! - 8 polyphonic voices with PolyBLEP oscillators
//! - 2-pole SVF filter per voice (stability-clamped)
//! - Exponential ADSR envelopes
//! - Stereo ping-pong delay with damped feedback
//! - Schroeder reverb (4 comb + 2 allpass)
//! - Clean gain staging with transparent limiter
//!
//! All DSP runs in native ARM — zero GC, zero allocations in the audio path.

use jni::JNIEnv;
use jni::objects::JClass;
use jni::sys::{jbyteArray, jdoubleArray};
use std::f64::consts::PI;
use std::sync::Mutex;

static ENGINE: Mutex<Option<SynthEngine>> = Mutex::new(None);

fn lock_engine() -> std::sync::MutexGuard<'static, Option<SynthEngine>> {
    match ENGINE.lock() {
        Ok(guard) => guard,
        Err(poisoned) => poisoned.into_inner(),
    }
}

pub const SR: f64 = 44100.0;
const TWO_PI: f64 = 2.0 * PI;
pub const CHUNK: usize = 735;
pub const CHUNK_BYTES: usize = CHUNK * 2 * 2; // stereo 16-bit
const NUM_VOICES: usize = 8;
const DELAY_LEN: usize = (SR * 0.375) as usize;
const NOTE_OFF: i32 = 0xFF;

// ===================== VOICE PRESET =====================

#[derive(Clone, Copy)]
pub struct Preset {
    wave: u8,       // 0=saw,1=pulse,2=sine,3=tri,4=noise,5=fm
    cutoff: f64,    // 0-1
    reso: f64,      // 0-1
    atk_ms: f64,
    dec_ms: f64,
    sus: f64,
    rel_ms: f64,
    filt_env: f64,  // filter envelope amount
    pw: f64,        // pulse width
    fm_ratio: f64,
    fm_idx: f64,
    dl_send: f64,   // delay send
    rv_send: f64,   // reverb send
    pan: f64,       // 0=left, 1=right
}

const PRESETS: [Preset; 8] = [
    // 0: Lead — saw, filter sweep
    Preset { wave: 0, cutoff: 0.62, reso: 0.2, atk_ms: 5.0, dec_ms: 100.0, sus: 0.7, rel_ms: 200.0,
             filt_env: 0.25, pw: 0.5, fm_ratio: 1.0, fm_idx: 0.0, dl_send: 0.15, rv_send: 0.2, pan: 0.4 },
    // 1: Bass — saw, punchy
    Preset { wave: 0, cutoff: 0.32, reso: 0.3, atk_ms: 2.0, dec_ms: 120.0, sus: 0.6, rel_ms: 100.0,
             filt_env: 0.4, pw: 0.5, fm_ratio: 1.0, fm_idx: 0.0, dl_send: 0.0, rv_send: 0.05, pan: 0.5 },
    // 2: Pad — triangle, slow
    Preset { wave: 3, cutoff: 0.72, reso: 0.05, atk_ms: 300.0, dec_ms: 400.0, sus: 0.8, rel_ms: 800.0,
             filt_env: 0.05, pw: 0.5, fm_ratio: 1.0, fm_idx: 0.0, dl_send: 0.1, rv_send: 0.45, pan: 0.55 },
    // 3: Hi-hat — noise, short
    Preset { wave: 4, cutoff: 0.82, reso: 0.1, atk_ms: 0.5, dec_ms: 40.0, sus: 0.0, rel_ms: 30.0,
             filt_env: 0.0, pw: 0.5, fm_ratio: 1.0, fm_idx: 0.0, dl_send: 0.0, rv_send: 0.1, pan: 0.45 },
    // 4: FM Bell
    Preset { wave: 5, cutoff: 0.9, reso: 0.0, atk_ms: 1.0, dec_ms: 800.0, sus: 0.15, rel_ms: 400.0,
             filt_env: 0.0, pw: 0.5, fm_ratio: 3.0, fm_idx: 2.0, dl_send: 0.2, rv_send: 0.35, pan: 0.6 },
    // 5: Pluck — pulse
    Preset { wave: 1, cutoff: 0.38, reso: 0.3, atk_ms: 1.0, dec_ms: 150.0, sus: 0.1, rel_ms: 120.0,
             filt_env: 0.45, pw: 0.4, fm_ratio: 1.0, fm_idx: 0.0, dl_send: 0.25, rv_send: 0.1, pan: 0.35 },
    // 6: Sub — sine
    Preset { wave: 2, cutoff: 0.95, reso: 0.0, atk_ms: 8.0, dec_ms: 50.0, sus: 0.9, rel_ms: 150.0,
             filt_env: 0.0, pw: 0.5, fm_ratio: 1.0, fm_idx: 0.0, dl_send: 0.0, rv_send: 0.0, pan: 0.5 },
    // 7: FX — FM aggressive
    Preset { wave: 5, cutoff: 0.48, reso: 0.35, atk_ms: 1.0, dec_ms: 200.0, sus: 0.0, rel_ms: 80.0,
             filt_env: 0.25, pw: 0.5, fm_ratio: 7.0, fm_idx: 4.0, dl_send: 0.15, rv_send: 0.25, pan: 0.65 },
];

// ===================== VOICE =====================

struct Voice {
    freq: f64,
    phase: f64,
    vol: f64,
    active: bool,
    note_on: bool,
    preset_idx: usize,

    // ADSR
    env_stage: u8, // 0=off,1=atk,2=dec,3=sus,4=rel
    env_level: f64,
    env_time: f64,
    rel_start: f64,

    // Filter envelope
    fenv_level: f64,
    fenv_time: f64,

    // ZDF SVF
    svf_ic1eq: f64,
    svf_ic2eq: f64,
    cutoff_smooth: f64,

    // FM
    fm_phase: f64,

    // Noise LFSR
    lfsr: u32,
    noise_val: f64,
    noise_cnt: u32,
}

impl Voice {
    fn new(idx: usize) -> Self {
        Voice {
            freq: 0.0, phase: 0.0, vol: 0.0,
            active: false, note_on: false, preset_idx: idx,
            env_stage: 0, env_level: 0.0, env_time: 0.0, rel_start: 0.0,
            fenv_level: 0.0, fenv_time: 0.0,
            svf_ic1eq: 0.0, svf_ic2eq: 0.0, cutoff_smooth: 0.0,
            fm_phase: 0.0,
            lfsr: 0x7FFF, noise_val: 0.0, noise_cnt: 0,
        }
    }

    fn trigger(&mut self, freq: f64, vol: f64) {
        self.freq = freq;
        self.vol = vol;
        self.env_stage = 1;
        self.env_time = 0.0;
        self.env_level = 0.0;
        self.fenv_level = 1.0;
        self.fenv_time = 0.0;
        self.note_on = true;
        self.active = true;
        self.svf_ic1eq = 0.0;
        self.svf_ic2eq = 0.0;
    }

    fn release(&mut self) {
        if self.env_stage >= 1 && self.env_stage <= 3 {
            self.rel_start = self.env_level;
            self.env_stage = 4;
            self.env_time = 0.0;
        }
        self.note_on = false;
    }

    #[inline(always)]
    fn generate(&mut self) -> f64 {
        if !self.active || self.env_stage == 0 { return 0.0; }

        let dt = 1.0 / SR;
        let p = &PRESETS[self.preset_idx];

        // --- ADSR ---
        match self.env_stage {
            1 => {
                self.env_time += dt;
                let a = p.atk_ms / 1000.0;
                self.env_level = if a < 0.001 { 1.0 }
                    else { 1.0 - (-self.env_time * 5.0 / a).exp() };
                if self.env_level >= 0.99 {
                    self.env_level = 1.0;
                    self.env_stage = 2;
                    self.env_time = 0.0;
                }
            }
            2 => {
                self.env_time += dt;
                let d = p.dec_ms / 1000.0;
                self.env_level = if d < 0.001 { p.sus }
                    else { p.sus + (1.0 - p.sus) * (-self.env_time * 5.0 / d).exp() };
                if self.env_level <= p.sus + 0.001 {
                    self.env_level = p.sus;
                    self.env_stage = 3;
                }
            }
            3 => { self.env_level = p.sus; }
            4 => {
                self.env_time += dt;
                let r = p.rel_ms / 1000.0;
                self.env_level = if r < 0.001 { 0.0 }
                    else { self.rel_start * (-self.env_time * 5.0 / r).exp() };
                if self.env_level < 0.0001 {
                    self.env_level = 0.0;
                    self.env_stage = 0;
                    self.active = false;
                    return 0.0;
                }
            }
            _ => {}
        }

        // Filter envelope
        self.fenv_time += dt;
        self.fenv_level = (-self.fenv_time * 6.0).exp().clamp(0.0, 1.0);

        let ph_inc = self.freq / SR;

        // --- Oscillator (PolyBLEP) ---
        let raw = match p.wave {
            0 => { // Saw
                let naive = 2.0 * self.phase - 1.0;
                naive - poly_blep(self.phase, ph_inc)
            }
            1 => { // Pulse
                let naive = if self.phase < p.pw { 1.0 } else { -1.0 };
                naive + poly_blep(self.phase, ph_inc)
                      - poly_blep((self.phase - p.pw + 1.0) % 1.0, ph_inc)
            }
            2 => (self.phase * TWO_PI).sin(), // Sine
            3 => {
                let naive = 4.0 * (self.phase - 0.5).abs() - 1.0;
                let slope = if self.phase < 0.5 { 4.0 } else { -4.0 };
                naive + slope * poly_blamp(self.phase, ph_inc)
                      + (-slope) * poly_blamp((self.phase + 0.5) % 1.0, ph_inc)
            }
            4 => { // Noise — full-rate LFSR through a one-pole LP (no stepping)
                let bit = (self.lfsr ^ (self.lfsr >> 1)) & 1;
                self.lfsr = (self.lfsr >> 1) | (bit << 14);
                let white = self.lfsr as f64 / 16384.0 - 1.0;
                // LP coefficient tracks pitch: higher notes → brighter hat
                let lp_a = (self.freq / SR * 6.0).clamp(0.02, 0.5);
                self.noise_val += lp_a * (white - self.noise_val);
                self.noise_val
            }
            5 => { // FM — anti-aliased by shrinking index as mod freq approaches Nyquist
                self.fm_phase += self.freq * p.fm_ratio / SR;
                if self.fm_phase > 1e6 { self.fm_phase -= self.fm_phase.floor(); }
                // Bandwidth ≈ 2*(idx+1)*mod_freq; keep it under 0.8*Nyquist
                let mod_f = self.freq * p.fm_ratio;
                let max_idx = ((SR * 0.4) / mod_f.max(1.0) - 1.0).max(0.0);
                let safe_idx = p.fm_idx.min(max_idx);
                let modulator = (self.fm_phase * TWO_PI).sin() * safe_idx * self.env_level;
                ((self.phase + modulator) * TWO_PI).sin()
            }
            _ => 0.0,
        };

        // Advance phase
        self.phase += ph_inc;
        if self.phase >= 1.0 { self.phase -= self.phase.floor(); }

        // --- ZDF SVF (Zavalishin topology, tanh saturation on integrators) ---
        let cut_target = (p.cutoff + p.filt_env * self.fenv_level).clamp(0.0, 1.0);
        self.cutoff_smooth += 0.005 * (cut_target - self.cutoff_smooth);
        let cut_hz = 20.0 * (2.0_f64).powf(self.cutoff_smooth * 10.0);
        let g = (PI * (cut_hz / SR).clamp(0.0, 0.49)).tan();
        let k = 2.0 * (1.0 - p.reso * 0.95).max(0.05);

        let a1 = 1.0 / (1.0 + g * (g + k));
        let a2 = g * a1;
        let a3 = g * a2;

        let v3 = raw - self.svf_ic2eq;
        let v1 = a1 * self.svf_ic1eq + a2 * v3;
        let v2 = self.svf_ic2eq + a2 * self.svf_ic1eq + a3 * v3;

        self.svf_ic1eq = 2.0 * tanh_cheap(v1) - self.svf_ic1eq;
        self.svf_ic2eq = 2.0 * tanh_cheap(v2) - self.svf_ic2eq;
        self.svf_ic1eq = flush_denorm(self.svf_ic1eq);
        self.svf_ic2eq = flush_denorm(self.svf_ic2eq);

        v2 * self.env_level * self.vol
    }
}

// ===================== EFFECTS =====================

struct Delay {
    buf_l: Vec<f64>,
    buf_r: Vec<f64>,
    pos: usize,
    damp_l: f64,
    damp_r: f64,
}

impl Delay {
    fn new() -> Self {
        Delay {
            buf_l: vec![0.0; DELAY_LEN],
            buf_r: vec![0.0; (SR * 0.5) as usize],
            pos: 0, damp_l: 0.0, damp_r: 0.0,
        }
    }

    #[inline(always)]
    fn process(&mut self, in_l: f64, in_r: f64) -> (f64, f64) {
        let pos_l = self.pos % self.buf_l.len();
        let pos_r = self.pos % self.buf_r.len();
        let tap_l = self.buf_l[pos_l];
        let tap_r = self.buf_r[pos_r];
        self.damp_l += 0.35 * (tap_r - self.damp_l);
        self.damp_r += 0.35 * (tap_l - self.damp_r);
        self.buf_l[pos_l] = flush_denorm(in_l + tanh_cheap(self.damp_l * 0.4));
        self.buf_r[pos_r] = flush_denorm(in_r + tanh_cheap(self.damp_r * 0.4));
        self.pos += 1;
        (tap_l, tap_r)
    }

    fn clear(&mut self) {
        self.buf_l.fill(0.0);
        self.buf_r.fill(0.0);
        self.damp_l = 0.0;
        self.damp_r = 0.0;
        self.pos = 0;
    }
}

struct DattorroAllpass {
    buf: Vec<f64>,
    pos: usize,
}

impl DattorroAllpass {
    fn new(len: usize) -> Self {
        DattorroAllpass { buf: vec![0.0; len], pos: 0 }
    }

    #[inline(always)]
    fn process(&mut self, input: f64, coeff: f64) -> f64 {
        let idx = self.pos % self.buf.len();
        let delayed = self.buf[idx];
        let node = input - coeff * delayed;
        self.buf[idx] = flush_denorm(node);
        self.pos += 1;
        delayed + coeff * node
    }

    fn clear(&mut self) { self.buf.fill(0.0); self.pos = 0; }
}

struct DattorroDelay {
    buf: Vec<f64>,
    pos: usize,
}

impl DattorroDelay {
    fn new(len: usize) -> Self {
        DattorroDelay { buf: vec![0.0; len], pos: 0 }
    }

    #[inline(always)]
    fn read(&self) -> f64 {
        self.buf[self.pos % self.buf.len()]
    }

    #[inline(always)]
    fn write_advance(&mut self, val: f64) {
        let idx = self.pos % self.buf.len();
        self.buf[idx] = flush_denorm(val);
        self.pos += 1;
    }

    fn clear(&mut self) { self.buf.fill(0.0); self.pos = 0; }
}

struct Reverb {
    pre_ap: [DattorroAllpass; 4],
    tank_ap_l: DattorroAllpass,
    tank_ap_r: DattorroAllpass,
    tank_dl_l: DattorroDelay,
    tank_dl_r: DattorroDelay,
    damp_l: f64,
    damp_r: f64,
    decay: f64,
}

impl Reverb {
    fn new() -> Self {
        Reverb {
            pre_ap: [
                DattorroAllpass::new(142), DattorroAllpass::new(107),
                DattorroAllpass::new(379), DattorroAllpass::new(277),
            ],
            tank_ap_l: DattorroAllpass::new(672),
            tank_ap_r: DattorroAllpass::new(908),
            tank_dl_l: DattorroDelay::new(4453),
            tank_dl_r: DattorroDelay::new(4217),
            damp_l: 0.0,
            damp_r: 0.0,
            decay: 0.75,
        }
    }

    #[inline(always)]
    fn process(&mut self, input: f64) -> f64 {
        let mut x = input;
        for ap in &mut self.pre_ap {
            x = ap.process(x, 0.5);
        }

        let tank_r_out = self.tank_dl_r.read();
        let left_in = x + tank_r_out * self.decay;
        let left_ap = self.tank_ap_l.process(left_in, -0.5);
        self.damp_l += 0.4 * (left_ap - self.damp_l);
        self.tank_dl_l.write_advance(self.damp_l * self.decay);

        let tank_l_out = self.tank_dl_l.read();
        let right_in = x + tank_l_out * self.decay;
        let right_ap = self.tank_ap_r.process(right_in, -0.5);
        self.damp_r += 0.4 * (right_ap - self.damp_r);
        self.tank_dl_r.write_advance(self.damp_r * self.decay);

        (tank_l_out + tank_r_out) * 0.5
    }

    fn clear(&mut self) {
        for ap in &mut self.pre_ap { ap.clear(); }
        self.tank_ap_l.clear();
        self.tank_ap_r.clear();
        self.tank_dl_l.clear();
        self.tank_dl_r.clear();
        self.damp_l = 0.0;
        self.damp_r = 0.0;
    }
}

// ===================== DC BLOCKER =====================

/// First-order DC blocking filter: y[n] = x[n] - x[n-1] + R * y[n-1]
/// R = 0.995 gives a ~35 Hz cutoff at 44.1 kHz — removes DC and sub-bass
/// rumble from reverb/delay recirculation without affecting audible content.
struct DcBlocker {
    x_prev: f64,
    y_prev: f64,
}

impl DcBlocker {
    fn new() -> Self { DcBlocker { x_prev: 0.0, y_prev: 0.0 } }

    #[inline(always)]
    fn process(&mut self, x: f64) -> f64 {
        let y = x - self.x_prev + 0.995 * self.y_prev;
        self.x_prev = x;
        self.y_prev = y;
        y
    }

    fn clear(&mut self) { self.x_prev = 0.0; self.y_prev = 0.0; }
}

// ===================== ENGINE =====================

pub struct SynthEngine {
    voices: Vec<Voice>,
    delay: Delay,
    reverb: Reverb,
    dc_l: DcBlocker,
    dc_r: DcBlocker,
    out_buf: Vec<u8>,
    pub track_levels: [f64; 8],
    pub master_l: f64,
    pub master_r: f64,
    waveform: Vec<f64>,
    wf_idx: usize,
    dbg_cnt: u64,
}

impl SynthEngine {
    pub fn new() -> Self {
        SynthEngine {
            voices: (0..NUM_VOICES).map(Voice::new).collect(),
            delay: Delay::new(),
            reverb: Reverb::new(),
            dc_l: DcBlocker::new(),
            dc_r: DcBlocker::new(),
            out_buf: vec![0u8; CHUNK_BYTES],
            track_levels: [0.0; 8],
            master_l: 0.0, master_r: 0.0,
            waveform: vec![0.0; 320],
            wf_idx: 0,
            dbg_cnt: 0,
        }
    }

    pub fn trigger_row(&mut self, notes: &[i32], vols: &[i32]) {
        for t in 0..NUM_VOICES {
            let note = notes[t];
            let vol = vols[t];
            if note == NOTE_OFF {
                self.voices[t].release();
            } else if note >= 1 && note <= 127 {
                let freq = 440.0 * (2.0_f64).powf((note as f64 - 69.0) / 12.0);
                let v = if vol > 0 { vol as f64 / 255.0 } else { 0.8 };
                self.voices[t].trigger(freq, v);
            }
        }
    }

    pub fn generate_chunk(&mut self) -> &[u8] {
        // ---- Silence gate ----
        // If no voice is active, flush the effect buffers so no stale delay/
        // reverb tail or denormal dust leaks through, and emit pure digital
        // zeros. This guarantees true silence before the first note arrives
        // and between notes while the sequencer idles on empty rows.
        let any_active = self.voices.iter().any(|v| v.active);
        if !any_active {
            self.delay.clear();
            self.reverb.clear();
            self.dc_l.clear();
            self.dc_r.clear();
            self.out_buf.fill(0);
            self.master_l *= 0.7;
            self.master_r *= 0.7;
            for t in 0..8 { self.track_levels[t] *= 0.7; }
            self.dbg_cnt += 1;
            return &self.out_buf;
        }

        let mut tpk = [0.0f64; 8];
        let mut pk_l = 0.0f64;
        let mut pk_r = 0.0f64;

        for i in 0..CHUNK {
            let mut mix_l = 0.0;
            let mut mix_r = 0.0;
            let mut dl_l = 0.0;
            let mut dl_r = 0.0;
            let mut rv_in = 0.0;

            for t in 0..NUM_VOICES {
                let s = self.voices[t].generate();
                if s == 0.0 { continue; }

                let p = &PRESETS[t];
                let scaled = s * 0.85;
                let pan_l = (p.pan * PI * 0.5).cos();
                let pan_r = (p.pan * PI * 0.5).sin();
                let sl = scaled * pan_l;
                let sr = scaled * pan_r;
                mix_l += sl;
                mix_r += sr;

                if p.dl_send > 0.0 { dl_l += sl * p.dl_send; dl_r += sr * p.dl_send; }
                if p.rv_send > 0.0 { rv_in += (sl + sr) * 0.5 * p.rv_send; }

                let pk = scaled.abs();
                if pk > tpk[t] { tpk[t] = pk; }
            }

            // Effects
            let (tap_l, tap_r) = self.delay.process(dl_l, dl_r);
            let rv = self.reverb.process(rv_in);

            let mut out_l = mix_l + tap_l * 0.3 + rv * 0.25;
            let mut out_r = mix_r + tap_r * 0.3 + rv * 0.25;

            // Master gain
            out_l *= 0.35;
            out_r *= 0.35;

            // Transparent limiter (pure arithmetic, no transcendentals)
            out_l = soft_limit(out_l);
            out_r = soft_limit(out_r);

            // DC blocker — removes sub-bass rumble from reverb/delay recirculation
            out_l = self.dc_l.process(out_l);
            out_r = self.dc_r.process(out_r);

            if out_l.abs() > pk_l { pk_l = out_l.abs(); }
            if out_r.abs() > pk_r { pk_r = out_r.abs(); }

            // Waveform capture
            self.waveform[self.wf_idx] = (out_l + out_r) * 0.5;
            self.wf_idx = (self.wf_idx + 1) % 320;

            // 16-bit LE PCM
            let sl = (out_l * 32000.0) as i32;
            let sr = (out_r * 32000.0) as i32;
            let sl = sl.clamp(-32768, 32767) as i16;
            let sr = sr.clamp(-32768, 32767) as i16;
            let off = i * 4;
            self.out_buf[off] = sl as u8;
            self.out_buf[off + 1] = (sl >> 8) as u8;
            self.out_buf[off + 2] = sr as u8;
            self.out_buf[off + 3] = (sr >> 8) as u8;
        }

        // Smoothed meters
        self.master_l = self.master_l * 0.7 + pk_l * 0.3;
        self.master_r = self.master_r * 0.7 + pk_r * 0.3;
        for t in 0..8 {
            self.track_levels[t] = self.track_levels[t] * 0.7 + tpk[t] * 0.3;
        }

        self.dbg_cnt += 1;

        &self.out_buf
    }

    pub fn all_notes_off(&mut self) {
        for v in &mut self.voices {
            v.note_on = false;
            v.env_stage = 0;
            v.active = false;
            v.svf_ic1eq = 0.0;
            v.svf_ic2eq = 0.0;
        }
        self.delay.clear();
        self.reverb.clear();
        self.master_l = 0.0;
        self.master_r = 0.0;
        self.track_levels = [0.0; 8];
    }
}

// ===================== HELPERS =====================

#[inline(always)]
fn poly_blep(t: f64, dt: f64) -> f64 {
    let p = t % 1.0;
    if p < dt {
        let x = p / dt;
        x + x - x * x - 1.0
    } else if p > 1.0 - dt {
        let x = (p - 1.0) / dt;
        x * x + x + x + 1.0
    } else {
        0.0
    }
}

#[inline(always)]
fn poly_blamp(t: f64, dt: f64) -> f64 {
    let p = t % 1.0;
    if p < dt {
        let x = p / dt;
        let u = 1.0 - x;
        -u * u * u / 3.0 * dt
    } else if p > 1.0 - dt {
        let x = (p - 1.0) / dt + 1.0;
        x * x * x / 3.0 * dt
    } else {
        0.0
    }
}

#[inline(always)]
fn tanh_cheap(x: f64) -> f64 {
    let x2 = x * x;
    x * (27.0 + x2) / (27.0 + 9.0 * x2)
}

#[inline(always)]
fn flush_denorm(x: f64) -> f64 {
    if x.abs() < 1e-15 { 0.0 } else { x }
}

#[inline(always)]
fn soft_limit(x: f64) -> f64 {
    if x > 0.85 { let d = x - 0.85; 0.85 + d / (1.0 + d * 6.0) }
    else if x < -0.85 { let d = -x - 0.85; -(0.85 + d / (1.0 + d * 6.0)) }
    else { x }
}

// ===================== JNI EXPORTS =====================

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_m8droid_audio_NativeSynth_init(_env: JNIEnv, _class: JClass) {
    let mut engine = lock_engine();
    *engine = Some(SynthEngine::new());
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_m8droid_audio_NativeSynth_destroy(_env: JNIEnv, _class: JClass) {
    let mut engine = lock_engine();
    *engine = None;
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_m8droid_audio_NativeSynth_triggerRow<'a>(
    mut env: JNIEnv<'a>, _class: JClass<'a>, notes: jbyteArray, vols: jbyteArray
) {
    let mut engine = lock_engine();
    if let Some(ref mut eng) = *engine {
        let mut n_buf = [0i8; 8];
        let mut v_buf = [0i8; 8];
        let notes_arr = unsafe { jni::objects::JByteArray::from_raw(notes) };
        let vols_arr = unsafe { jni::objects::JByteArray::from_raw(vols) };
        let _ = env.get_byte_array_region(&notes_arr, 0, &mut n_buf);
        let _ = env.get_byte_array_region(&vols_arr, 0, &mut v_buf);
        let mut note_arr = [0i32; 8];
        let mut vol_arr = [0i32; 8];
        for i in 0..8 {
            note_arr[i] = n_buf[i] as i32 & 0xFF;
            vol_arr[i] = v_buf[i] as i32 & 0xFF;
        }
        eng.trigger_row(&note_arr, &vol_arr);
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_m8droid_audio_NativeSynth_generateChunk<'a>(
    env: JNIEnv<'a>, _class: JClass<'a>
) -> jbyteArray {
    let mut engine = lock_engine();
    let output = match env.new_byte_array(CHUNK_BYTES as i32) {
        Ok(arr) => arr,
        Err(_) => return std::ptr::null_mut(),
    };
    if let Some(ref mut eng) = *engine {
        let pcm = eng.generate_chunk();
        let signed: &[i8] = unsafe { std::mem::transmute(pcm) };
        let _ = env.set_byte_array_region(&output, 0, signed);
    }
    output.into_raw()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_m8droid_audio_NativeSynth_allNotesOff(_env: JNIEnv, _class: JClass) {
    let mut engine = lock_engine();
    if let Some(ref mut eng) = *engine {
        eng.all_notes_off();
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_m8droid_audio_NativeSynth_getMasterLevels<'a>(
    env: JNIEnv<'a>, _class: JClass<'a>
) -> jdoubleArray {
    let engine = lock_engine();
    let arr = match env.new_double_array(2) {
        Ok(a) => a,
        Err(_) => return std::ptr::null_mut(),
    };
    if let Some(ref eng) = *engine {
        let _ = env.set_double_array_region(&arr, 0, &[eng.master_l, eng.master_r]);
    }
    arr.into_raw()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_m8droid_audio_NativeSynth_getTrackLevels<'a>(
    env: JNIEnv<'a>, _class: JClass<'a>
) -> jdoubleArray {
    let engine = lock_engine();
    let arr = match env.new_double_array(8) {
        Ok(a) => a,
        Err(_) => return std::ptr::null_mut(),
    };
    if let Some(ref eng) = *engine {
        let _ = env.set_double_array_region(&arr, 0, &eng.track_levels);
    }
    arr.into_raw()
}
