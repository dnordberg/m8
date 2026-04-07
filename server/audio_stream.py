#!/usr/bin/env python3
"""M8 Headless Audio Streaming Server.

Captures USB audio from a Teensy 4.1 running M8 Headless firmware and
streams Opus-encoded audio to connected WebSocket clients.

Usage:
    python3 audio_stream.py [--device hw:Teensy] [--host 127.0.0.1] [--port 8766]
"""

from __future__ import annotations

import argparse
import asyncio
import json
import logging
import signal
import struct
import subprocess
import sys
from pathlib import Path
from typing import Optional, Set

import websockets
from websockets.server import WebSocketServerProtocol

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------

TEENSY_VENDOR_ID = 0x16C0  # PJRC vendor ID used by Teensy boards
DEFAULT_WS_HOST = "127.0.0.1"
DEFAULT_WS_PORT = 8766
DEFAULT_SAMPLE_RATE = 44100
DEFAULT_CHANNELS = 2
DEFAULT_BITRATE = 128000  # 128 kbps Opus
RECONNECT_INTERVAL = 2.0  # Seconds between device reconnection attempts
FRAME_DURATION_MS = 20  # Opus frame duration in milliseconds
# Bytes per frame: sample_rate * channels * 2 (16-bit) * frame_duration / 1000
# e.g., 44100 * 2 * 2 * 20 / 1000 = 3528 bytes per 20ms frame

# ---------------------------------------------------------------------------
# Logging
# ---------------------------------------------------------------------------

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)
logger = logging.getLogger("m8-audio")

# ---------------------------------------------------------------------------
# ALSA device detection
# ---------------------------------------------------------------------------


def find_teensy_audio_device() -> Optional[str]:
    """Scan ALSA capture devices for a Teensy audio interface.

    Checks both /proc/asound for USB vendor ID and arecord -L output
    for device names containing 'Teensy'.
    """
    # Strategy 1: Check /proc/asound/cards for Teensy by name.
    try:
        cards_path = Path("/proc/asound/cards")
        if cards_path.exists():
            cards_text = cards_path.read_text()
            for line in cards_text.splitlines():
                line_stripped = line.strip()
                # Lines look like: " 1 [Teensy        ]: USB-Audio - Teensy MIDI/Audio"
                if "Teensy" in line_stripped or "teensy" in line_stripped.lower():
                    # Extract card number from beginning of line.
                    parts = line_stripped.split()
                    if parts and parts[0].isdigit():
                        card_num = parts[0]
                        device = f"hw:{card_num},0"
                        logger.info(
                            "Auto-detected Teensy audio on ALSA card %s (%s)",
                            card_num,
                            device,
                        )
                        return device
    except OSError as exc:
        logger.debug("Could not read /proc/asound/cards: %s", exc)

    # Strategy 2: Check USB device vendor IDs in /proc/asound.
    try:
        asound_path = Path("/proc/asound")
        if asound_path.exists():
            for card_dir in sorted(asound_path.iterdir()):
                if not card_dir.name.startswith("card"):
                    continue
                usbid_path = card_dir / "usbid"
                if usbid_path.exists():
                    usbid = usbid_path.read_text().strip().lower()
                    # Teensy vendor ID is 16c0.
                    if usbid.startswith("16c0:"):
                        card_num = card_dir.name.replace("card", "")
                        device = f"hw:{card_num},0"
                        logger.info(
                            "Auto-detected Teensy audio via USB ID %s on %s",
                            usbid,
                            device,
                        )
                        return device
    except OSError as exc:
        logger.debug("Could not scan /proc/asound: %s", exc)

    # Strategy 3: Try arecord -l and look for Teensy.
    try:
        result = subprocess.run(
            ["arecord", "-l"],
            capture_output=True,
            text=True,
            timeout=5,
        )
        for line in result.stdout.splitlines():
            if "teensy" in line.lower():
                # Lines look like: "card 1: Teensy [Teensy], device 0: USB Audio [USB Audio]"
                parts = line.split(":")
                if parts:
                    card_part = parts[0].strip()
                    card_tokens = card_part.split()
                    for i, token in enumerate(card_tokens):
                        if token == "card" and i + 1 < len(card_tokens):
                            card_num = card_tokens[i + 1].rstrip(",")
                            device = f"hw:{card_num},0"
                            logger.info(
                                "Auto-detected Teensy audio via arecord: %s",
                                device,
                            )
                            return device
    except (subprocess.SubprocessError, FileNotFoundError) as exc:
        logger.debug("arecord scan failed: %s", exc)

    return None


def check_device_available(device: str) -> bool:
    """Test whether an ALSA capture device is currently available."""
    try:
        result = subprocess.run(
            ["arecord", "-D", device, "-d", "0", "-f", "S16_LE", "-c", "2", "-r", "44100", "/dev/null"],
            capture_output=True,
            text=True,
            timeout=3,
        )
        # arecord -d 0 will exit quickly; a zero-length recording means the device works.
        return result.returncode == 0
    except (subprocess.SubprocessError, FileNotFoundError):
        return False


# ---------------------------------------------------------------------------
# Audio Streamer
# ---------------------------------------------------------------------------


class M8AudioStreamer:
    """Captures audio from Teensy USB and streams raw PCM via WebSocket.

    Audio is captured using arecord (ALSA) as raw S16_LE PCM and streamed
    directly to WebSocket clients with a 1-byte format header (0x01 = PCM).
    Opus encoding is skipped since bandwidth over Tailscale LAN is sufficient.
    """

    def __init__(
        self,
        device: Optional[str],
        ws_host: str,
        ws_port: int,
        sample_rate: int,
        channels: int,
        bitrate: int,
    ) -> None:
        self.device = device
        self.ws_host = ws_host
        self.ws_port = ws_port
        self.sample_rate = sample_rate
        self.channels = channels
        self.bitrate = bitrate

        self.clients: Set[WebSocketServerProtocol] = set()
        self.audio_connected = asyncio.Event()
        self._shutdown = asyncio.Event()
        self._capture_process: Optional[asyncio.subprocess.Process] = None
        self._active_device: Optional[str] = None

    # -- lifecycle ----------------------------------------------------------

    async def start(self) -> None:
        """Start the audio streamer: detect device, start WebSocket server, stream."""
        loop = asyncio.get_running_loop()
        for sig in (signal.SIGINT, signal.SIGTERM):
            loop.add_signal_handler(sig, self._request_shutdown)

        async with websockets.serve(
            self._ws_handler,
            self.ws_host,
            self.ws_port,
            ping_interval=20,
            ping_timeout=20,
        ) as server:
            logger.info(
                "WebSocket server listening on ws://%s:%d", self.ws_host, self.ws_port
            )

            capture_task = asyncio.create_task(self._capture_loop())

            await self._shutdown.wait()
            logger.info("Shutdown requested -- cleaning up")

            capture_task.cancel()
            try:
                await capture_task
            except asyncio.CancelledError:
                pass

            await self._stop_capture()

            if self.clients:
                await asyncio.gather(
                    *(
                        ws.close(1001, "server shutting down")
                        for ws in set(self.clients)
                    ),
                    return_exceptions=True,
                )

            server.close()
            await server.wait_closed()

        logger.info("Audio streamer stopped")

    def _request_shutdown(self) -> None:
        logger.info("Received shutdown signal")
        self._shutdown.set()

    # -- audio device management --------------------------------------------

    async def _wait_for_device(self) -> str:
        """Block until a Teensy audio device is available, return ALSA device string."""
        while not self._shutdown.is_set():
            target = self.device or find_teensy_audio_device()
            if target is not None:
                if check_device_available(target):
                    logger.info("Audio device ready: %s", target)
                    return target
                else:
                    logger.warning(
                        "Device %s found but not available for capture", target
                    )
            else:
                logger.info(
                    "No Teensy audio device detected -- retrying in %.0fs",
                    RECONNECT_INTERVAL,
                )
            await asyncio.sleep(RECONNECT_INTERVAL)
        raise asyncio.CancelledError("Shutdown during device wait")

    async def _start_capture(self, device: str) -> None:
        """Start the arecord raw PCM capture process.

        Streams raw S16_LE PCM directly from arecord — no Opus encoding.
        Each WebSocket message is prepended with a 1-byte format header
        (0x01 = raw PCM) so the client can identify the format.
        """
        self._active_device = device

        arecord_cmd = [
            "arecord",
            "-D", device,
            "-f", "S16_LE",
            "-c", str(self.channels),
            "-r", str(self.sample_rate),
            "-t", "raw",      # Raw PCM output, no WAV header
            "--buffer-size", "4096",
        ]

        logger.info("Starting capture: %s", " ".join(arecord_cmd))

        # Create arecord process — its stdout gives us raw PCM.
        arecord_proc = await asyncio.create_subprocess_exec(
            *arecord_cmd,
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE,
        )

        self._arecord_process = arecord_proc
        self._capture_process = arecord_proc  # same process for raw PCM
        self.audio_connected.set()

        await self._broadcast_control(
            "audio_connected", device=device
        )

        logger.info("Audio capture started on %s (raw PCM, %d Hz, %d ch)",
                     device, self.sample_rate, self.channels)

    async def _stop_capture(self) -> None:
        """Stop the capture pipeline."""
        self.audio_connected.clear()

        for proc_attr in ("_arecord_process", "_capture_process"):
            proc = getattr(self, proc_attr, None)
            if proc is not None:
                try:
                    proc.terminate()
                    try:
                        await asyncio.wait_for(proc.wait(), timeout=3.0)
                    except asyncio.TimeoutError:
                        logger.warning("Process %s did not exit, killing", proc_attr)
                        proc.kill()
                        await proc.wait()
                except ProcessLookupError:
                    pass  # Already exited
                setattr(self, proc_attr, None)

        if self._active_device:
            logger.info("Audio capture stopped on %s", self._active_device)
        self._active_device = None

    async def _reconnect_capture(self) -> None:
        """Stop current capture and attempt to restart."""
        await self._stop_capture()
        await self._broadcast_control("audio_disconnected")
        logger.info("Attempting audio device reconnection...")

    # -- capture -> websocket -----------------------------------------------

    async def _capture_loop(self) -> None:
        """Continuously capture audio and broadcast to WebSocket clients.

        Handles device detection, reconnection on USB disconnect, and
        continuous streaming.
        """
        while not self._shutdown.is_set():
            try:
                device = await self._wait_for_device()
                await self._start_capture(device)

                # Read raw PCM data and broadcast to clients.
                assert self._capture_process is not None
                assert self._capture_process.stdout is not None

                # PCM format header byte (0x01 = raw PCM).
                FORMAT_RAW_PCM = b"\x01"

                # Read raw PCM in 4KB chunks (~23ms at 44100Hz stereo 16-bit).
                chunk_size = 4096

                while not self._shutdown.is_set():
                    data = await self._capture_process.stdout.read(chunk_size)
                    if not data:
                        # Process ended (device disconnected or error).
                        retcode = await self._capture_process.wait()
                        logger.warning(
                            "Capture pipeline ended (exit code %s) -- "
                            "device may be disconnected",
                            retcode,
                        )
                        # Check stderr for diagnostics.
                        stderr_data = b""
                        if self._capture_process.stderr:
                            try:
                                stderr_data = await asyncio.wait_for(
                                    self._capture_process.stderr.read(),
                                    timeout=1.0,
                                )
                            except asyncio.TimeoutError:
                                pass
                        if stderr_data:
                            logger.debug(
                                "Capture stderr: %s",
                                stderr_data.decode(errors="replace").strip(),
                            )
                        await self._reconnect_capture()
                        break

                    # Prepend format header (0x01 = raw PCM) so client
                    # can distinguish PCM from Opus frames.
                    await self._broadcast_bytes(FORMAT_RAW_PCM + data)

            except asyncio.CancelledError:
                raise
            except Exception:
                logger.exception("Unexpected error in capture loop")
                await self._reconnect_capture()
                await asyncio.sleep(RECONNECT_INTERVAL)

    # -- websocket handler --------------------------------------------------

    async def _ws_handler(self, ws: WebSocketServerProtocol) -> None:
        """Handle a single WebSocket client connection."""
        remote = ws.remote_address
        logger.info("Client connected: %s:%s", remote[0], remote[1])
        self.clients.add(ws)

        # Send current status.
        if self.audio_connected.is_set() and self._active_device:
            try:
                await ws.send(
                    json.dumps({
                        "event": "audio_connected",
                        "device": self._active_device,
                        "sample_rate": self.sample_rate,
                        "channels": self.channels,
                        "format": "s16le",
                        "codec": "pcm",
                    })
                )
            except websockets.ConnectionClosed:
                self.clients.discard(ws)
                return

        try:
            # Audio streaming is unidirectional (server -> client).
            # We still consume incoming messages to handle pings/control.
            async for message in ws:
                # Future: could handle volume control, mute, etc.
                if isinstance(message, str):
                    try:
                        msg = json.loads(message)
                        event = msg.get("event")
                        if event == "ping":
                            await ws.send(json.dumps({"event": "pong"}))
                        elif event == "status":
                            await ws.send(json.dumps({
                                "event": "status",
                                "audio_connected": self.audio_connected.is_set(),
                                "device": self._active_device,
                                "clients": len(self.clients),
                            }))
                        else:
                            logger.debug("Unknown client event: %s", event)
                    except json.JSONDecodeError:
                        logger.debug("Non-JSON message from client: %s", message[:100])
        except websockets.ConnectionClosed:
            pass
        finally:
            self.clients.discard(ws)
            logger.info("Client disconnected: %s:%s", remote[0], remote[1])

    # -- broadcast helpers --------------------------------------------------

    async def _broadcast_bytes(self, data: bytes) -> None:
        """Send raw bytes to every connected WebSocket client."""
        if not self.clients:
            return
        stale: list[WebSocketServerProtocol] = []
        results = await asyncio.gather(
            *(ws.send(data) for ws in self.clients),
            return_exceptions=True,
        )
        for ws, result in zip(self.clients, results):
            if isinstance(result, Exception):
                stale.append(ws)
        for ws in stale:
            self.clients.discard(ws)

    async def _broadcast_control(self, event: str, **kwargs: str) -> None:
        """Send a JSON control message to every connected WebSocket client."""
        payload = json.dumps({"event": event, **kwargs})
        if not self.clients:
            return
        stale: list[WebSocketServerProtocol] = []
        results = await asyncio.gather(
            *(ws.send(payload) for ws in self.clients),
            return_exceptions=True,
        )
        for ws, result in zip(self.clients, results):
            if isinstance(result, Exception):
                stale.append(ws)
        for ws in stale:
            self.clients.discard(ws)


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------


def parse_args(argv: Optional[list[str]] = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="M8 Headless Audio Streaming Server",
    )
    parser.add_argument(
        "--device",
        default=None,
        help="ALSA capture device (default: auto-detect Teensy, e.g. hw:1,0)",
    )
    parser.add_argument(
        "--host",
        default=DEFAULT_WS_HOST,
        help=f"WebSocket bind address (default: {DEFAULT_WS_HOST})",
    )
    parser.add_argument(
        "--port",
        type=int,
        default=DEFAULT_WS_PORT,
        help=f"WebSocket port (default: {DEFAULT_WS_PORT})",
    )
    parser.add_argument(
        "--sample-rate",
        type=int,
        default=DEFAULT_SAMPLE_RATE,
        help=f"Audio sample rate in Hz (default: {DEFAULT_SAMPLE_RATE})",
    )
    parser.add_argument(
        "--channels",
        type=int,
        default=DEFAULT_CHANNELS,
        help=f"Audio channels (default: {DEFAULT_CHANNELS})",
    )
    parser.add_argument(
        "--bitrate",
        type=int,
        default=DEFAULT_BITRATE,
        help=f"Opus encoding bitrate in bps (default: {DEFAULT_BITRATE})",
    )
    parser.add_argument(
        "--verbose",
        "-v",
        action="store_true",
        help="Enable debug logging",
    )
    return parser.parse_args(argv)


def main(argv: Optional[list[str]] = None) -> None:
    args = parse_args(argv)

    if args.verbose:
        logging.getLogger().setLevel(logging.DEBUG)

    logger.info("Starting M8 Audio Streamer")
    logger.info("  Device : %s", args.device or "(auto-detect)")
    logger.info("  WS     : ws://%s:%d", args.host, args.port)
    logger.info(
        "  Audio  : %d Hz, %d ch, raw PCM (S16_LE)",
        args.sample_rate,
        args.channels,
    )

    # Verify that arecord is available.
    try:
        subprocess.run(
            ["arecord", "--version"],
            capture_output=True,
            timeout=5,
        )
    except FileNotFoundError:
        logger.error("arecord not found. Install: apt-get install alsa-utils")
        sys.exit(1)
    except subprocess.SubprocessError:
        pass  # --version may return non-zero; that's OK, binary exists.

    streamer = M8AudioStreamer(
        device=args.device,
        ws_host=args.host,
        ws_port=args.port,
        sample_rate=args.sample_rate,
        channels=args.channels,
        bitrate=args.bitrate,
    )

    try:
        asyncio.run(streamer.start())
    except KeyboardInterrupt:
        pass

    logger.info("Exiting")


if __name__ == "__main__":
    main()
