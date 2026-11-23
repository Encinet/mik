use super::super::chat::binding_key;
use super::{Inbound, Locale, Reply, Runtime, arguments};
use crate::domains::community::{BindingChange, Redemption};
use std::{
    collections::HashMap,
    sync::Mutex,
    time::{Duration, Instant},
};
use uuid::Uuid;

#[derive(Default)]
pub(in crate::domains::social) struct Confirmations {
    pending: Mutex<HashMap<Sender, Confirmation>>,
}

#[derive(Eq, Hash, PartialEq)]
struct Sender {
    platform: &'static str,
    issuer: String,
    conversation: String,
    subject: String,
}

struct Confirmation {
    binding: Uuid,
    token: String,
    expires: Instant,
}

pub(super) async fn link(
    runtime: &Runtime,
    sender: &Inbound,
    locale: &Locale,
    code: &str,
    prefix: &str,
) -> Reply {
    if code.is_empty() {
        return Reply::fixed(locale.format(
            "social-binding-help",
            arguments([format!("{prefix}bind").into()]),
        ));
    }
    let external = sender.external();
    let Some(key) = binding_key(&external) else {
        return unavailable(locale);
    };
    let result = runtime
        .identity
        .redeem_external(key.clone(), &external.display_name, code)
        .await;
    let id = match result {
        Ok(Redemption::Applied(receipt)) if receipt.change == BindingChange::Refreshed => {
            let Some(binding) = runtime.identity.bindings().find(&key) else {
                return unavailable(locale);
            };
            return Reply::dynamic(locale.format(
                "social-binding-already-linked",
                arguments([
                    binding.player_name.clone().into(),
                    format!("{prefix}profile").into(),
                ]),
            ));
        }
        Ok(Redemption::Applied(_)) => "social-binding-link-completed",
        Ok(Redemption::InvalidOrExpired) => {
            return Reply::fixed(locale.format(
                "social-binding-code-invalid",
                arguments([format!("{prefix}bind").into()]),
            ));
        }
        Ok(Redemption::OtherPlatform) => "social-binding-platform-mismatch",
        Ok(Redemption::ExternalIdentityInUse) => "social-binding-external-in-use",
        Ok(Redemption::PlayerScopeInUse) => "social-binding-player-scope-in-use",
        Ok(Redemption::MembershipAbsent) => "social-binding-membership-required",
        Ok(Redemption::MembershipUnknown) => "social-binding-membership-unavailable",
        Ok(Redemption::RateLimited { retry_at }) => return rate_limit(locale, retry_at),
        Err(_) => return unavailable(locale),
    };
    Reply::fixed(locale.text(id))
}

pub(super) async fn unlink(
    runtime: &Runtime,
    sender: &Inbound,
    locale: &Locale,
    argument: &str,
    prefix: &str,
) -> Reply {
    let Some(key) = binding_key(&sender.external()) else {
        return unavailable(locale);
    };
    let Some(binding) = runtime.identity.bindings().find(&key) else {
        return Reply::fixed(locale.text("social-binding-not-bound"));
    };
    let principal = Sender {
        platform: sender.platform.id(),
        issuer: sender.account.clone(),
        subject: sender.sender.clone(),
        conversation: sender.conversation.clone(),
    };
    let binding_id = binding.id;
    if argument.is_empty() {
        let token = Uuid::new_v4().simple().to_string()[..8].to_owned();
        let Ok(mut pending) = runtime.confirmations.pending.lock() else {
            return unavailable(locale);
        };
        let now = Instant::now();
        pending.retain(|_, confirmation| confirmation.expires > now);
        if pending.len() >= 512 && !pending.contains_key(&principal) {
            return unavailable(locale);
        }
        pending.insert(
            principal,
            Confirmation {
                binding: binding_id,
                token: token.clone(),
                expires: now + Duration::from_secs(120),
            },
        );
        return Reply::fixed(format!(
            "{}\n{prefix}unbind confirm {token}",
            locale.format(
                "social-binding-unlink-prompt",
                arguments([binding.account.to_string().into()])
            )
        ));
    }
    let Some((word, token)) = argument.split_once(char::is_whitespace) else {
        return retry_confirmation(locale, prefix);
    };
    if word != "confirm"
        && !super::Locale::all().any(|locale| locale.text("social-unlink-confirmation") == word)
    {
        return retry_confirmation(locale, prefix);
    }
    let accepted = runtime
        .confirmations
        .pending
        .lock()
        .is_ok_and(|mut pending| {
            if pending.get(&principal).is_some_and(|attempt| {
                attempt.binding == binding_id
                    && attempt.token == token.trim()
                    && attempt.expires > Instant::now()
            }) {
                pending.remove(&principal);
                return true;
            }
            false
        });
    if !accepted {
        return retry_confirmation(locale, prefix);
    }
    match runtime.identity.unlink_external(key, binding_id).await {
        Ok(true) => Reply::fixed(locale.text("social-binding-unlink-completed")),
        Ok(false) => retry_confirmation(locale, prefix),
        Err(_) => unavailable(locale),
    }
}

fn rate_limit(locale: &Locale, retry_at: i64) -> Reply {
    let minutes = retry_at
        .saturating_sub(crate::domains::chat::now())
        .max(1)
        .unsigned_abs()
        .div_ceil(60_000);
    Reply::fixed(locale.format("social-binding-rate-limited", arguments([minutes.into()])))
}

fn unavailable(locale: &Locale) -> Reply {
    Reply::fixed(locale.text("social-binding-unavailable"))
}

fn retry_confirmation(locale: &Locale, prefix: &str) -> Reply {
    Reply::fixed(format!(
        "{} {prefix}unbind",
        locale.text("social-binding-confirmation-expired")
    ))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn binding_retry_timestamp_is_presented_in_rounded_up_minutes() {
        let retry_at = crate::domains::chat::now() + 90_000;

        assert!(
            rate_limit(Locale::get("en-US"), retry_at)
                .text
                .contains("2 minutes")
        );
    }
}
