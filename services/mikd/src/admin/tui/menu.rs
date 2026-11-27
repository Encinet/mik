use super::{
    form::{Confirmation, Prompt},
    jobs::Work,
};

pub(super) struct Menu {
    pub title: &'static str,
    pub target: Option<String>,
    pub items: Vec<Item>,
    pub selected: usize,
}

pub(super) struct Item {
    pub label: &'static str,
    pub shortcut: Option<char>,
    pub action: Choice,
}

pub(super) enum Choice {
    Run(Work),
    Form(Prompt, String),
    Confirm(Confirmation),
}

impl Item {
    pub fn run(label: &'static str, work: Work) -> Self {
        Self {
            label,
            shortcut: None,
            action: Choice::Run(work),
        }
    }

    pub fn form(label: &'static str, prompt: Prompt, initial: impl Into<String>) -> Self {
        Self {
            label,
            shortcut: None,
            action: Choice::Form(prompt, initial.into()),
        }
    }

    pub fn confirm(label: &'static str, confirm: Confirmation) -> Self {
        Self {
            label,
            shortcut: None,
            action: Choice::Confirm(confirm),
        }
    }

    pub fn key(mut self, shortcut: char) -> Self {
        self.shortcut = Some(shortcut);
        self
    }
}
