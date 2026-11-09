# MIK LX custom-source extension

MIK accepts trusted LX Music custom-source JavaScript files from
`plugins/mik/music/sources/local`. Local scripts are loaded recursively in
relative-path order; numeric filename prefixes can define fallback priority.
The official LX `musicUrl` action remains the minimum playback capability.
MIK also supports these optional actions:

- `musicSearch`: search songs
- `lyric`: load lyrics for one song

Only `musicUrl` is required. `musicSearch` is a MIK extension and `lyric` is
an optional LX-compatible capability. Existing LX scripts that only declare
`musicUrl` continue to work for MIK online song search and playback.
For online song search, MIK queries the catalog of each healthy declared
`kw`, `kg`, `tx`, `wy`, or `mg` channel, then sends the resulting MusicInfo back
to that script through `musicUrl`. Scripts without MIK search actions therefore
remain useful, while MIK never obtains a playback URL directly. MIK also reads
public playlist details from the built-in `kw`, `kg`, `tx`, `wy`, and `mg`
platform catalogs. Playlist import is a MIK catalog capability rather than a
custom-source action; imported tracks are still sent to a compatible script's
`musicUrl` action when played.
`/music sources` reports this built-in search capability as
`musicsearch(catalog)` and playlist import as `playlist(catalog)` for each
eligible channel.

The runtime provides the LX desktop `lx` surface used by common custom sources:
HTTP(S) requests, `currentScriptInfo`, buffer conversion, MD5, AES/RSA helpers,
random bytes, zlib inflate/deflate, and browser-style timeout/interval timers.
Scripts run without host-class, file,
native, or direct I/O access. Requests use a fixed 20-second timeout and bounded
request/response sizes; each script can have at most 16 concurrent HTTP requests.
Three consecutive failures open the circuit for 60 seconds.
Install only sources you trust because a source can make outbound HTTP requests.

Music startup settings live in `plugins/mik/music/settings.yml`; see
[Music files and configuration](music-storage.md). Run `/music reload` after
changing local scripts and `/music sources` to inspect capabilities, health,
failures, retry time, and imported subscription status.

## URL imports

Custodians can import a trusted source with:

```text
/music sources import https://example.invalid/lx-source.js
/music sources import file:///srv/mik-sources/lx-source.js
```

MIK validates the downloaded script with an isolated runtime before registering
it. The script is copied into a managed snapshot; a `file://` source is never
executed directly from its original location.

```text
plugins/mik/music/sources/
├── local/
│   └── administrator-managed.js
├── managed/
│   └── remote-<URL SHA-256 prefix>.js
└── subscriptions.json
```

`subscriptions.json` is an internal subscription registry. MIK owns it and the
hash-named files in `managed`. Keep both when backing up music sources: managed
scripts are retained working snapshots. Scripts placed in the `sources` root or
unregistered files manually copied into `managed` are not loaded.

Imported HTTP(S) and `file://` sources are checked at startup, by `/music reload`,
by `/music sources update`, and periodically according to
`settings.yml` (`sources.refresh-interval-minutes`, six hours by default;
`0` disables periodic checks). HTTP(S) updates
use `If-None-Match` and `If-Modified-Since` where available. Downloads are limited
to 2 MiB and 20 seconds. A changed script is validated before an atomic snapshot
replacement, and LX runtimes are reloaded only when content changes. Failed
downloads and invalid updates retain the last working snapshot and expose the
error through `/music sources`.

Install or import only trusted sources because source scripts can make outbound
requests. A managed snapshot is independent of the original `file://` target;
updates replace only the managed copy.

Declare supported actions on each music source:

```js
send(EVENT_NAMES.inited, {
  status: true,
  sources: {
    kw: {
      type: 'music',
      actions: ['musicUrl', 'musicSearch', 'lyric'],
      qualitys: ['128k', '320k']
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

Lyrics are optional. When no healthy source declares `lyric`, MIK attempts the
HTTP(S) `lrcUrl` stored in the provider MusicInfo and may use `trcUrl` as a
translation track. Results are bounded and cached separately from audio; no
lyrics are included in disc snapshots or cached music metadata.

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

MIK calls healthy scripts in relative-path order, aggregates search results,
deduplicates stable IDs, and keeps partial results when one script fails.
Action responses are limited to 2 MiB and 200 accepted items per request.

Use `/music sources` as a custodian to inspect source IDs, supported channels,
actions, failure counts, retry time, and the last error. After the fixed
three-failure threshold, MIK temporarily skips a source. A timeout counts as one
failure; only repeated timeouts reaching that same threshold close the runtime,
which is then re-created after the retry delay.
