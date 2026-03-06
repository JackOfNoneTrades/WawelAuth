import os
import shutil
import subprocess
from pathlib import Path

import pytest


REPO_ROOT = Path(__file__).resolve().parents[2]
MAIN_CLASSES = REPO_ROOT / "build" / "classes" / "java" / "main"

HARNESS_SOURCE = """
import java.net.InetSocketAddress;
import java.net.SocketAddress;

import org.fentanylsolutions.wawelauth.wawelcore.util.NetworkAddressUtil;

public final class NetworkAddressHarness {
    private NetworkAddressHarness() {}

    private static final class FakeSocketAddress extends SocketAddress {
        private static final long serialVersionUID = 1L;
        private final String raw;

        FakeSocketAddress(String raw) {
            this.raw = raw;
        }

        @Override
        public String toString() {
            return raw;
        }
    }

    public static void main(String[] args) {
        String mode = args[0];
        if ("format".equals(mode)) {
            String host = "__NULL__".equals(args[1]) ? null : args[1];
            int port = Integer.parseInt(args[2]);
            System.out.print(NetworkAddressUtil.formatHostPort(host, port));
            return;
        }
        if ("parse".equals(mode)) {
            String raw = "__NULL__".equals(args[1]) ? null : args[1];
            System.out.print(NetworkAddressUtil.extractIpFromSocketString(raw));
            return;
        }
        if ("looks".equals(mode)) {
            String value = "__NULL__".equals(args[1]) ? null : args[1];
            System.out.print(NetworkAddressUtil.looksLikeIp(value));
            return;
        }
        if ("extractInet".equals(mode)) {
            InetSocketAddress address = new InetSocketAddress(args[1], Integer.parseInt(args[2]));
            System.out.print(NetworkAddressUtil.extractIp(address));
            return;
        }
        if ("extractFake".equals(mode)) {
            System.out.print(NetworkAddressUtil.extractIp(new FakeSocketAddress(args[1])));
            return;
        }
        throw new IllegalArgumentException("Unknown mode: " + mode);
    }
}
""".strip()


def _compile_main_classes():
    subprocess.run(
        ["./gradlew", "-q", "compileJava"],
        cwd=REPO_ROOT,
        check=True,
        capture_output=True,
        text=True,
    )


@pytest.fixture(scope="session")
def java_harness(tmp_path_factory):
    if shutil.which("javac") is None or shutil.which("java") is None:
        pytest.skip("JDK not available in PATH")

    has_main_classes = MAIN_CLASSES.exists() and any(MAIN_CLASSES.rglob("*.class"))
    if not has_main_classes:
        try:
            _compile_main_classes()
        except subprocess.CalledProcessError as exc:
            pytest.skip(f"Unable to compile Java classes for harness: {exc}")

    if not MAIN_CLASSES.exists() or not any(MAIN_CLASSES.rglob("*.class")):
        pytest.skip("Main Java classes not found for harness")

    harness_dir = tmp_path_factory.mktemp("network_address_harness")
    src_file = harness_dir / "NetworkAddressHarness.java"
    src_file.write_text(HARNESS_SOURCE, encoding="utf-8")

    subprocess.run(
        ["javac", "-cp", str(MAIN_CLASSES), str(src_file)],
        cwd=harness_dir,
        check=True,
        capture_output=True,
        text=True,
    )

    classpath = os.pathsep.join([str(MAIN_CLASSES), str(harness_dir)])
    return classpath


def _run_harness(classpath: str, *args: str) -> str:
    result = subprocess.run(
        ["java", "-cp", classpath, "NetworkAddressHarness", *args],
        cwd=REPO_ROOT,
        check=True,
        capture_output=True,
        text=True,
    )
    return result.stdout.strip()


def test_format_host_port_ipv4(java_harness):
    assert _run_harness(java_harness, "format", "127.0.0.1", "25565") == "127.0.0.1:25565"


def test_format_host_port_ipv6_adds_brackets(java_harness):
    assert _run_harness(java_harness, "format", "2001:db8::1", "25565") == "[2001:db8::1]:25565"


def test_format_host_port_ipv6_keeps_existing_brackets(java_harness):
    assert _run_harness(java_harness, "format", "[2001:db8::1]", "25565") == "[2001:db8::1]:25565"


def test_extract_ip_from_socket_string_ipv4(java_harness):
    assert _run_harness(java_harness, "parse", "/127.0.0.1:25565") == "127.0.0.1"


def test_extract_ip_from_socket_string_ipv6(java_harness):
    assert _run_harness(java_harness, "parse", "/[2001:db8::1]:25565") == "2001:db8::1"


def test_extract_ip_from_socket_string_with_hostname_prefix(java_harness):
    assert _run_harness(java_harness, "parse", "hostname/127.0.0.1:25565") == "127.0.0.1"


def test_extract_ip_from_inet_socket_address(java_harness):
    assert _run_harness(java_harness, "extractInet", "127.0.0.1", "25565") == "127.0.0.1"


def test_extract_ip_from_fallback_socket_to_string(java_harness):
    assert _run_harness(java_harness, "extractFake", "/[2001:db8::1]:25565") == "2001:db8::1"


@pytest.mark.parametrize(
    "value,expected",
    [
        ("127.0.0.1", "true"),
        ("2001:db8::1", "true"),
        ("example.com", "true"),
        ("__NULL__", "false"),
    ],
)
def test_looks_like_ip(java_harness, value, expected):
    assert _run_harness(java_harness, "looks", value) == expected
