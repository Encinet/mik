# MIK LX custom-source extension

MIK accepts trusted LX Music custom-source JavaScript files from
`plugins/mik/lxmusic/local`. Local scripts are loaded recursively in
relative-path order; numeric filename prefixes can define fallback priority.
The official LX `musicUrl` action remains the minimum playback capability.
Online song search optionally uses one MIK action:

- `musicSearch`: search songs

Only `musicUrl` is part of the LX Music custom-source protocol. `musicSearch`
is a MIK extension. Existing LX scripts that only declare `musicUrl` work for
MIK online song search.
For online song search, MIK queries the catalog of each healthy declared
`kw`, `kg`, `tx`, `wy`, or `mg` channel, then sends the resulting MusicInfo back
to that script through `musicUrl`. Scripts without MIK search actions therefore
remain useful, while MIK never obtains a playback URL directly. MIK does not
provide online song-list discovery or detail loading.
`/music sources` reports this built-in search capability as
`musicsearch(catalog)` for each eligible channel.

The runtime provides the LX desktop `lx` surface used by common custom sources:
HTTP(S) requests, `currentScriptInfo`, buffer conversion, MD5, AES/RSA helpers,
random bytes, zlib inflate/deflate, and browser-style timeout/interval timers.
Scripts run without host-class, file,
native, or direct I/O access. Requests use a fixed 20-second timeout and bounded
request/response sizes; each script can have at most 16 concurrent HTTP requests.
Three consecutive failures open the circuit for 60 seconds.
Install only sources you trust because a source can make outbound HTTP requests.

There is no user-editable music-specific configuration file. Run `/music reload`
after changing local scripts and `/music sources` to inspect capabilities,
health, failures, retry time, and imported subscription status.

## URL imports

Managers can import a trusted source with:

```text
/music sources import https://example.invalid/lx-source.js
/music sources import file:///srv/mik-sources/lx-source.js
```

MIK validates the downloaded script with an isolated runtime before registering
it. The script is copied into a managed snapshot; a `file://` source is never
executed directly from its original location.

```text
plugins/mik/lxmusic/
├── local/
│   └── administrator-managed.js
└── remote/
    ├── sources.json
    └── remote-<URL SHA-256 prefix>.js
```

`remote/sources.json` is an internal subscription registry, not a music
configuration file. MIK owns it and the hash-named files in `remote`. Scripts
placed in the `lxmusic` root or manually copied into `remote` are not loaded.

Imported HTTP(S) and `file://` sources are checked at startup, by `/music reload`,
by `/music sources update`, and automatically every six hours. HTTP(S) updates
use `If-None-Match` and `If-Modified-Since` where available. Downloads are limited
to 2 MiB and 20 seconds. A changed script is validated before an atomic snapshot
replacement, and LX runtimes are reloaded only when content changes. Failed
downloads and invalid updates retain the last working snapshot and expose the
error through `/music sources`.

Use `/music sources remove <remote-id>` to remove a managed subscription and its
snapshot. This command cannot remove files from `local` or the original target
of a `file://` URL. Install or import only trusted sources because source scripts
can make outbound requests.

Declare supported actions on each music source:

```js
send(EVENT_NAMES.inited, {
  status: true,
  sources: {
    kw: {
      type: 'music',
      actions: ['musicUrl', 'musicSearch'],
      qualitys: ['128k', '320k']
    }
  }
})
```

The request handler receives `{ action, source, info }`. It may return a value
or a Promise.

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

Use `/music sources` as a manager to inspect source IDs, supported channels,
actions, failure counts, retry time, and the last error. After the fixed
three-failure threshold, MIK temporarily skips a source. Timed-out runtimes are
closed and re-created after the retry delay.
