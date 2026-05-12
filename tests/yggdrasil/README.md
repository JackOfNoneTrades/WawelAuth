# Yggdrasil Compliance Tests (Pytest)

This suite validates the WawelAuth Yggdrasil implementation against the
`authlib-docs` spec, including auth/session/profile/texture flows.

## What It Tests

- API metadata shape and ALI header (`X-Authlib-Injector-API-Location`)
- Error schema/status behavior on malformed and missing routes
- Auth lifecycle:
  - `authenticate`
  - `refresh`
  - `validate`
  - `invalidate`
  - `signout`
- Session handshake:
  - `join`
  - `hasJoined`
- Profile APIs:
  - profile by UUID (signed + unsigned behavior)
  - bulk lookup by names
- Texture APIs:
  - upload/delete
  - texture file fetch
  - invalid upload handling
  - PNG metadata sanitization checks

## Internet-Sourced Fixtures

These fixtures were downloaded from Mojang texture CDN:

- `fixtures/net/notch_skin.png`
  - Source: `https://textures.minecraft.net/texture/292009a4925b58f02c77dadc3ecef07ea4c7472f64e0fdc32ce5522489362680`
- `fixtures/net/jeb_cape.png`
  - Source: `https://textures.minecraft.net/texture/9e507afc56359978a3eb3e32367042b853cddd0995d17d0da995662913fb00f7`

## Prerequisites

- Python 3.9+
- A running WawelAuth server instance reachable over HTTP/HTTPS
- At least one valid account for authenticated tests

Optional:

- A profile with `uploadableTextures` including `skin` and/or `cape` to run upload round-trip tests.
  Upload tests are skipped automatically when permissions are missing.

## Setup

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r tests/yggdrasil/requirements.txt
```

## Environment Variables

- `YGG_BASE_URL` (default: `http://127.0.0.1:25565/auth`)
- `YGG_USERNAME` (required for authenticated tests)
- `YGG_PASSWORD` (required for authenticated tests)
- `YGG_PROFILE_ID` (optional preferred profile UUID without dashes)
- `YGG_VERIFY_TLS` (`true`/`false`, default `false`)
- `YGG_TIMEOUT_SECONDS` (default `8.0`)

Example:

```bash
export YGG_BASE_URL="http://127.0.0.1:25565/auth"
export YGG_USERNAME="test_user"
export YGG_PASSWORD="test_password"
pytest -q tests/yggdrasil -m integration
```
