#!/usr/bin/env python3
"""M8 Headless Emulator — virtual M8 tracker over WebSocket.

Replaces the physical Teensy + bridge.py. Speaks the same SLIP-encoded
binary protocol the Android app expects, renders a functional tracker UI,
and responds to key input.

Usage:
    python3 m8_emulator.py [--host 0.0.0.0] [--port 8765] [--audio-port 8766]
"""

from __future__ import annotations

import argparse
import asyncio
import colorsys
import logging
import math
import random
import signal
import struct
import time
from dataclasses import dataclass, field
from typing import Optional, Set

try:
    import numpy as np
    HAS_NUMPY = True
except ImportError:
    np = None
    HAS_NUMPY = False

import websockets
from websockets.server import WebSocketServerProtocol

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------

WIDTH = 320
HEIGHT = 240
FONT_W = 8
FONT_H = 10

# SLIP encoding
SLIP_END = 0xC0
SLIP_ESC = 0xDB
SLIP_ESC_END = 0xDC
SLIP_ESC_ESC = 0xDD

# Commands FROM M8
DRAW_RECT = 0xFE
DRAW_CHAR = 0xFD
DRAW_WAVEFORM = 0xFC
DRAW_JOYPAD = 0xFB
SYSTEM_INFO = 0xFF

# Commands TO M8
CMD_KEY_STATE = 0x43
CMD_DISCONNECT = 0x44
CMD_ENABLE_DISPLAY = 0x45
CMD_RESET_DISPLAY = 0x52

# Key bits
KEY_UP = 0x01
KEY_DOWN = 0x02
KEY_LEFT = 0x04
KEY_RIGHT = 0x08
KEY_OPTION = 0x10
KEY_EDIT = 0x20
KEY_SHIFT = 0x40
KEY_PLAY = 0x80

FPS = 30
FRAME_INTERVAL = 1.0 / FPS

# Logging
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)
logger = logging.getLogger("m8-emulator")

# ---------------------------------------------------------------------------
# SLIP encoding
# ---------------------------------------------------------------------------


def slip_encode(data: bytes) -> bytes:
    """SLIP-encode a frame."""
    out = bytearray()
    out.append(SLIP_END)
    for b in data:
        if b == SLIP_END:
            out.append(SLIP_ESC)
            out.append(SLIP_ESC_END)
        elif b == SLIP_ESC:
            out.append(SLIP_ESC)
            out.append(SLIP_ESC_ESC)
        else:
            out.append(b)
    out.append(SLIP_END)
    return bytes(out)


# ---------------------------------------------------------------------------
# M8 Draw command builders
# ---------------------------------------------------------------------------


def cmd_draw_rect(x: int, y: int, w: int, h: int, r: int, g: int, b: int) -> bytes:
    """Build a DRAW_RECT command."""
    return struct.pack("<BHHHHBBB", DRAW_RECT, x, y, w, h, r, g, b)


def cmd_draw_char(x: int, y: int, char: int, fg: tuple, bg: tuple) -> bytes:
    """Build a DRAW_CHAR command. fg/bg are (r, g, b) tuples."""
    return struct.pack(
        "<BHHBBBBBBB",
        DRAW_CHAR, x, y, char,
        fg[0], fg[1], fg[2],
        bg[0], bg[1], bg[2],
    )


def cmd_draw_waveform(x: int, y: int, r: int, g: int, b: int, data: bytes) -> bytes:
    """Build a DRAW_WAVEFORM command."""
    header = struct.pack("<BHHBBB", DRAW_WAVEFORM, x, y, r, g, b)
    return header + data


def cmd_system_info(major: int = 3, minor: int = 1, patch: int = 0) -> bytes:
    """Build a SYSTEM_INFO response."""
    return struct.pack("<BBBB", SYSTEM_INFO, major, minor, patch)


# ---------------------------------------------------------------------------
# Text rendering helper
# ---------------------------------------------------------------------------


def draw_text(text: str, x: int, y: int, fg: tuple, bg: tuple) -> list[bytes]:
    """Generate DRAW_CHAR commands for a string."""
    cmds = []
    for i, ch in enumerate(text):
        cmds.append(cmd_draw_char(x + i * FONT_W, y, ord(ch), fg, bg))
    return cmds


# ---------------------------------------------------------------------------
# M8 Synthesizer — ported from Kotlin M8Synth
# ---------------------------------------------------------------------------

SAMPLE_RATE = 44100
AUDIO_CHANNELS = 2
CHUNK_SAMPLES = 735  # ~16.7ms per chunk
TWO_PI = 2.0 * math.pi

# Waveform types
WAVE_SINE = 0
WAVE_SAW = 1
WAVE_PULSE = 2
WAVE_TRIANGLE = 3
WAVE_NOISE = 4
WAVE_FM = 5

# Filter types
FILTER_LP = 0
FILTER_HP = 1
FILTER_BP = 2


def note_to_freq(midi_note: int) -> float:
    """Convert M8 note number to frequency (matches Kotlin noteToFreq)."""
    midi = midi_note + 23
    return 440.0 * (2.0 ** ((midi - 69) / 12.0))


def _poly_blep(t: float, dt: float) -> float:
    """PolyBLEP anti-aliasing correction."""
    p = t % 1.0
    dtn = max(dt, 1e-10)
    if p < dtn:
        x = p / dtn
        return 2.0 * x - x * x - 1.0
    elif p > 1.0 - dtn:
        x = (p - 1.0) / dtn
        return x * x + 2.0 * x + 1.0
    return 0.0


def _tanh_clip(x: float) -> float:
    """Fast tanh approximation for soft clipping."""
    if x > 3.0:
        return 1.0
    if x < -3.0:
        return -1.0
    x2 = x * x
    return x * (27.0 + x2) / (27.0 + 9.0 * x2)


@dataclass
class ADSR:
    attack: float   # seconds
    decay: float    # seconds
    sustain: float  # 0.0-1.0 level
    release: float  # seconds


@dataclass
class VoicePreset:
    waveform: int
    pulse_width: float = 0.5
    filter_type: int = FILTER_LP
    filter_cutoff: float = 0.7
    filter_resonance: float = 0.0
    adsr: ADSR = None
    pwm_depth: float = 0.0
    pwm_rate: float = 0.0
    filter_lfo_depth: float = 0.0
    filter_lfo_rate: float = 0.0
    filter_env_depth: float = 0.0
    filter_env_decay: float = 0.3
    fm_ratio: float = 2.0
    fm_index: float = 1.0
    chorus_send: float = 0.0
    reverb_send: float = 0.2
    delay_send: float = 0.0
    pan: float = 0.5

    def __post_init__(self):
        if self.adsr is None:
            self.adsr = ADSR(0.01, 0.1, 0.7, 0.3)


# Track instrument presets — matches Kotlin TRACK_PRESETS exactly
TRACK_PRESETS = [
    # Track 0: Lead — pulse with PWM, medium attack, filter sweep
    VoicePreset(WAVE_PULSE, 0.45, FILTER_LP, 0.7, 0.3,
                adsr=ADSR(0.005, 0.1, 0.7, 0.3),
                pwm_depth=0.2, pwm_rate=3.5, filter_lfo_depth=0.15, filter_lfo_rate=2.0),
    # Track 1: Bass — sawtooth, fast attack, low filter
    VoicePreset(WAVE_SAW, 0.0, FILTER_LP, 0.35, 0.45,
                adsr=ADSR(0.002, 0.15, 0.6, 0.15),
                filter_env_depth=0.5, filter_env_decay=0.2),
    # Track 2: Pad — triangle + slow attack, high cutoff, chorus
    VoicePreset(WAVE_TRIANGLE, 0.0, FILTER_LP, 0.85, 0.1,
                adsr=ADSR(0.3, 0.4, 0.8, 1.0),
                chorus_send=0.6, reverb_send=0.5),
    # Track 3: Hi-hat / percussion — noise, very short
    VoicePreset(WAVE_NOISE, 0.0, FILTER_HP, 0.6, 0.2,
                adsr=ADSR(0.001, 0.05, 0.0, 0.03)),
    # Track 4: FM bell
    VoicePreset(WAVE_FM, 0.0, FILTER_LP, 0.9, 0.0,
                adsr=ADSR(0.001, 0.8, 0.2, 0.5),
                fm_ratio=3.0, fm_index=2.5, reverb_send=0.4),
    # Track 5: Pluck — pulse, fast decay
    VoicePreset(WAVE_PULSE, 0.5, FILTER_LP, 0.55, 0.35,
                adsr=ADSR(0.001, 0.2, 0.1, 0.15),
                filter_env_depth=0.6, filter_env_decay=0.15),
    # Track 6: Sub bass — sine, clean
    VoicePreset(WAVE_SINE, 0.0, FILTER_LP, 0.95, 0.0,
                adsr=ADSR(0.01, 0.05, 0.9, 0.2)),
    # Track 7: SFX — FM, short, noisy
    VoicePreset(WAVE_FM, 0.0, FILTER_BP, 0.5, 0.6,
                adsr=ADSR(0.001, 0.3, 0.0, 0.1),
                fm_ratio=7.0, fm_index=5.0),
]


class Voice:
    """Single synth voice with oscillator, envelope, and filter."""

    def __init__(self, track_index: int):
        self.track_index = track_index
        self.frequency = 0.0
        self.phase = 0.0
        self.volume = 0.0
        self.active = False
        self.note_on = False
        self.preset: VoicePreset = TRACK_PRESETS[track_index % len(TRACK_PRESETS)]

        # ADSR envelope: 0=idle, 1=attack, 2=decay, 3=sustain, 4=release
        self.env_stage = 0
        self.env_level = 0.0
        self.env_time = 0.0

        # Filter envelope
        self.filter_env_level = 0.0
        self.filter_env_time = 0.0

        # LFO phases
        self.pwm_lfo_phase = 0.0
        self.filter_lfo_phase = 0.0

        # FM operator phase
        self.fm_phase = 0.0

        # State-variable filter state
        self.svf_low = 0.0
        self.svf_band = 0.0
        self.svf_high = 0.0

        # Noise LFSR
        self.noise_lfsr = 0x7FFF
        self.noise_value = 0.0
        self.noise_sample_count = 0

    def trigger_note(self, freq: float, vol: float):
        self.frequency = freq
        self.volume = vol
        self.env_stage = 1  # attack
        self.env_time = 0.0
        self.filter_env_level = 1.0
        self.filter_env_time = 0.0
        self.note_on = True
        self.active = True

    def release_note(self):
        if self.env_stage != 0 and self.env_stage != 4:
            self.env_stage = 4  # release
            self.env_time = 0.0
        self.note_on = False

    def process_envelope(self, dt: float):
        adsr = self.preset.adsr
        stage = self.env_stage

        if stage == 1:  # Attack
            self.env_time += dt
            if adsr.attack <= 0.0:
                self.env_level = 1.0
            else:
                self.env_level = min(1.0, max(0.0, self.env_time / adsr.attack))
            if self.env_level >= 1.0:
                self.env_stage = 2
                self.env_time = 0.0
        elif stage == 2:  # Decay
            self.env_time += dt
            if adsr.decay <= 0.0:
                decay_progress = 1.0
            else:
                decay_progress = min(1.0, max(0.0, self.env_time / adsr.decay))
            self.env_level = 1.0 - (1.0 - adsr.sustain) * decay_progress
            if decay_progress >= 1.0:
                self.env_stage = 3
        elif stage == 3:  # Sustain
            self.env_level = adsr.sustain
        elif stage == 4:  # Release
            self.env_time += dt
            if adsr.release <= 0.0:
                release_progress = 1.0
            else:
                release_progress = min(1.0, max(0.0, self.env_time / adsr.release))
            self.env_level *= (1.0 - release_progress)
            if release_progress >= 1.0 or self.env_level < 0.0001:
                self.env_stage = 0
                self.env_level = 0.0
                self.active = False

        # Filter envelope (independent decay)
        if self.preset.filter_env_depth > 0.0:
            self.filter_env_time += dt
            if self.preset.filter_env_decay <= 0.0:
                f_decay = 1.0
            else:
                f_decay = min(1.0, max(0.0, self.filter_env_time / self.preset.filter_env_decay))
            self.filter_env_level = 1.0 - f_decay

    def generate_sample(self) -> float:
        if not self.active or self.env_stage == 0:
            return 0.0

        _sin = math.sin
        phase = self.phase
        phase_inc = self.frequency / SAMPLE_RATE
        preset = self.preset
        env_level = self.env_level
        wf = preset.waveform

        # PWM modulation
        if preset.pwm_depth > 0.0:
            self.pwm_lfo_phase += preset.pwm_rate * (1.0 / SAMPLE_RATE)
            mod = _sin(self.pwm_lfo_phase * TWO_PI) * preset.pwm_depth
            current_pw = preset.pulse_width + mod
            if current_pw < 0.1: current_pw = 0.1
            elif current_pw > 0.9: current_pw = 0.9
        else:
            current_pw = preset.pulse_width

        # Generate raw waveform (inlined for speed)
        if wf == WAVE_SINE:
            raw = _sin(phase * TWO_PI)
        elif wf == WAVE_SAW:
            naive = 2.0 * (phase - math.floor(phase + 0.5))
            # Inline polyBLEP
            p = phase % 1.0
            blep = 0.0
            if phase_inc > 1e-10:
                if p < phase_inc:
                    x = p / phase_inc
                    blep = 2.0 * x - x * x - 1.0
                elif p > 1.0 - phase_inc:
                    x = (p - 1.0) / phase_inc
                    blep = x * x + 2.0 * x + 1.0
            raw = naive - blep
        elif wf == WAVE_PULSE:
            p = phase % 1.0
            naive = 1.0 if p < current_pw else -1.0
            blep1 = 0.0
            blep2 = 0.0
            if phase_inc > 1e-10:
                if p < phase_inc:
                    x = p / phase_inc
                    blep1 = 2.0 * x - x * x - 1.0
                elif p > 1.0 - phase_inc:
                    x = (p - 1.0) / phase_inc
                    blep1 = x * x + 2.0 * x + 1.0
                p2 = (phase - current_pw) % 1.0
                if p2 < phase_inc:
                    x = p2 / phase_inc
                    blep2 = 2.0 * x - x * x - 1.0
                elif p2 > 1.0 - phase_inc:
                    x = (p2 - 1.0) / phase_inc
                    blep2 = x * x + 2.0 * x + 1.0
            raw = naive + blep1 - blep2
        elif wf == WAVE_TRIANGLE:
            p = phase % 1.0
            raw = 4.0 * (p - 0.5 if p >= 0.5 else 0.5 - p) - 1.0
        elif wf == WAVE_NOISE:
            self.noise_sample_count += 1
            period = int(SAMPLE_RATE / max(20.0, self.frequency))
            if period < 1: period = 1
            if self.noise_sample_count >= period:
                self.noise_sample_count = 0
                lfsr = self.noise_lfsr
                bit = (lfsr ^ (lfsr >> 1)) & 1
                lfsr = (lfsr >> 1) | (bit << 14)
                self.noise_lfsr = lfsr
                self.noise_value = (lfsr / 0x7FFF) * 2.0 - 1.0
            raw = self.noise_value
        elif wf == WAVE_FM:
            fm_phase = self.fm_phase + (self.frequency * preset.fm_ratio) / SAMPLE_RATE
            if fm_phase > 1e6: fm_phase -= 1e6
            self.fm_phase = fm_phase
            modulator = _sin(fm_phase * TWO_PI) * preset.fm_index * env_level
            raw = _sin((phase + modulator) * TWO_PI)
        else:
            raw = 0.0

        # Advance oscillator phase
        phase += phase_inc
        if phase > 1e6: phase -= 1e6
        self.phase = phase

        # Apply amplitude envelope
        enveloped = raw * env_level * self.volume

        # State-variable filter
        filter_type = preset.filter_type
        filter_res = preset.filter_resonance

        # Filter LFO
        if preset.filter_lfo_depth > 0.0:
            self.filter_lfo_phase += preset.filter_lfo_rate * (1.0 / SAMPLE_RATE)
            lfo_mod = _sin(self.filter_lfo_phase * TWO_PI) * preset.filter_lfo_depth
        else:
            lfo_mod = 0.0

        cutoff_norm = preset.filter_cutoff + preset.filter_env_depth * self.filter_env_level + lfo_mod
        if cutoff_norm < 0.0: cutoff_norm = 0.0
        elif cutoff_norm > 1.0: cutoff_norm = 1.0

        # For very high cutoffs on LP with low resonance, bypass filter
        if cutoff_norm >= 0.85 and filter_type == FILTER_LP and filter_res < 0.1:
            return enveloped

        # Map 0-1 to 20Hz-~15500Hz
        cutoff_hz = 20.0 * (2.0 ** (cutoff_norm * 9.6))
        fn = cutoff_hz / SAMPLE_RATE
        if fn > 0.35: fn = 0.35
        f = 2.0 * _sin(math.pi * fn)
        q = 1.0 - (filter_res if filter_res < 0.95 else 0.95)

        # SVF stability: f must be < 2*q
        max_f = 1.9 * q
        if f > max_f: f = max_f

        svf_low = self.svf_low
        svf_band = self.svf_band

        svf_high = enveloped - svf_low - q * svf_band
        svf_band += f * svf_high
        svf_low += f * svf_band

        # Safety clamp
        if svf_low > 4.0: svf_low = 4.0
        elif svf_low < -4.0: svf_low = -4.0
        if svf_band > 4.0: svf_band = 4.0
        elif svf_band < -4.0: svf_band = -4.0

        self.svf_low = svf_low
        self.svf_band = svf_band
        self.svf_high = svf_high

        if filter_type == FILTER_LP:
            return svf_low
        elif filter_type == FILTER_HP:
            return svf_high
        elif filter_type == FILTER_BP:
            return svf_band
        return svf_low


class StereoDelay:
    """Stereo ping-pong delay effect."""

    def __init__(self, time_l: float = 0.375, time_r: float = 0.25,
                 feedback: float = 0.45, mix: float = 0.3, damping: float = 0.3):
        self.time_l = time_l
        self.time_r = time_r
        self.feedback = feedback
        self.mix = mix
        self.damping = damping
        self.max_samples = int(SAMPLE_RATE * 2.0)
        self.buf_l = [0.0] * self.max_samples
        self.buf_r = [0.0] * self.max_samples
        self.pos_l = 0
        self.pos_r = 0
        self.lp_l = 0.0
        self.lp_r = 0.0

    def process(self, in_l: float, in_r: float):
        delay_l = max(1, min(self.max_samples - 1, int(self.time_l * SAMPLE_RATE)))
        delay_r = max(1, min(self.max_samples - 1, int(self.time_r * SAMPLE_RATE)))

        read_l = (self.pos_l - delay_l + self.max_samples) % self.max_samples
        read_r = (self.pos_r - delay_r + self.max_samples) % self.max_samples

        tap_l = self.buf_l[read_l]
        tap_r = self.buf_r[read_r]

        # One-pole damping filter on feedback
        self.lp_l += self.damping * (tap_l - self.lp_l)
        self.lp_r += self.damping * (tap_r - self.lp_r)

        # Ping-pong: left feeds right, right feeds left
        self.buf_l[self.pos_l] = in_l + self.lp_r * self.feedback
        self.buf_r[self.pos_r] = in_r + self.lp_l * self.feedback

        self.pos_l = (self.pos_l + 1) % self.max_samples
        self.pos_r = (self.pos_r + 1) % self.max_samples

        return (in_l + tap_l * self.mix, in_r + tap_r * self.mix)

    def clear(self):
        for i in range(self.max_samples):
            self.buf_l[i] = 0.0
            self.buf_r[i] = 0.0
        self.lp_l = 0.0
        self.lp_r = 0.0


class Chorus:
    """Stereo chorus with dual modulated delay lines."""

    def __init__(self, rate: float = 0.8, depth: float = 0.003,
                 mix: float = 0.4, base_delay: float = 0.012):
        self.rate = rate
        self.depth = depth
        self.mix = mix
        self.base_delay = base_delay
        self.max_samples = int(SAMPLE_RATE * 0.1)  # 100ms max
        self.buf_l = [0.0] * self.max_samples
        self.buf_r = [0.0] * self.max_samples
        self.write_pos = 0
        self.lfo_phase1 = 0.0
        self.lfo_phase2 = 0.33

    def _read_interp(self, buf: list, pos: float) -> float:
        size = len(buf)
        p = ((pos % size) + size) % size
        i0 = int(p)
        frac = p - i0
        i1 = (i0 + 1) % size
        return buf[i0] * (1.0 - frac) + buf[i1] * frac

    def process(self, in_l: float, in_r: float):
        self.buf_l[self.write_pos] = in_l
        self.buf_r[self.write_pos] = in_r

        self.lfo_phase1 += self.rate / SAMPLE_RATE
        self.lfo_phase2 += self.rate / SAMPLE_RATE
        if self.lfo_phase1 > 1.0:
            self.lfo_phase1 -= 1.0
        if self.lfo_phase2 > 1.0:
            self.lfo_phase2 -= 1.0

        mod1 = math.sin(self.lfo_phase1 * TWO_PI) * self.depth
        mod2 = math.sin(self.lfo_phase2 * TWO_PI) * self.depth

        delay_l = max(1.0, min(self.max_samples - 2.0, (self.base_delay + mod1) * SAMPLE_RATE))
        delay_r = max(1.0, min(self.max_samples - 2.0, (self.base_delay + mod2) * SAMPLE_RATE))

        tap_l = self._read_interp(self.buf_l, self.write_pos - delay_l)
        tap_r = self._read_interp(self.buf_r, self.write_pos - delay_r)

        self.write_pos = (self.write_pos + 1) % self.max_samples

        return (in_l + (tap_l - in_l) * self.mix, in_r + (tap_r - in_r) * self.mix)

    def clear(self):
        for i in range(self.max_samples):
            self.buf_l[i] = 0.0
            self.buf_r[i] = 0.0


class PlateReverb:
    """Schroeder plate reverb: 8 comb filters + 4 allpass."""

    def __init__(self, mix: float = 0.25, decay: float = 0.7, damping: float = 0.4):
        self.mix = mix
        self.decay = decay
        self.damping = damping

        # Comb filter delay lengths (prime-ish for diffusion)
        self.comb_lengths = [1557, 1617, 1491, 1422, 1277, 1356, 1188, 1116]
        self.comb_bufs = [[0.0] * length for length in self.comb_lengths]
        self.comb_pos = [0] * len(self.comb_lengths)
        self.comb_lp = [0.0] * len(self.comb_lengths)

        # Allpass delay lengths
        self.ap_lengths = [225, 556, 441, 341]
        self.ap_bufs = [[0.0] * length for length in self.ap_lengths]
        self.ap_pos = [0] * len(self.ap_lengths)

    def _allpass(self, idx: int, inp: float) -> float:
        buf = self.ap_bufs[idx]
        pos = self.ap_pos[idx]
        tap = buf[pos]
        g = 0.5
        output = -inp + tap
        buf[pos] = inp + tap * g
        self.ap_pos[idx] = (pos + 1) % self.ap_lengths[idx]
        return output

    def process(self, in_l: float, in_r: float):
        inp = (in_l + in_r) * 0.5

        out_l = 0.0
        out_r = 0.0

        for i in range(len(self.comb_bufs)):
            buf = self.comb_bufs[i]
            pos = self.comb_pos[i]
            tap = buf[pos]

            # Damped feedback
            self.comb_lp[i] += self.damping * (tap - self.comb_lp[i])
            buf[pos] = inp + self.comb_lp[i] * self.decay

            self.comb_pos[i] = (pos + 1) % self.comb_lengths[i]

            # Split to L/R (alternating)
            if i % 2 == 0:
                out_l += tap
            else:
                out_r += tap

        half = len(self.comb_bufs) // 2
        if half > 0:
            out_l /= half
            out_r /= half

        # Series allpass filters for diffusion
        out_l = self._allpass(0, out_l)
        out_l = self._allpass(1, out_l)
        out_r = self._allpass(2, out_r)
        out_r = self._allpass(3, out_r)

        return (in_l + out_l * self.mix, in_r + out_r * self.mix)

    def clear(self):
        for buf in self.comb_bufs:
            for i in range(len(buf)):
                buf[i] = 0.0
        for i in range(len(self.comb_pos)):
            self.comb_pos[i] = 0
            self.comb_lp[i] = 0.0
        for buf in self.ap_bufs:
            for i in range(len(buf)):
                buf[i] = 0.0
        for i in range(len(self.ap_pos)):
            self.ap_pos[i] = 0


class M8Synth:
    """
    Advanced polyphonic synthesizer for the M8 emulator.

    Features: ADSR envelopes, PolyBLEP waveforms, PWM, 2-op FM,
    state-variable filter per voice, noise LFSR, stereo ping-pong delay,
    chorus, plate reverb, soft clipping, DC offset removal.

    Generates 16-bit stereo PCM at 44100Hz.
    """

    def __init__(self):
        self.voices = [Voice(i) for i in range(8)]
        self.delay = StereoDelay()
        self.chorus = Chorus()
        self.reverb = PlateReverb()

        # DC offset removal (high-pass at ~5Hz)
        self.dc_l = 0.0
        self.dc_r = 0.0
        self.dc_coeff = 1.0 - (TWO_PI * 5.0 / SAMPLE_RATE)

        # BPM-synced row advance tracking
        self._samples_since_row = 0

    def trigger_row(self, row_data):
        """
        Trigger notes from the current tracker row.
        row_data: list of 8 tracks, each (note, instrument, volume, fx, fx2).
        """
        for track in range(min(len(row_data), len(self.voices))):
            data = row_data[track]
            note = data[0]
            vol = data[2]
            voice = self.voices[track]

            if note > 0:
                freq = note_to_freq(note)
                v = (vol & 0xFF) / 255.0
                voice.trigger_note(freq, v)
            # note == 0 means continue — existing note keeps playing

    def generate_chunk(self) -> bytes:
        """Generate one chunk of stereo PCM audio.

        Hot path is heavily optimized to minimize Python overhead:
        - Local variable caching for attribute lookups
        - Precomputed pan gains
        - Inlined tanh clip
        """
        buf = bytearray(CHUNK_SAMPLES * AUDIO_CHANNELS * 2)
        dt = 1.0 / SAMPLE_RATE

        # Cache frequently accessed attributes as locals
        voices = self.voices
        chorus_process = self.chorus.process
        delay_process = self.delay.process
        reverb_process = self.reverb.process
        dc_coeff = self.dc_coeff
        dc_l = self.dc_l
        dc_r = self.dc_r
        _sin = math.sin
        _cos = math.cos
        _pi = math.pi
        _floor = math.floor
        _abs = abs
        _pack = struct.pack_into

        # Pre-compute per-voice constants (pan gains, send levels)
        active_voices = []
        for v in voices:
            if v.active:
                pan = (v.track_index / 7.0) * 0.6 + 0.2
                pan_l = _cos(pan * _pi * 0.5) * 0.15  # gain * pan_l
                pan_r = _sin(pan * _pi * 0.5) * 0.15  # gain * pan_r
                p = v.preset
                active_voices.append((v, pan_l, pan_r,
                                      p.chorus_send, p.reverb_send, p.delay_send))

        for i in range(CHUNK_SAMPLES):
            mix_l = 0.0
            mix_r = 0.0
            chorus_in_l = 0.0
            chorus_in_r = 0.0
            reverb_in_l = 0.0
            reverb_in_r = 0.0
            delay_in_l = 0.0
            delay_in_r = 0.0

            # Update active voices list when voices deactivate
            j = 0
            while j < len(active_voices):
                v, pan_l, pan_r, ch_send, rv_send, dl_send = active_voices[j]
                if not v.active:
                    active_voices.pop(j)
                    continue

                v.process_envelope(dt)
                if not v.active:
                    active_voices.pop(j)
                    continue

                sample = v.generate_sample()

                out_l = sample * pan_l
                out_r = sample * pan_r

                mix_l += out_l
                mix_r += out_r

                if ch_send > 0.0:
                    chorus_in_l += out_l * ch_send
                    chorus_in_r += out_r * ch_send
                if rv_send > 0.0:
                    reverb_in_l += out_l * rv_send
                    reverb_in_r += out_r * rv_send
                if dl_send > 0.0:
                    delay_in_l += out_l * dl_send
                    delay_in_r += out_r * dl_send

                j += 1

            # Process effects
            ch_l, ch_r = chorus_process(chorus_in_l, chorus_in_r)
            dl_l, dl_r = delay_process(delay_in_l, delay_in_r)
            rv_l, rv_r = reverb_process(reverb_in_l, reverb_in_r)

            # Mix effects into output
            out_l = mix_l + ch_l + dl_l + rv_l
            out_r = mix_r + ch_r + dl_r + rv_r

            # DC offset removal
            prev_dc_l = dc_l
            prev_dc_r = dc_r
            dc_l = out_l - prev_dc_l * dc_coeff
            dc_r = out_r - prev_dc_r * dc_coeff
            out_l = out_l - dc_l
            out_r = out_r - dc_r

            # Inline soft clip (tanh approximation)
            if out_l > 3.0:
                out_l = 1.0
            elif out_l < -3.0:
                out_l = -1.0
            else:
                x2 = out_l * out_l
                out_l = out_l * (27.0 + x2) / (27.0 + 9.0 * x2)

            if out_r > 3.0:
                out_r = 1.0
            elif out_r < -3.0:
                out_r = -1.0
            else:
                x2 = out_r * out_r
                out_r = out_r * (27.0 + x2) / (27.0 + 9.0 * x2)

            # Convert to 16-bit LE
            s_l = int(out_l * 32767.0)
            s_r = int(out_r * 32767.0)
            if s_l > 32767: s_l = 32767
            elif s_l < -32768: s_l = -32768
            if s_r > 32767: s_r = 32767
            elif s_r < -32768: s_r = -32768

            _pack("<hh", buf, i * 4, s_l, s_r)

        # Store back DC state
        self.dc_l = dc_l
        self.dc_r = dc_r

        return bytes(buf)

    def generate_silence(self) -> bytes:
        """Generate silence (when tracker is stopped)."""
        return bytes(CHUNK_SAMPLES * AUDIO_CHANNELS * 2)

    def all_notes_off(self):
        """Kill all voices and clear effects."""
        for v in self.voices:
            v.active = False
            v.env_stage = 0
            v.env_level = 0.0
            v.svf_low = 0.0
            v.svf_band = 0.0
            v.svf_high = 0.0
        self.delay.clear()
        self.chorus.clear()
        self.reverb.clear()
        self.dc_l = 0.0
        self.dc_r = 0.0


# ---------------------------------------------------------------------------
# M8 Tracker State
# ---------------------------------------------------------------------------

# Color palette — matches M8 defaults
C_BG = (0, 0, 0)
C_TEXT = (0, 255, 0)         # Green
C_TEXT_DIM = (0, 128, 0)     # Dim green
C_CURSOR = (255, 255, 255)   # White cursor
C_CURSOR_BG = (60, 60, 180)  # Blue highlight
C_HEADER = (255, 100, 0)     # Orange headers
C_VALUE = (200, 200, 255)    # Light blue values
C_PLAY_ON = (255, 50, 50)    # Red when playing
C_MUTED = (80, 80, 80)       # Muted track
C_WAVEFORM = (0, 200, 255)   # Cyan waveform

# Tracker screens
SCREEN_SONG = 0
SCREEN_CHAIN = 1
SCREEN_PHRASE = 2
SCREEN_INSTRUMENT = 3
SCREEN_TABLE = 4
SCREEN_MIXER = 5
SCREEN_EFFECTS = 6
SCREEN_SETTINGS = 7

SCREEN_NAMES = [
    "SONG", "CHAIN", "PHRASE", "INSTR",
    "TABLE", "MIXER", "FX", "CONFIG",
]

# Note names
NOTES = ["C-", "C#", "D-", "D#", "E-", "F-", "F#", "G-", "G#", "A-", "A#", "B-"]


def note_name(n: int) -> str:
    if n == 0:
        return "---"
    note = (n - 1) % 12
    octave = (n - 1) // 12
    return f"{NOTES[note]}{octave}"


def hex2(v: int) -> str:
    return f"{v:02X}"


@dataclass
class TrackerState:
    """Virtual M8 tracker state."""
    screen: int = SCREEN_PHRASE
    cursor_x: int = 0
    cursor_y: int = 0
    playing: bool = False
    play_row: int = 0
    bpm: int = 120
    octave: int = 4

    # Phrase data — 8 tracks x 16 rows, each cell is (note, instrument, volume, fx1, fx2)
    phrase_data: list = field(default_factory=list)
    # Song data — 8 tracks x 256 rows of chain numbers
    song_data: list = field(default_factory=list)

    # For animation
    frame_count: int = 0
    waveform_phase: float = 0.0

    def __post_init__(self):
        if not self.phrase_data:
            self._generate_demo_phrase()
        if not self.song_data:
            self.song_data = [[random.randint(0, 15) if random.random() > 0.3 else 0xFF
                               for _ in range(8)] for _ in range(256)]

    def _generate_demo_phrase(self):
        """Generate a demo phrase — C minor pattern matching Kotlin M8Emulator."""
        # Key of C minor: C=25, Eb=28, G=32, Bb=35
        # Track 0: Lead melody — expressive phrase (pulse w/ PWM)
        lead_notes = [
            60, 0, 63, 0,  67, 0, 70, 63,   # C5 . Eb5 . G5 . Bb5 Eb5
            72, 0, 70, 67,  63, 0, 60, 0     # C6 . Bb5 G5 Eb5 . C5 .
        ]
        # Track 1: Bass — driving eighth-note root pattern (saw)
        bass_notes = [
            36, 0, 36, 0,  36, 0, 36, 48,   # C3 . C3 . C3 . C3 C4
            39, 0, 39, 0,  43, 0, 43, 0     # Eb3 . Eb3 . G3 . G3 .
        ]
        # Track 2: Pad chords — sustained (triangle)
        pad_notes = [
            60, 0, 0, 0,  0, 0, 0, 0,       # C5 (hold)
            63, 0, 0, 0,  67, 0, 0, 0        # Eb5 (hold) G5 (hold)
        ]
        # Track 3: Hi-hat pattern (noise)
        hat_notes = [
            80, 0, 80, 0,  80, 0, 80, 0,    # steady eighths
            80, 0, 80, 80, 80, 0, 80, 0     # variation with ghost note
        ]
        hat_vols = [
            0xCC, 0, 0x88, 0,  0xCC, 0, 0x88, 0,
            0xCC, 0, 0x88, 0x55, 0xCC, 0, 0x88, 0
        ]
        # Track 4: FM bell — sparse accents on beat
        bell_notes = [
            72, 0, 0, 0,  0, 0, 0, 0,
            0, 0, 0, 0,   79, 0, 0, 0        # C6 . . . . . . . . . . . G6 . . .
        ]
        # Track 5: Pluck arpeggios
        pluck_notes = [
            0, 60, 0, 63,  0, 67, 0, 63,
            0, 60, 0, 67,  0, 72, 0, 67
        ]
        # Track 6: Sub bass — octave below bass, only on downbeats (sine)
        sub_notes = [
            24, 0, 0, 0,  0, 0, 0, 0,
            27, 0, 0, 0,  31, 0, 0, 0
        ]
        # Track 7: FX hits (FM)
        fx_notes = [
            0, 0, 0, 0,  0, 0, 0, 96,
            0, 0, 0, 0,  0, 0, 0, 0
        ]

        all_tracks = [lead_notes, bass_notes, pad_notes, hat_notes,
                      bell_notes, pluck_notes, sub_notes, fx_notes]

        self.phrase_data = []
        for row in range(16):
            track_row = []
            for track in range(8):
                note = all_tracks[track][row]

                if track == 3:
                    vol = hat_vols[row]  # per-step hat volume
                elif note > 0:
                    vol = 0xCC  # default volume
                else:
                    vol = 0

                inst = track  # instrument matches track
                fx = 0
                track_row.append((note, inst, vol, fx, 0))
            self.phrase_data.append(track_row)

    def handle_key(self, keys: int):
        """Process key input."""
        if keys & KEY_UP:
            self.cursor_y = max(0, self.cursor_y - 1)
        if keys & KEY_DOWN:
            self.cursor_y = min(15, self.cursor_y + 1)
        if keys & KEY_LEFT:
            self.cursor_x = max(0, self.cursor_x - 1)
        if keys & KEY_RIGHT:
            self.cursor_x = min(7, self.cursor_x + 1)
        if keys & KEY_PLAY:
            self.playing = not self.playing
            if self.playing:
                self.play_row = 0
        if keys & KEY_OPTION:
            self.screen = (self.screen + 1) % len(SCREEN_NAMES)
        if keys & KEY_EDIT:
            self.screen = (self.screen - 1) % len(SCREEN_NAMES)
        if keys & KEY_SHIFT:
            self.octave = (self.octave % 8) + 1


# ---------------------------------------------------------------------------
# Frame renderer — generates M8 protocol commands
# ---------------------------------------------------------------------------


class M8Renderer:
    """Renders the M8 tracker UI as protocol commands."""

    def render_frame(self, state: TrackerState) -> list[bytes]:
        """Render a full frame as a list of SLIP-encoded commands."""
        cmds: list[bytes] = []

        # Clear screen
        cmds.append(cmd_draw_rect(0, 0, WIDTH, HEIGHT, *C_BG))

        # Header bar
        cmds.extend(self._render_header(state))

        # Main content area based on current screen
        if state.screen == SCREEN_PHRASE:
            cmds.extend(self._render_phrase(state))
        elif state.screen == SCREEN_SONG:
            cmds.extend(self._render_song(state))
        elif state.screen == SCREEN_MIXER:
            cmds.extend(self._render_mixer(state))
        elif state.screen == SCREEN_INSTRUMENT:
            cmds.extend(self._render_instrument(state))
        else:
            cmds.extend(self._render_placeholder(state))

        # Waveform at bottom
        cmds.extend(self._render_waveform(state))

        # Footer
        cmds.extend(self._render_footer(state))

        state.frame_count += 1
        state.waveform_phase += 0.15

        # Note: play_row advance is handled by the audio loop (BPM-synced)

        # SLIP-encode all commands
        return [slip_encode(cmd) for cmd in cmds]

    def _render_header(self, state: TrackerState) -> list[bytes]:
        cmds = []
        # Header background
        cmds.append(cmd_draw_rect(0, 0, WIDTH, 12, 20, 20, 40))

        # Screen name tabs
        for i, name in enumerate(SCREEN_NAMES):
            x = i * 40
            if i == state.screen:
                cmds.append(cmd_draw_rect(x, 0, 39, 12, 60, 60, 120))
                cmds.extend(draw_text(name, x + 2, 1, C_CURSOR, (60, 60, 120)))
            else:
                cmds.extend(draw_text(name[:3], x + 6, 1, C_TEXT_DIM, (20, 20, 40)))

        return cmds

    def _render_phrase(self, state: TrackerState) -> list[bytes]:
        cmds = []
        y_start = 16
        row_h = FONT_H + 2

        # Column headers
        headers = ["ROW", "NT1", "I1", "NT2", "I2", "NT3", "I3", "VOL"]
        for i, h in enumerate(headers):
            cmds.extend(draw_text(h, 4 + i * 38, y_start, C_HEADER, C_BG))

        # Phrase rows
        for row in range(16):
            y = y_start + (row + 1) * row_h
            is_play_row = state.playing and row == state.play_row
            is_cursor_row = row == state.cursor_y

            # Row number
            row_bg = (40, 0, 0) if is_play_row else C_BG
            row_fg = C_PLAY_ON if is_play_row else C_TEXT_DIM
            cmds.extend(draw_text(hex2(row), 4, y, row_fg, row_bg))

            # Track data
            if row < len(state.phrase_data):
                track_data = state.phrase_data[row]
                col = 0
                for track in range(min(3, len(track_data))):
                    note, inst, vol, fx, _ = track_data[track]

                    # Note column
                    nx = 4 + (col + 1) * 38
                    is_cursor = is_cursor_row and state.cursor_x == track
                    cell_bg = C_CURSOR_BG if is_cursor else row_bg
                    cell_fg = C_CURSOR if is_cursor else C_VALUE

                    n_str = note_name(note)
                    cmds.extend(draw_text(n_str, nx, y, cell_fg, cell_bg))

                    # Instrument column
                    ix = 4 + (col + 2) * 38
                    i_str = hex2(inst) if note > 0 else "--"
                    cmds.extend(draw_text(i_str, ix, y, C_TEXT_DIM if note == 0 else C_TEXT, row_bg))

                    col += 2

                # Volume for first track
                note0 = track_data[0][0]
                vol0 = track_data[0][2]
                vx = 4 + 7 * 38
                v_str = hex2(vol0) if note0 > 0 else "--"
                cmds.extend(draw_text(v_str, vx, y, C_TEXT_DIM if note0 == 0 else (200, 100, 255), row_bg))

        return cmds

    def _render_song(self, state: TrackerState) -> list[bytes]:
        cmds = []
        y_start = 16
        row_h = FONT_H + 2

        cmds.extend(draw_text("SONG VIEW", 120, y_start, C_HEADER, C_BG))

        # Track headers
        for t in range(8):
            cmds.extend(draw_text(f"T{t+1}", 4 + t * 38, y_start + row_h, C_HEADER, C_BG))

        # Song rows (show 16 visible)
        base_row = max(0, state.cursor_y - 8)
        for i in range(16):
            row = base_row + i
            if row >= 256:
                break
            y = y_start + (i + 2) * row_h
            is_cursor = row == state.cursor_y

            cmds.extend(draw_text(hex2(row), 4, y - row_h + 12, C_TEXT_DIM, C_BG))

            for t in range(8):
                val = state.song_data[row][t] if row < len(state.song_data) else 0xFF
                x = 4 + t * 38
                bg = C_CURSOR_BG if (is_cursor and t == state.cursor_x) else C_BG
                fg = C_MUTED if val == 0xFF else C_VALUE
                txt = "--" if val == 0xFF else hex2(val)
                cmds.extend(draw_text(txt, x, y, fg, bg))

        return cmds

    def _render_mixer(self, state: TrackerState) -> list[bytes]:
        cmds = []
        y_start = 20

        cmds.extend(draw_text("MIXER", 136, y_start, C_HEADER, C_BG))

        # 8 channel strips
        for ch in range(8):
            x = 8 + ch * 38
            y = y_start + 16

            # Channel label
            cmds.extend(draw_text(f"CH{ch+1}", x, y, C_TEXT, C_BG))

            # Volume bar (animated)
            bar_h = 100
            bar_y = y + 14
            level = int(50 + 50 * math.sin(state.waveform_phase + ch * 0.8))
            cmds.append(cmd_draw_rect(x, bar_y, 28, bar_h, 30, 30, 30))
            bar_fill = int(bar_h * level / 100)
            g_val = int(255 * level / 100)
            cmds.append(cmd_draw_rect(x, bar_y + bar_h - bar_fill, 28, bar_fill, 0, g_val, 0))

            # Level value
            is_cursor = state.cursor_x == ch
            bg = C_CURSOR_BG if is_cursor else C_BG
            cmds.extend(draw_text(hex2(level), x + 4, bar_y + bar_h + 4, C_VALUE, bg))

            # Pan
            cmds.extend(draw_text("C", x + 8, bar_y + bar_h + 16, C_TEXT_DIM, C_BG))

        return cmds

    def _render_instrument(self, state: TrackerState) -> list[bytes]:
        cmds = []
        y_start = 16

        cmds.extend(draw_text(f"INSTRUMENT {hex2(state.cursor_x)}", 100, y_start, C_HEADER, C_BG))

        params = [
            ("TYPE", "WAVSYNTH"),
            ("NAME", f"SYNTH {state.cursor_x:02d}"),
            ("SHAPE", "SAW"),
            ("SIZE", "80"),
            ("MULT", "01"),
            ("WARP", "00"),
            ("MIRROR", "OFF"),
            ("FILTER", "LP"),
            ("CUTOFF", "FF"),
            ("RES", "40"),
            ("AMP", "80"),
            ("LIM", "CLIP"),
            ("PAN", "80"),
            ("DRY", "C0"),
            ("CHO", "00"),
            ("DEL", "20"),
            ("REV", "40"),
        ]

        for i, (name, val) in enumerate(params):
            y = y_start + 14 + i * (FONT_H + 2)
            is_cursor = i == state.cursor_y
            bg = C_CURSOR_BG if is_cursor else C_BG
            cmds.extend(draw_text(f"{name:8s}", 8, y, C_TEXT, bg))
            cmds.extend(draw_text(val, 80, y, C_VALUE if is_cursor else C_TEXT_DIM, bg))

        return cmds

    def _render_placeholder(self, state: TrackerState) -> list[bytes]:
        cmds = []
        name = SCREEN_NAMES[state.screen]
        cmds.extend(draw_text(f"{name} VIEW", 120, 60, C_HEADER, C_BG))
        cmds.extend(draw_text("USE OPT/EDIT TO", 88, 100, C_TEXT_DIM, C_BG))
        cmds.extend(draw_text("SWITCH SCREENS", 96, 114, C_TEXT_DIM, C_BG))
        cmds.extend(draw_text("ARROWS TO NAVIGATE", 72, 140, C_TEXT, C_BG))
        cmds.extend(draw_text("PLAY TO START/STOP", 72, 154, C_PLAY_ON, C_BG))
        return cmds

    def _render_waveform(self, state: TrackerState) -> list[bytes]:
        cmds = []
        wave_y = HEIGHT - 36
        wave_w = 280

        # Generate waveform data
        wave_data = bytearray(wave_w)
        for i in range(wave_w):
            t = state.waveform_phase + i * 0.05
            # Composite waveform
            v = math.sin(t) * 0.5
            v += math.sin(t * 2.3) * 0.25
            v += math.sin(t * 0.7 + 1.0) * 0.25
            if state.playing:
                v *= 0.8 + 0.2 * math.sin(state.waveform_phase * 0.3)
            else:
                v *= 0.3  # Quieter when stopped
            wave_data[i] = max(0, min(255, int(128 + v * 80)))

        cmds.append(cmd_draw_waveform(20, wave_y, *C_WAVEFORM, bytes(wave_data)))
        return cmds

    def _render_footer(self, state: TrackerState) -> list[bytes]:
        cmds = []
        y = HEIGHT - 12

        # Footer background
        cmds.append(cmd_draw_rect(0, y, WIDTH, 12, 20, 20, 40))

        # BPM
        cmds.extend(draw_text(f"BPM:{state.bpm}", 4, y + 1, C_TEXT, (20, 20, 40)))

        # Play state
        play_text = "PLAY" if state.playing else "STOP"
        play_color = C_PLAY_ON if state.playing else C_TEXT_DIM
        cmds.extend(draw_text(play_text, 80, y + 1, play_color, (20, 20, 40)))

        # Octave
        cmds.extend(draw_text(f"OCT:{state.octave}", 140, y + 1, C_TEXT, (20, 20, 40)))

        # Row
        cmds.extend(draw_text(f"R:{hex2(state.play_row)}", 210, y + 1, C_VALUE, (20, 20, 40)))

        # Cursor pos
        cmds.extend(draw_text(
            f"X:{state.cursor_x} Y:{hex2(state.cursor_y)}",
            260, y + 1, C_TEXT_DIM, (20, 20, 40)
        ))

        return cmds


# ---------------------------------------------------------------------------
# WebSocket server
# ---------------------------------------------------------------------------


class M8EmulatorServer:
    """WebSocket server that emulates an M8 headless device."""

    def __init__(self, host: str, port: int, audio_port: int) -> None:
        self.host = host
        self.port = port
        self.audio_port = audio_port
        self.clients: Set[WebSocketServerProtocol] = set()
        self.audio_clients: Set[WebSocketServerProtocol] = set()
        self.state = TrackerState()
        self.renderer = M8Renderer()
        self.synth = M8Synth()
        self._shutdown = asyncio.Event()
        self._last_keys = 0
        self._was_playing = False
        self._audio_samples_since_row = 0

    async def start(self) -> None:
        loop = asyncio.get_running_loop()
        for sig in (signal.SIGINT, signal.SIGTERM):
            loop.add_signal_handler(sig, self._request_shutdown)

        # Start both WebSocket servers
        async with websockets.serve(
            self._ws_handler, self.host, self.port,
            ping_interval=20, ping_timeout=20,
        ) as display_server:
            async with websockets.serve(
                self._audio_handler, self.host, self.audio_port,
                ping_interval=20, ping_timeout=20,
            ) as audio_server:
                logger.info("M8 Emulator listening:")
                logger.info("  Display : ws://%s:%d", self.host, self.port)
                logger.info("  Audio   : ws://%s:%d", self.host, self.audio_port)

                render_task = asyncio.create_task(self._render_loop())
                audio_task = asyncio.create_task(self._audio_loop())

                await self._shutdown.wait()
                logger.info("Shutdown requested")

                render_task.cancel()
                audio_task.cancel()

                for task in (render_task, audio_task):
                    try:
                        await task
                    except asyncio.CancelledError:
                        pass

                display_server.close()
                audio_server.close()
                await display_server.wait_closed()
                await audio_server.wait_closed()

        logger.info("M8 Emulator stopped")

    def _request_shutdown(self) -> None:
        logger.info("Received shutdown signal")
        self._shutdown.set()

    async def _ws_handler(self, ws: WebSocketServerProtocol) -> None:
        remote = ws.remote_address
        logger.info("Display client connected: %s:%s", remote[0], remote[1])
        self.clients.add(ws)

        # Send system info and serial connected event
        try:
            await ws.send(slip_encode(cmd_system_info()))
            import json
            await ws.send(json.dumps({"event": "serial_connected", "device": "M8 Emulator"}))
        except websockets.ConnectionClosed:
            self.clients.discard(ws)
            return

        try:
            async for message in ws:
                data = message if isinstance(message, bytes) else message.encode()
                self._process_input(data)
        except websockets.ConnectionClosed:
            pass
        finally:
            self.clients.discard(ws)
            logger.info("Display client disconnected: %s:%s", remote[0], remote[1])

    async def _audio_handler(self, ws: WebSocketServerProtocol) -> None:
        remote = ws.remote_address
        logger.info("Audio client connected: %s:%s", remote[0], remote[1])
        self.audio_clients.add(ws)

        try:
            async for message in ws:
                pass  # Audio is output-only
        except websockets.ConnectionClosed:
            pass
        finally:
            self.audio_clients.discard(ws)
            logger.info("Audio client disconnected: %s:%s", remote[0], remote[1])

    def _process_input(self, data: bytes) -> None:
        """Process commands from the Android app."""
        if len(data) < 1:
            return

        cmd = data[0]
        if cmd == CMD_KEY_STATE and len(data) >= 2:
            new_keys = data[1]
            # Detect newly pressed keys (rising edge)
            pressed = new_keys & ~self._last_keys
            if pressed:
                self.state.handle_key(pressed)
            self._last_keys = new_keys
        elif cmd == CMD_ENABLE_DISPLAY:
            logger.info("Display enabled by client")
        elif cmd == CMD_RESET_DISPLAY:
            logger.info("Display reset by client")
        elif cmd == CMD_DISCONNECT:
            logger.info("Client requested disconnect")

    async def _render_loop(self) -> None:
        """Render frames and broadcast to connected display clients."""
        while not self._shutdown.is_set():
            start = time.monotonic()

            if self.clients:
                frames = self.renderer.render_frame(self.state)
                # Batch all commands into one send
                payload = b"".join(frames)

                stale = []
                results = await asyncio.gather(
                    *(ws.send(payload) for ws in self.clients),
                    return_exceptions=True,
                )
                for ws, result in zip(list(self.clients), results):
                    if isinstance(result, Exception):
                        stale.append(ws)
                for ws in stale:
                    self.clients.discard(ws)

            elapsed = time.monotonic() - start
            sleep_time = max(0, FRAME_INTERVAL - elapsed)
            await asyncio.sleep(sleep_time)

    async def _audio_loop(self) -> None:
        """Generate and broadcast audio data to connected audio clients.

        Uses the M8Synth for full synthesis (ADSR, PolyBLEP waveforms, filters,
        delay, chorus, reverb). Row advance is BPM-synced: rows_per_beat = 4,
        so row_duration_samples = SAMPLE_RATE * 60 / (bpm * 4).
        """
        chunk_duration = CHUNK_SAMPLES / SAMPLE_RATE

        while not self._shutdown.is_set():
            start_time = time.monotonic()

            if self.audio_clients:
                if self.state.playing:
                    # Detect play state transitions
                    if not self._was_playing:
                        self._was_playing = True
                        self._audio_samples_since_row = 0
                        # Trigger the first row immediately
                        if self.state.phrase_data:
                            row_data = self.state.phrase_data[self.state.play_row % 16]
                            self.synth.trigger_row(row_data)

                    # BPM-synced row advance: rows_per_beat = 4
                    row_duration_samples = int(SAMPLE_RATE * 60.0 / (self.state.bpm * 4))
                    self._audio_samples_since_row += CHUNK_SAMPLES

                    if self._audio_samples_since_row >= row_duration_samples:
                        self._audio_samples_since_row -= row_duration_samples
                        self.state.play_row = (self.state.play_row + 1) % 16
                        if self.state.phrase_data:
                            row_data = self.state.phrase_data[self.state.play_row % 16]
                            self.synth.trigger_row(row_data)

                    # Generate audio chunk via synth
                    pcm = self.synth.generate_chunk()
                else:
                    # Stopped — silence and clean up
                    if self._was_playing:
                        self._was_playing = False
                        self.synth.all_notes_off()
                    pcm = self.synth.generate_silence()

                # Send with 0x01 header (raw PCM marker)
                payload = b"\x01" + pcm

                stale = []
                results = await asyncio.gather(
                    *(ws.send(payload) for ws in self.audio_clients),
                    return_exceptions=True,
                )
                for ws, result in zip(list(self.audio_clients), results):
                    if isinstance(result, Exception):
                        stale.append(ws)
                for ws in stale:
                    self.audio_clients.discard(ws)

            elapsed = time.monotonic() - start_time
            sleep_time = max(0, chunk_duration - elapsed)
            await asyncio.sleep(sleep_time)


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="M8 Headless Emulator")
    parser.add_argument("--host", default="0.0.0.0", help="Bind address (default: 0.0.0.0)")
    parser.add_argument("--port", type=int, default=8765, help="Display WebSocket port (default: 8765)")
    parser.add_argument("--audio-port", type=int, default=8766, help="Audio WebSocket port (default: 8766)")
    parser.add_argument("--verbose", "-v", action="store_true", help="Debug logging")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    if args.verbose:
        logging.getLogger().setLevel(logging.DEBUG)

    logger.info("Starting M8 Emulator")
    logger.info("  Display : ws://%s:%d", args.host, args.port)
    logger.info("  Audio   : ws://%s:%d", args.host, args.audio_port)

    server = M8EmulatorServer(
        host=args.host,
        port=args.port,
        audio_port=args.audio_port,
    )

    try:
        asyncio.run(server.start())
    except KeyboardInterrupt:
        pass

    logger.info("Exiting")


if __name__ == "__main__":
    main()
