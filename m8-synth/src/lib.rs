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

const SR: f64 = 44100.0;
const TWO_PI: f64 = 2.0 * PI;
const CHUNK: usize = 735;
const CHUNK_BYTES: usize = CHUNK * 2 * 2; // stereo 16-bit
const NUM_VOICES: usize = 8;
const DELAY_LEN: usize = (SR * 0.375) as usize;
const NOTE_OFF: i32 = 0xFF;

// ===================== VOICE PRESET =====================

#[derive(Clone, Copy)]
struct Preset {
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

    // SVF
    svf_lo: f64,
    svf_bd: f64,

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
            svf_lo: 0.0, svf_bd: 0.0,
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
            3 => 4.0 * (self.phase - 0.5).abs() - 1.0, // Triangle
            4 => { // Noise
                self.noise_cnt += 1;
                let period = (SR / self.freq.max(20.0)) as u32;
                if self.noise_cnt >= period.max(1) {
                    self.noise_cnt = 0;
                    let bit = (self.lfsr ^ (self.lfsr >> 1)) & 1;
                    self.lfsr = (self.lfsr >> 1) | (bit << 14);
                    self.noise_val = self.lfsr as f64 / 0x3FFF as f64 - 1.0;
                }
                self.noise_val
            }
            5 => { // FM
                self.fm_phase += self.freq * p.fm_ratio / SR;
                if self.fm_phase > 1e6 { self.fm_phase -= self.fm_phase.floor(); }
                let modulator = (self.fm_phase * TWO_PI).sin() * p.fm_idx * self.env_level;
                ((self.phase + modulator) * TWO_PI).sin()
            }
            _ => 0.0,
        };

        // Advance phase
        self.phase += ph_inc;
        if self.phase >= 1.0 { self.phase -= self.phase.floor(); }

        // --- 2-pole SVF (clamped for stability) ---
        let cut_norm = (p.cutoff + p.filt_env * self.fenv_level).clamp(0.0, 1.0);
        let cut_hz = 20.0 * (2.0_f64).powf(cut_norm * 10.0);
        let q = (1.0 - p.reso * 0.95).max(0.5);
        let f = (2.0 * (PI * (cut_hz / SR).clamp(0.0, 0.48)).sin())
                .min(2.0 * q - 0.01);

        let hp = raw - self.svf_lo - q * self.svf_bd;
        self.svf_bd += f * hp;
        self.svf_lo += f * self.svf_bd;
        self.svf_bd = self.svf_bd.clamp(-4.0, 4.0);
        self.svf_lo = self.svf_lo.clamp(-4.0, 4.0);
        if !self.svf_bd.is_finite() { self.svf_bd = 0.0; }
        if !self.svf_lo.is_finite() { self.svf_lo = 0.0; }

        self.svf_lo * self.env_level * self.vol
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
        // Damped cross-feedback
        self.damp_l += 0.35 * (tap_r - self.damp_l);
        self.damp_r += 0.35 * (tap_l - self.damp_r);
        self.buf_l[pos_l] = (in_l + self.damp_l * 0.4).clamp(-2.0, 2.0);
        self.buf_r[pos_r] = (in_r + self.damp_r * 0.4).clamp(-2.0, 2.0);
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

struct Reverb {
    combs: [Vec<f64>; 4],
    comb_pos: [usize; 4],
    comb_damp: [f64; 4],
    aps: [Vec<f64>; 2],
    ap_pos: [usize; 2],
}

impl Reverb {
    fn new() -> Self {
        Reverb {
            combs: [
                vec![0.0; 1116], vec![0.0; 1188],
                vec![0.0; 1277], vec![0.0; 1356],
            ],
            comb_pos: [0; 4],
            comb_damp: [0.0; 4],
            aps: [vec![0.0; 556], vec![0.0; 441]],
            ap_pos: [0; 2],
        }
    }

    #[inline(always)]
    fn process(&mut self, input: f64) -> f64 {
        let mut sum = 0.0;
        for c in 0..4 {
            let len = self.combs[c].len();
            let pos = self.comb_pos[c] % len;
            let tap = self.combs[c][pos];
            self.comb_damp[c] += 0.3 * (tap - self.comb_damp[c]);
            self.combs[c][pos] = (input + self.comb_damp[c] * 0.84).clamp(-2.0, 2.0);
            self.comb_pos[c] += 1;
            sum += tap;
        }
        sum *= 0.25;

        for a in 0..2 {
            let len = self.aps[a].len();
            let pos = self.ap_pos[a] % len;
            let tap = self.aps[a][pos];
            let temp = -sum * 0.5 + tap;
            self.aps[a][pos] = (sum + tap * 0.5).clamp(-2.0, 2.0);
            self.ap_pos[a] += 1;
            sum = temp;
        }
        sum
    }

    fn clear(&mut self) {
        for c in &mut self.combs { c.fill(0.0); }
        for a in &mut self.aps { a.fill(0.0); }
        self.comb_pos = [0; 4];
        self.ap_pos = [0; 2];
        self.comb_damp = [0.0; 4];
    }
}

// ===================== ENGINE =====================

struct SynthEngine {
    voices: Vec<Voice>,
    delay: Delay,
    reverb: Reverb,
    out_buf: Vec<u8>,
    track_levels: [f64; 8],
    master_l: f64,
    master_r: f64,
    waveform: Vec<f64>,
    wf_idx: usize,
    dbg_cnt: u64,
}

impl SynthEngine {
    fn new() -> Self {
        SynthEngine {
            voices: (0..NUM_VOICES).map(Voice::new).collect(),
            delay: Delay::new(),
            reverb: Reverb::new(),
            out_buf: vec![0u8; CHUNK_BYTES],
            track_levels: [0.0; 8],
            master_l: 0.0, master_r: 0.0,
            waveform: vec![0.0; 320],
            wf_idx: 0,
            dbg_cnt: 0,
        }
    }

    fn trigger_row(&mut self, notes: &[i32], vols: &[i32]) {
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

    fn generate_chunk(&mut self) -> &[u8] {
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

    fn all_notes_off(&mut self) {
        for v in &mut self.voices {
            v.note_on = false;
            v.env_stage = 0;
            v.active = false;
            v.svf_lo = 0.0;
            v.svf_bd = 0.0;
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
fn soft_limit(x: f64) -> f64 {
    if x > 0.85 { let d = x - 0.85; 0.85 + d / (1.0 + d * 6.0) }
    else if x < -0.85 { let d = -x - 0.85; -(0.85 + d / (1.0 + d * 6.0)) }
    else { x }
}

// ===================== JNI EXPORTS =====================

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_m8_audio_NativeSynth_init(_env: JNIEnv, _class: JClass) {
    let mut engine = ENGINE.lock().unwrap();
    *engine = Some(SynthEngine::new());
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_m8_audio_NativeSynth_destroy(_env: JNIEnv, _class: JClass) {
    let mut engine = ENGINE.lock().unwrap();
    *engine = None;
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_m8_audio_NativeSynth_triggerRow<'a>(
    mut env: JNIEnv<'a>, _class: JClass<'a>, notes: jbyteArray, vols: jbyteArray
) {
    let mut engine = ENGINE.lock().unwrap();
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
pub extern "system" fn Java_com_m8_audio_NativeSynth_generateChunk<'a>(
    env: JNIEnv<'a>, _class: JClass<'a>
) -> jbyteArray {
    let mut engine = ENGINE.lock().unwrap();
    let output = env.new_byte_array(CHUNK_BYTES as i32).unwrap();
    if let Some(ref mut eng) = *engine {
        let pcm = eng.generate_chunk();
        // Safe: we're copying from our buffer to the JNI array
        let signed: &[i8] = unsafe { std::mem::transmute(pcm) };
        env.set_byte_array_region(&output, 0, signed).unwrap();
    }
    output.into_raw()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_m8_audio_NativeSynth_allNotesOff(_env: JNIEnv, _class: JClass) {
    let mut engine = ENGINE.lock().unwrap();
    if let Some(ref mut eng) = *engine {
        eng.all_notes_off();
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_m8_audio_NativeSynth_getMasterLevels<'a>(
    env: JNIEnv<'a>, _class: JClass<'a>
) -> jdoubleArray {
    let engine = ENGINE.lock().unwrap();
    let arr = env.new_double_array(2).unwrap();
    if let Some(ref eng) = *engine {
        env.set_double_array_region(&arr, 0, &[eng.master_l, eng.master_r]).unwrap();
    }
    arr.into_raw()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_m8_audio_NativeSynth_getTrackLevels<'a>(
    env: JNIEnv<'a>, _class: JClass<'a>
) -> jdoubleArray {
    let engine = ENGINE.lock().unwrap();
    let arr = env.new_double_array(8).unwrap();
    if let Some(ref eng) = *engine {
        env.set_double_array_region(&arr, 0, &eng.track_levels).unwrap();
    }
    arr.into_raw()
}
