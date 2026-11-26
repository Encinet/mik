# NBS imports and note playback

Open `mikd admin`, select Library, and press `a` to import a `.nbs` file. `mikd` preserves the original and leases a compiled `NOTE_EVENTS` v1 score to Paper. The resulting track uses the same catalog, queue, disc and rhythm workflows as any other managed track.

## Compilation and output

- The importer accepts classic NBS and versions 1–6. It reads title, author and original author for the catalog and keeps other header data in the original file. An omitted optional tail uses neutral layer defaults; a partially written record is rejected.
- Tempo and tempo-change events compile to absolute microseconds from the start of the score. The score preserves order within each timestamp, note velocity and pitch, layer gain and panning, mute/solo behavior, and supported sound-stop ranges. Stops do not become notes.
- It represents finite or infinite loops with a start, an end at the score duration, and an optional repeat count. A loop is not expanded while compiling. Looping scores do not offer seeking; the player and its captions follow the loop's actual media position.
- The score uses the Minecraft note-block tone vocabulary, including the four trumpet sounds. Custom trumpet names and file names can map to those tones. Other custom instruments fall back to harp with their declared base pitch; custom samples are not opened or distributed.
- The importer limits the input to 16 MiB and 200,000 events. Paper verifies the leased representation before using the same event timing for note output and rhythm analysis. The game scheduler bounds catch-up after a stall rather than replaying an unlimited burst.

Note playback uses the game's sound events and does not require listeners to have Plasmo Voice installed. The API target for the trumpet events is in [paper-plugin.yml](../src/main/resources/paper-plugin.yml). For installation and replacement, see [music library administration](../deploy/mikd/README.md#音乐库与音源); for playback and calibration, see [music and rhythm gameplay](music.md#音游与校准).
