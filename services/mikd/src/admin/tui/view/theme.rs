use super::FOCUS;
use ratatui::style::{Color, Modifier, Style};
use std::env;

#[derive(Clone, Copy)]
pub(in crate::admin::tui) struct Theme {
    pub background: Style,
    pub chrome: Style,
    pub navigation: Style,
    pub selection: Style,
    pub editor: Style,
    pub outline: Style,
    pub monochrome: bool,
}

impl Theme {
    pub const PLAIN: Self = Self {
        background: Style::new(),
        chrome: Style::new(),
        navigation: Style::new(),
        selection: FOCUS,
        editor: FOCUS,
        outline: Style::new(),
        monochrome: false,
    };

    const MONOCHROME: Self = Self {
        monochrome: true,
        ..Self::PLAIN
    };

    const TRUE_COLOR: Self = Self::colored([
        Color::Rgb(17, 25, 39),
        Color::Rgb(26, 38, 56),
        Color::Rgb(22, 32, 48),
        Color::Rgb(13, 32, 35),
        Color::Rgb(145, 232, 215),
        Color::Rgb(219, 230, 241),
        Color::Rgb(110, 139, 166),
    ]);

    const INDEXED: Self = Self::colored([
        Color::Indexed(234),
        Color::Indexed(236),
        Color::Indexed(235),
        Color::Indexed(16),
        Color::Indexed(116),
        Color::Indexed(254),
        Color::Indexed(109),
    ]);

    const fn colored(
        [base, chrome, nav, selected_text, selected_bg, text, outline]: [Color; 7],
    ) -> Self {
        Self {
            background: Style::new().fg(text).bg(base),
            chrome: Style::new().fg(text).bg(chrome),
            navigation: Style::new().fg(text).bg(nav),
            selection: Style::new()
                .fg(selected_text)
                .bg(selected_bg)
                .add_modifier(Modifier::BOLD),
            editor: Style::new().fg(text).bg(chrome),
            outline: Style::new().fg(outline),
            monochrome: false,
        }
    }

    pub fn detect() -> Self {
        if env::var_os("NO_COLOR").is_some_and(|value| !value.is_empty()) {
            return Self::MONOCHROME;
        }
        let term = env::var("TERM").unwrap_or_default();
        if term == "dumb" {
            return Self::MONOCHROME;
        }
        if env::var("COLORTERM").is_ok_and(|value| matches!(value.as_str(), "truecolor" | "24bit"))
            || term.ends_with("-direct")
        {
            Self::TRUE_COLOR
        } else if term.contains("256color") {
            Self::INDEXED
        } else {
            Self::PLAIN
        }
    }
}
