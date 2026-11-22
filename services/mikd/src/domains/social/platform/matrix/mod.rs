mod assistant;
mod config;
mod content;
mod late_keys;
mod render;
mod request;
mod roster;
mod session;
mod sync;

pub(super) use assistant::reply as assistant_reply;
pub(in crate::domains::social) use config::Matrix;
pub(in crate::domains::social) use render::MatrixReply;

use super::super::{
    avatar,
    chat::{Inbound, Inline, Outbound, Platform, SpannedNode},
    commands::{self, Input},
    config::Conversation,
    locale::Locale,
    runtime::Runtime,
};
use super::ReplyDelivery;
use futures_util::{FutureExt, future::join_all};
use matrix_sdk::ruma::{
    EventId, OwnedRoomId, OwnedUserId, UserId,
    events::{
        relation::{Reply, Thread},
        room::message::{
            AddMentions, Relation, RoomMessageEventContent, RoomMessageEventContentWithoutRelation,
            TextMessageEventContent,
        },
    },
};
use matrix_sdk::{
    Client, Room, RoomState,
    attachment::{AttachmentConfig, AttachmentInfo, BaseImageInfo},
    config::RequestConfig,
    room::reply::{EnforceThread, Reply as AttachmentReply},
};
use mik_protocol::wire::ChatExternalIdentity;
use std::{io, path::Path, sync::Arc, time::Duration};
use tokio::sync::{mpsc, watch};

pub(in crate::domains::social) async fn run(
    config: Matrix,
    state: &Path,
    runtime: Arc<Runtime>,
) -> io::Result<()> {
    let client = session::open(&config, state).await?;
    let (ready, synced) = watch::channel(sync::Status::default());
    let (roster_changes, observed_rosters) = watch::channel(());
    let (inbound, messages) = mpsc::channel(64);
    let (missing_keys, pending) = mpsc::channel(64);
    let result = tokio::select! {
        result = sync::run(&config, &client, &runtime, ready, &roster_changes, &inbound, missing_keys) => result,
        result = late_keys::run(&client, synced.clone(), pending, &inbound) => result,
        () = deliver(&config, &client, &runtime, synced.clone()) => Ok(()),
        () = receive_all(&config, &runtime, messages, synced.clone()) => Ok(()),
        () = roster::run(&config, &client, runtime.membership(), observed_rosters) => Ok(()),
    };
    runtime.membership().clear_matrix();
    result
}

async fn receive_all(
    config: &Matrix,
    runtime: &Runtime,
    messages: mpsc::Receiver<sync::Input>,
    ready: watch::Receiver<sync::Status>,
) {
    super::dispatch(
        messages,
        |sync::Input { epoch, room, event }| {
            if ready.borrow().epoch != Some(epoch) || event.sender() == &config.user {
                return None;
            }
            let conversation = config.rooms.get(room.room_id())?;
            let event = event.project(&config.user)?;
            let input = commands::classify(
                &event.body.text,
                event.body.commands,
                conversation,
                config.command_prefix,
            )?;
            Some((input, (epoch, room, conversation, event)))
        },
        |input, (epoch, room, conversation, event)| {
            let mut ready = ready.clone();
            async move {
                if ready.borrow().epoch != Some(epoch) || room.state() != RoomState::Joined {
                    return;
                }
                tokio::select! {
                    biased;
                    _ = ready.wait_for(|state| state.epoch != Some(epoch)) => {},
                    () = receive(runtime, &config.user, &room, conversation, input, event) => {},
                }
            }
        },
    )
    .await;
}

async fn receive(
    runtime: &Runtime,
    bot: &UserId,
    room: &Room,
    conversation: &Conversation,
    input: Input,
    event: content::ProjectedEvent,
) {
    let content::ProjectedEvent {
        sender,
        id: event_id,
        body,
        reply_to,
        thread_root,
    } = event;
    let name = display_name(room, &sender).await;
    let mut inline = Vec::with_capacity(body.inline.len());
    for SpannedNode { range, node, style } in body.inline {
        let node = match node {
            Inline::Mention(user) => Inline::Mention(ChatExternalIdentity {
                platform: Platform::Matrix.id().into(),
                issuer: user.server_name().to_string(),
                subject: user.to_string(),
                display_name: display_name(room, &user).await,
                ..Default::default()
            }),
            Inline::Text => Inline::Text,
            Inline::Link(url) => Inline::Link(url),
            Inline::Description(kind) => Inline::Description(kind),
        };
        inline.push(SpannedNode { range, node, style });
    }
    let incoming = Inbound {
        platform: Platform::Matrix,
        account: sender.server_name().to_string(),
        conversation: room.room_id().to_string(),
        event: event_id.to_string(),
        sender: sender.to_string(),
        name,
        content: body.text,
        reply_to: reply_to.as_ref().map(ToString::to_string),
        thread_root: thread_root.as_ref().map(ToString::to_string),
        inline,
        description: body.description,
        commands: body.commands,
    };
    let Input::Command(command) = input else {
        runtime.publish(incoming, conversation).await;
        return;
    };
    let replied_author = reply_to
        .as_deref()
        .map(|target| reply_author(room, target, bot));
    if let Some(reply) = runtime
        .answer(
            &incoming,
            conversation,
            command,
            replied_author,
            ReplyDelivery::matrix(),
        )
        .await
    {
        if room.state() != RoomState::Joined || !room.encryption_state().is_encrypted() {
            return;
        }
        let Some(mut content) = render::command_reply(reply.body) else {
            return;
        };
        if let (Some(png), Ok(mime)) = (reply.avatar, "image/png".parse()) {
            let attachment = AttachmentConfig::new()
                .txn_id(event_id.as_str().into())
                .caption(Some(TextMessageEventContent::plain(
                    content.body().to_owned(),
                )))
                .info(AttachmentInfo::Image(BaseImageInfo {
                    width: Some(avatar::SIZE.into()),
                    height: Some(avatar::SIZE.into()),
                    ..Default::default()
                }))
                .reply(Some(AttachmentReply {
                    event_id: event_id.clone(),
                    enforce_thread: EnforceThread::MaybeThreaded,
                    add_mentions: AddMentions::No,
                }));
            if room
                .send_attachment("avatar.png", &mime, png, attachment)
                .await
                .is_ok()
            {
                return;
            }
        }
        content.relates_to = Some(reply_relation(event_id.clone(), thread_root));
        if send(room, content, Some(event_id.as_str())).await
            && let Some(receipt) = reply.receipt
        {
            receipt.delivered();
        }
    }
}

fn reply_relation(
    event_id: matrix_sdk::ruma::OwnedEventId,
    thread_root: Option<matrix_sdk::ruma::OwnedEventId>,
) -> Relation<RoomMessageEventContentWithoutRelation> {
    match thread_root {
        Some(root) => Relation::Thread(Thread::reply(root, event_id)),
        None => Relation::Reply(Reply::with_event_id(event_id)),
    }
}

async fn reply_author(room: &Room, target: &EventId, bot: &UserId) -> Option<ChatExternalIdentity> {
    let request = RequestConfig::new().disable_retry();
    let event = tokio::time::timeout(
        Duration::from_secs(2),
        room.load_or_fetch_event(target, Some(request)).boxed(),
    )
    .await
    .ok()?
    .ok()?;
    let message = content::decrypted_message(&event)?;
    let sender = message.sender();
    if message.id().as_str() != target.as_str() || sender.as_str() == bot.as_str() {
        return None;
    }
    Some(ChatExternalIdentity {
        platform: Platform::Matrix.id().into(),
        issuer: sender.server_name().to_string(),
        subject: sender.to_string(),
        ..Default::default()
    })
}

async fn display_name(room: &Room, user: &OwnedUserId) -> String {
    room.get_member_no_sync(user)
        .await
        .ok()
        .flatten()
        .map_or_else(
            || user.to_string(),
            |member| member.name().chars().take(128).collect(),
        )
}

async fn deliver(
    config: &Matrix,
    client: &Client,
    runtime: &Runtime,
    ready: watch::Receiver<sync::Status>,
) {
    join_all(
        config.rooms.iter().map(|(id, conversation)| {
            deliver_room(id, conversation, client, runtime, ready.clone())
        }),
    )
    .await;
    std::future::pending::<()>().await;
}

async fn deliver_room(
    id: &OwnedRoomId,
    conversation: &Conversation,
    client: &Client,
    runtime: &Runtime,
    ready: watch::Receiver<sync::Status>,
) {
    let mut feed = runtime.subscribe(conversation);
    while let Some(event) = feed.recv().await {
        let epoch = ready.borrow().epoch;
        if epoch.is_none() {
            continue;
        }
        let Some(room) = client.get_room(id) else {
            continue;
        };
        let Some(post) =
            Outbound::for_conversation(&event, Platform::Matrix, id.as_str(), conversation)
        else {
            continue;
        };
        let message =
            render::message(&room, runtime, Locale::get(&conversation.locale), &post).await;
        if ready.borrow().epoch == epoch {
            send(&room, message, Some(post.message_id)).await;
        }
    }
}

async fn send(room: &Room, content: RoomMessageEventContent, transaction: Option<&str>) -> bool {
    if room.state() != RoomState::Joined || !room.encryption_state().is_encrypted() {
        return false;
    }
    let mut send = room.send(content);
    if let Some(transaction) = transaction {
        send = send.with_transaction_id(transaction.into());
    }
    if send.await.is_err() {
        eprintln!("mikd encrypted Matrix delivery failed");
        return false;
    }
    true
}

#[cfg(test)]
mod tests {
    use super::*;
    use matrix_sdk::ruma::event_id;

    #[test]
    fn command_replies_preserve_the_thread_root_and_immediate_parent() {
        let question = event_id!("$question");
        let root = event_id!("$root");
        let mut content = RoomMessageEventContent::text_plain("answer");
        content.relates_to = Some(reply_relation(question.to_owned(), Some(root.to_owned())));
        let threaded = serde_json::to_value(&content).unwrap();
        assert_eq!(threaded["m.relates_to"]["rel_type"], "m.thread");
        assert_eq!(threaded["m.relates_to"]["event_id"], root.as_str());
        assert_eq!(
            threaded["m.relates_to"]["m.in_reply_to"]["event_id"],
            question.as_str()
        );
        assert_ne!(threaded["m.relates_to"]["is_falling_back"], true);

        content.relates_to = Some(reply_relation(question.to_owned(), None));
        let main = serde_json::to_value(&content).unwrap();
        assert_eq!(
            main["m.relates_to"],
            serde_json::json!({"m.in_reply_to": {"event_id": question.as_str()}})
        );
    }
}
