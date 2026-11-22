use super::*;

fn text(out: &mut Vec<u8>, text: &[u8]) {
    out.extend(u32::try_from(text.len()).unwrap().to_le_bytes());
    out.extend(text);
}

fn header(version: u8, layers: u16, tempo: u16, repeats: u8, title: &[u8]) -> Vec<u8> {
    let mut out = Vec::new();
    if version == 0 {
        out.extend(3u16.to_le_bytes());
    } else {
        out.extend([0, 0, version, if version >= 5 { 20 } else { 16 }]);
        if version >= 3 {
            out.extend(3u16.to_le_bytes());
        }
    }
    out.extend(layers.to_le_bytes());
    for value in [title, b"Author", b"Original", b"Description"] {
        text(&mut out, value);
    }
    out.extend(tempo.to_le_bytes());
    out.extend([0; 23]);
    text(&mut out, b"");
    if version >= 4 {
        out.extend([u8::from(repeats > 0), repeats, 1, 0]);
    }
    out
}

fn layer(out: &mut Vec<u8>, version: u8, mode: u8, gain: u8, pan: u8) {
    text(out, b"Layer");
    if version >= 4 {
        out.push(mode);
    }
    out.push(gain);
    if version >= 2 {
        out.push(pan);
    }
}

fn instrument(out: &mut Vec<u8>, name: &str, key: u8) {
    text(out, name.as_bytes());
    text(out, b"folder/sample.ogg");
    out.extend([key, 0]);
}

fn sound(event: &NoteEvent) -> &NoteSound {
    let Some(Action::Sound(value)) = event.action.as_ref() else {
        panic!("expected sound");
    };
    value
}

#[test]
fn compiles_every_nbs_version_with_its_note_and_layer_layout() {
    for version in 0..=6 {
        let mut out = header(version, 1, 1000, 2, "标题".as_bytes());
        out.extend([3, 0, 1, 0, 0, 46]);
        if version >= 4 {
            out.extend([40, 125, 50, 0]);
        }
        out.extend([0; 4]);
        layer(&mut out, version, 2, 60, 80);
        out.push(0);
        let imported = compile(out.as_slice()).unwrap();
        assert_eq!(
            (
                imported.title.as_str(),
                imported.artist.as_str(),
                imported.original_author.as_str()
            ),
            ("标题", "Author", "Original")
        );
        let score = imported.score;
        assert_eq!(score.duration_us, 300_000);
        assert_eq!(
            score.r#loop,
            (version >= 4).then_some(NoteLoop {
                start_us: 100_000,
                repeats: Some(2)
            })
        );
        assert_eq!(score.events[0].time_us, 200_000);
        let sound = sound(&score.events[0]);
        assert_eq!(sound.gain, if version >= 4 { 2400 } else { 6000 });
        assert_eq!(sound.pitch_cents, if version >= 4 { 150 } else { 100 });
        assert_eq!(
            sound.pan,
            match version {
                0 | 1 => 0,
                2 | 3 => -10,
                _ => 2,
            }
        );
    }
}

#[test]
fn compiles_tempo_changes_loop_boundaries_and_same_time_voice_stops() {
    let mut out = header(6, 3, 1000, 1, b"Control events");
    out.extend([1, 0, 1, 0, 16, 45, 100, 100, 0, 0, 0, 0]);
    out.extend([1, 0, 1, 0, 20, 45, 100, 100, 44, 1]); // 300 / 15 = 20 ticks/s
    out.extend([1, 0, 21, 45, 227, 99, 2, 0]); // overloaded range payload, not gain/pan
    out.extend([1, 0, 22, 46, 1, 125, 50, 0, 0, 0]);
    out.extend([1, 0, 1, 0, 19, 45, 100, 100, 0, 0, 0, 0, 0, 0]);
    layer(&mut out, 6, 2, 100, 100);
    layer(&mut out, 6, 1, 100, 100);
    layer(&mut out, 6, 2, 1, 80);
    out.push(3);
    instrument(&mut out, "Tempo Changer", 45);
    instrument(&mut out, "Sound Stopper", 45);
    instrument(&mut out, "鏽蝕小號", 40);
    let score = compile(out.as_slice()).unwrap().score;
    assert_eq!(score.duration_us, 200_000);
    assert_eq!(score.r#loop.unwrap().start_us, 100_000);
    assert_eq!(
        score
            .events
            .iter()
            .map(|event| event.time_us)
            .collect::<Vec<_>>(),
        [0, 100_000, 100_000, 150_000]
    );
    assert_eq!(sound(&score.events[0]).tone, "trumpet");
    assert_eq!(
        score.events[1].action,
        Some(Action::Stop(NoteStop {
            first_voice: Some(1),
            last_voice: Some(32_766)
        }))
    );
    let custom = sound(&score.events[2]);
    assert_eq!(
        (custom.voice, custom.gain, custom.pan, custom.pitch_cents),
        (2, 1, 2, -350)
    );
    assert_eq!(custom.tone, "trumpet_weathered");
    assert_eq!(sound(&score.events[3]).tone, "trumpet_oxidized");
}

#[test]
fn preserves_dense_frames_and_omitted_optional_sections_but_rejects_truncation() {
    let mut out = header(6, 300, u16::MAX, 0, b"\xe9");
    out.extend([1, 0]);
    for _ in 0..300 {
        out.extend([1, 0, 0, 45, 100, 100, 0, 128]);
    }
    out.extend([0; 4]);
    let imported = compile(out.as_slice()).unwrap();
    assert_eq!(imported.title, "é");
    assert_eq!(imported.score.events.len(), 300);
    assert_eq!(
        sound(&imported.score.events[299]).pitch_cents,
        i32::from(i16::MIN)
    );
    assert_eq!(sound(&imported.score.events[299]).gain, 10_000);
    assert_eq!(sound(&imported.score.events[299]).voice, 299);
    assert!(compile(&out[..out.len() - 3]).is_err());
    layer(&mut out, 6, 2, 100, 100);
    assert!(compile(out.as_slice()).is_err());
    assert!(compile([0, 0, 99, 16].as_slice()).is_err());
    assert!(compile(header(5, 1, 0, 0, b"zero").as_slice()).is_err());
}
