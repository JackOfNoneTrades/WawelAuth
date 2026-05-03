# WawelAuth Implementation Spec

This document is an implementation-derived specification for the current codebase. It is intended to be the source material for user-facing documentation, admin guides, and API docs.

This spec is based on code and tests in the repository, not on `README.md`.

## 1. Product Summary

WawelAuth is a Minecraft 1.7.10 Forge mod that combines:

- A client-side account manager for multiple authentication providers.
- A server-side Yggdrasil-compatible auth/session/profile service.
- A fallback-provider bridge for Mojang and other authlib-injector-compatible ecosystems.
- Client rendering improvements for modern skins, HD skins, animated capes, and optional 3D skin layers.
- A server admin web UI and a customizable public landing page.

At a high level, the mod lets a 1.7.10 client and/or server participate in a modern multi-provider identity flow without replacing the base game transport.

## 2. Compatibility and Runtime Model

### Supported platform

- Minecraft `1.7.10`
- Forge `10.13.4.1614`
- Mod id: `wawelauth`

### Declared dependencies

- Required: UniMixins
- Required: FentLib
- Required on client only: ModularUI2
- Optional: TabFaces for extra face rendering integration

### Deployment modes

WawelAuth supports three practical deployment modes:

1. Client only
   The player gets provider management, account switching, texture handling, local/offline accounts, singleplayer account selection, and rendering features.
2. Server only
   The server exposes WawelAuth HTTP endpoints, local account management, fallback-provider verification, admin UI, public page, and provider-aware player-list tooling.
3. Client + server
   This is the full feature set. Mixed-provider skin handling, provider-aware runtime trust, player-provider sync, and WawelAuth-specific convenience flows work best in this mode.

### Dedicated-server requirement

The dedicated-server module only starts when all of the following are true:

- `server.json` has `wawelAuthEnabled=true`
- the server is not an integrated/singleplayer server
- the server is running in `online-mode=true`
- `publicBaseUrl` is configured
- `apiRoot` is a relative path, not a full URL
- the mod reaches early dedicated-server startup

Behavioral notes:

- Integrated singleplayer never starts the WawelAuth server module.
- On dedicated servers, invalid startup conditions are rejected before world load.
- Dedicated startup hard-stops with a visible error if `online-mode=false`.
- Dedicated startup hard-stops with a visible error if `publicBaseUrl` is missing.
- Dedicated startup hard-stops with a visible error if `apiRoot` looks like an absolute URL instead of a relative path.
- For CI smoke tests only, missing `publicBaseUrl` is bypassed when `WAWELAUTH_CI` or `GITHUB_ACTIONS` is truthy.

## 3. Architecture Overview

### 3.1 Client-side subsystems

- `WawelClient`
  Owns the client database, provider registry, account manager, session bridge, local-auth provider resolver, connection provider cache, and texture resolver.
- `ProviderRegistry`
  Seeds default providers, manages custom providers, performs ALI discovery, and stores provider trust data such as public-key fingerprints.
- `AccountManager`
  Authenticates accounts, refreshes/validates tokens in the background, manages offline accounts, and performs texture upload/reset operations.
- `SessionBridge`
  Swaps the active Minecraft session, redirects `joinServer`, builds connection trust sets, validates texture signatures/domains, and imports the launcher's Mojang session when possible.
- UI layer
  Provides the account manager, login/register dialogs, provider management, per-server account picker, local-auth trust/login flow, texture selection/upload UI, and singleplayer account selection.

### 3.2 Server-side subsystems

- `WawelServer`
  Composition root for the whole server module.
- `AuthService`
  Implements Yggdrasil auth endpoints and WawelAuth credential-management extensions.
- `SessionService`
  Implements `join`, `hasJoined`, and profile lookup behavior.
- `TextureService`
  Handles upload/delete validation, sanitization, storage, and cache invalidation.
- `FallbackProxyService`
  Resolves fallback profile/session lookups and proxies fallback textures.
- `LocalSessionVerifier`
  Hooks the vanilla login pipeline and checks local auth first, then configured fallbacks.
- `AdminWebService`
  When enabled, serves `/admin` and `/api/wawelauth/admin/*`; when disabled, mounts no admin routes.
- `PublicPageService`
  Serves a customizable public site and a machine-readable public info API.

### 3.3 Shared transport layer

WawelAuth multiplexes HTTP and Minecraft traffic on the same server port.

- Incoming connections are sniffed by `ProtocolSwitchHandler`.
- HTTP methods (`GET`, `POST`, `PUT`, `DELETE`, `HEAD`, `OPTIONS`, `PATCH`) are switched into an HTTP pipeline.
- All other traffic stays on the vanilla Minecraft pipeline.

This means a WawelAuth server can expose its auth/admin/public HTTP surface on the same socket used for Minecraft gameplay.

## 4. Core Concepts and Data Model

### 4.1 Provider

A provider is an auth ecosystem definition stored on the client.

Fields of note:

- human-readable `name` as the primary key
- `type`: `BUILTIN`, `DEFAULT`, or `CUSTOM`
- `apiRoot`
- `authServerUrl`
- `sessionServerUrl`
- `servicesUrl`
- `skinDomains`
- optional public signing key and fingerprint
- optional Microsoft OAuth endpoint overrides
- optional per-provider HTTP/SOCKS proxy settings

Provider types:

- `BUILTIN`
  Used for the special Offline Account provider.
- `DEFAULT`
  Seeded from `default-providers/*.json`.
- `CUSTOM`
  Added manually or converted from a missing default definition.

### 4.2 Client account

A client account is a stored authenticated identity for one provider.

Fields of note:

- numeric local row id
- provider name
- user UUID
- bound profile UUID and profile name
- access token
- optional Microsoft refresh token
- client token
- status and error state
- local skin/cape paths for offline accounts

Account statuses:

- `VALID`
- `REFRESHED`
- `UNVERIFIED`
- `UNAUTHED`
- `EXPIRED`

### 4.3 Server user

A WawelAuth server user is the login credential object.

- unique user UUID
- unique username
- PBKDF2-HMAC-SHA256 password hash
- PBKDF2 salt
- admin flag
- locked flag
- preferred language

### 4.4 Server profile

A WawelAuth server profile is the playable game identity.

- unique in-game UUID
- unique player name
- owner user UUID
- derived offline UUID for migration/compatibility
- skin model
- optional skin/cape/elytra hashes
- animated-cape flag
- uploadable texture permissions

### 4.5 Token

A WawelAuth token represents a Yggdrasil session.

- `accessToken` is the primary key
- `clientToken` is client-provided
- token may be bound to exactly one profile
- refresh invalidates the old token and creates a new token with `version + 1`
- state is `VALID`, `TEMPORARILY_INVALID`, or `INVALID`

### 4.6 Invite

An invite is an optional registration gate.

- invite code
- creation timestamp
- creator UUID, if known
- remaining uses, where `-1` means unlimited

### 4.7 Server capabilities

Server capabilities are runtime-only metadata learned from server-list ping or from WawelAuth join sync.

They include:

- whether the server advertises WawelAuth at all
- whether local auth is supported
- local auth API root
- local auth signing-key fingerprint and raw base64 key
- local auth skin domains
- accepted auth server URLs
- accepted provider descriptors

These capabilities drive auto-selection, trust establishment, and texture verification on the client.

## 5. Persistence and File Layout

### 5.1 Client-side files

`local.json` is always stored at:

```text
config/wawelauth/local.json
```

It contains:

- `debugMode`
- `useOsConfigDir`

Data location rules:

- If `useOsConfigDir=true` on the client, WawelAuth stores shared client data in the OS-specific data directory.
- If `useOsConfigDir=false`, WawelAuth stores client data under the local Minecraft config tree.

Implementation-visible client storage:

- `accounts.db`
- `singleplayer_account.json`
- default provider definitions under `config/wawelauth/default-providers/`
- server-list binding data stored inside `servers.dat` through mixed-in `ServerData` fields

Important nuance:

- Default provider definition files are always looked up under the local config directory, not the OS-shared client data directory.

### 5.2 Server-side files

Server config lives under:

```text
config/wawelauth/
```

Key files:

- `server.json`
- `fallback-servers.json`
- `data/wawelauth.db`
- `data/private.der`
- `data/public.der`
- `data/textures/<sha256>.png`
- `data/textures/<sha256>.gif`
- `public-page/` custom public site directory

The private signing key is generated on first run and stored with owner-only permissions where the platform supports that.

## 6. Client Feature Specification

### 6.1 Provider management

Client provider behavior is:

- A Microsoft/Mojang default provider is seeded from bundled JSON if no default provider files exist.
- A built-in `Offline Account` provider is always created.
- Custom providers are added through a two-step trust flow:
  - ALI discovery resolves the provider API root and metadata.
  - The user is shown the provider public-key fingerprint.
  - The provider is not persisted until explicitly trusted.

Custom-provider discovery uses:

- ALI header resolution
- metadata fetch from the resolved API root
- automatic derivation of auth/session/services endpoints
- extraction of `skinDomains`
- extraction of `signaturePublickey`

### 6.2 Supported provider categories

WawelAuth client currently supports:

- Mojang/Microsoft accounts through browser OAuth
- custom Yggdrasil/authlib-injector providers by URL
- WawelAuth local-auth servers discovered from ping capability data
- explicit offline/local-only accounts

### 6.3 Microsoft OAuth flow

Microsoft login is a browser-based loopback flow:

1. Open browser to Microsoft authorize URL.
2. Receive auth code on a local HTTP callback.
3. Exchange code for Microsoft access and refresh tokens.
4. Exchange Microsoft token for Xbox Live token.
5. Exchange XBL token for XSTS token.
6. Exchange XSTS token for Minecraft services token.
7. Fetch Minecraft profile.

Defaults can be overridden through JVM properties or environment variables:

- client id
- redirect URI

### 6.4 Offline accounts

Offline accounts are local identities with:

- provider `Offline Account`
- vanilla offline UUID (`OfflinePlayer:<name>`)
- synthetic access token format `offline:<unsigned-uuid>`
- status `UNAUTHED`

Offline accounts may also store local skin/cape file paths for client-only cosmetic use.

### 6.5 Account authentication behavior

Normal Yggdrasil-style login:

- calls `/authserver/authenticate`
- requests `requestUser=true`
- binds the first available profile with `/authserver/refresh` if authenticate did not return a selected profile
- upserts the client account by provider/profile or by unbound provider/user identity

WawelAuth-specific self-service account operations:

- register: `POST /api/wawelauth/register`
- change password: `POST /api/wawelauth/change-password`
- delete account: `POST /api/wawelauth/delete-account`

Built-in providers do not support WawelAuth registration flows.

### 6.6 Background token lifecycle

The client starts a background scheduler that:

- validates all stored accounts on startup
- re-validates stale accounts every 60 seconds
- retries `UNVERIFIED` accounts aggressively after offline startup
- never auto-recovers `EXPIRED` accounts without user action

The status model is:

- `VALID` or `REFRESHED`: token is currently usable
- `UNVERIFIED`: provider could not be reached, cached identity is retained
- `EXPIRED`: validation and refresh failed; the user must log in again
- `UNAUTHED`: explicit offline account

### 6.7 Session activation

Before connecting to a server, WawelAuth can replace the active Minecraft session with a chosen account.

Rules:

- Per-server binding is persisted in the multiplayer server list.
- If no binding exists, WawelAuth performs a non-persistent auto-pick only when exactly one stored account matches the server capability payload.
- If no unique match exists, it restores the original launcher session.
- Singleplayer has a separate persisted selected-account slot.

### 6.8 Multiplayer server selection UX

The client adds WawelAuth UI into:

- multiplayer server list
- world-select screen
- a global account-manager screen

User-facing behaviors include:

- bind an account to a specific multiplayer server entry
- manage local auth for a WawelAuth server directly from that server entry
- display selected-account faces in several UI locations
- select a dedicated account for launching singleplayer worlds

### 6.9 Local-auth trust model

Local-auth providers learned from a server are identified by public-key fingerprint, not by server address.

Consequences:

- retargeting a server entry can invalidate old bindings
- the same server identity can be reused across address changes if the key stays the same
- managed local-auth provider names are generated from the server domain or fingerprint

### 6.10 Client proxy support

WawelAuth has two distinct proxy features:

- per-provider proxy for auth/services HTTP traffic
  - supported types: `HTTP`, `SOCKS`
- per-server gameplay proxy for actual Minecraft socket traffic

Gameplay proxy behavior includes:

- validation of address, port, and optional credentials
- explicit probe support from the server-account picker UI
- HTTP `CONNECT` and SOCKS tunnel support

### 6.11 Client config categories

In addition to `local.json`, WawelAuth registers GTNHLib-managed config categories for client behavior and rendering.

The client account-manager category exposes:

- `defaultProvider`
  - default `""`
  - auto-select this provider when no provider is explicitly chosen
- `disableSkinUpload`
  - default `["ely\\.by"]`
  - regexes matched against provider name or API root
- `disableCapeUpload`
  - default `["ely\\.by", "^Mojang$"]`
  - regexes matched against provider name or API root
- `disableTextureReset`
  - default `["ely\\.by"]`
  - regexes matched against provider name or API root

These `disable*` lists are UI-policy controls. They suppress client-side controls for providers that should not expose specific texture actions.

## 7. Client Rendering and Texture Specification

### 7.1 Skin and cape loading

Vanilla skin loading is suppressed. All skin/cape resolution flows through WawelAuth.

Provider resolution order is effectively:

- current connection provider map for remote players on WawelAuth servers
- active account provider for in-world context
- stored account provider for out-of-world identity lookup
- Mojang provider for the local player in singleplayer with no explicit account

### 7.2 Trust rules for texture verification

Texture verification is connection-scoped.

- If the connected server does not advertise WawelAuth, the trust set is the active provider plus Mojang.
- If the server does advertise WawelAuth, the trust set is built from the advertised accepted providers and local-auth identity.
- Vanilla Mojang texture domains are always accepted.
- Provider skin-domain allowlists are enforced.

### 7.3 Modern skin support

The client supports:

- 64x64 modern skins
- conversion of legacy 64x32 skins into 64x64 form
- slim arm models
- HD skins that preserve larger dimensions through the resolver/render stack

### 7.4 Animated capes

Animated capes are supported client-side.

- GIF capes are tracked and ticked every client tick.
- trackers are cleared on player join and world unload.
- animated capes are a WawelAuth server feature and are surfaced through the normal textures property with CAPE metadata.

### 7.5 3D skin layers

Optional 3D skin-layer rendering supports:

- hat
- jacket
- left sleeve
- right sleeve
- left pants
- right pants
- cape
- skull rendering

Important configuration points:

- master 3D toggle
- per-layer 3D toggles
- modern skin support toggle
- armor-hide behavior
- fast-render mode
- render-distance LOD
- voxel size multipliers

## 8. Server Module Specification

### 8.1 Startup behavior

When the server module starts it:

- loads and validates `server.json`
- seeds `fallback-servers.json` if missing
- on dedicated servers, rejects invalid startup before world load:
  - integrated/singleplayer is skipped entirely
  - `online-mode=false` aborts startup
  - missing `publicBaseUrl` aborts startup
  - absolute-like `apiRoot` aborts startup
  - missing `publicBaseUrl` is bypassed only for CI via `WAWELAUTH_CI` or `GITHUB_ACTIONS`
- auto-adds the effective API-root host into `skinDomains`
- initializes SQLite storage
- discovers fallback public signing keys when possible
- loads or generates the local RSA signing keypair
- constructs all DAOs and services
- registers Yggdrasil routes
- registers admin routes only when `server.admin.enabled=true`
- registers public-page routes

### 8.2 Single-port HTTP multiplexing

The server listens for HTTP on the same socket as Minecraft gameplay.

HTTP branch settings are configurable:

- read timeout
- maximum aggregated request body size

The HTTP pipeline is only used after protocol detection. Vanilla gameplay timeouts remain unchanged.

### 8.3 Local auth vs fallback auth

The server module has two separate auth roles:

- local auth
  - WawelAuth-hosted users, profiles, invites, tokens, and textures
- fallback auth
  - external providers checked in configured order for session/profile/skin resolution

Login verification order is:

1. local auth `hasJoined`, if local auth is enabled
2. configured fallback providers, in order

### 8.4 Duplicate-name protection

Before remote verification, the server rejects a login if a player with the same username is already online.

### 8.5 Per-player provider tracking

During successful login verification, the server records the player's originating session-server URL.

This is used to:

- advertise provider associations to WawelAuth clients on join
- let clients resolve the correct provider for each online player
- improve mixed-provider texture handling

## 9. Server Config Schema

### 9.1 `server.json`

Top-level fields and defaults:

| Field | Default | Meaning |
| --- | --- | --- |
| `wawelAuthEnabled` | `true` | Master enable for the server module |
| `enableLocalAuth` | `true` | Enables local-auth capability advertisement and local first-pass login verification |
| `serverName` | `"A Wawel Auth Server"` | Public server name |
| `publicBaseUrl` | `""` | Public-facing base URL used to build auth API URLs |
| `apiRoot` | `"auth"` | Relative auth/API mount path under `publicBaseUrl` |
| `enablePublicPage` | `true` | Enables the public site |
| `enablePublicInfoApi` | `true` | Enables the public JSON info endpoint |
| `publicPagePath` | `"/"` | Mount path for the public site |
| `publicInfoApiPath` | `"__server-info"` | Public info API path, relative to `publicPagePath` unless absolute |
| `server-address` | `"localhost:25565"` | Human-facing advertised address |
| `skinDomains` | `[]` | Explicit trusted texture domains |

`meta` defaults:

| Field | Default |
| --- | --- |
| `implementationName` | `"Wawel Auth"` |
| `serverHomepage` | `""` |
| `serverRegister` | `""` |
| `publicDescription` | `""` |

`features` defaults:

| Field | Default |
| --- | --- |
| `legacySkinApi` | `false` |
| `noMojangNamespace` | `true` |
| `usernameCheck` | `true` |

`registration` defaults:

| Field | Default |
| --- | --- |
| `policy` | `INVITE_ONLY` |
| `playerNameRegex` | `^[a-zA-Z0-9_]{3,16}$` |
| `defaultUploadableTextures` | `["skin","cape"]` |

`invites` defaults:

| Field | Default |
| --- | --- |
| `defaultUses` | `1` |

`textures` defaults:

| Field | Default |
| --- | --- |
| `maxSkinWidth` | `64` |
| `maxSkinHeight` | `64` |
| `maxCapeWidth` | `64` |
| `maxCapeHeight` | `32` |
| `maxFileSizeBytes` | `1048576` |
| `allowElytra` | `true` |
| `allowAnimatedCapes` | `true` |
| `maxCapeFrameCount` | `256` |
| `maxAnimatedCapeFileSizeBytes` | `10485760` |

`tokens` defaults:

| Field | Default |
| --- | --- |
| `maxPerUser` | `10` |
| `sessionTimeoutMs` | `30000` |

`http` defaults:

| Field | Default |
| --- | --- |
| `readTimeoutSeconds` | `10` |
| `maxContentLengthBytes` | `1048576` |

`admin` defaults:

| Field | Default |
| --- | --- |
| `enabled` | `true` |
| `token` | `""` |
| `tokenEnvVar` | `"WAWELAUTH_ADMIN_TOKEN"` |
| `sessionTtlMs` | `1800000` |

Validation rules include:

- all texture dimensions and size limits must be positive
- `maxCapeFrameCount >= 2`
- `http.maxContentLengthBytes >= textures.maxFileSizeBytes`
- fallback provider names must not contain whitespace

Derived/public URL rules:

- `getEffectiveApiRoot()` is built from `publicBaseUrl + apiRoot`
- `publicBaseUrl` without a scheme is normalized as `http://...`
- `apiRoot=""` publishes the API at the base URL root
- `apiRoot="auth"` publishes the API at `/auth`
- dedicated startup rejects absolute-like `apiRoot` values such as full URLs
- dedicated startup rejects missing `publicBaseUrl`, except in CI smoke-test mode

### 9.2 `fallback-servers.json`

Fallback providers are stored separately and loaded into runtime server config.

Each fallback entry may define:

- `enabled`
- `name`
- `apiRoot`
- `sessionServerUrl`
- `accountUrl`
- `servicesUrl`
- `signaturePublicKeyBase64`
- `skinDomains`
- `cacheTtlSeconds`

The bundled default file seeds Mojang as the first fallback.

## 10. Yggdrasil and Extension HTTP API

Unless otherwise stated, the route examples below are shown without the optional path prefix derived from the relative `apiRoot`.

Example:

- if `apiRoot = ""`, use `/authserver/authenticate`
- if `apiRoot = "auth"`, use `/auth/authserver/authenticate`
- if `apiRoot = "auth/v2"`, use `/auth/v2/authserver/authenticate`

### 10.1 Common HTTP behavior

All HTTP responses include:

- `X-Authlib-Injector-API-Location`
- `X-Content-Type-Options: nosniff`
- `X-Frame-Options: DENY`
- `Referrer-Policy: no-referrer`
- a restrictive CSP

Error payload format:

```json
{
  "error": "ForbiddenOperationException",
  "errorMessage": "Invalid token."
}
```

Common error types:

- `IllegalArgumentException` for `400`
- `ForbiddenOperationException` for `403`
- `NotFoundOperationException` for `404`

The ALI header value is the API route prefix derived from the relative `apiRoot`. If `apiRoot` is empty, the header is `/`.

### 10.2 Metadata

`GET /` or `GET /<api-prefix>/`

Returns:

- `meta`
- `skinDomains`
- `signaturePublickey`

`meta.feature` includes:

- `non_email_login=true`
- `legacy_skin_api`
- `no_mojang_namespace`
- `enable_profile_key=false`
- `enable_mojang_anti_features=false`
- `username_check`

### 10.3 Auth endpoints

| Method | Path | Behavior |
| --- | --- | --- |
| `POST` | `/authserver/authenticate` | Creates a token and returns `accessToken`, `clientToken`, `availableProfiles`, optional `selectedProfile`, optional `user` |
| `POST` | `/authserver/refresh` | Invalidates old token and returns a new one; may bind a profile |
| `POST` | `/authserver/validate` | Returns `204` if the token pair is valid |
| `POST` | `/authserver/invalidate` | Returns `204` even if the token does not exist |
| `POST` | `/authserver/signout` | Invalidates all tokens for the username/password pair |
| `POST` | `/api/wawelauth/register` | WawelAuth extension for local account registration |
| `POST` | `/api/wawelauth/change-password` | WawelAuth extension for self-service password change |
| `POST` | `/api/wawelauth/delete-account` | WawelAuth extension for self-service account deletion |

Authentication semantics:

- re-authenticating with the same `clientToken` invalidates older tokens for that same user/client-token pair
- different `clientToken` values may coexist
- refresh preserves the client token and rotates the access token
- a bound token cannot be rebound to a different profile

### 10.4 Session and profile endpoints

| Method | Path | Behavior |
| --- | --- | --- |
| `POST` | `/sessionserver/session/minecraft/join` | Creates a pending session for the selected profile |
| `GET` | `/sessionserver/session/minecraft/hasJoined` | Consumes the pending session and returns a signed full profile on success; returns `204` on silent failure |
| `GET` | `/sessionserver/session/minecraft/profile/{uuid}` | Returns a full profile; signs textures only when `unsigned=false` |
| `GET` | `/api/users/profiles/minecraft/{name}` | Name-to-UUID lookup |
| `POST` | `/api/profiles/minecraft` | Bulk lookup for up to 10 names |

Important behavior:

- `hasJoined` falls back to external providers if no local pending session exists
- `hasJoined` consumes a pending session; repeating the same request after success returns `204`
- dashed and undashed UUID input is accepted for the profile endpoint
- WawelAuth-aware clients may request re-signing of fallback profiles through a private query marker

### 10.5 Texture endpoints

| Method | Path | Behavior |
| --- | --- | --- |
| `PUT` | `/api/user/profile/{uuid}/{textureType}` | Uploads `skin`, `cape`, or `elytra` |
| `DELETE` | `/api/user/profile/{uuid}/{textureType}` | Clears the selected texture |
| `GET` | `/textures/{hash}` | Serves stored PNG or GIF by content hash |
| `GET` | `/textures/proxy/{fallbackKey}/{encodedUrl}` | Proxies an upstream fallback texture |

Upload requirements:

- Bearer token is required
- the token owner must own the profile
- the profile must explicitly allow the texture type in `uploadableTextures`

Texture shape rules:

- skin and elytra: multiples of `64x32` or `64x64`
- cape: multiples of `64x32` or `22x17`

Transformation rules:

- legacy `64x32` skins are converted to `64x64`
- `22x17` capes are padded to the next `64x32` multiple
- PNG uploads are decoded and re-encoded to strip metadata
- GIF uploads are only accepted for capes

Animated cape rules:

- server must allow animated capes
- minimum 2 frames
- maximum frame count set by config
- maximum GIF size set by config
- served as `image/gif`

Storage rules:

- files are content-addressed by SHA-256
- identical uploads deduplicate automatically
- unreferenced files are deleted on reset

### 10.6 Compatibility stubs

| Method | Path | Behavior |
| --- | --- | --- |
| `GET` | `/api/user/security/location` | Always `204` |
| `GET` | `/blockedservers` | Always `200`, empty text payload |

## 11. Admin Web UI and Admin API

### 11.1 Surface

- when `server.admin.enabled=true`:
  - UI root: `/admin`
  - API root: `/api/wawelauth/admin/*`
- when `server.admin.enabled=false`:
  - no admin UI or admin API routes are mounted

Implementation note:

- if admin is enabled but no token is configured, the bootstrap/page surface still mounts so the UI can explicitly instruct the operator to set a token
- authenticated admin operations only work when `server.admin.enabled=true` and a valid admin token is configured

### 11.2 Authentication model

Admin auth uses a static admin secret from:

1. `server.admin.tokenEnvVar`, if set and present in the environment
2. otherwise `server.admin.token`

Login behavior:

- `POST /api/wawelauth/admin/login`
- on HTTPS, send `token`
- on non-HTTPS, send `encryptedToken` using the server's RSA public key and `RSA/ECB/OAEPWithSHA-1AndMGF1Padding`

Session behavior:

- login returns a short-lived admin session token
- every authenticated request refreshes expiry to create sliding expiration
- session token can be provided by:
  - Bearer auth header
  - `X-WawelAuth-Admin-Session`
  - `wawelauth_admin_session` cookie

### 11.3 Bootstrap/session endpoints

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/wawelauth/admin/bootstrap` | UI bootstrap data including whether login must be encrypted |
| `POST` | `/api/wawelauth/admin/login` | Create admin session |
| `POST` | `/api/wawelauth/admin/logout` | Revoke admin session |
| `GET` | `/api/wawelauth/admin/session` | Validate current session and return expiry |

### 11.4 Management endpoints

| Category | Endpoints |
| --- | --- |
| Stats | `GET /stats` |
| Users | `GET /users`, `POST /users/{uuid}/delete`, `POST /users/{uuid}/reset-password`, `POST /users/{uuid}/reset-textures` |
| Profiles | `POST /profiles/{uuid}/set-uuid`, `POST /profiles/{uuid}/use-offline-uuid` |
| Providers | `GET /providers`, `POST /resolve-profile`, `GET /avatar` |
| Whitelist | `GET /whitelist`, `POST /whitelist/add`, `POST /whitelist/remove`, `POST /whitelist/enabled` |
| Ops | `GET /ops`, `POST /ops/add`, `POST /ops/remove` |
| Invites | `GET /invites`, `POST /invites`, `DELETE /invites/{code}`, `POST /invites/purge` |
| Config | `GET /config/server`, `POST /config/server`, `GET /config/server-properties`, `POST /config/server-properties` |

Operational notes:

- whitelist/op mutations are marshaled onto the Minecraft server thread
- whitelist/op entries store provider bindings in a separate DAO
- profile UUID changes invalidate the owner's tokens
- the admin config API currently exposes the editable subset of server config, not every internal field

## 12. Public Page and Public Info API

### 12.1 Public page

The public site is served from:

```text
config/wawelauth/public-page/
```

Behavior:

- bundled template files are copied in if no custom top-level site exists yet
- files are frozen into an in-memory route map at startup/refresh time
- only those exact frozen routes are served
- the site mount path is controlled by `publicPagePath`

Managed built-in asset routes under the public page include:

- `__server-icon.png`
- `__server-icon.gif`
- `__player-avatar.png?uuid=<uuid>`

### 12.2 Public info API

If enabled, the public info API returns a JSON payload containing:

- `apiVersion`
- `generatedAt`
- `cacheTtlSeconds`
- `branding`
- `server`
- `links`
- `icons`
- `modlist`

The `server` object includes:

- server name and description
- MOTD
- online/max player counts
- connected player list
- advertised address and address warning
- registration state
- fallback provider summary

The live-status section is cached for 5 seconds.

## 13. Minecraft Command Specification

### 13.1 `/wawelauth`

Registered only when the server module is active.

Subcommands:

- `/wawelauth register <username> <password>`
- `/wawelauth invite create [uses|unlimited]`
- `/wawelauth invite list`
- `/wawelauth invite delete <code>`
- `/wawelauth invite purge`
- `/wawelauth test`

Notes:

- command permission level is 4
- register creates a user and a same-named profile in one transaction
- invite creation uses a grouped human-readable code format
- invite creation copies the code to the initiating player's clipboard when possible

### 13.2 Provider-qualified vanilla commands

WawelAuth extends vanilla whitelist/op commands to support provider-qualified names:

- `/whitelist add <username>@<provider>`
- `/whitelist remove <username>@<provider>`
- `/op <username>@<provider>`
- `/deop <username>@<provider>`

Provider keys may refer to:

- a configured fallback provider `name`
- local aliases: `local`, `localauth`, `wawelauth`, `self`

The implementation intentionally requires provider-qualified targets for these WawelAuth-aware resolution paths.

## 14. Ping Capability Payload

WawelAuth advertises capability data in server-list ping and in a join-sync custom payload.

Payload fields include:

- `version`
- `providesYggdrasilService`
- `apiRoot`
- `acceptedAuthServerUrls`
- `localAuthSupported`
- `localAuthApiRoot`
- `localAuthPublicKeyFingerprint`
- `localAuthPublicKeyBase64`
- `localAuthSkinDomains`
- `acceptedProviderDescriptors`

Accepted provider descriptors may contain:

- `name`
- `apiRoot`
- `authServerUrl`
- `sessionServerUrl`
- `servicesUrl`
- `signaturePublickey`
- `skinDomains`

Client uses:

- account auto-selection
- local-auth provider creation
- per-connection trust set construction
- per-player provider association mapping

For the local WawelAuth server entry, `apiRoot` and `localAuthApiRoot` are the full effective public API root derived from `publicBaseUrl + apiRoot`.

## 15. Security Model

Current security-relevant behavior:

- local account passwords use PBKDF2-HMAC-SHA256 with 210,000 iterations, 256-bit output, and 128-bit random salt
- profile textures are signed with the server RSA key when required by endpoint semantics
- server RSA key size is 4096 bits
- provider public-key fingerprints are shown during custom-provider trust flow
- admin login requires RSA-encrypted token submission when the request is not recognized as HTTPS
- PNG uploads are sanitized by re-encoding before storage
- texture domains are verified against trusted provider allowlists

Operational warnings that docs should call out:

- `publicBaseUrl` must point at the real public base URL clients use
- `apiRoot` is a relative path under `publicBaseUrl`, not a full URL
- changing the server signing key breaks trust continuity for WawelAuth clients and authlib-injector clients
- the private key must never be shared

## 16. Documentation-Relevant Constraints and Edge Cases

- The old README is not authoritative.
- `enableLocalAuth` affects capability advertisement and local-first verification, but the server module still mounts its HTTP routes when the overall server module is enabled.
- Integrated singleplayer does not run the WawelAuth server module.
- Dedicated startup hard-stops before world load if `online-mode=false`.
- Dedicated startup hard-stops before world load if `publicBaseUrl` is missing, except when `WAWELAUTH_CI` or `GITHUB_ACTIONS` enables the CI-only bypass.
- Dedicated startup hard-stops before world load if `apiRoot` is configured as a full URL instead of a relative path.
- Public page route overlap with auth/admin routes is possible and only warned about at runtime.
- Admin routes are not mounted at all when `server.admin.enabled=false`.
- Admin config editing currently covers the exposed editable subset of server config, not every internal field.
- Mixed-provider skin handling works best when both client and server run WawelAuth.
- The client can auto-select a matching account for a server only when exactly one stored account matches the advertised provider set.

## 17. Recommended Documentation Split

This spec is broad enough to drive at least these downstream docs:

- Installation and dependency guide
- Client user guide
- Server setup guide
- Provider management guide
- Local-auth quickstart
- Admin web UI guide
- Public page customization guide
- HTTP API reference
- Texture and skin compatibility guide
- Security and operational notes
