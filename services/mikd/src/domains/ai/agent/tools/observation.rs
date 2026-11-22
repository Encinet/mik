//! Model-facing facts and optional handles, independent of the producing tool.

use super::evidence::{Evidence, Handle};
use crate::domains::ai::{
    knowledge::{ArticleRef, LookupStatus},
    reference::Reference,
};
use serde_json::{Value, json};
use std::sync::Mutex;

pub(super) struct ToolObservation {
    fields: Value,
    groups: Vec<(&'static str, Vec<ObservedRecord>)>,
}

pub(super) struct ObservedRecord {
    fields: Value,
    handle: Option<Handle>,
}

impl ObservedRecord {
    pub(super) fn citable(fields: Value, reference: Option<Reference>) -> Self {
        Self {
            fields,
            handle: reference.map(Handle::Citation),
        }
    }

    pub(super) fn article(fields: Value, article: ArticleRef) -> Self {
        Self {
            fields,
            handle: Some(Handle::Article(article)),
        }
    }
}

impl ToolObservation {
    pub(super) fn new(status: LookupStatus, mut fields: Value) -> Self {
        fields["status"] = json!(status);
        Self::plain(fields)
    }

    fn plain(fields: Value) -> Self {
        Self {
            fields,
            groups: Vec::new(),
        }
    }

    pub(super) fn data(data: Value) -> Self {
        Self::plain(Value::Object([("data".into(), data)].into_iter().collect()))
    }

    pub(super) fn error(status: LookupStatus, error: &str) -> Self {
        Self::new(status, json!({"error": error}))
    }

    pub(super) fn unavailable(error: &str) -> Self {
        Self::error(LookupStatus::Unavailable, error)
    }

    pub(super) fn invalid(error: &str) -> Self {
        Self::plain(json!({"status": "invalid_request", "error": error}))
    }

    pub(super) fn with_records(
        mut self,
        key: &'static str,
        records: impl IntoIterator<Item = ObservedRecord>,
    ) -> Self {
        self.groups.push((key, records.into_iter().collect()));
        self
    }

    pub(super) fn for_model(self, evidence: &Mutex<Evidence>, max_bytes: usize) -> Value {
        let bounded = if self.groups.is_empty() {
            within_size(self.fields, max_bytes)
        } else {
            let Ok(mut evidence) = evidence.lock() else {
                return Self::unavailable("source references are unavailable").fields;
            };
            evidence.publish(|evidence| {
                let mut fields = self.fields;
                for (key, records) in self.groups {
                    fields[key] = Value::Array(
                        records
                            .into_iter()
                            .map(|mut record| {
                                if let Some(handle) = record.handle {
                                    evidence.attach(&mut record.fields, handle);
                                }
                                record.fields
                            })
                            .collect(),
                    );
                }
                within_size(fields, max_bytes)
            })
        };
        bounded.unwrap_or_else(|| {
            Self::unavailable("observation is too large; use a narrower query").fields
        })
    }
}

fn within_size(fields: Value, max_bytes: usize) -> Option<Value> {
    (serde_json::to_vec(&fields).ok()?.len() <= max_bytes).then_some(fields)
}

pub(super) fn label(text: &str) -> String {
    text.chars()
        .filter(|character| {
            !character.is_control() && !matches!(character, '§' | '\u{2028}' | '\u{2029}')
        })
        .take(128)
        .collect::<String>()
        .trim()
        .into()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn only_delivered_observations_allocate_handles_within_the_output_budget() {
        let evidence = Mutex::<Evidence>::default();
        let facts = json!({"label": "East Bridge"});
        let reference = Reference::new("East Bridge".into(), Some("https://example.org/bridge"));
        let observation = |source| {
            ToolObservation::data(json!({}))
                .with_records("hits", [ObservedRecord::citable(facts.clone(), source)])
        };
        let plain = observation(None).for_model(&evidence, 512);
        let limit = serde_json::to_vec(&plain).unwrap().len();
        let rejected = observation(Some(reference.clone())).for_model(&evidence, limit);
        assert_eq!(rejected["status"], "unavailable");
        assert!(evidence.lock().unwrap().references().is_empty());

        let accepted = observation(Some(reference.clone())).for_model(&evidence, 512);
        assert_eq!(accepted["hits"][0]["reference_id"], "r1");
        let again = observation(Some(reference)).for_model(&evidence, 512);
        assert_eq!(again, accepted);
        assert_eq!(evidence.lock().unwrap().references().len(), 1);
    }
}
