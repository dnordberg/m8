#!/usr/bin/env python3
"""M8 Headless WebSocket-to-Serial Bridge.

Bridges a Teensy 4.1 running M8 Headless firmware to WebSocket clients,
enabling remote access from Android app or browser.

Usage:
    python3 bridge.py [--serial /dev/ttyACM0] [--baud 9600] [--host 127.0.0.1] [--port 8765]
"""

from __future__ import annotations

import argparse
import asyncio
import glob
import json
import logging
import signal
import sys
from typing import Optional, Set

import serial
import serial.tools.list_ports
import serial_asyncio
import websockets
from websockets.server import WebSocketServerProtocol

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------

TEENSY_VENDOR_ID = 0x16C0  # PJRC vendor ID used by Teensy boards
DEFAULT_BAUD_RATE = 9600
DEFAULT_WS_HOST = "0.0.0.0"
DEFAULT_WS_PORT = 8765
RECONNECT_INTERVAL = 2.0  # Seconds between serial reconnection attempts
SERIAL_READ_SIZE = 4096  # Bytes per serial read

# ---------------------------------------------------------------------------
# Logging
# ---------------------------------------------------------------------------

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)
logger = logging.getLogger("m8-bridge")

# ---------------------------------------------------------------------------
# Serial helpers
# ---------------------------------------------------------------------------


def find_teensy_device() -> Optional[str]:
    """Scan serial ports for a Teensy device by USB vendor ID."""
    for port_info in serial.tools.list_ports.comports():
        if port_info.vid == TEENSY_VENDOR_ID:
            logger.info(
                "Auto-detected Teensy on %s (vid=0x%04X, pid=0x%04X)",
                port_info.device,
                port_info.vid,
                port_info.pid or 0,
            )
            return port_info.device

    # Fallback: check /dev/ttyACM* if list_ports metadata is absent.
    acm_devices = sorted(glob.glob("/dev/ttyACM*"))
    if acm_devices:
        logger.warning(
            "No Teensy vendor ID match; falling back to first ACM device: %s",
            acm_devices[0],
        )
        return acm_devices[0]

    return None


async def wait_for_teensy(device: Optional[str], baud: int) -> serial.Serial:
    """Block until a serial device is available, then open and return it."""
    while True:
        target = device or find_teensy_device()
        if target is not None:
            try:
                ser = serial.Serial(target, baud, timeout=0)
                logger.info("Opened serial port %s at %d baud", target, baud)
                return ser
            except serial.SerialException as exc:
                logger.warning("Failed to open %s: %s", target, exc)
        else:
            logger.info(
                "No M8 Teensy detected -- retrying in %.0fs", RECONNECT_INTERVAL
            )
        await asyncio.sleep(RECONNECT_INTERVAL)


# ---------------------------------------------------------------------------
# Bridge
# ---------------------------------------------------------------------------


class M8Bridge:
    """Bidirectional WebSocket <-> Serial bridge for M8 Headless.

    Serial data is broadcast to every connected WebSocket client. Messages
    from any WebSocket client are forwarded to the serial port.
    """

    def __init__(
        self,
        serial_device: Optional[str],
        baud: int,
        ws_host: str,
        ws_port: int,
    ) -> None:
        self.serial_device = serial_device
        self.baud = baud
        self.ws_host = ws_host
        self.ws_port = ws_port

        self.clients: Set[WebSocketServerProtocol] = set()
        self.serial_port: Optional[serial.Serial] = None
        self.serial_connected = asyncio.Event()
        self._shutdown = asyncio.Event()
        self._reader: Optional[asyncio.StreamReader] = None
        self._writer: Optional[asyncio.StreamWriter] = None

    # -- lifecycle ----------------------------------------------------------

    async def start(self) -> None:
        """Start the bridge: open serial, start WebSocket server, forward."""
        loop = asyncio.get_running_loop()
        for sig in (signal.SIGINT, signal.SIGTERM):
            loop.add_signal_handler(sig, self._request_shutdown)

        await self._open_serial()

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

            reader_task = asyncio.create_task(self._serial_read_loop())

            await self._shutdown.wait()
            logger.info("Shutdown requested -- cleaning up")

            reader_task.cancel()
            try:
                await reader_task
            except asyncio.CancelledError:
                pass

            if self.clients:
                await asyncio.gather(
                    *(
                        ws.close(1001, "server shutting down")
                        for ws in set(self.clients)
                    ),
                    return_exceptions=True,
                )

            self._close_serial()
            server.close()
            await server.wait_closed()

        logger.info("Bridge stopped")

    def _request_shutdown(self) -> None:
        logger.info("Received shutdown signal")
        self._shutdown.set()

    # -- serial management --------------------------------------------------

    async def _open_serial(self) -> None:
        """Open the serial port, waiting until the device is available."""
        self.serial_port = await wait_for_teensy(self.serial_device, self.baud)

        self._reader, self._writer = await serial_asyncio.open_serial_connection(
            url=self.serial_port.port,
            baudrate=self.baud,
        )
        # Close the synchronous serial object -- serial_asyncio opens its own.
        self.serial_port.close()

        self.serial_connected.set()
        await self._broadcast_control(
            "serial_connected", device=self.serial_port.port
        )

    def _close_serial(self) -> None:
        """Close the serial port if open."""
        self.serial_connected.clear()
        if self._writer is not None:
            self._writer.close()
            self._writer = None
        self._reader = None
        logger.info("Serial port closed")

    async def _reconnect_serial(self) -> None:
        """Close current serial connection and attempt to reopen."""
        self._close_serial()
        await self._broadcast_control("serial_disconnected")
        logger.info("Attempting serial reconnection...")
        await self._open_serial()

    # -- serial -> websocket ------------------------------------------------

    async def _serial_read_loop(self) -> None:
        """Continuously read from serial and broadcast to WebSocket clients."""
        while not self._shutdown.is_set():
            await self.serial_connected.wait()

            try:
                assert self._reader is not None
                data = await self._reader.read(SERIAL_READ_SIZE)
                if not data:
                    logger.warning(
                        "Serial read returned empty -- device may be disconnected"
                    )
                    await self._reconnect_serial()
                    continue

                await self._broadcast_bytes(data)

            except (serial.SerialException, OSError) as exc:
                logger.error("Serial read error: %s", exc)
                await self._reconnect_serial()
            except asyncio.CancelledError:
                raise
            except Exception:
                logger.exception("Unexpected error in serial read loop")
                await self._reconnect_serial()

    # -- websocket handler --------------------------------------------------

    async def _ws_handler(self, ws: WebSocketServerProtocol) -> None:
        """Handle a single WebSocket client connection."""
        remote = ws.remote_address
        logger.info("Client connected: %s:%s", remote[0], remote[1])
        self.clients.add(ws)

        if self.serial_connected.is_set():
            try:
                device = self.serial_device or "(auto-detected)"
                await ws.send(
                    json.dumps({"event": "serial_connected", "device": device})
                )
            except websockets.ConnectionClosed:
                self.clients.discard(ws)
                return

        try:
            async for message in ws:
                if not self.serial_connected.is_set():
                    logger.warning("Dropping WS message -- serial not connected")
                    continue

                data = message if isinstance(message, bytes) else message.encode()
                try:
                    assert self._writer is not None
                    self._writer.write(data)
                    await self._writer.drain()
                except (serial.SerialException, OSError) as exc:
                    logger.error("Serial write error: %s", exc)
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
        description="M8 Headless WebSocket-to-Serial Bridge",
    )
    parser.add_argument(
        "--serial",
        default=None,
        help="Serial device path (default: auto-detect Teensy)",
    )
    parser.add_argument(
        "--baud",
        type=int,
        default=DEFAULT_BAUD_RATE,
        help=f"Serial baud rate (default: {DEFAULT_BAUD_RATE})",
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

    logger.info("Starting M8 Bridge")
    logger.info(
        "  Serial : %s @ %d baud", args.serial or "(auto-detect)", args.baud
    )
    logger.info("  WS     : ws://%s:%d", args.host, args.port)

    bridge = M8Bridge(
        serial_device=args.serial,
        baud=args.baud,
        ws_host=args.host,
        ws_port=args.port,
    )

    try:
        asyncio.run(bridge.start())
    except KeyboardInterrupt:
        pass

    logger.info("Exiting")


if __name__ == "__main__":
    main()
