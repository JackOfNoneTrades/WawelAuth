#!/usr/bin/env python3
"""Traffic generator for the WawelAuth embedded HTTP server.

Spawns worker threads that loop over a weighted mix of endpoints, plus probe
threads for cheap HTTP metadata and optional Minecraft status ping. The probe
latencies are the interesting before/after number: when handlers run on the
Netty event loop, heavy requests can stall cheap probes and same-port MC status.

Usage:
    python traffic_gen.py --workers 8 --duration 30
    python traffic_gen.py --workers 16 --delay 0 --username foo --password bar
    python traffic_gen.py --scenario fallback --workers 32 --duration 300 --live-interval 5
    python traffic_gen.py --mc-probe --mc-host 127.0.0.1 --mc-port 25565

Use a long duration when you want to join with a client and feel gameplay under
load. Increase --workers and lower --delay until MC status p99/max spikes.
"""

from __future__ import annotations

import argparse
import json
import os
import random
import socket
import threading
import time
import uuid
from collections import Counter, defaultdict
from dataclasses import dataclass
from typing import Callable
from urllib.error import HTTPError
from urllib.parse import quote, urlparse
from urllib.request import Request, urlopen


DEFAULT_BASE_URL = "http://127.0.0.1:25565/auth"
MC_PROTOCOL_1_7_10 = 5


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    p.add_argument(
        "--base-url",
        default=os.getenv("YGG_BASE_URL", DEFAULT_BASE_URL),
        help="Server base URL (default: env YGG_BASE_URL or http://127.0.0.1:25565/auth)",
    )
    p.add_argument("--workers", type=int, default=8, help="Concurrent HTTP worker threads (default: 8)")
    p.add_argument("--duration", type=float, default=30.0, help="Run time in seconds (default: 30)")
    p.add_argument(
        "--delay",
        type=float,
        default=0.05,
        help="Pause per worker between requests in seconds, 0 = hammer (default: 0.05)",
    )
    p.add_argument("--timeout", type=float, default=30.0, help="Per-request timeout (default: 30)")
    p.add_argument("--username", default=os.getenv("YGG_USERNAME"), help="Enable authenticate load (PBKDF2)")
    p.add_argument("--password", default=os.getenv("YGG_PASSWORD"))
    p.add_argument(
        "--scenario",
        choices=("mixed", "fallback", "auth", "cheap"),
        default="mixed",
        help="Preset endpoint weights. Explicit weight args override the preset. (default: mixed)",
    )
    p.add_argument("--metadata-weight", type=int, default=None, help="Weight for GET / metadata load")
    p.add_argument("--profile-name-weight", type=int, default=None, help="Weight for name lookup misses")
    p.add_argument("--profile-uuid-weight", type=int, default=None, help="Weight for UUID profile fallback misses")
    p.add_argument("--hasjoined-weight", type=int, default=None, help="Weight for direct hasJoined fallback misses")
    p.add_argument("--validate-weight", type=int, default=None, help="Weight for bad-token validate requests")
    p.add_argument("--auth-weight", type=int, default=None, help="Weight for authenticate requests when credentials exist")
    p.add_argument(
        "--probe-interval",
        type=float,
        default=0.25,
        help="Interval of the cheap metadata probe in seconds (default: 0.25)",
    )
    p.add_argument(
        "--mc-probe",
        action="store_true",
        help="Also probe Minecraft server-list status on the same port.",
    )
    p.add_argument("--mc-host", default=None, help="Minecraft status host (default: host from --base-url)")
    p.add_argument("--mc-port", type=int, default=None, help="Minecraft status port (default: port from --base-url)")
    p.add_argument(
        "--mc-probe-interval",
        type=float,
        default=1.0,
        help="Interval of Minecraft status probes in seconds (default: 1.0)",
    )
    p.add_argument(
        "--live-interval",
        type=float,
        default=0.0,
        help="Print rolling summaries every N seconds while the test runs. 0 disables. (default: 0)",
    )
    return p.parse_args()


class Stats:
    def __init__(self) -> None:
        self.lock = threading.Lock()
        self.latencies: dict[str, list[float]] = defaultdict(list)
        self.errors: dict[str, int] = defaultdict(int)
        self.statuses: dict[str, Counter[str]] = defaultdict(Counter)

    def record(self, name: str, seconds: float, ok: bool, status: str) -> None:
        with self.lock:
            self.latencies[name].append(seconds * 1000.0)
            self.statuses[name][status] += 1
            if not ok:
                self.errors[name] += 1

    def snapshot(self) -> tuple[dict[str, list[float]], dict[str, int], dict[str, Counter[str]]]:
        with self.lock:
            latencies = {name: list(values) for name, values in self.latencies.items()}
            errors = dict(self.errors)
            statuses = {name: Counter(values) for name, values in self.statuses.items()}
        return latencies, errors, statuses


@dataclass(frozen=True)
class RequestSpec:
    name: str
    method: str
    path_factory: Callable[[], str]
    kwargs_factory: Callable[[], dict]


def percentile(values: list[float], pct: float) -> float:
    if not values:
        return 0.0
    idx = min(len(values) - 1, int(len(values) * pct / 100.0))
    return sorted(values)[idx]


def scenario_weights(scenario: str) -> dict[str, int]:
    if scenario == "cheap":
        return {
            "metadata": 10,
            "profile_name": 0,
            "profile_uuid": 0,
            "hasjoined": 0,
            "validate": 0,
            "auth": 0,
        }
    if scenario == "auth":
        return {
            "metadata": 1,
            "profile_name": 1,
            "profile_uuid": 0,
            "hasjoined": 0,
            "validate": 2,
            "auth": 8,
        }
    if scenario == "fallback":
        return {
            "metadata": 1,
            "profile_name": 0,
            "profile_uuid": 4,
            "hasjoined": 4,
            "validate": 0,
            "auth": 0,
        }
    return {
        "metadata": 1,
        "profile_name": 3,
        "profile_uuid": 3,
        "hasjoined": 0,
        "validate": 2,
        "auth": 2,
    }


def resolved_weights(args: argparse.Namespace) -> dict[str, int]:
    weights = scenario_weights(args.scenario)
    overrides = {
        "metadata": args.metadata_weight,
        "profile_name": args.profile_name_weight,
        "profile_uuid": args.profile_uuid_weight,
        "hasjoined": args.hasjoined_weight,
        "validate": args.validate_weight,
        "auth": args.auth_weight,
    }
    for key, value in overrides.items():
        if value is not None:
            weights[key] = max(0, value)
    return weights


def build_requests(args: argparse.Namespace) -> tuple[list[RequestSpec], dict[str, int]]:
    """Build a weighted endpoint mix from the selected scenario and overrides."""
    weights = resolved_weights(args)
    specs: list[tuple[RequestSpec, int]] = [
        (
            RequestSpec(
                "metadata GET /",
                "GET",
                lambda: "/",
                lambda: {},
            ),
            weights["metadata"],
        ),
        (
            RequestSpec(
                "profile-by-name miss",
                "GET",
                lambda: "/api/users/profiles/minecraft/stress" + str(random.randint(1, 99999999)),
                lambda: {},
            ),
            weights["profile_name"],
        ),
        (
            RequestSpec(
                "profile-by-uuid miss",
                "GET",
                lambda: "/sessionserver/session/minecraft/profile/" + uuid.uuid4().hex,
                lambda: {},
            ),
            weights["profile_uuid"],
        ),
        (
            RequestSpec(
                "hasJoined fallback miss",
                "GET",
                make_hasjoined_path,
                lambda: {},
            ),
            weights["hasjoined"],
        ),
        (
            RequestSpec(
                "validate (bad token)",
                "POST",
                lambda: "/authserver/validate",
                lambda: {"json": {"accessToken": "bogus", "clientToken": "bogus"}},
            ),
            weights["validate"],
        ),
    ]
    if args.username and args.password:
        specs.append(
            (
                RequestSpec(
                    "authenticate (PBKDF2)",
                    "POST",
                    lambda: "/authserver/authenticate",
                    lambda: {
                        "json": {
                            "username": args.username,
                            "password": args.password,
                            "agent": {"name": "Minecraft", "version": 1},
                        }
                    },
                ),
                weights["auth"],
            )
        )
    expanded = []
    for spec, weight in specs:
        expanded.extend([spec] * weight)
    if not expanded:
        raise SystemExit("No request load enabled. Pick a scenario or set at least one endpoint weight > 0.")
    return expanded, weights


def make_hasjoined_path() -> str:
    username = "stress" + str(random.randint(1, 99999999))
    server_id = uuid.uuid4().hex
    return (
        "/sessionserver/session/minecraft/hasJoined?username="
        + quote(username)
        + "&serverId="
        + quote(server_id)
    )


def worker(args: argparse.Namespace, mix: list[RequestSpec], stats: Stats, stop: threading.Event) -> None:
    while not stop.is_set():
        spec = random.choice(mix)
        start = time.perf_counter()
        ok = False
        status_label = "EXC"
        try:
            status = http_request(
                spec.method,
                args.base_url + spec.path_factory(),
                timeout=args.timeout,
                kwargs=spec.kwargs_factory(),
            )
            status_label = str(status)
            ok = status < 500
        except Exception:
            pass
        stats.record(spec.name, time.perf_counter() - start, ok, status_label)
        if args.delay > 0:
            stop.wait(args.delay)


def probe(args: argparse.Namespace, stats: Stats, stop: threading.Event) -> None:
    while not stop.is_set():
        start = time.perf_counter()
        ok = False
        status_label = "EXC"
        try:
            status = http_request("GET", args.base_url + "/", timeout=args.timeout, kwargs={})
            status_label = str(status)
            ok = status < 500
        except Exception:
            pass
        stats.record("PROBE metadata", time.perf_counter() - start, ok, status_label)
        stop.wait(args.probe_interval)


def http_request(method: str, url: str, timeout: float, kwargs: dict) -> int:
    headers = {"Connection": "close"}
    data = None
    if "json" in kwargs:
        data = json.dumps(kwargs["json"]).encode("utf-8")
        headers["Content-Type"] = "application/json"
        headers["Accept"] = "application/json"

    req = Request(url, data=data, headers=headers, method=method)
    try:
        with urlopen(req, timeout=timeout) as resp:
            resp.read(1024)
            return resp.status
    except HTTPError as e:
        e.read(1024)
        return e.code


def mc_status_probe(args: argparse.Namespace, stats: Stats, stop: threading.Event) -> None:
    host, port = resolve_mc_target(args)
    while not stop.is_set():
        start = time.perf_counter()
        ok = False
        status_label = "EXC"
        try:
            minecraft_status(host, port, args.timeout)
            ok = True
            status_label = "OK"
        except Exception:
            pass
        stats.record("PROBE minecraft status", time.perf_counter() - start, ok, status_label)
        stop.wait(args.mc_probe_interval)


def resolve_mc_target(args: argparse.Namespace) -> tuple[str, int]:
    parsed = urlparse(args.base_url)
    host = args.mc_host or parsed.hostname or "127.0.0.1"
    if args.mc_port is not None:
        port = args.mc_port
    elif parsed.port is not None:
        port = parsed.port
    elif parsed.scheme == "https":
        port = 443
    else:
        port = 80
    return host, port


def minecraft_status(host: str, port: int, timeout: float) -> dict:
    with socket.create_connection((host, port), timeout=timeout) as sock:
        sock.settimeout(timeout)
        handshake = (
            write_varint(0)
            + write_varint(MC_PROTOCOL_1_7_10)
            + write_string(host)
            + port.to_bytes(2, byteorder="big", signed=False)
            + write_varint(1)
        )
        send_packet(sock, handshake)
        send_packet(sock, write_varint(0))

        packet = recv_packet(sock)
        packet_id, pos = read_varint(packet, 0)
        if packet_id != 0:
            raise ValueError("unexpected status response packet")
        payload, _ = read_string(packet, pos)
        return json.loads(payload)


def send_packet(sock: socket.socket, payload: bytes) -> None:
    sock.sendall(write_varint(len(payload)) + payload)


def recv_packet(sock: socket.socket) -> bytes:
    length, _ = read_varint_from_socket(sock)
    if length < 0 or length > 1_048_576:
        raise ValueError("invalid packet length")
    data = bytearray()
    while len(data) < length:
        chunk = sock.recv(length - len(data))
        if not chunk:
            raise EOFError("socket closed while reading packet")
        data.extend(chunk)
    return bytes(data)


def write_string(value: str) -> bytes:
    data = value.encode("utf-8")
    return write_varint(len(data)) + data


def read_string(data: bytes, pos: int) -> tuple[str, int]:
    length, pos = read_varint(data, pos)
    end = pos + length
    if end > len(data):
        raise EOFError("string exceeds packet length")
    return data[pos:end].decode("utf-8"), end


def write_varint(value: int) -> bytes:
    out = bytearray()
    value &= 0xFFFFFFFF
    while True:
        b = value & 0x7F
        value >>= 7
        if value:
            out.append(b | 0x80)
        else:
            out.append(b)
            return bytes(out)


def read_varint(data: bytes, pos: int) -> tuple[int, int]:
    value = 0
    for shift in range(0, 35, 7):
        if pos >= len(data):
            raise EOFError("truncated varint")
        b = data[pos]
        pos += 1
        value |= (b & 0x7F) << shift
        if not b & 0x80:
            return value, pos
    raise ValueError("varint too long")


def read_varint_from_socket(sock: socket.socket) -> tuple[int, int]:
    value = 0
    read = 0
    for shift in range(0, 35, 7):
        raw = sock.recv(1)
        if not raw:
            raise EOFError("socket closed while reading varint")
        read += 1
        b = raw[0]
        value |= (b & 0x7F) << shift
        if not b & 0x80:
            return value, read
    raise ValueError("varint too long")


def live_reporter(args: argparse.Namespace, stats: Stats, stop: threading.Event) -> None:
    previous_latencies: dict[str, int] = defaultdict(int)
    previous_errors: dict[str, int] = defaultdict(int)
    previous_statuses: dict[str, Counter[str]] = defaultdict(Counter)
    while not stop.wait(args.live_interval):
        latencies, errors, statuses = stats.snapshot()
        lines = []
        for name in sorted(latencies):
            values = latencies[name]
            start = previous_latencies.get(name, 0)
            window = values[start:]
            previous_latencies[name] = len(values)
            err_delta = errors.get(name, 0) - previous_errors.get(name, 0)
            previous_errors[name] = errors.get(name, 0)
            if not window:
                continue
            status_delta = statuses.get(name, Counter()) - previous_statuses.get(name, Counter())
            previous_statuses[name] = statuses.get(name, Counter())
            lines.append(
                f"{name}: n={len(window)} err={err_delta} "
                f"status={format_status_counts(status_delta)} "
                f"p50={percentile(window, 50):.1f}ms p90={percentile(window, 90):.1f}ms "
                f"p99={percentile(window, 99):.1f}ms max={max(window):.1f}ms"
            )
        if lines:
            print("\n[live] last %.0fs" % args.live_interval)
            for line in lines:
                print("  " + line)


def print_summary(stats: Stats, duration: float) -> None:
    latencies, errors, statuses = stats.snapshot()
    total = sum(len(v) for v in latencies.values())
    duration = max(duration, 0.001)
    duration_label = f"{duration:.1f}" if duration < 10 else f"{duration:.0f}"
    print(f"\n{total} samples in {duration_label}s ({total / duration:.1f} samples/s)\n")
    header = f"{'endpoint':<30} {'count':>6} {'err':>5} {'status':<20} {'p50':>8} {'p90':>8} {'p99':>8} {'max':>8}"
    print(header)
    print("-" * len(header))
    for name in sorted(latencies):
        lat = latencies[name]
        print(
            f"{name:<30} {len(lat):>6} {errors.get(name, 0):>5} "
            f"{format_status_counts(statuses.get(name, Counter())):<20} "
            f"{percentile(lat, 50):>7.1f}ms {percentile(lat, 90):>7.1f}ms "
            f"{percentile(lat, 99):>7.1f}ms {max(lat):>7.1f}ms"
        )


def format_status_counts(counts: Counter[str]) -> str:
    if not counts:
        return "-"
    parts = []
    for status in sorted(counts, key=status_sort_key):
        parts.append(f"{status}={counts[status]}")
    text = ",".join(parts)
    if len(text) <= 20:
        return text
    return text[:17] + "..."


def status_sort_key(value: str) -> tuple[int, str]:
    try:
        return 0, f"{int(value):03d}"
    except ValueError:
        return 1, value


def auth_preflight(args: argparse.Namespace) -> None:
    if not (args.username and args.password):
        return
    start = time.perf_counter()
    try:
        status = http_request(
            "POST",
            args.base_url + "/authserver/authenticate",
            timeout=args.timeout,
            kwargs={
                "json": {
                    "username": args.username,
                    "password": args.password,
                    "agent": {"name": "Minecraft", "version": 1},
                }
            },
        )
        elapsed_ms = (time.perf_counter() - start) * 1000.0
        print(f"auth preflight: status={status} latency={elapsed_ms:.1f}ms")
        if status != 200:
            print("note: status != 200 does not prove PBKDF2 ran; missing users and rate limits can return before hashing\n")
    except Exception as e:
        elapsed_ms = (time.perf_counter() - start) * 1000.0
        print(f"auth preflight: failed after {elapsed_ms:.1f}ms: {e}\n")


def main() -> None:
    args = parse_args()
    mix, weights = build_requests(args)
    if not (args.username and args.password):
        print("note: no --username/--password, skipping authenticate (PBKDF2) load\n")
    if weights["hasjoined"] > 0 or weights["profile_uuid"] > 0:
        print("note: fallback-miss load can call enabled upstream fallback servers for every miss\n")
    if weights["auth"] > 0:
        auth_preflight(args)

    stats = Stats()
    stop = threading.Event()
    threads = [threading.Thread(target=probe, args=(args, stats, stop), daemon=True)]
    if args.mc_probe:
        threads.append(threading.Thread(target=mc_status_probe, args=(args, stats, stop), daemon=True))
    if args.live_interval > 0:
        threads.append(threading.Thread(target=live_reporter, args=(args, stats, stop), daemon=True))
    threads += [
        threading.Thread(target=worker, args=(args, mix, stats, stop), daemon=True) for _ in range(args.workers)
    ]

    print(
        f"target={args.base_url} scenario={args.scenario} workers={args.workers} "
        f"delay={args.delay}s duration={args.duration}s"
    )
    if args.mc_probe:
        host, port = resolve_mc_target(args)
        print(f"mc_probe={host}:{port} interval={args.mc_probe_interval}s")
    print("weights=" + ", ".join(f"{key}={value}" for key, value in sorted(weights.items())))
    started_at = time.perf_counter()
    for t in threads:
        t.start()
    try:
        time.sleep(args.duration)
    except KeyboardInterrupt:
        print("\ninterrupted")
    stop.set()
    for t in threads:
        t.join(timeout=5)

    print_summary(stats, time.perf_counter() - started_at)


if __name__ == "__main__":
    main()
