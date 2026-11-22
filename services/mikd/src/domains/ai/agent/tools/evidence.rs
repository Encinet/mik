//! Turn-local handles for observations with player-visible references.

use crate::domains::ai::{knowledge::ArticleRef, reference::Reference};
use serde_json::Value;

pub(super) enum Handle {
    Citation(Reference),
    Article(ArticleRef),
}

#[derive(Default)]
pub(super) struct Evidence {
    references: Vec<Reference>,
    articles: Vec<ArticleRef>,
}

impl Evidence {
    pub(super) fn attach(&mut self, fields: &mut Value, handle: Handle) {
        let Some(object) = fields.as_object_mut() else {
            return;
        };
        let (key, id) = match handle {
            Handle::Citation(reference) => (
                "reference_id",
                register(&mut self.references, reference, 'r'),
            ),
            Handle::Article(article) => ("article_id", register(&mut self.articles, article, 'w')),
        };
        object.insert(key.into(), Value::String(id));
    }

    pub(super) fn publish(
        &mut self,
        render: impl FnOnce(&mut Self) -> Option<Value>,
    ) -> Option<Value> {
        let references = self.references.len();
        let articles = self.articles.len();
        let output = render(self);
        if output.is_none() {
            self.references.truncate(references);
            self.articles.truncate(articles);
        }
        output
    }

    pub(super) fn article(&self, id: &str) -> Option<ArticleRef> {
        self.articles.get(index(id, 'w')?).cloned()
    }

    pub(super) fn references(&self) -> &[Reference] {
        &self.references
    }
}

fn register<Entry: PartialEq>(entries: &mut Vec<Entry>, entry: Entry, prefix: char) -> String {
    let index = entries
        .iter()
        .position(|existing| *existing == entry)
        .unwrap_or_else(|| {
            entries.push(entry);
            entries.len() - 1
        });
    format!("{prefix}{}", index + 1)
}

fn index(id: &str, prefix: char) -> Option<usize> {
    let number = id.strip_prefix(prefix)?;
    if number.starts_with('0') || !number.bytes().all(|byte| byte.is_ascii_digit()) {
        return None;
    }
    number.parse::<usize>().ok()?.checked_sub(1)
}
