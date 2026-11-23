use super::text::{Segment, Text, valid_item_id};
use crate::domains::ai::{
    reference::{Reference, canonical_url},
    reply::questions,
};
use mik_protocol::wire::{AssistantAskLabel, assistant_part::Target};

pub(super) struct Annotations<'a> {
    references: &'a [Reference],
    question: &'a str,
    questions: Vec<String>,
    omitted: bool,
}

#[derive(Default)]
pub(super) struct Annotation {
    pub(super) label: Option<String>,
    pub(super) target: Option<Target>,
    pub(super) item_id: String,
}

impl<'a> Annotations<'a> {
    pub(super) fn new(question: &'a str, references: &'a [Reference]) -> Self {
        Self {
            references,
            question,
            questions: Vec::new(),
            omitted: false,
        }
    }

    pub(super) const fn omitted(&self) -> bool {
        self.omitted
    }

    pub(super) fn resolve(
        &mut self,
        destination: &str,
        authored_label: &str,
    ) -> Option<Annotation> {
        let default_label = authored_label == destination || authored_label.trim().is_empty();
        if let Some(id) = destination.strip_prefix("ref:") {
            if let Some(reference) = self.reference(id) {
                return Some(Annotation {
                    label: default_label.then(|| reference.label().to_owned()),
                    target: reference.url().map(|url| Target::Url(url.to_owned())),
                    ..Default::default()
                });
            }
        } else if destination == "ask:" {
            if questions::offer(&mut self.questions, self.question, authored_label) {
                return Some(Annotation {
                    target: Some(Target::AskLabel(AssistantAskLabel {})),
                    ..Default::default()
                });
            }
        } else if let Some(id) = destination.strip_prefix("item:") {
            if valid_item_id(id) {
                return Some(Annotation {
                    item_id: id.into(),
                    ..Default::default()
                });
            }
        } else if let Some(url) = verified_url(self.references, destination) {
            return Some(Annotation {
                label: default_label.then(|| url.clone()),
                target: Some(Target::Url(url)),
                ..Default::default()
            });
        }
        self.omitted = true;
        None
    }

    pub(super) fn segments(&mut self, text: &Text) -> Vec<Segment> {
        let references = self.references;
        text.segments(|raw| verified_url(references, raw), &mut self.omitted)
    }

    fn reference(&self, id: &str) -> Option<&Reference> {
        let number = id.strip_prefix('r')?;
        if number.starts_with('0') || !number.bytes().all(|byte| byte.is_ascii_digit()) {
            return None;
        }
        self.references
            .get(number.parse::<usize>().ok()?.checked_sub(1)?)
    }
}

fn verified_url(references: &[Reference], raw: &str) -> Option<String> {
    let url = canonical_url(raw)?;
    references
        .iter()
        .filter_map(Reference::url)
        .find(|&candidate| candidate == url.as_str())
        .map(str::to_owned)
}
