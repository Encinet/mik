use serde_json::{Value, json};

#[derive(Clone, Copy)]
pub(super) enum Provider {
    Ovh,
    Eden,
}

pub(super) enum Verdict {
    Accepted,
    Rejected,
}

#[derive(Debug, thiserror::Error)]
pub(super) enum InvalidResponse {
    #[error("response body exceeds size limit")]
    TooLarge,
    #[error("invalid JSON")]
    Json,
    #[error("expected exactly one entry in choices")]
    Choices,
    #[error("unexpected choices[0].finish_reason ({0})")]
    FinishReason(&'static str),
    #[error("expected text in choices[0].message.content")]
    Content,
    #[error("invalid Safety/Categories/Refusal verdict in choices[0].message.content")]
    GuardFormat,
    #[error("Eden field {field}: {reason}")]
    EdenField {
        field: &'static str,
        reason: &'static str,
    },
}

impl Provider {
    pub(super) const fn name(self) -> &'static str {
        match self {
            Self::Ovh => "ovhcloud",
            Self::Eden => "edenai",
        }
    }

    pub(super) fn endpoint(self) -> &'static str {
        match self {
            Self::Ovh => "https://oai.endpoints.kepler.ai.cloud.ovh.net/v1/chat/completions",
            Self::Eden => "https://api.edenai.run/v3/universal-ai",
        }
    }

    pub(super) fn request(self, model: &str, text: &str) -> Value {
        match self {
            Self::Ovh => json!({"model": model, "temperature": 0, "max_tokens": 512,
                "stream": false, "messages": [{"role": "user", "content": text}]}),
            Self::Eden => json!({"model": model, "input": {"text": text}}),
        }
    }

    pub(super) fn verdict(self, body: &[u8]) -> Result<Verdict, InvalidResponse> {
        let root: Value = serde_json::from_slice(body).map_err(|_| InvalidResponse::Json)?;
        match self {
            Self::Ovh => ovh(&root),
            Self::Eden => eden(&root),
        }
    }
}

fn ovh(root: &Value) -> Result<Verdict, InvalidResponse> {
    let choices = root["choices"].as_array().ok_or(InvalidResponse::Choices)?;
    let [choice] = choices.as_slice() else {
        return Err(InvalidResponse::Choices);
    };
    match choice["finish_reason"].as_str() {
        Some("stop") => (),
        reason => {
            return Err(InvalidResponse::FinishReason(match reason {
                Some("length") => "length",
                Some("content_filter") => "content_filter",
                Some("tool_calls") => "tool_calls",
                Some(_) => "unrecognized",
                None => "missing or not text",
            }));
        }
    }
    let content = choice["message"]["content"]
        .as_str()
        .ok_or(InvalidResponse::Content)?
        .trim();
    let (safety, categories) = content
        .strip_prefix("Safety:")
        .and_then(|verdict| verdict.split_once("Categories:"))
        .ok_or(InvalidResponse::GuardFormat)?;
    if !safety.ends_with(char::is_whitespace) {
        return Err(InvalidResponse::GuardFormat);
    }
    let mut lines = categories.lines();
    let categories = lines.next().ok_or(InvalidResponse::GuardFormat)?.trim();
    if categories.is_empty()
        || lines.next().is_some_and(|line| {
            !matches!(
                line.strip_prefix("Refusal:").map(str::trim),
                Some("Yes" | "No")
            )
        })
        || lines.next().is_some()
    {
        return Err(InvalidResponse::GuardFormat);
    }
    match safety.trim() {
        "Safe" if categories == "None" => Ok(Verdict::Accepted),
        "Unsafe" | "Controversial" => Ok(Verdict::Rejected),
        _ => Err(InvalidResponse::GuardFormat),
    }
}

fn eden(root: &Value) -> Result<Verdict, InvalidResponse> {
    if !root.is_object() {
        return Err(eden_error("response", "expected an object"));
    }
    for (field, expected) in [
        ("status", "success"),
        ("provider", "ovhcloud"),
        ("feature", "text"),
        ("subfeature", "moderation"),
    ] {
        let actual = eden_field(root, field)?
            .as_str()
            .ok_or_else(|| eden_error(field, "expected a string"))?;
        if actual != expected {
            return Err(eden_error(field, "unexpected value"));
        }
    }
    let output = eden_field(root, "output")?;
    if !output.is_object() {
        return Err(eden_error("output", "expected an object"));
    }
    let safe = lowest_risk(output, EdenRisk::Overall)?;
    let mut risky = false;
    if !output["items"].is_null() {
        let items = output["items"]
            .as_array()
            .ok_or_else(|| eden_error("output.items", "expected an array"))?;
        for item in items {
            if !item.is_object() {
                return Err(eden_error("output.items[]", "expected an object"));
            }
            let label = eden_field(item, "output.items[].label")?
                .as_str()
                .ok_or_else(|| eden_error("output.items[].label", "expected a string"))?;
            if label.trim().is_empty() {
                return Err(eden_error("output.items[].label", "empty string"));
            }
            if !lowest_risk(item, EdenRisk::Item)? {
                risky = true;
            }
        }
    }
    Ok(if safe && !risky {
        Verdict::Accepted
    } else {
        Verdict::Rejected
    })
}

#[derive(Clone, Copy)]
enum EdenRisk {
    Overall,
    Item,
}

fn lowest_risk(value: &Value, risk: EdenRisk) -> Result<bool, InvalidResponse> {
    let (level_field, score_field) = match risk {
        EdenRisk::Overall => ("output.nsfw_likelihood", "output.nsfw_likelihood_score"),
        EdenRisk::Item => (
            "output.items[].likelihood",
            "output.items[].likelihood_score",
        ),
    };
    let level = eden_number(value, level_field)?;
    if level.fract() != 0.0 {
        return Err(eden_error(level_field, "expected an integer"));
    }
    let zero_overall = matches!(risk, EdenRisk::Overall) && level == 0.0;
    if !zero_overall && !(1.0..=5.0).contains(&level) {
        return Err(eden_error(level_field, "outside the 1..=5 range"));
    }
    let score = eden_number(value, score_field)?;
    if !(0.0..=1.0).contains(&score) {
        return Err(eden_error(score_field, "outside the 0..=1 range"));
    }
    if zero_overall && score != 0.0 {
        return Err(eden_error(
            score_field,
            "expected zero when nsfw_likelihood is zero",
        ));
    }
    Ok(level < 2.0)
}

fn eden_number(value: &Value, field: &'static str) -> Result<f64, InvalidResponse> {
    eden_field(value, field)?
        .as_f64()
        .ok_or_else(|| eden_error(field, "expected a number"))
}

fn eden_field<'body>(
    value: &'body Value,
    field: &'static str,
) -> Result<&'body Value, InvalidResponse> {
    let key = field.rsplit_once('.').map_or(field, |(_, key)| key);
    match value.get(key) {
        None => Err(eden_error(field, "missing")),
        Some(Value::Null) => Err(eden_error(field, "null")),
        Some(value) => Ok(value),
    }
}

const fn eden_error(field: &'static str, reason: &'static str) -> InvalidResponse {
    InvalidResponse::EdenField { field, reason }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn eden_verdict(output: &Value) -> Result<Verdict, InvalidResponse> {
        let body = json!({
            "status": "success",
            "provider": "ovhcloud",
            "feature": "text",
            "subfeature": "moderation",
            "output": output
        });
        Provider::Eden.verdict(body.to_string().as_bytes())
    }

    #[test]
    fn eden_reports_the_exact_invalid_field_without_echoing_values() {
        for (output, field, reason) in [
            (json!({}), "output.nsfw_likelihood", "missing"),
            (
                json!({"nsfw_likelihood": 1}),
                "output.nsfw_likelihood_score",
                "missing",
            ),
            (
                json!({"nsfw_likelihood": null}),
                "output.nsfw_likelihood",
                "null",
            ),
            (
                json!({"nsfw_likelihood": "untrusted\nvalue"}),
                "output.nsfw_likelihood",
                "expected a number",
            ),
            (
                json!({"nsfw_likelihood": 1.5}),
                "output.nsfw_likelihood",
                "expected an integer",
            ),
            (
                json!({"nsfw_likelihood": 6}),
                "output.nsfw_likelihood",
                "outside the 1..=5 range",
            ),
            (
                json!({"nsfw_likelihood": 0, "nsfw_likelihood_score": 0.5}),
                "output.nsfw_likelihood_score",
                "expected zero when nsfw_likelihood is zero",
            ),
            (
                json!({"nsfw_likelihood": 1, "nsfw_likelihood_score": 1.1}),
                "output.nsfw_likelihood_score",
                "outside the 0..=1 range",
            ),
            (
                json!({"nsfw_likelihood": 0, "nsfw_likelihood_score": 0.0,
                       "items": [{"label": "Toxic", "likelihood": 1}]}),
                "output.items[].likelihood_score",
                "missing",
            ),
            (
                json!({"nsfw_likelihood": 0, "nsfw_likelihood_score": 0.0,
                       "items": [{"label": "Toxic", "likelihood": 0, "likelihood_score": 0.0}]}),
                "output.items[].likelihood",
                "outside the 1..=5 range",
            ),
        ] {
            let Err(error) = eden_verdict(&output) else {
                panic!("an invalid Eden response must not produce a verdict");
            };
            assert_eq!(error.to_string(), format!("Eden field {field}: {reason}"));
        }
    }

    #[test]
    fn eden_keeps_valid_risk_verdicts() {
        assert!(matches!(
            eden_verdict(&json!({"nsfw_likelihood": 1, "nsfw_likelihood_score": 0.0})),
            Ok(Verdict::Accepted)
        ));
        assert!(matches!(
            eden_verdict(&json!({"nsfw_likelihood": 1, "nsfw_likelihood_score": 0.0,
                                "items": [{"label": "Toxic", "likelihood": 2,
                                           "likelihood_score": 0.3}]})),
            Ok(Verdict::Rejected)
        ));
    }

    #[test]
    fn eden_accepts_a_zero_overall_risk_only_without_risky_items() {
        assert!(matches!(
            eden_verdict(&json!({"nsfw_likelihood": 0, "nsfw_likelihood_score": 0.0})),
            Ok(Verdict::Accepted)
        ));
        assert!(matches!(
            eden_verdict(&json!({"nsfw_likelihood": 0, "nsfw_likelihood_score": 0.0,
                                "items": [{"label": "Toxic", "likelihood": 2,
                                           "likelihood_score": 0.3}]})),
            Ok(Verdict::Rejected)
        ));
    }

    #[test]
    fn invalid_finish_reason_is_not_copied_into_diagnostics() {
        let body = json!({"choices": [{
            "finish_reason": "untrusted value\nforged log entry",
            "message": {"content": "Safety: Safe\nCategories: None"},
        }]});
        let Err(error) = Provider::Ovh.verdict(body.to_string().as_bytes()) else {
            panic!("an unknown finish reason must not produce a verdict");
        };
        assert_eq!(
            error.to_string(),
            "unexpected choices[0].finish_reason (unrecognized)"
        );
    }
}
