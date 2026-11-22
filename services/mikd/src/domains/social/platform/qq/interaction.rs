use super::super::super::{
    chat::{Inbound, Platform},
    commands::Command,
    followups::FollowUpKey,
    runtime::Runtime,
};
use super::{Qq, ReplyDelivery, keyboard, moderation::Gate, send};
use qqbot_sdk_rs::{Bot, EventRouter, InteractionCreate};
use serde_json::json;
use std::{future::Ready, sync::Arc, time::Duration};
use tokio::{
    sync::mpsc,
    time::{Instant, timeout_at},
};

const INLINE_KEYBOARD: u8 = 11;
const GROUP_CHAT: u8 = 1;
const ACK_BUDGET: Duration = Duration::from_secs(2);

#[repr(u8)]
enum Ack {
    Accepted = 0,
    Busy = 2,
    NotPermitted = 4,
}

pub(super) struct Click {
    event: InteractionCreate,
    inbound: Inbound,
    key: FollowUpKey,
    ack_by: Instant,
}

impl Click {
    fn from_event(config: &Qq, event: InteractionCreate) -> Option<Self> {
        if event.application_id.as_deref() != Some(config.app_id.as_str())
            || event.r#type != Some(INLINE_KEYBOARD)
            || event.scene.as_deref() != Some("group")
            || event.chat_type.is_some_and(|kind| kind != GROUP_CHAT)
        {
            return None;
        }
        let group = event.group_openid.as_deref()?;
        let route = config.groups.get(group)?;
        if !route.commands || !route.ai {
            return None;
        }
        let event_id = event
            .id
            .as_deref()
            .filter(|value| !value.trim().is_empty())?;
        // Only the group member who triggered the event is the caller. resolved.user_id
        // belongs to the guild scene and never substitutes for group_member_openid.
        let sender = event
            .group_member_openid
            .as_deref()
            .filter(|value| !value.trim().is_empty())?;
        let data = event
            .data
            .as_ref()
            .filter(|data| data.r#type == Some(INLINE_KEYBOARD))?;
        let key = keyboard::key(data.resolved.as_ref()?)?;
        let inbound = Inbound {
            platform: Platform::Qq,
            account: config.app_id.clone(),
            conversation: group.into(),
            event: format!("interaction:{event_id}"),
            sender: sender.into(),
            name: String::new(),
            content: String::new(),
            reply_to: None,
            thread_root: None,
            inline: Vec::new(),
            description: None,
            commands: true,
        };
        Some(Self {
            event,
            inbound,
            key,
            ack_by: Instant::now() + ACK_BUDGET,
        })
    }

    async fn ack(&self, code: Ack) -> bool {
        let body = json!({ "code": code as u8 });
        if matches!(
            timeout_at(self.ack_by, self.event.respond(&body)).await,
            Ok(Ok(_))
        ) {
            true
        } else {
            eprintln!("mikd QQ button acknowledgement failed");
            false
        }
    }

    async fn accept(self, runtime: &Runtime, ready: &mpsc::Sender<(Self, String)>) {
        if Instant::now() >= self.ack_by {
            return;
        }
        let Ok(slot) = ready.try_reserve() else {
            self.ack(Ack::Busy).await;
            return;
        };
        let Some(question) = runtime.claim_question(&self.inbound, &self.key) else {
            drop(slot);
            self.ack(Ack::NotPermitted).await;
            return;
        };
        if self.ack(Ack::Accepted).await {
            slot.send((self, question));
        }
    }

    async fn answer(
        self,
        config: &Qq,
        runtime: &Runtime,
        bot: &Bot,
        question: String,
        gate: Gate<'_>,
    ) {
        let Some(route) = config.groups.get(&self.inbound.conversation) else {
            return;
        };
        let Some(reply) = runtime
            .answer(
                &self.inbound,
                route,
                Command::ai(question, config.command_prefix),
                None::<Ready<_>>,
                ReplyDelivery::qq(gate, true),
            )
            .await
        else {
            return;
        };
        send::command(
            send::Target::Interaction(&self.event),
            bot,
            runtime,
            &self.inbound,
            route,
            reply,
        )
        .await;
    }
}

pub(super) async fn listen(router: &EventRouter, config: Arc<Qq>, clicks: mpsc::Sender<Click>) {
    router
        .on::<InteractionCreate, _, _>(move |event| {
            let click = Click::from_event(&config, event);
            let clicks = clicks.clone();
            async move {
                if let Some(click) = click
                    && clicks.try_send(click).is_err()
                {
                    eprintln!("mikd QQ button input is backpressured");
                }
                Ok(())
            }
        })
        .await;
}

pub(super) async fn receive(
    clicks: mpsc::Receiver<Click>,
    config: &Qq,
    runtime: &Runtime,
    bot: &Bot,
    gate: Gate<'_>,
) {
    let (ready, accepted) = mpsc::channel(8);
    // Never wait for an AI worker or make an HTTP request in the Gateway dispatcher.
    tokio::join!(
        async move {
            super::super::consume(clicks, 8, |click| click.accept(runtime, &ready)).await;
        },
        super::super::consume(accepted, 4, |(click, question)| async move {
            click.answer(config, runtime, bot, question, gate).await;
        }),
    );
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::Value;

    #[test]
    fn group_click_scope_comes_from_the_gateway_app_group_and_actual_member() {
        let config: Qq = serde_yaml_ng::from_str(
            "app_id: qq-app\ngroups:\n  trusted-group: { commands: true, ai: true }\n  disabled: { commands: true }",
        )
        .unwrap();
        let payload = json!({
            "application_id": "qq-app",
            "id": "interaction-id",
            "type": 11,
            "scene": "group",
            "chat_type": 1,
            "group_openid": "trusted-group",
            "group_member_openid": "actual-clicker",
            "data": { "type": 11, "resolved": {
                "user_id": "not-the-group-clicker",
                "button_data": "mik-ai:df30a0173de24481a8d8f38460d4728a",
            } },
        });
        let click =
            Click::from_event(&config, serde_json::from_value(payload.clone()).unwrap()).unwrap();
        assert_eq!(click.inbound.account, "qq-app");
        assert_eq!(click.inbound.conversation, "trusted-group");
        assert_eq!(click.inbound.sender, "actual-clicker");
        assert_eq!(click.inbound.event, "interaction:interaction-id");
        assert!(click.inbound.content.is_empty());

        for (key, value) in [
            ("application_id", json!("wrong-app")),
            ("group_openid", json!("wrong-group")),
            ("group_openid", json!("disabled")),
            ("group_member_openid", Value::Null),
            ("scene", json!("c2c")),
            ("chat_type", json!(2)),
            ("type", json!(12)),
            (
                "data",
                json!({"type": 11, "resolved": {"button_data": "/ai how?"}}),
            ),
        ] {
            let mut spoof = payload.clone();
            spoof[key] = value;
            assert!(Click::from_event(&config, serde_json::from_value(spoof).unwrap()).is_none());
        }
    }
}
