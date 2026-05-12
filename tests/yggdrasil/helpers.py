from __future__ import annotations

import base64
import hashlib
import json
import struct
from dataclasses import dataclass
from typing import Any, Dict, Iterable, Optional, Set, Tuple
from urllib.parse import urljoin, urlparse

import requests
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding

ALI_HEADER = "X-Authlib-Injector-API-Location"


@dataclass(frozen=True)
class YggSettings:
    base_url: str
    username: Optional[str]
    password: Optional[str]
    profile_id: Optional[str]
    verify_tls: bool
    timeout_seconds: float


@dataclass(frozen=True)
class AuthContext:
    access_token: str
    client_token: str
    profile_id: str
    profile_name: str


class YggClient:
    def __init__(self, settings: YggSettings):
        self.settings = settings
        self.expected_api_location = self._api_location_from_base_url(settings.base_url)

    def _url(self, path: str) -> str:
        return urljoin(self.settings.base_url.rstrip("/") + "/", path.lstrip("/"))

    @staticmethod
    def _api_location_from_base_url(base_url: str) -> str:
        path = urlparse(base_url).path.rstrip("/")
        return path or "/"

    def request(self, method: str, path: str, **kwargs: Any) -> requests.Response:
        kwargs.setdefault("timeout", self.settings.timeout_seconds)
        kwargs.setdefault("verify", self.settings.verify_tls)
        return requests.request(method, self._url(path), **kwargs)

    @staticmethod
    def json_body(response: requests.Response) -> Any:
        try:
            return response.json()
        except json.JSONDecodeError as exc:
            raise AssertionError(
                f"Expected JSON response, got status={response.status_code} body={response.text!r}"
            ) from exc

    def assert_ali_header(self, response: requests.Response) -> None:
        actual = response.headers.get(ALI_HEADER)
        assert actual == self.expected_api_location, (
            f"Missing/invalid {ALI_HEADER}: {actual!r}; "
            f"expected {self.expected_api_location!r}"
        )

    @staticmethod
    def assert_error(
        response: requests.Response,
        expected_status: int,
        expected_error: str,
        message_contains: Optional[str] = None,
    ) -> Dict[str, Any]:
        assert response.status_code == expected_status, response.text
        body = YggClient.json_body(response)
        assert body.get("error") == expected_error, body
        assert "errorMessage" in body, body
        if message_contains is not None:
            assert message_contains in str(body.get("errorMessage")), body
        return body

    def authenticate(
        self,
        username: str,
        password: str,
        client_token: Optional[str] = None,
        request_user: bool = True,
    ) -> requests.Response:
        payload: Dict[str, Any] = {
            "agent": {"name": "Minecraft", "version": 1},
            "username": username,
            "password": password,
            "requestUser": request_user,
        }
        if client_token is not None:
            payload["clientToken"] = client_token
        return self.request("POST", "/authserver/authenticate", json=payload)

    def refresh(
        self,
        access_token: str,
        client_token: Optional[str],
        selected_profile: Optional[Dict[str, str]] = None,
        request_user: bool = True,
    ) -> requests.Response:
        payload: Dict[str, Any] = {
            "accessToken": access_token,
            "requestUser": request_user,
        }
        if client_token is not None:
            payload["clientToken"] = client_token
        if selected_profile is not None:
            payload["selectedProfile"] = selected_profile
        return self.request("POST", "/authserver/refresh", json=payload)

    def validate(self, access_token: str, client_token: Optional[str]) -> requests.Response:
        payload: Dict[str, Any] = {"accessToken": access_token}
        if client_token is not None:
            payload["clientToken"] = client_token
        return self.request("POST", "/authserver/validate", json=payload)

    def invalidate(self, access_token: str, client_token: Optional[str]) -> requests.Response:
        payload: Dict[str, Any] = {"accessToken": access_token}
        if client_token is not None:
            payload["clientToken"] = client_token
        return self.request("POST", "/authserver/invalidate", json=payload)

    def signout(self, username: str, password: str) -> requests.Response:
        return self.request(
            "POST",
            "/authserver/signout",
            json={"username": username, "password": password},
        )

    def join(self, access_token: str, profile_id: str, server_id: str) -> requests.Response:
        return self.request(
            "POST",
            "/sessionserver/session/minecraft/join",
            json={
                "accessToken": access_token,
                "selectedProfile": profile_id,
                "serverId": server_id,
            },
        )

    def has_joined(
        self, username: str, server_id: str, ip: Optional[str] = None
    ) -> requests.Response:
        params: Dict[str, str] = {"username": username, "serverId": server_id}
        if ip is not None:
            params["ip"] = ip
        return self.request("GET", "/sessionserver/session/minecraft/hasJoined", params=params)

    def profile_by_uuid(self, profile_id: str, unsigned: Optional[bool]) -> requests.Response:
        params: Dict[str, str] = {}
        if unsigned is not None:
            params["unsigned"] = "true" if unsigned else "false"
        return self.request(
            "GET",
            f"/sessionserver/session/minecraft/profile/{profile_id}",
            params=params or None,
        )

    def name_to_uuid(self, player_name: str) -> requests.Response:
        return self.request(
            "GET",
            f"/api/users/profiles/minecraft/{player_name}",
        )

    def bulk_profiles(self, names: Iterable[str]) -> requests.Response:
        return self.request("POST", "/api/profiles/minecraft", json=list(names))

    def upload_texture(
        self,
        profile_id: str,
        texture_type: str,
        access_token: Optional[str],
        file_name: str,
        file_bytes: bytes,
        model: Optional[str] = None,
        content_type: str = "image/png",
    ) -> requests.Response:
        headers = {}
        if access_token is not None:
            headers["Authorization"] = f"Bearer {access_token}"
        files = {"file": (file_name, file_bytes, content_type)}
        data = {}
        if model is not None:
            data["model"] = model
        return self.request(
            "PUT",
            f"/api/user/profile/{profile_id}/{texture_type}",
            headers=headers,
            files=files,
            data=data if data else None,
        )

    def delete_texture(
        self, profile_id: str, texture_type: str, access_token: str
    ) -> requests.Response:
        return self.request(
            "DELETE",
            f"/api/user/profile/{profile_id}/{texture_type}",
            headers={"Authorization": f"Bearer {access_token}"},
        )

    def login(
        self, username: str, password: str, preferred_profile_id: Optional[str] = None
    ) -> AuthContext:
        auth = self.authenticate(username=username, password=password, request_user=True)
        assert auth.status_code == 200, auth.text
        self.assert_ali_header(auth)
        body = self.json_body(auth)

        access_token = body["accessToken"]
        client_token = body["clientToken"]
        selected = body.get("selectedProfile")

        if selected is None:
            available = body.get("availableProfiles") or []
            assert available, "authenticate() returned no selectedProfile and no availableProfiles"
            picked = None
            if preferred_profile_id is not None:
                for candidate in available:
                    if candidate.get("id") == preferred_profile_id:
                        picked = candidate
                        break
            if picked is None:
                picked = available[0]

            ref = self.refresh(
                access_token=access_token,
                client_token=client_token,
                selected_profile={"id": picked["id"], "name": picked["name"]},
                request_user=True,
            )
            assert ref.status_code == 200, ref.text
            self.assert_ali_header(ref)
            ref_body = self.json_body(ref)
            return AuthContext(
                access_token=ref_body["accessToken"],
                client_token=ref_body["clientToken"],
                profile_id=ref_body["selectedProfile"]["id"],
                profile_name=ref_body["selectedProfile"]["name"],
            )

        return AuthContext(
            access_token=access_token,
            client_token=client_token,
            profile_id=selected["id"],
            profile_name=selected["name"],
        )


def properties_to_map(profile_body: Dict[str, Any]) -> Dict[str, Dict[str, Any]]:
    result: Dict[str, Dict[str, Any]] = {}
    for prop in profile_body.get("properties", []):
        name = prop.get("name")
        if isinstance(name, str):
            result[name] = prop
    return result


def decode_textures_property(profile_body: Dict[str, Any]) -> Tuple[Dict[str, Any], Dict[str, Any]]:
    props = properties_to_map(profile_body)
    assert "textures" in props, f"textures property not present: {profile_body}"
    textures_prop = props["textures"]
    raw = textures_prop.get("value")
    assert isinstance(raw, str) and raw, f"Invalid textures value: {textures_prop}"
    decoded = base64.b64decode(raw).decode("utf-8")
    return textures_prop, json.loads(decoded)


def parse_uploadable_textures(profile_body: Dict[str, Any]) -> Set[str]:
    props = properties_to_map(profile_body)
    upload = props.get("uploadableTextures")
    if not upload:
        return set()
    value = upload.get("value")
    if not isinstance(value, str) or not value:
        return set()
    return {part.strip().lower() for part in value.split(",") if part.strip()}


def verify_textures_signature(metadata: Dict[str, Any], textures_prop: Dict[str, Any]) -> None:
    signature = textures_prop.get("signature")
    assert isinstance(signature, str) and signature, "textures.signature missing"
    value = textures_prop.get("value")
    assert isinstance(value, str) and value, "textures.value missing"

    pub_pem = metadata.get("signaturePublickey")
    assert isinstance(pub_pem, str) and pub_pem, "metadata.signaturePublickey missing"

    public_key = serialization.load_pem_public_key(pub_pem.encode("utf-8"))
    public_key.verify(
        base64.b64decode(signature),
        value.encode("utf-8"),
        padding.PKCS1v15(),
        hashes.SHA1(),
    )


def make_absolute_url(base_url: str, maybe_relative_url: str) -> str:
    return urljoin(base_url.rstrip("/") + "/", maybe_relative_url)


def sha256_hex(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def png_has_chunk(png_data: bytes, chunk_name: bytes) -> bool:
    if len(chunk_name) != 4:
        raise ValueError("chunk_name must be 4 bytes")
    png_sig = b"\x89PNG\r\n\x1a\n"
    if not png_data.startswith(png_sig):
        return False
    offset = len(png_sig)
    total = len(png_data)
    while offset + 12 <= total:
        length = struct.unpack(">I", png_data[offset : offset + 4])[0]
        ctype = png_data[offset + 4 : offset + 8]
        if ctype == chunk_name:
            return True
        offset += 12 + length
    return False
