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
        "<BHHBBBBBB",
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
        """Generate a demo phrase with some notes."""
        self.phrase_data = []
        # A simple melodic pattern
        pattern_notes = [
            37, 0, 0, 0, 41, 0, 0, 0,
            44, 0, 0, 0, 48, 0, 44, 0,
        ]
        for row in range(16):
            track_row = []
            for track in range(8):
                if track == 0:
                    note = pattern_notes[row]
                elif track == 1 and row % 4 == 0:
                    note = random.choice([25, 37, 49])
                elif track == 2 and row % 2 == 0:
                    note = random.randint(60, 80)
                else:
                    note = 0
                inst = random.randint(0, 7) if note > 0 else 0
                vol = random.randint(0x80, 0xFF) if note > 0 else 0
                fx = random.randint(0, 0x20) if note > 0 and random.random() > 0.7 else 0
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

        # Advance play position
        if state.playing and state.frame_count % (FPS // 4) == 0:
            state.play_row = (state.play_row + 1) % 16

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
        self._shutdown = asyncio.Event()
        self._last_keys = 0

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
        """Generate and broadcast audio data to connected audio clients."""
        sample_rate = 44100
        chunk_samples = 1024
        chunk_duration = chunk_samples / sample_rate
        phase = 0.0

        while not self._shutdown.is_set():
            if self.audio_clients and self.state.playing:
                # Generate a simple sine wave as raw PCM with 0x01 header
                freq = 440.0 * (2 ** ((self.state.octave - 4) / 1.0))
                samples = bytearray(chunk_samples * 2 * 2)  # 16-bit stereo

                for i in range(chunk_samples):
                    t = phase + i / sample_rate
                    # Mix a few harmonics for a richer sound
                    v = math.sin(2 * math.pi * freq * t) * 0.3
                    v += math.sin(2 * math.pi * freq * 2 * t) * 0.15
                    v += math.sin(2 * math.pi * freq * 0.5 * t) * 0.1
                    sample = max(-32768, min(32767, int(v * 32767)))
                    offset = i * 4
                    struct.pack_into("<hh", samples, offset, sample, sample)

                phase += chunk_samples / sample_rate
                # Keep phase from growing unbounded
                if phase > 1.0:
                    phase -= 1.0

                # Send with 0x01 header (raw PCM marker)
                payload = b"\x01" + bytes(samples)

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

            await asyncio.sleep(chunk_duration)


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
