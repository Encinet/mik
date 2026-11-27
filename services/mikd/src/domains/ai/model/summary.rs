use super::{MAX_MODEL_RESPONSE, check_reply, failure, http_failure, primary_choice};
use reqwest::Response;
use rig_core::{
    ProviderError,
    http_client::framing::SseFramer,
    providers::openai::wire::Chat,
    wire::{Decoder, Wire, WireEvent, WireFrame, document::Reassemble},
};
use serde_json::Value;
use tokio::sync::watch;

const SUMMARY_LIMIT: usize = 384;

#[allow(
    clippy::result_large_err,
    reason = "The model transport returns Rig's ProviderError"
)]
pub(super) async fn stream(
    mut response: Response,
    wire: &Chat,
    updates: &watch::Sender<String>,
) -> Result<WireFrame, ProviderError> {
    let decoder = wire.decoder();
    let mut document = <Chat as Wire>::Reassembler::default();
    let mut framer = SseFramer::new();
    let mut summary = Summary::default();
    let mut remaining = MAX_MODEL_RESPONSE;
    while let Some(chunk) = response
        .chunk()
        .await
        .map_err(|error| http_failure(&error, "could not read model stream"))?
    {
        remaining = remaining
            .checked_sub(chunk.len())
            .ok_or_else(|| failure("model response exceeds its size limit"))?;
        for event in framer.push(&chunk) {
            if event.data == "[DONE]" {
                let reply = document.finish();
                check_reply(&reply).map_err(failure)?;
                let bytes =
                    serde_json::to_vec(&reply).map_err(|_| failure("invalid model JSON"))?;
                return Ok(WireFrame::from_bytes(bytes));
            }
            let frame: Value = serde_json::from_str(&event.data)
                .map_err(|_| failure("invalid model stream frame"))?;
            if frame.get("error").is_some_and(|error| !error.is_null()) {
                return Err(failure("model stream reported an error"));
            }
            if let WireEvent::Known(event) = decoder.classify(WireFrame::Text(event.data)) {
                document.absorb(&event);
                if let Some(choice) = primary_choice(&frame) {
                    if let Some(delta) = choice.get("delta") {
                        summary.update(delta, updates);
                    } else if let Some(message) = choice.get("message") {
                        whole_message(message, updates);
                    }
                }
            }
        }
    }
    Err(failure("model stream ended before completion"))
}

pub(super) fn whole(reply: &Value, updates: &watch::Sender<String>) {
    if let Some(message) = primary_choice(reply).and_then(|choice| choice.get("message")) {
        whole_message(message, updates);
    }
}

fn whole_message(message: &Value, updates: &watch::Sender<String>) {
    Summary::default().update(message, updates);
}

#[derive(Default)]
struct Summary {
    index: u64,
    text: String,
    len: usize,
}

impl Summary {
    fn update(&mut self, message: &Value, updates: &watch::Sender<String>) {
        let Some(details) = message.get("reasoning_details").and_then(Value::as_array) else {
            return;
        };
        for detail in details {
            if detail.get("type").and_then(Value::as_str) != Some("reasoning.summary") {
                continue;
            }
            let Some(fragment) = detail.get("summary").and_then(Value::as_str) else {
                continue;
            };
            let index = detail
                .get("index")
                .and_then(Value::as_u64)
                .unwrap_or(self.index);
            if index != self.index {
                *self = Self {
                    index,
                    ..Self::default()
                };
            }
            for character in fragment
                .chars()
                .filter_map(display_char)
                .take(SUMMARY_LIMIT + 1 - self.len)
            {
                self.text.push(character);
                self.len += 1;
            }
        }
        let mut text = self.text.split_whitespace().collect::<Vec<_>>().join(" ");
        if self.len > SUMMARY_LIMIT {
            text.pop();
            text.push('…');
        }
        if !text.is_empty() {
            updates.send_if_modified(|current| {
                if *current == text {
                    return false;
                }
                *current = text;
                true
            });
        }
    }
}

fn display_char(character: char) -> Option<char> {
    if character.is_whitespace() {
        Some(' ')
    } else if character.is_control()
        || matches!(character, '\u{202a}'..='\u{202e}' | '\u{2066}'..='\u{2069}')
    {
        None
    } else {
        Some(character)
    }
}
