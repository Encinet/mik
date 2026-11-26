mod approval;
mod assistant;
mod config;
mod content;
mod interaction;
mod keyboard;
pub(super) mod moderation;
mod render;
mod reply;
mod roster;
mod send;

pub(in crate::domains::social) use config::Qq;
pub(crate) use moderation::QqModeration;

use self::{approval::Approvals, moderation::Gate};
use super::super::{
    chat::{Inbound, Outbound, Platform},
    commands::{self, Input},
    config::{Conversation, secret},
    locale::Locale,
    runtime::Runtime,
};
use super::{RenderedAi, ReplyDelivery};
use crate::domains::chat::{ConversationAddress, NativeEvent};
use crate::domains::community::ExternalKey;
use futures_util::future::join_all;
use mik_protocol::wire::{AssistantReply, ChatExternalIdentity};
use qqbot_sdk_rs::{
    Bot, BotEvent, BotMode, EventRouter, GatewayConfig, GatewayShardManager, GroupAtMessageCreate,
    GroupDelRobot, GroupJoinRequest, GroupMemberAdd, GroupMemberRemove, GroupMessageCreate,
    Intents, ReadyEvent, SdkError, User,
};
use std::{io, sync::Arc, time::Duration};
use tokio::{
    sync::{Notify, mpsc},
    time::sleep,
};
use uuid::Uuid;

pub(in crate::domains::social) async fn run(
    config: Qq,
    runtime: Arc<Runtime>,
    moderation: Option<QqModeration>,
) -> io::Result<()> {
    let config = Arc::new(config);
    let approvals = Approvals::open(&config)?;
    let bot = Arc::new(
        Bot::new(
            &config.app_id,
            secret("MIKD_QQ_APP_SECRET")?,
            BotMode::PublicWebSocket,
        )
        .map_err(|_| io::Error::other("could not open QQ client"))?,
    );
    let (incoming, messages) = mpsc::channel(64);
    for topic in ["GROUP_AT_MESSAGE_CREATE", "GROUP_MESSAGE_CREATE"] {
        let incoming = incoming.clone();
        let runtime = runtime.clone();
        let issuer = config.app_id.clone();
        bot.on(topic, move |event| {
            let incoming = incoming.clone();
            let runtime = runtime.clone();
            let issuer = issuer.clone();
            async move {
                if let Some((message, replied_author)) = normalize(&issuer, &event) {
                    runtime
                        .membership()
                        .observe_qq(message.conversation(), &message.sender, true);
                    if incoming.try_send((event, message, replied_author)).is_err() {
                        eprintln!("mikd QQ input is backpressured");
                    }
                }
                Ok(())
            }
        })
        .await;
    }
    let (applications, requests) = mpsc::channel(64);
    let (clicks, interactions) = mpsc::channel(16);
    let router = bot.event_router();
    let ai_buttons = runtime.assistant.configured()
        && config
            .groups
            .values()
            .any(|conversation| conversation.commands && conversation.ai);
    if ai_buttons {
        interaction::listen(&router, config.clone(), clicks).await;
    }
    let roster_wakeup = Arc::new(Notify::new());
    observe_membership(&router, &runtime, config.clone(), roster_wakeup.clone()).await;
    router
        .on::<GroupJoinRequest, _, _>(move |request| {
            let applications = applications.clone();
            async move {
                if applications.try_send(request).is_err() {
                    eprintln!("mikd QQ group approval queue is unavailable");
                }
                Ok(())
            }
        })
        .await;
    let mut gateway = GatewayConfig::for_bot(&bot);
    gateway.intents =
        Intents::from_bits(Intents::GROUP_AND_C2C_EVENT | Intents::GROUP_MEMBER_EVENT);
    if ai_buttons {
        gateway.intents.insert(Intents::INTERACTION);
    }
    let gateway = discover(&bot, &router, &gateway).await?;
    let gate = moderation.as_ref().map_or(Gate::Unavailable, Gate::Check);
    let result = tokio::select! {
        result = gateway.run() => result.map_err(|_| io::Error::other("QQ Gateway stopped")),
        () = receive(&config, &runtime, &bot, messages, ai_buttons, gate) => Ok(()),
        () = interaction::receive(interactions, &config, &runtime, &bot, gate), if ai_buttons => Ok(()),
        () = deliver(&config, &runtime, &bot, gate) => Ok(()),
        () = approvals.run(&bot, requests) => Ok(()),
        () = roster::refresh(&config, &runtime, &bot, &roster_wakeup) => Ok(()),
    };
    runtime.membership().set_qq_ready(false);
    result
}

pub(super) async fn prepare_assistant(
    answer: &AssistantReply,
    locale: &Locale,
    generation: Option<Uuid>,
    gate: Gate<'_>,
    prefix: &str,
) -> Option<RenderedAi> {
    let rendered = assistant::reply(answer, locale, generation, prefix);
    gate.reply(&rendered.review)
        .await
        .then_some(rendered.message)
}

async fn discover(
    bot: &Arc<Bot>,
    router: &EventRouter,
    config: &GatewayConfig,
) -> io::Result<GatewayShardManager> {
    let mut delay = Duration::from_secs(1);
    loop {
        match GatewayShardManager::new(bot.clone(), router.clone(), config.clone()).await {
            Ok(gateway) => return Ok(gateway),
            Err(error) if discovery_retryable(&error) => {
                if delay == Duration::from_secs(1) {
                    eprintln!("mikd QQ Gateway discovery unavailable; retrying with backoff");
                }
                sleep(delay).await;
                delay = (delay * 2).min(Duration::from_secs(30));
            }
            Err(_) => {
                return Err(io::Error::other(
                    "QQ Gateway discovery failed; check credentials and permissions",
                ));
            }
        }
    }
}

fn discovery_retryable(error: &SdkError) -> bool {
    match error {
        SdkError::Http(error) => error.is_connect() || error.is_timeout(),
        SdkError::Api {
            status: 429 | 500..=599,
            ..
        }
        | SdkError::RateLimited(_)
        | SdkError::Timeout(_) => true,
        _ => false,
    }
}

async fn observe_membership(
    router: &qqbot_sdk_rs::EventRouter,
    runtime: &Arc<Runtime>,
    config: Arc<Qq>,
    roster_wakeup: Arc<Notify>,
) {
    let added = runtime.clone();
    router
        .on::<GroupMemberAdd, _, _>(move |event| {
            let runtime = added.clone();
            async move {
                if let (Some(group), Some(member)) = (event.group_openid, event.member_openid) {
                    runtime.membership().observe_qq(&group, &member, true);
                }
                Ok(())
            }
        })
        .await;
    let removed = runtime.clone();
    router
        .on::<GroupMemberRemove, _, _>(move |event| {
            let runtime = removed.clone();
            let config = config.clone();
            async move {
                if let (Some(group), Some(member)) = (event.group_openid, event.member_openid) {
                    runtime.membership().observe_qq(&group, &member, false);
                    if config
                        .groups
                        .get(&group)
                        .is_some_and(|room| room.membership)
                        && let Ok(key) =
                            ExternalKey::new("qq", config.app_id.clone(), String::new(), member)
                    {
                        runtime.identity.record_qq_departure(key).await;
                    }
                }
                Ok(())
            }
        })
        .await;
    let left = runtime.clone();
    router
        .on::<GroupDelRobot, _, _>(move |event| {
            let runtime = left.clone();
            async move {
                if let Some(group) = event.group_openid {
                    runtime.membership().clear_qq(Some(&group));
                }
                Ok(())
            }
        })
        .await;
    let fresh = runtime.clone();
    router
        .on::<ReadyEvent, _, _>(move |_| {
            let runtime = fresh.clone();
            let roster_wakeup = roster_wakeup.clone();
            async move {
                runtime.membership().clear_qq(None);
                runtime.membership().set_qq_ready(true);
                roster_wakeup.notify_one();
                Ok(())
            }
        })
        .await;
}

async fn receive(
    config: &Qq,
    runtime: &Runtime,
    bot: &Bot,
    messages: mpsc::Receiver<(BotEvent, Inbound, Option<reply::Author>)>,
    keyboard_route: bool,
    gate: Gate<'_>,
) {
    super::dispatch(
        messages,
        |(event, incoming, replied_author)| {
            let conversation = config.groups.get(incoming.conversation())?;
            let input = commands::classify(
                &incoming.content,
                incoming.commands,
                conversation,
                config.command_prefix,
            )?;
            Some((input, (event, incoming, replied_author, conversation)))
        },
        |input, (event, incoming, replied_author, conversation)| async move {
            let Input::Command(command) = input else {
                runtime.publish(incoming, conversation).await;
                return;
            };
            let replied_author = replied_author.map(|author| async move {
                match author {
                    reply::Author::Member(subject) => Some(ChatExternalIdentity {
                        platform: Platform::Qq.id().into(),
                        issuer: config.app_id.clone(),
                        subject,
                        ..Default::default()
                    }),
                    reply::Author::Unknown => None,
                }
            });
            let target = send::Target::Message(&event);
            let Some(reply) = runtime
                .answer(
                    &incoming,
                    conversation,
                    command,
                    replied_author,
                    ReplyDelivery::qq(gate, keyboard_route),
                    |_| target.thinking(conversation),
                )
                .await
            else {
                return;
            };
            send::command(target, bot, runtime, &incoming, conversation, reply).await;
        },
    )
    .await;
}

fn normalize(issuer: &str, event: &BotEvent) -> Option<(Inbound, Option<reply::Author>)> {
    let (id, group, author, text, mentions, attachments, card, kind, elements, scene, extra) =
        if event.name == "GROUP_AT_MESSAGE_CREATE" {
            let message = event.parse::<GroupAtMessageCreate>().ok()?;
            (
                message.id,
                message.group_openid,
                message.author,
                message.content,
                message.mentions,
                message.attachments,
                message.ark_data,
                message.message_type,
                message.msg_elements,
                message.message_scene,
                message.extra,
            )
        } else {
            let message = event.parse::<GroupMessageCreate>().ok()?;
            (
                message.id,
                message.group_openid,
                message.author,
                message.content,
                message.mentions,
                message.attachments,
                message.ark_data,
                message.message_type,
                message.msg_elements,
                message.message_scene,
                message.extra,
            )
        };
    let author = author?;
    if author.bot == Some(true) || author.is_you == Some(true) {
        return None;
    }
    let sender = author.member_openid.as_ref()?.clone();
    let body = content::project(
        issuer,
        text.as_deref().unwrap_or_default(),
        &mentions,
        kind,
        &attachments,
        card.as_ref(),
        &elements,
    );
    let has_reference = extra
        .pointer("/message_reference/message_id")
        .and_then(serde_json::Value::as_str)
        .is_some_and(|id| !id.is_empty())
        || scene
            .as_ref()
            .and_then(qqbot_sdk_rs::MessageScene::ref_msg_idx)
            .is_some();
    let replied_author = reply::author(kind, &elements, has_reference);
    let message = Inbound {
        platform: Platform::Qq,
        account: issuer.into(),
        native: NativeEvent {
            source: ConversationAddress {
                namespace: issuer.into(),
                id: group?,
            },
            event_id: id?,
            event_scope: issuer.into(),
        },
        sender,
        name: display_name(&author),
        content: body.text,
        assistant_thread: None,
        inline: body.inline,
        description: body.description,
        commands: body.commands,
    };
    Some((message, replied_author))
}

fn display_name(user: &User) -> String {
    user.member_nickname
        .as_deref()
        .or(user.username.as_deref())
        .filter(|name| !name.trim().is_empty())
        .unwrap_or("QQ 用户")
        .chars()
        .take(128)
        .collect()
}

async fn deliver(config: &Qq, runtime: &Runtime, bot: &Bot, gate: Gate<'_>) {
    join_all(config.groups.iter().map(|(id, conversation)| {
        deliver_group(&config.app_id, id, conversation, runtime, bot, gate)
    }))
    .await;
    std::future::pending::<()>().await;
}

async fn deliver_group(
    issuer: &str,
    id: &str,
    conversation: &Conversation,
    runtime: &Runtime,
    bot: &Bot,
    gate: Gate<'_>,
) {
    let mut feed = runtime.subscribe(conversation);
    while let Some(event) = feed.recv().await {
        let Some(post) = Outbound::for_conversation(&event, Platform::Qq, issuer, id, conversation)
        else {
            continue;
        };
        let message = render::message(
            issuer,
            id,
            runtime,
            Locale::get(&conversation.locale),
            &post,
        );
        if gate.bridge(&message.review).await
            && let Err(error) = bot.api().messages().send_group(id, message.message).await
        {
            match error {
                SdkError::Api { status, code, .. } => {
                    eprintln!("mikd QQ chat delivery failed (HTTP {status}, code {code})");
                }
                _ => eprintln!("mikd QQ chat delivery failed"),
            }
        }
    }
}
