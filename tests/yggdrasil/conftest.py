from __future__ import annotations

import os
from pathlib import Path

import pytest

from .helpers import AuthContext, YggClient, YggSettings


def _env_flag(name: str, default: bool) -> bool:
    raw = os.getenv(name)
    if raw is None:
        return default
    return raw.strip().lower() in {"1", "true", "yes", "on"}


@pytest.fixture(scope="session")
def settings() -> YggSettings:
    return YggSettings(
        base_url=os.getenv("YGG_BASE_URL", "http://127.0.0.1:25565/auth"),
        username=os.getenv("YGG_USERNAME"),
        password=os.getenv("YGG_PASSWORD"),
        profile_id=os.getenv("YGG_PROFILE_ID"),
        verify_tls=_env_flag("YGG_VERIFY_TLS", False),
        timeout_seconds=float(os.getenv("YGG_TIMEOUT_SECONDS", "8.0")),
    )


@pytest.fixture(scope="session")
def client(settings: YggSettings) -> YggClient:
    return YggClient(settings)


@pytest.fixture(scope="session")
def metadata(client: YggClient) -> dict:
    response = client.request("GET", "/")
    assert response.status_code == 200, response.text
    client.assert_ali_header(response)
    return client.json_body(response)


@pytest.fixture
def require_credentials(settings: YggSettings) -> None:
    if not settings.username or not settings.password:
        pytest.skip("Set YGG_USERNAME and YGG_PASSWORD to run authenticated tests.")


@pytest.fixture
def auth_context(
    client: YggClient, settings: YggSettings, require_credentials: None
) -> AuthContext:
    assert settings.username is not None
    assert settings.password is not None
    return client.login(
        username=settings.username,
        password=settings.password,
        preferred_profile_id=settings.profile_id,
    )


@pytest.fixture(scope="session")
def notch_skin_png_path() -> Path:
    path = Path(__file__).parent / "fixtures" / "net" / "notch_skin.png"
    assert path.exists(), f"Missing fixture file: {path}"
    return path


@pytest.fixture(scope="session")
def jeb_cape_png_path() -> Path:
    path = Path(__file__).parent / "fixtures" / "net" / "jeb_cape.png"
    assert path.exists(), f"Missing fixture file: {path}"
    return path
