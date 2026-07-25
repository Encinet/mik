# NBS redstone music

NBS trumpet sounds require a Minecraft/Paper version that provides the four
`block.note_block.trumpet*` sound events. This project targets Paper 26.1.2.

Place Open Note Block Studio `.nbs` files anywhere below `plugins/mik/music`.
The directory is scanned recursively. Run `/music reload` after adding or
replacing files.

NBS songs use the normal MIK disc, jukebox queue, stop, and auto-play flows.
They are played as spatial Minecraft note-block sounds at the jukebox rather
than being decoded by Lavaplayer.

Supported data includes:

- legacy NBS and modern versions 1 through 5
- tempo, layers, layer volume, stereo panning, note velocity, and fine pitch
- finite and infinite NBS loops
- the 16 established Minecraft note-block instruments
- trumpet, exposed trumpet, weathered trumpet, and oxidized trumpet

Layer volume from 0% through 100% is preserved for every supported NBS version.
Versions 4 and 5 also preserve each note's 0%-100% velocity; legacy files and
versions 1 through 3 use 100% note velocity as defined by the format. Playback
multiplies these two values linearly. A zero in either field is silent, while a
very low non-zero combination remains non-zero and is not raised to an artificial
minimum volume.

Playback accepts the complete NBS tempo field from 0.01 through 655.35 ticks per second.
Songs above 20 ticks per second advance multiple song ticks during one Paper server tick.
One song tick may contain at most 256 notes, and one file may contain at most 200,000 notes.
Short scheduler delays preserve elapsed song time. After a long server stall, playback keeps
at most one second or 20 song ticks of stale timing. For tempos above 400 ticks per second,
one normal server tick's required progress is retained so the configured tempo remains
representable. Older timing debt is discarded instead of causing an unbounded burst.

For files that declare 20 vanilla instruments, IDs 16 through 19 map to the
four trumpet oxidation stages. Standard files that declare 16 vanilla
instruments can provide the same sounds as custom instruments. MIK recognizes
custom instrument names or sample paths containing `trumpet`, together with
`exposed`, `weathered`, or `oxidized`/`oxidised`. Chinese names such as `小号`,
`斑驳小号`, `锈蚀小号`, and `氧化小号` are also recognized.

Custom audio samples are not loaded. Unrecognized custom instruments safely
fall back to the vanilla harp sound.
