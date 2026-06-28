# Mik Web Player Authentication Design

## Background

Current code already has three useful boundaries:

1. `mikdata` exposes public data under `/api/*`. The route table is allowlisted in `../mikdata/src/index.ts`, and public JSON currently uses wildcard CORS from `../mikdata/src/http.ts`.
2. `mikdata` exposes operational admin UI/API under `/admin*`. Those routes are already protected by Cloudflare Access in `../mikdata/src/access.ts`.
3. The Minecraft plugin exposes a local HTTP API from `ApiModule` on `127.0.0.1:35353`. `mikdata` reaches it through the existing VPC service.

The new requirement is not only "add login". It introduces a player account layer for the web site:

```text
Minecraft player account -> web session -> private player web features
```

This design keeps the existing public API and admin API as separate surfaces. Player login gets its own surface and storage model.

## Goals

- Let a Minecraft player log in to `mcmik.top` without a password.
- Use the Minecraft account UUID as the root player account key.
- Allow only formal members and above, represented by `group.member`, `group.helper`, or `group.manager`, to use player web features.
- Avoid exposing plugin HTTP endpoints to browsers.
- Keep public data cacheable and independent from login state.
- Keep Cloudflare Access as the admin identity mechanism.
- Leave `mikdata -> plugin` communication inside the current trusted internal network model.
- Support exactly two player login methods: in-game verification code and Passkey/WebAuthn.

## Non-Goals

- Do not create a password account system.
- Do not use email as the primary account identifier.
- Do not merge Cloudflare Access admin users with Minecraft player users.
- Do not make the plugin responsible for web session creation.
- Do not move existing public data routes behind authentication.
- Do not support login links, URL tickets, email login, OAuth login, or password login.
- Do not provide player web access to non-member players, even if they can reach the login page.

## Existing Observations

`mikdata/src/index.ts` currently routes requests by prefix:

```text
/admin* -> Cloudflare Access
/api/*  -> public data API
```

`mikdata/src/http.ts` currently has one JSON helper that always attaches public CORS:

```text
Access-Control-Allow-Origin: *
Access-Control-Allow-Methods: GET, OPTIONS
```

This is correct for public GET data, but it should not be reused for session or account endpoints.

`mikweb/src/shared/api/data-api-url.ts` currently points to `https://data.mcmik.top/api`. That helper should remain public-data-only. Authenticated calls should not be added to it.

`ApiModule` currently exposes:

```text
GET /api/players
GET /api/announcements
GET /api/bans
```

It also checks that requests come from local/internal network context. The login design adds a narrow authentication confirmation API to this same module.

## Target Architecture

```text
Browser
  |
  | same-origin UI and auth calls
  v
mikweb on mcmik.top
  |  \
  |   \ public data reads can still call data.mcmik.top/api directly
  |
  | server-side auth calls
  v
mikdata on data.mcmik.top
  |
  | VPC service
  v
Mik plugin ApiModule on 127.0.0.1:35353
```

The key split is:

```text
Public data path:
browser -> data.mcmik.top/api/*

Authenticated player path:
browser -> mcmik.top/api/auth/* -> mikweb route handler -> data.mcmik.top/auth/*

Admin path:
browser -> data.mcmik.top/admin* -> Cloudflare Access -> mikdata admin
```

This keeps authenticated cookies host-only on `mcmik.top`. `data.mcmik.top` remains the source of truth for auth state, but it does not need to set browser cookies directly.

## Trust Boundaries

### Browser

The browser is untrusted. It can hold:

- UI state
- short-lived challenge IDs

It should not hold:

- session secrets in `localStorage`
- long-lived bearer tokens

### mikweb

`mikweb` is the browser-facing application. It owns:

- login page
- account UI
- host-only session cookie
- route handlers under `/api/auth/*`
- CSRF checks for same-origin authenticated mutations

`mikweb` does not own:

- canonical session storage
- canonical player account data
- plugin confirmation state

### mikdata

`mikdata` is the auth and data authority. It owns:

- player account records
- login challenges
- sessions
- Passkey credentials
- route policy for public/auth/admin API route categories

`mikdata` should not share its public CORS helper with authenticated routes.

### Plugin

The plugin proves that an online Minecraft player confirmed a login challenge. It owns:

- `/weblogin <code>` command
- temporary in-memory challenge confirmation map
- formal-member-or-above eligibility check

The plugin does not create web sessions and does not decide web UI authorization beyond returning the confirmed Minecraft player and one compact role string.

## Identity Model

The root account key is Minecraft UUID.

```ts
interface PlayerAccount {
  playerUuid: string;
  currentName: string;
  updatedAt: string;
  role: 'member' | 'helper' | 'manager';
  passkeys: PasskeyCredentialRecord[];
  disabledAt?: string;
}

interface PasskeyCredentialRecord {
  credentialId: string;
  publicKey: string;
  counter: number;
  transports?: string[];
  createdAt: string;
  lastUsedAt?: string;
  displayName?: string;
}
```

The account is created lazily when an in-game confirmation succeeds.

The stored role is derived from `group.manager`, `group.helper`, and `group.member` in that order. Player web access requires the derived role to be one of `manager`, `helper`, or `member`. Staff-only web features should still define their own authorization rules instead of treating the player login surface as the admin surface.

## Session Model

The browser receives a host-only cookie from `mcmik.top`:

```http
Set-Cookie: __Host-mik_sid=<opaque>; Path=/; Secure; HttpOnly; SameSite=Lax; Max-Age=2592000
```

The cookie value is an opaque random ID. It does not contain player UUID, role, or expiry data.

`mikdata` stores only a hash of the session ID:

```ts
interface PlayerSession {
  sidHash: string;
  playerUuid: string;
  issuedAt: string;
  lastSeenAt: string;
  idleExpiresAt: string;
  absoluteExpiresAt: string;
  authMethod: 'minecraft-challenge' | 'passkey';
  userAgentHash?: string;
  ipPrefixHash?: string;
  revokedAt?: string;
}
```

Recommended expiry:

- Idle expiry: 30 days.
- Absolute expiry: 90 days.
- High-risk actions: require recent login, for example within 10 minutes.

Session rotation:

- Rotate session after login.
- Rotate session after Passkey enrollment.
- Rotate session after privilege-sensitive role changes if those become web-visible.

## Storage Design

Use a new Durable Object class in `mikdata`, for example `AuthStore`.

```text
AUTH_STORE Durable Object
  name: global
  storage: SQLite-backed Durable Object
```

Tables:

```sql
CREATE TABLE accounts (
  player_uuid TEXT PRIMARY KEY,
  current_name TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  role TEXT NOT NULL,
  disabled_at TEXT
);

CREATE TABLE login_challenges (
  challenge_id TEXT PRIMARY KEY,
  display_code_hash TEXT NOT NULL,
  browser_nonce_hash TEXT NOT NULL,
  status TEXT NOT NULL,
  created_at TEXT NOT NULL,
  expires_at TEXT NOT NULL,
  confirmed_player_uuid TEXT,
  confirmed_player_name TEXT,
  confirmed_at TEXT,
  consumed_at TEXT,
  attempt_count INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE sessions (
  sid_hash TEXT PRIMARY KEY,
  player_uuid TEXT NOT NULL,
  issued_at TEXT NOT NULL,
  last_seen_at TEXT NOT NULL,
  idle_expires_at TEXT NOT NULL,
  absolute_expires_at TEXT NOT NULL,
  auth_method TEXT NOT NULL,
  user_agent_hash TEXT,
  ip_prefix_hash TEXT,
  revoked_at TEXT
);

CREATE TABLE passkeys (
  credential_id TEXT PRIMARY KEY,
  player_uuid TEXT NOT NULL,
  public_key TEXT NOT NULL,
  counter INTEGER NOT NULL,
  transports_json TEXT,
  created_at TEXT NOT NULL,
  last_used_at TEXT,
  display_name TEXT
);

CREATE INDEX sessions_player_uuid_idx ON sessions(player_uuid);
CREATE INDEX passkeys_player_uuid_idx ON passkeys(player_uuid);
CREATE INDEX login_challenges_expires_at_idx ON login_challenges(expires_at);
```

KV can still be used for public data such as buildings. Auth state needs stronger read-after-write behavior and single-consumer semantics for challenges, so Durable Object storage is a better fit.

## Free Plan Budget

The design assumes `mikdata` stays on the Cloudflare Workers Free plan. The relevant limits are:

- Workers Free allows 100,000 requests per day.
- Workers Free allows 50 subrequests per Worker invocation.
- Workers Free CPU time per HTTP request is 10 ms.
- Workers KV Free allows 100,000 reads per day and 1,000 writes per day.
- Workers KV still limits writes to the same key to 1 write per second.
- Durable Objects are available on Workers Free when using the SQLite storage backend.
- SQLite-backed Durable Objects on Workers Free have 5 GB total account storage. The per-object storage ceiling is higher in the platform table, but Free plan behavior should be designed conservatively and avoid concentrating unbounded data in one object.
- A single Durable Object is inherently single-threaded and has a soft throughput limit around 1,000 requests per second.
- Workers Cache API calls count against per-request Cache API/subrequest budgets.

These limits make request count and fan-out more important than raw CPU. The auth design should avoid introducing background polling or repeated session validation requests from every visible component.

Quota references:

- Cloudflare Workers limits: `https://developers.cloudflare.com/workers/platform/limits/`
- Cloudflare Workers KV limits: `https://developers.cloudflare.com/kv/platform/limits/`
- Cloudflare Durable Objects limits: `https://developers.cloudflare.com/durable-objects/platform/limits/`
- Cloudflare Workers Cache API: `https://developers.cloudflare.com/workers/runtime-apis/cache/`

### Request Budget Model

The current `mikweb` player status provider polls `/api/players` every 10 seconds while the page is visible. One always-open tab can create about 8,640 `mikdata` requests per day:

```text
24 hours * 60 minutes * 6 requests/minute = 8,640 requests/day
```

Twelve always-open tabs would approach the Workers Free daily request limit before counting buildings, announcements, bans, admin usage, auth, or crawlers.

The design should treat public live status as the main request-count risk. Login itself is short-lived and low-frequency; Passkey login is also low-frequency. Public status polling can become the dominant quota consumer.

## Caching and Performance Design

### Public Data Tiers

Public data should be grouped by volatility:

| Data | Volatility | Browser behavior | Worker cache behavior |
| --- | --- | --- | --- |
| `/api/players` | high | poll only while visible, 30s baseline | 10-15s fresh, 60s stale |
| `/api/announcements` | medium | load once per page, reuse in memory | 5-10 min fresh, 7-14 days stale |
| `/api/buildings` | low | load on demand, client memory TTL | 5-10 min fresh, 10-30 min stale |
| `/api/bans` | low-medium | avoid home-page eager load | 5-30 min fresh, 7 days stale |
| `/health` | operational | no browser polling | no-store or very short TTL |

The existing proxy cache in `../mikdata/src/proxy.ts` already uses memory cache plus Workers Cache API for plugin-backed routes. The main adjustment is reducing browser request rate and adding aggregated public endpoints where the web UI currently needs multiple datasets together.

### Aggregated Public Endpoints

Add narrow aggregate endpoints for pages that need several public datasets:

```text
GET /api/home-summary
GET /api/pcl2-summary
```

`/api/home-summary` can return:

```ts
interface HomeSummary {
  players: PlayerOnlinePayload;
  announcementsPreview: AnnouncementItem[];
  buildingCount: number;
  peakOnline: number;
  generatedAt: string;
}
```

`/api/pcl2-summary` can return the exact data needed by `mikweb/src/modules/pcl2/server/pcl2-homepage-data.ts`, rather than making the page load `players`, `announcements`, `buildings`, and `bans` separately.

This does not replace canonical endpoints. It reduces requests and subrequests for common UI paths.

### Player Status Polling

The player status UI should use an adaptive strategy:

```text
visible tab, page has live panel in viewport:
  poll every 30 seconds

visible tab, live panel not in viewport:
  poll every 60-120 seconds

hidden tab:
  stop polling

network error or upstream unavailable:
  exponential backoff to 2-5 minutes

server returns X-Next-Poll-After:
  client uses that value as the minimum next poll interval
```

`/api/players` should return a lightweight header:

```text
X-Next-Poll-After: 30
```

This lets `mikdata` slow clients down during upstream errors or quota pressure without shipping a new web build.

The UI should avoid polling from multiple providers on the same page. A single `PlayerStatusProvider` instance should own the request and share state through React context.

### Auth Polling

Login challenge polling is short-lived, but it can still create bursts if the login page is left open. Use a bounded polling policy:

```text
0-20 seconds:
  poll every 2 seconds

20-90 seconds:
  poll every 5 seconds

90 seconds until expiry:
  poll every 10 seconds

hidden tab:
  stop polling and refresh once when visible
```

The challenge TTL should stay short, for example 2-5 minutes. The login page should stop polling immediately after:

- `confirmed`
- `expired`
- `consumed`
- unrecoverable error

`mikdata` should not call the plugin for every browser poll. `AuthStore` should cache plugin confirmation status for a short interval:

```text
pending challenge plugin check interval: at least 2 seconds
confirmed challenge: no further plugin status checks until complete
expired challenge: no plugin call
```

This keeps a single login page from turning into dozens of `mikdata -> plugin` calls.

### Session Validation

`mikweb` should not call `/auth/session` from every component. Use one `GET /api/auth/me` request per page load or app bootstrap, then keep the account summary in `AuthProvider`.

For server route handlers that need authentication:

```text
Read __Host-mik_sid
Forward to mikdata once
Cache the result only inside the current request context
```

Do not put authenticated session responses into Workers Cache API. They contain per-user state and should stay `Cache-Control: no-store`.

### Durable Object Load Shaping

A single global `AuthStore` is acceptable for the expected scale, but it should avoid unnecessary storage work:

- Keep active login challenges in DO memory and persist only the minimal challenge row.
- Use indexed point lookups by `challenge_id`, `sid_hash`, and `credential_id`.
- Maintain per-player Passkey index keys such as `account-passkey:{playerUuid}:{credentialId}` so account pages do not scan all credentials.
- Avoid table scans on hot paths.
- Clean expired challenges and sessions with an alarm or opportunistic cleanup, capped per invocation.
- Store role as a compact string.
- Store session IDs only as hashes.

If player auth traffic grows, shard `AuthStore` by player UUID for long-lived account/session operations while keeping challenge creation in a small number of challenge shards:

```text
AuthChallengeStore: shard by challengeId prefix
AuthAccountStore: shard by playerUuid prefix
```

This is not required initially, but the schema should avoid assuming that every operation must stay in one global object forever.

### KV Usage

KV should not be placed on hot login paths. The KV Free write budget is low, and KV is better suited here for public, low-churn data such as buildings.

For buildings:

- Keep the current summary cache behavior.
- Continue serving list/detail from memory or Workers Cache before reading KV.
- Rebuild summary only on writes.
- Avoid per-request KV reads for every building card.

For auth:

- Use Durable Object storage, not KV.
- Do not write a KV record for every session touch.

### Cache API Usage

Workers Cache API is useful for public GET responses, but it has two implications:

1. Cache entries are local to the data center where they were written.
2. Cache API calls count against per-request Cache API/subrequest budgets.

Use it for coarse public responses:

```text
players
announcements
buildings summary
bans
home-summary
pcl2-summary
```

Avoid many Cache API operations inside a single request. A public route should normally perform at most:

```text
1 cache.match
1 upstream fetch on miss/stale refresh
1 cache.put in waitUntil after successful fetch
```

Authenticated routes should not use Cache API.

### CPU and Payload Constraints

Workers Free CPU time is tight enough that hot paths should avoid repeated JSON validation and large object transformations inside `mikdata`.

Guidelines:

- Keep public proxy responses as prebuilt JSON when possible.
- Keep player status payload small.
- For `home-summary`, include counts and previews, not full buildings and full bans.
- Do not compute search indexes inside `mikdata`; keep wiki/building search client-side or precomputed in `mikweb`.
- Use `ctx.waitUntil()` for cache refresh and cleanup work that does not need to block the response.
- Cap import/admin payload sizes separately from public/auth payloads.

### Quota-Aware Degradation

When upstream plugin calls fail or quota pressure is suspected, degrade public live features before auth:

```text
1. Increase X-Next-Poll-After on /api/players.
2. Serve stale public cached data.
3. Hide player list detail and show only online count if available.
4. Keep login and logout functional.
5. Fail closed for authenticated mutations if AuthStore is unavailable.
```

This order keeps the site usable while protecting the authentication path.

## Route Design

### mikweb Routes

Browser calls only these auth routes:

```text
POST /api/auth/challenges
GET  /api/auth/challenges/:challengeId
POST /api/auth/challenges/:challengeId/complete
GET  /api/auth/me
POST /api/auth/logout
POST /api/auth/passkeys/options/register
POST /api/auth/passkeys/register
POST /api/auth/passkeys/options/login
POST /api/auth/passkeys/login
DELETE /api/auth/passkeys/:credentialId
```

All `mikweb` auth route handlers:

- require same-origin `Origin` on mutating requests
- use `Cache-Control: no-store`
- forward server-side to `mikdata`
- set or clear the `__Host-mik_sid` cookie
- never expose raw session IDs in JSON responses

`mikweb` should keep public data helpers separate:

```ts
publicDataApiUrl('/players') // https://data.mcmik.top/api/players
authApiPath('/api/auth/me')  // same-origin mikweb route
```

### mikdata Routes

`mikdata` routes should be split by surface:

```text
/api/*       public, mostly GET, wildcard CORS allowed
/auth/*      server-to-server from mikweb, no wildcard CORS
/me/*        authenticated player API, no wildcard CORS
/admin*      Cloudflare Access, no public CORS
```

The response helpers should reflect those surfaces:

```ts
publicJson()
authJson()
adminJson()
```

`authJson()` and `/auth/*` should use `Cache-Control: no-store`.

If `mikweb` is the only browser auth entry, `/auth/*` in `mikdata` can require a server-side `MIKWEB_AUTH_CLIENT_SECRET` from `mikweb`. This is not part of `mikdata -> plugin` trust. It prevents arbitrary browsers from using `mikdata` as a direct session issuer.

This design treats that server-side secret as part of the target architecture:

```text
Browser -> mikweb /api/auth/*
  allowed

Browser -> mikdata /auth/*
  rejected by route policy or missing mikweb client credential

mikweb route handler -> mikdata /auth/*
  allowed with MIKWEB_AUTH_CLIENT_SECRET
```

The secret is only for `mikweb -> mikdata` auth operations. It does not add an authentication requirement to `mikdata -> plugin` calls.

### Plugin Routes

Add narrow internal routes to `ApiModule`:

```text
GET  /api/auth/challenges/:code
POST /api/auth/challenges/:code/consume
```

Response shape:

```json
{
  "status": "confirmed",
  "player": {
    "uuid": "00000000-0000-0000-0000-000000000000",
    "name": "Player",
    "role": "member"
  },
  "confirmedAt": "2026-06-28T00:00:00.000Z"
}
```

Other statuses:

```json
{ "status": "pending" }
{ "status": "expired" }
{ "status": "not_found" }
{ "status": "consumed" }
```

The consume operation must be atomic from the plugin's perspective. A confirmed challenge should only be exchanged once.

## Login Protocol

Only two login protocols are supported:

```text
1. In-game verification code
2. Passkey/WebAuthn
```

The in-game verification code is the bootstrap method. Passkey is available after the Minecraft UUID has been bound to a web account.

### Primary Flow: Web Challenge + In-Game Confirmation

```text
Browser -> mikweb:
  POST /api/auth/challenges

mikweb -> mikdata:
  POST /auth/challenges

mikdata:
  create challengeId
  create displayCode
  create browserNonce
  store hash(displayCode), hash(browserNonce), expiresAt

mikdata -> mikweb:
  challengeId, displayCode, expiresAt

mikweb -> browser:
  Set-Cookie: __Host-mik_login=<browserNonce>; Path=/; Secure; HttpOnly; SameSite=Lax; Max-Age=300
  challengeId, displayCode, expiresAt

Player:
  /weblogin <displayCode>

Plugin:
  validates player has group.member or above
  records displayCode -> player uuid/name/role

Browser -> mikweb:
  GET /api/auth/challenges/:challengeId

mikweb -> mikdata:
  GET /auth/challenges/:challengeId

mikdata:
  asks plugin for displayCode status if local status is pending
  returns pending or confirmed player preview

Browser:
  shows confirmed player name and uuid

Browser -> mikweb:
  POST /api/auth/challenges/:challengeId/complete

mikweb:
  reads __Host-mik_login cookie

mikweb -> mikdata:
  POST /auth/challenges/:challengeId/complete
  body includes browserNonce

mikdata:
  validates browserNonce
  validates challenge not expired or consumed
  consumes plugin confirmation
  upserts account
  creates session

mikdata -> mikweb:
  session id, account summary

mikweb -> browser:
  Set-Cookie: __Host-mik_sid=<sessionId>; Path=/; Secure; HttpOnly; SameSite=Lax
  Clear-Cookie: __Host-mik_login
  account summary
```

The display code is not a password. It binds an existing browser challenge to an in-game confirmation. The browser still confirms the player preview before completing the session.

If the player does not have `group.member`, `group.helper`, or `group.manager`, the plugin rejects `/weblogin <displayCode>` and does not write a confirmation record. `mikdata` also verifies that the consumed confirmation includes an eligible `role` before creating a session.

### Passkey Flow

Passkey is a second login method after the account has been created by Minecraft confirmation.

Registration:

```text
logged-in player -> mikweb -> mikdata creates WebAuthn registration challenge
browser navigator.credentials.create()
mikweb -> mikdata verifies attestation response
mikdata stores credential public key and counter
mikdata writes account-passkey secondary index key
```

Login:

```text
browser -> mikweb -> mikdata creates WebAuthn authentication challenge
browser navigator.credentials.get()
mikweb -> mikdata verifies assertion
mikdata creates session
mikweb sets __Host-mik_sid
```

Recovery remains Minecraft confirmation. There is no password reset path.

## API Response Policy

Public API:

```text
Access-Control-Allow-Origin: *
Access-Control-Allow-Methods: GET, OPTIONS
Cache-Control: public / route-specific
```

Auth API:

```text
Cache-Control: no-store
X-Content-Type-Options: nosniff
Referrer-Policy: no-referrer
no wildcard CORS
```

Player Account API:

```text
mikweb /api/account/* -> mikdata /me/*
Cache-Control: no-store
requires valid player session
does not use Workers Cache API
```

Admin API:

```text
Cloudflare Access required
Cache-Control: no-store
no wildcard CORS
```

`OPTIONS` handling should also be route-aware. Public `/api/*` can keep permissive preflight. `/auth/*` and `/admin*` should not inherit public CORS.

## Browser Mutation Boundary

Session cookies are `HttpOnly`, host-only, and `SameSite=Lax`, so JavaScript cannot read them and cross-site POST requests should not carry them. Browsers do not send session IDs to `mikdata`; `mikweb` reads the cookie server-side and forwards the session ID with the shared `MIKWEB_AUTH_CLIENT_SECRET`.

`mikdata` does not store a separate CSRF secret. Authenticated routes rely on BFF-only access, session validation, and `no-store` responses. If stricter browser-side mutation protection is needed later, add `Origin` / `Sec-Fetch-Site` checks in `mikweb` before forwarding requests.

## Rate Limits and Abuse Controls

Challenge creation:

- limit by IP prefix and browser nonce
- keep active challenge count per browser low, for example 3
- expire challenges after 2-5 minutes

Challenge completion:

- allow limited polling, for example once every 1-2 seconds
- lock after repeated invalid browser nonce attempts
- consume challenge on successful session creation

In-game command:

- limit `/weblogin <code>` attempts per player
- limit invalid codes per player per minute
- make a confirmed code single-use

Passkey:

- expire WebAuthn challenges quickly
- bind challenge to session or login transaction
- reject replayed assertion counters when supported by authenticator data

## Plugin Data Structures

The plugin needs concurrency-safe state because command handling and HTTP handling run on different execution contexts.

```java
record WebLoginConfirmation(
    UUID playerUuid,
    String playerName,
    String role,
    Instant confirmedAt,
    Instant expiresAt,
    AtomicBoolean consumed
) {}

ConcurrentHashMap<String, WebLoginConfirmation> confirmations;
```

`/weblogin <code>` stores a confirmation. `/api/auth/challenges/:code/consume` atomically flips `consumed` and returns the confirmed player account and role.

Cleanup can run periodically and remove expired confirmations.

## mikdata Module Layout

Suggested files:

```text
src/auth/types.ts
src/auth/cookies.ts
src/auth/challenges.ts
src/auth/sessions.ts
src/auth/passkeys.ts
src/auth/auth-store.ts
src/auth/routes.ts
src/auth/plugin-client.ts
src/http.ts
src/index.ts
```

`src/http.ts` should expose explicit helpers:

```ts
publicJson(body, status, request, env, headers?)
authJson(body, status, headers?)
adminJson(body, status, headers?)
```

`src/index.ts` should become a clearer router:

```text
OPTIONS route-aware handling
/health
/admin*
/auth*
/me*
/api*
fallback 404
```

## mikweb Module Layout

Suggested files:

```text
src/app/api/auth/challenges/route.ts
src/app/api/auth/challenges/[challengeId]/route.ts
src/app/api/auth/challenges/[challengeId]/complete/route.ts
src/app/api/auth/me/route.ts
src/app/api/auth/logout/route.ts
src/app/api/auth/passkeys/.../route.ts
src/app/[locale]/login/page.tsx
src/app/[locale]/account/page.tsx
src/app/[locale]/account/security/page.tsx
src/modules/auth/model/auth-types.ts
src/modules/auth/model/auth-provider.tsx
src/modules/auth/ui/login-page.tsx
src/modules/account/ui/account-page.tsx
src/modules/account/ui/account-security-page.tsx
src/modules/account/ui/account-menu.tsx
src/shared/api/public-data-api-url.ts
src/shared/api/fetch-public-json.ts
src/shared/api/fetch-auth-json.ts
```

`fetchValidatedJson` can stay as the validator core, but fetch policy should be split:

```ts
fetchPublicJson() // public data, no credentials, external data API
fetchAuthJson()   // same-origin, no-store, CSRF when needed
```

`AuthProvider` should sit beside the existing `PlayerStatusProvider` and `BuildingsProvider` in `src/app/[locale]/layout.tsx`. It should make one `GET /api/auth/me` call at app bootstrap, then expose:

```ts
interface AuthContextValue {
  account: PlayerAccountSummary | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  refreshAccount: () => Promise<void>;
}
```

## User Experience

Login page states:

```text
idle
challenge-created
waiting-for-game-confirmation
confirmed-player-preview
complete
expired
error
```

The confirmed preview should show:

- player name
- player UUID
- Minecraft avatar
- confirmation time
- a button to continue as that player

The UI should not silently finish login just because a code was entered in game. The final browser-side confirmation prevents accidental login to the wrong account when a code is pasted into chat or shown on stream.

## Player Account Interface

The web UI should include a player personal area after login. This is the first consumer of player account and Passkey state, so it belongs in this design rather than as a separate UI-only task.

### Routes

```text
/{locale}/login
/{locale}/account
/{locale}/account/security
```

`/{locale}/login` handles both supported login methods:

- in-game verification code
- Passkey/WebAuthn

`/{locale}/account` is the player overview page.

`/{locale}/account/security` is the security page for Passkeys, current session, and logout.

Unauthenticated requests to `/{locale}/account*` should render a login-required state with a link to `/{locale}/login`. Avoid redirect loops because localized routes and static rendering can make redirect behavior harder to debug.

### Header Entry

The current header already has right-side controls for theme, locale, and online player status. The player account entry should live in that same control area:

```text
logged out:
  compact Login button with a user icon

logged in:
  Minecraft avatar button
  dropdown:
    player name
    UUID short form
    Account
    Security
    Logout
```

This keeps the main navigation focused on public site sections. The account entry is stateful and belongs with other controls, not with `Home`, `Buildings`, `Wiki`, `Map`, and `Bans`.

The existing online-player dropdown should stay separate. It shows server presence. The account menu shows the current authenticated viewer.

### Account Overview

`/{locale}/account` should show compact personal state:

```text
Player account
  avatar
  current Minecraft name
  UUID

Server role
  role

Personal quick links
  open security settings
  open buildings filtered by this player, if builder UUID data exists
  open server map
```

Do not put high-frequency live data on this page unless it is already available from `AuthProvider` or public cached endpoints. The account page should not start another player status poller.

### Security Page

`/{locale}/account/security` should show:

```text
Login methods
  In-game verification code: always available as recovery
  Passkey: enrolled / not enrolled

Passkeys
  list credential display names
  last used time
  add Passkey
  remove Passkey after recent login

Session
  current login method
  issuedAt
  idleExpiresAt
  logout
```

If active-session listing is implemented later, keep it on this page and load it on demand. Do not fetch all sessions as part of the global `AuthProvider`.

### Account Data API

The account UI should use same-origin `mikweb` BFF endpoints:

```text
GET    /api/auth/me
POST   /api/auth/logout
GET    /api/account/summary
GET    /api/account/security
POST   /api/auth/passkeys/options/register
POST   /api/auth/passkeys/register
DELETE /api/auth/passkeys/:credentialId
```

`/api/auth/me` should remain the global bootstrap endpoint. `/api/account/summary` and `/api/account/security` can include heavier data and should only be called by account pages.

All account endpoints should be `Cache-Control: no-store`. The UI can keep the loaded account summary in React state, but no authenticated response should enter browser persistent cache or Workers Cache API.

Account UI summary:

- show current player account
- show active login method
- show Passkey enrollment state
- allow logout
- allow removing Passkeys after recent login

## Failure Modes

Challenge expires before in-game confirmation:

- browser shows expired state
- plugin rejects or ignores later confirmation
- user creates a new challenge

Player enters wrong code:

- plugin tells the player the code is invalid or expired
- no web state changes

Player is not a formal member:

- plugin rejects `/weblogin <code>`
- browser challenge remains pending until expiry
- login page explains that web login is only available to formal members

Browser completes a code confirmed by another player:

- browser shows preview first
- user can cancel and create a new challenge

Plugin unavailable:

- challenge status remains pending until expiry
- UI can show "server confirmation unavailable"
- no fallback session is created

mikdata AuthStore unavailable:

- login and authenticated player features fail closed
- public `/api/*` routes remain independent

Session expired:

- `/api/auth/me` returns unauthenticated
- UI returns to logged-out state
- public site still works

Passkey unavailable:

- user can recover by Minecraft confirmation

## Security Notes

- The display code is a correlation code, not a password.
- The browser session cookie is host-only on `mcmik.top`.
- `data.mcmik.top/api/*` remains public and cacheable.
- `data.mcmik.top/auth/*` should not inherit wildcard CORS.
- `data.mcmik.top/auth/*` should only issue sessions for requests carrying the `mikweb` server-side client credential.
- Session IDs are stored only as hashes in `mikdata`.
- Plugin confirmations are temporary and single-use.
- Admin access remains Cloudflare Access, separate from player login.

Relevant external references:

- OWASP Session Management Cheat Sheet
- OWASP Authentication Cheat Sheet
- OWASP Cross-Site Request Forgery Prevention Cheat Sheet
- W3C WebAuthn

## Open Decisions

These choices should be finalized before implementation:

1. Whether session revocation UI lists all active sessions or only supports current-session logout.

## Design Summary

The complete design introduces a player account layer without changing the role of existing public and admin surfaces:

```text
Public data:
browser -> data.mcmik.top/api/*

Player auth:
browser -> mcmik.top/api/auth/* -> mikweb -> mikdata AuthStore -> plugin confirmation

Admin:
browser -> data.mcmik.top/admin* -> Cloudflare Access
```

This keeps the public API simple, the player auth state centralized, and the plugin responsibility narrow. The first authentication factor is in-game Minecraft confirmation. Passkey becomes the passwordless repeat-login method after that account has been established.
