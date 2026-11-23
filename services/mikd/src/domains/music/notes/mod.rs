//! NBS is an import format. The only output is the bounded `NOTE_EVENTS` v1 score.
use mik_protocol::music::{
    NoteEvent, NoteLoop, NoteScore, NoteSound, NoteStop, note_event::Action,
};
use std::{
    io::{self, BufRead, BufReader, Read},
    time::Duration,
};

pub(super) const CONTENT_TYPE: &str = "application/vnd.mik.note-events+protobuf";
const MAX_TICK: i64 = 10_000_000;
const MAX_EVENTS: usize = 200_000;
const MAX_LAYERS: usize = 4096;
const TONES: [&str; 20] = [
    "harp",
    "bass",
    "basedrum",
    "snare",
    "hat",
    "guitar",
    "flute",
    "bell",
    "chime",
    "xylophone",
    "iron_xylophone",
    "cow_bell",
    "didgeridoo",
    "bit",
    "banjo",
    "pling",
    "trumpet",
    "trumpet_exposed",
    "trumpet_weathered",
    "trumpet_oxidized",
];

pub(super) struct Imported {
    pub title: String,
    pub artist: String,
    pub original_author: String,
    pub score: NoteScore,
}

struct Header {
    version: u8,
    instruments: u8,
    title: String,
    artist: String,
    original_author: String,
    length: u16,
    layers: usize,
    tempo: u16,
    loop_begin: Option<u16>,
    repeat: Option<u32>,
}

#[derive(Clone, Copy)]
struct Layer {
    mode: u8,
    volume: u8,
    pan: i32,
}

struct Note {
    tick: i64,
    layer: usize,
    instrument: u8,
    key: u8,
    velocity: u8,
    pan: u8,
    fine: i16,
}

struct Instrument {
    name: String,
    tone: &'static str,
    key: u8,
}

pub(super) fn compile(source: impl Read) -> io::Result<Imported> {
    let mut source = BufReader::new(source);
    let header = read_header(&mut source)?;
    let notes = read_notes(&mut source, &header)?;
    let length = i64::from(header.length).max(notes.last().map_or(1, |note| note.tick + 1));
    if length > MAX_TICK {
        return Err(super::invalid("NBS score too long"));
    }
    let (layers, custom) = read_tail(&mut source, &header)?;
    let score = normalize(&header, length, &notes, &layers, &custom)?;
    Ok(Imported {
        title: header.title,
        artist: header.artist,
        original_author: header.original_author,
        score,
    })
}

fn read_header(input: &mut impl Read) -> io::Result<Header> {
    let mut length = u16::from_le_bytes(read(input)?);
    let (version, instruments) = if length == 0 {
        let [version, instruments] = read(input)?;
        if !(1..=6).contains(&version) || usize::from(instruments) > TONES.len() {
            return Err(super::invalid("unsupported NBS header"));
        }
        if version >= 3 {
            length = u16::from_le_bytes(read(input)?);
        }
        (version, instruments)
    } else {
        (0, 10)
    };
    let layers = usize::from(u16::from_le_bytes(read(input)?));
    if layers > MAX_LAYERS {
        return Err(super::invalid("too many NBS layers"));
    }
    let title = text(input)?;
    let artist = text(input)?;
    let original_author = text(input)?;
    drop(text(input)?);
    let tempo = u16::from_le_bytes(read(input)?);
    if tempo == 0 {
        return Err(super::invalid("invalid NBS tempo"));
    }
    let _: [u8; 23] = read(input)?;
    drop(text(input)?);
    let (loop_begin, repeat) = if version >= 4 {
        let [enabled, repeats] = read(input)?;
        let tick = u16::from_le_bytes(read(input)?);
        (
            (enabled != 0).then_some(tick),
            (repeats != 0).then_some(u32::from(repeats)),
        )
    } else {
        (None, None)
    };
    Ok(Header {
        version,
        instruments,
        title,
        artist,
        original_author,
        length,
        layers,
        tempo,
        loop_begin,
        repeat,
    })
}

fn read_notes(input: &mut impl Read, header: &Header) -> io::Result<Vec<Note>> {
    let mut notes = Vec::new();
    let mut tick = -1_i64;
    loop {
        let jump = u16::from_le_bytes(read(input)?);
        if jump == 0 {
            break;
        }
        tick += i64::from(jump);
        if tick >= MAX_TICK {
            return Err(super::invalid("NBS score too long"));
        }
        let mut layer = 0;
        loop {
            let jump = u16::from_le_bytes(read(input)?);
            if jump == 0 {
                break;
            }
            layer += usize::from(jump);
            let [instrument, key] = read(input)?;
            if key > 87 || layer > header.layers || notes.len() == MAX_EVENTS {
                return Err(super::invalid("NBS note out of range"));
            }
            let (velocity, pan, fine) = if header.version >= 4 {
                let [velocity, pan] = read(input)?;
                (velocity, pan, i16::from_le_bytes(read(input)?))
            } else {
                (100, 100, 0)
            };
            notes.push(Note {
                tick,
                layer: layer - 1,
                instrument,
                key,
                velocity,
                pan,
                fine,
            });
        }
    }
    Ok(notes)
}

fn read_tail(
    input: &mut impl BufRead,
    header: &Header,
) -> io::Result<(Vec<Layer>, Vec<Instrument>)> {
    let mut layers = vec![
        Layer {
            mode: 0,
            volume: 100,
            pan: 0
        };
        header.layers
    ];
    // The optional layer section may be absent, but if present all declared layers must be readable.
    if input.fill_buf()?.is_empty() {
        return Ok((layers, Vec::new()));
    }
    for layer in &mut layers {
        drop(text(input)?);
        layer.mode = if header.version >= 4 {
            read::<1>(input)?[0]
        } else {
            0
        };
        layer.volume = read::<1>(input)?[0];
        layer.pan = if header.version >= 2 {
            i32::from(read::<1>(input)?[0]) - 100
        } else {
            0
        };
        if layer.mode > 2 || layer.volume > 100 || !(-100..=100).contains(&layer.pan) {
            return Err(super::invalid("NBS layer out of range"));
        }
    }
    let mut custom = Vec::new();
    if !input.fill_buf()?.is_empty() {
        for _ in 0..read::<1>(input)?[0] {
            let name = text(input)?.trim().to_lowercase();
            let filename = text(input)?.to_lowercase();
            let [key, _press] = read(input)?;
            if key > 87 {
                return Err(super::invalid("NBS custom instrument pitch out of range"));
            }
            let tone = custom_tone(&format!("{name} {filename}"));
            custom.push(Instrument { name, tone, key });
        }
    }
    Ok((layers, custom))
}

fn normalize(
    header: &Header,
    length: i64,
    notes: &[Note],
    layers: &[Layer],
    custom: &[Instrument],
) -> io::Result<NoteScore> {
    let solo = layers.iter().any(|layer| layer.mode == 2);
    let mut events = Vec::with_capacity(notes.len());
    let mut clock = Clock {
        tick: 0,
        elapsed: 0.0,
        tempo: f64::from(header.tempo) / 100.0,
    };
    let loop_tick = header
        .loop_begin
        .map(i64::from)
        .filter(|tick| *tick < length);
    let mut loop_time = None;
    for frame in notes.chunk_by(|a, b| a.tick == b.tick) {
        let tick = frame[0].tick;
        if let Some(start) = loop_tick.filter(|start| *start <= tick && loop_time.is_none()) {
            loop_time = Some(clock.at(start)?);
        }
        let time_us = clock.at(tick)?;
        for note in frame {
            let instrument = note
                .instrument
                .checked_sub(header.instruments)
                .and_then(|index| custom.get(usize::from(index)));
            let layer = layers[note.layer];
            let action = match instrument.map(|value| value.name.as_str()) {
                Some("tempo changer") if header.version >= 4 => {
                    let tempo = f64::from(note.fine.unsigned_abs()) / 15.0;
                    if tempo > 0.0 && tempo <= 655.35 {
                        clock.tempo = tempo;
                    }
                    continue;
                }
                Some("sound stopper") if header.version >= 5 => {
                    let first = u32::try_from(note.fine.max(0)).unwrap_or_default();
                    let last = u32::from(u16::from_le_bytes([
                        note.pan.wrapping_sub(100),
                        note.velocity.wrapping_sub(100),
                    ]));
                    Action::Stop(NoteStop {
                        first_voice: (first != 0).then_some(first - u32::from(first != 0)),
                        last_voice: (first != 0).then_some(last.max(first).saturating_sub(1)),
                    })
                }
                _ => {
                    let instrument_key = instrument.map_or(45, |value| value.key);
                    let tone = if note.instrument < header.instruments {
                        TONES[usize::from(note.instrument)]
                    } else {
                        instrument.map_or("harp", |value| value.tone)
                    };
                    if note.velocity > 100 || note.pan > 200 {
                        return Err(super::invalid("invalid NBS sound level/pan"));
                    }
                    let gain = if layer.mode == 1 || solo && layer.mode != 2 {
                        0
                    } else {
                        u32::from(note.velocity) * u32::from(layer.volume)
                    };
                    let pan = i32::from(note.pan) - 100;
                    Action::Sound(NoteSound {
                        voice: u32::try_from(note.layer).map_err(io::Error::other)?,
                        tone: tone.into(),
                        pitch_cents: (i32::from(note.key) + i32::from(instrument_key) - 90) * 100
                            + i32::from(note.fine),
                        gain,
                        pan: if layer.pan == 0 {
                            pan
                        } else {
                            i32::midpoint(pan, layer.pan)
                        },
                    })
                }
            };
            events.push(NoteEvent {
                time_us,
                action: Some(action),
            });
        }
    }
    if let Some(start) = loop_tick.filter(|_| loop_time.is_none()) {
        loop_time = Some(clock.at(start)?);
    }
    Ok(NoteScore {
        duration_us: clock.at(length)?,
        r#loop: loop_time.map(|start_us| NoteLoop {
            start_us,
            repeats: header.repeat,
        }),
        events,
    })
}

struct Clock {
    tick: i64,
    elapsed: f64,
    tempo: f64,
}
impl Clock {
    fn at(&mut self, tick: i64) -> io::Result<u64> {
        self.elapsed +=
            f64::from(i32::try_from(tick - self.tick).map_err(io::Error::other)?) / self.tempo;
        self.tick = tick;
        let elapsed = Duration::try_from_secs_f64(self.elapsed).map_err(io::Error::other)?;
        u64::try_from(elapsed.as_micros()).map_err(io::Error::other)
    }
}

fn custom_tone(name: &str) -> &'static str {
    let has = |words: &[&str]| words.iter().any(|word| name.contains(word));
    if !has(&["trumpet", "小号", "小號"]) {
        "harp"
    } else if has(&["exposed", "斑驳", "斑駁"]) {
        "trumpet_exposed"
    } else if has(&["oxidized", "oxidised", "氧化"]) {
        "trumpet_oxidized"
    } else if has(&["weathered", "锈蚀", "鏽蝕"]) {
        "trumpet_weathered"
    } else {
        "trumpet"
    }
}

fn read<const N: usize>(input: &mut impl Read) -> io::Result<[u8; N]> {
    let mut bytes = [0; N];
    input.read_exact(&mut bytes)?;
    Ok(bytes)
}

fn text(input: &mut impl Read) -> io::Result<String> {
    let length = u32::from_le_bytes(read(input)?) as usize;
    if length > 1 << 20 {
        return Err(super::invalid("oversized NBS string"));
    }
    let mut bytes = vec![0; length];
    input.read_exact(&mut bytes)?;
    Ok(match String::from_utf8(bytes) {
        Ok(text) => text,
        Err(error) => error
            .as_bytes()
            .iter()
            .map(|&byte| char::from(byte))
            .collect(),
    })
}

#[cfg(test)]
mod tests;
