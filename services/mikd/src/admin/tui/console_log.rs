use ratatui::style::Color;

const BASE: [Color; 8] = [
    Color::Black,
    Color::Red,
    Color::Green,
    Color::Yellow,
    Color::Blue,
    Color::Magenta,
    Color::Cyan,
    Color::Gray,
];
const BRIGHT: [Color; 8] = [
    Color::DarkGray,
    Color::LightRed,
    Color::LightGreen,
    Color::LightYellow,
    Color::LightBlue,
    Color::LightMagenta,
    Color::LightCyan,
    Color::White,
];
pub(super) const CSI: &str = "\x1b[";

/// Interprets only SGR foreground colors. The caller sanitizes remaining control characters.
pub(super) fn segments(line: &str) -> impl Iterator<Item = (&str, Option<Color>)> {
    let mut color = None;
    line.split(CSI).enumerate().map(move |(index, part)| {
        if index > 0
            && let Some(end) = part.bytes().position(|byte| !(0x20..=0x3f).contains(&byte))
        {
            let command = part.as_bytes()[end];
            if (0x40..=0x7e).contains(&command) {
                let parameters = &part[..end];
                if command == b'm'
                    && parameters
                        .bytes()
                        .all(|byte| byte.is_ascii_digit() || byte == b';')
                {
                    color = foreground(parameters, color);
                }
                return (&part[end + 1..], color);
            }
        }
        (part, color)
    })
}

pub(super) fn matches(line: &str, filter: &str) -> bool {
    if filter.is_empty() || !line.contains(CSI) {
        return line.contains(filter);
    }
    segments(line)
        .map(|(text, _)| text)
        .collect::<String>()
        .contains(filter)
}

fn foreground(parameters: &str, mut color: Option<Color>) -> Option<Color> {
    let mut codes = parameters.split(';');
    while let Some(code) = codes.next() {
        match code {
            "" | "0" | "39" => color = None,
            "38" | "48" | "58" => {
                let extended = match codes.next() {
                    Some("5") => component(codes.next()).map(Color::Indexed),
                    Some("2") => {
                        let channels = [codes.next(), codes.next(), codes.next()];
                        match channels.map(component) {
                            [Some(r), Some(g), Some(b)] => Some(Color::Rgb(r, g, b)),
                            _ => None,
                        }
                    }
                    _ => None,
                };
                if code == "38" && extended.is_some() {
                    color = extended;
                }
            }
            _ => match code.parse::<usize>() {
                Ok(n @ 30..=37) => color = Some(BASE[n - 30]),
                Ok(n @ 90..=97) => color = Some(BRIGHT[n - 90]),
                _ => {}
            },
        }
    }
    color
}

fn component(value: Option<&str>) -> Option<u8> {
    value?.parse().ok()
}

#[cfg(test)]
mod tests {
    use super::{Color, matches, segments};

    #[test]
    fn applies_foreground_only_and_filters_the_visible_text() {
        let line = "\x1b[31mold\x1b[48;2;12;255;90m log\x1b[38;5;220m warning\x1b[39m done\x1b[2J";
        assert_eq!(
            segments(line)
                .filter(|(text, _)| !text.is_empty())
                .collect::<Vec<_>>(),
            [
                ("old", Some(Color::Red)),
                (" log", Some(Color::Red)),
                (" warning", Some(Color::Indexed(220))),
                (" done", None),
            ]
        );
        assert!(matches(line, "old log warning"));
        assert!(!matches(line, "38;5"));
        assert_eq!(
            segments("\x1b[38;2;10;20;30mRGB\x1b[0m reset").nth(1),
            Some(("RGB", Some(Color::Rgb(10, 20, 30))))
        );
    }
}
