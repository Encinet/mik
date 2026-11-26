# MIK LX custom-source extension

MIK accepts trusted LX Music custom-source JavaScript files from
`/var/lib/mikd/music/sources/local` by default. Local scripts are loaded recursively in
relative-path order; numeric filename prefixes can define fallback priority.
Each channel declares only the actions it provides: `musicUrl` for playback,
`musicSearch` for discovery, `musicCollection` for a link type, and `lyric` for
lyrics. A discovery-only script can share a namespace with a playback script.
For the built-in `kw`, `kg`, `tx`, `wy`, and `mg` catalogs, a healthy
`musicUrl` provider can also enable built-in discovery and playlist lookup.

A channel name identifies a catalog namespace, not an individual script. Use
the same upstream stable key when two scripts refer to the same recording. Do
not use a shared namespace for unrelated ID schemes, or return a different
recording for an existing key. Paper receives an opaque TrackId; LX metadata,
script IDs, playback URLs and source selection stay in `mikd`.

The QuickJS host in `mikd` provides the LX desktop `lx` surface used by common custom sources:
HTTP(S) requests, `currentScriptInfo`, buffer conversion, MD5, AES/RSA helpers,
random bytes, zlib inflate/deflate, and browser-style timeout/interval timers.
`lx.utils.crypto.aesEncrypt` accepts byte arrays or UTF-8 strings for the
plaintext, key, and IV. AES keys must contain 16, 24, or 32 bytes after
encoding; CBC and CTR require a 16-byte IV. The host does not pad or truncate
keys. Use `lx.utils.buffer.from(value, 'hex')` or
`lx.utils.buffer.from(value, 'base64')` for encoded bytes.
`mikd` drives timers and callbacks during script initialization and LX actions;
idle sources do not run background jobs.
Scripts run without host-class, file,
native, or direct I/O access. HTTP requests have a 20-second maximum and bounded
request/response sizes; each script can have at most 16 concurrent HTTP requests.
Three consecutive failures open the circuit for 60 seconds.
Install only sources you trust because a source can make outbound HTTP requests.

`mikd` creates the [LX source directories](music-architecture.md#文件与配置).
Restart `mikd` after changing administrator-managed local scripts. Open [`mikd admin`](../deploy/mikd/README.md#本机运维) and select Sources. The list shows names and availability. Press Enter for the source ID, channel actions, and last error.

## URL imports

Press `a` on the Sources page and enter the URL for a trusted source.

```text
https://example.invalid/lx-source.js
file:///var/lib/mikd/music/sources/imports/lx-source.js
```

MIK initializes the downloaded script in a separate QuickJS context before
registering it. The script is copied into a managed snapshot; a `file://` source is never
executed directly from its original location. File subscriptions must resolve
to a regular file within the configured sources directory's `imports/` folder.

```text
/var/lib/mikd/music/sources/
├── local/
│   └── administrator-managed.js
├── imports/
└── subscriptions/
    └── remote-<URL SHA-256 prefix>.json
```

Each managed JSON file holds the source URL, HTTP validators, and the last
working script together.
Scripts placed directly in the `sources` root are not loaded.

Imported HTTP(S) and `file://` sources are checked at startup, when you
press `r` on the Sources page or in a subscription’s details, and every six hours. HTTP(S) updates
use `If-None-Match` and `If-Modified-Since` where available. Downloads are limited
to 2 MiB and 20 seconds. Up to four subscriptions can download and validate
independently. Commits recheck the installed version, so a late refresh cannot
overwrite a newer import or resurrect a removed source. A changed script is
validated before an atomic snapshot replacement. Failed
downloads and invalid updates retain the last working snapshot and expose the
error in the source details.

Install or import only trusted sources because source scripts can make outbound
requests. A managed snapshot is independent of the original `file://` target;
updates replace only the managed copy.

By default, a source has one runtime and requests share its state in order.
For a trusted script that supports independent request state and idempotent
initialization, add its exact ID from the source details to `sources/parallel-sources.txt`,
one ID per line, then restart `mikd`. For example, `local/demo.js` has ID `local:demo.js`.
An opted-in script can start a second isolated runtime when its
first is busy. The process-wide budget for extra runtimes follows its available
logical CPUs; each runtime retains its own memory limit, while backoff is shared
per source. Do not opt in scripts that rely on state shared between requests.

Declare supported actions on each music source:

```js
send(EVENT_NAMES.inited, {
  status: true,
  sources: {
    kw: {
      type: 'music',
      actions: ['musicUrl', 'musicSearch', 'musicCollection', 'lyric'],
      qualitys: ['128k', '320k'],
      collectionPrefix: 'https://example.invalid/playlists/'
    }
  }
})
```

The request handler receives `{ action, source, info }`. It may return a value
or a Promise.

## Lyrics

For `action: 'lyric'`, `info` is `{ musicInfo }`, where `musicInfo` is the
same complete LX MusicInfo object passed to `musicUrl`. The action can return
plain LRC text or an object containing original lyrics and optional secondary
tracks. MIK recognizes `lyric`, `lrc`, `lxlyric`, or `original` for the
original text; `tlyric`, `tlrc`, `translation`, or `trans` for translations;
and `rlyric`, `rlrc`, `romanization`, or `roma` for romanization. A `data`,
`result`, or `body` wrapper is also accepted.

```js
if (action === 'lyric') {
  return {
    lyric: '[00:01.20]Original lyric',
    tlyric: '[00:01.20]Translated lyric',
    rlyric: '[00:01.20]Romanized lyric'
  }
}
```

Lyrics are optional. `mikd` binds the normalized document to the selected
media version and prefers the script that provided that media when fetching
lyrics. An LX timestamp alone does not establish recording alignment. The
current LX adapter exposes the text as independent plain tracks; it does not
interpolate or synchronize lyrics. Use [admin lyrics set](../deploy/mikd/README.md#歌词校订)
for verified timings.

## Custom collection links

`collectionPrefix` declares one HTTPS origin and path prefix ending in `/`.
It must have no credentials, non-default port, query, or fragment. The
remaining path must be a single key of up to 128 ASCII letters, digits, hyphens
or underscores. For example, the declaration above accepts
`https://example.invalid/playlists/summer-2026`. The handler receives only
`info: { key: 'summer-2026' }`, not the user's URL, and returns a title and an
ordered list of the same song objects used by search:

```js
if (action === 'musicCollection') {
  return { name: 'Summer', list: songsForVerifiedKey(info.key) }
}
```

Only enabled adapters can handle links. The catalog deduplicates within the
preview, applies its result budget, and pages a single snapshot; saving the
preview creates no ongoing subscription. The Paper command or menu accepts a
link only and does not select a platform.

## Song search

`info` is `{ keyword, page, limit }`.

```js
return {
  list: [
    {
      id: 'kw_123',
      name: 'Song',
      singer: 'Artist',
      source: 'kw',
      songmid: '123',
      interval: '03:20',
      albumName: 'Album',
      types: ['128k', '320k']
    }
  ],
  total: 1,
  page: 1,
  limit: 30
}
```

Songs use the normal LX shape. Export-style objects containing `meta.songId`,
`meta.qualitys`, and `meta._qualitys` are also accepted.

`mikd` queries a bounded number of healthy sources concurrently and keeps
usable results when a source fails. Action responses are limited to 4 MiB.
Discovery keeps up to 200 upstream tracks before combining them with the
existing catalog. IDs and resolver metadata are persisted when a page is
returned, so a later restart or a different playback source does not change
the saved identity.

For the source that actually served a version, including cache hits, see
[music diagnostics](../deploy/mikd/README.md#音乐库与音源).
