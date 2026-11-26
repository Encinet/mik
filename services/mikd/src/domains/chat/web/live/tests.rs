use super::*;
use crate::domains::chat::{
    history::SendReceipt,
    model::{player_principal, room},
    person_mention, text_part,
    web::record::{COMPRESSED, inflate_request},
};
use mik_protocol::wire::{
    ChatBody, ChatDestination, ChatEvent, ChatPart, ChatPlayer, ChatRecord, ChatTextRun,
    ChatUserRecord, ChatVisibility,
    chat_destination::{Room, Target},
    chat_record::Kind,
};

const SENDER: &str = "00000000-0000-4000-8000-000000000001";
const MENTIONED: &str = "00000000-0000-4000-8000-000000000002";
const READER: &str = "00000000-0000-4000-8000-000000000003";

fn player(id: &str) -> ChatPlayer {
    ChatPlayer {
        id: id.into(),
        name: "Player".into(),
    }
}

fn event(destination: ChatDestination, visibility: Option<ChatVisibility>) -> LiveEvent {
    let body = ChatBody {
        parts: vec![
            text_part("ordinary broadcast ".repeat(12), None),
            ChatPart {
                content: Some(person_mention(
                    player_principal(player(MENTIONED)),
                    vec![ChatTextRun {
                        text: "@Player".into(),
                        style: None,
                    }],
                )),
            },
        ],
    };
    LiveEvent::new(Arc::new(Committed {
        cursor: 1,
        order_key: "order".into(),
        expires_at: 1000,
        source: None,
        receipt: Some(SendReceipt {
            sender: SENDER.into(),
            request_id: "web-send".into(),
            fingerprint: String::new(),
        }),
        entry: ChatEvent {
            message_id: "one".into(),
            visibility,
            record: Some(ChatRecord {
                platform: "web".into(),
                created_at_ms: 1,
                kind: Some(Kind::User(ChatUserRecord {
                    destination: Some(destination),
                    author: Some(player_principal(player(SENDER))),
                    body: Some(body),
                })),
            }),
        },
    }))
}

#[tokio::test]
async fn viewers_share_matching_records_and_keep_their_own_mentions_and_receipts() {
    let event = event(room(Room::Public), None);
    let target = Uuid::parse_str(MENTIONED).unwrap();
    let delivery = |viewer: &str, resolved| {
        event
            .for_viewer(viewer, false)
            .unwrap()
            .delivery(&[], |_| resolved)
            .unwrap()
            .unwrap()
    };
    let reader = delivery(READER, Some(target));
    let unlinked = delivery(MENTIONED, None);
    let (a, b) = tokio::join!(
        reader.prepare(true, || true),
        unlinked.prepare(true, || true)
    );
    let shared = a.unwrap().unwrap();
    assert!(Arc::ptr_eq(&shared, &b.unwrap().unwrap()));
    assert_eq!(shared.kind, COMPRESSED);
    let raw = reader.prepare(false, || true).await.unwrap().unwrap();
    assert_eq!(
        inflate_request(&shared.payload).unwrap().as_slice(),
        raw.payload
    );
    let ordinary: Value = serde_json::from_slice(&raw.payload).unwrap();
    assert!(ordinary["message"].get("requestId").is_none());
    assert!(ordinary["message"].get("mentioned").is_none());

    for (viewer, mentioned, receipt) in [
        (MENTIONED, Some(true), None),
        (SENDER, None, Some("web-send")),
    ] {
        let record = delivery(viewer, Some(target))
            .prepare(false, || true)
            .await
            .unwrap()
            .unwrap();
        let value: Value = serde_json::from_slice(&record.payload).unwrap();
        assert_eq!(value["message"]["mentioned"].as_bool(), mentioned);
        assert_eq!(value["message"]["requestId"].as_str(), receipt);
        assert_eq!(value["message"]["content"], ordinary["message"]["content"]);
    }
}

#[tokio::test]
async fn warm_records_still_require_the_event_audience_channel_and_visibility() {
    let private = ChatDestination {
        target: Some(Target::Recipient(player(MENTIONED))),
    };
    let event = event(
        private,
        Some(ChatVisibility {
            player_ids: vec![SENDER.into(), MENTIONED.into(), READER.into()],
        }),
    );
    assert!(event.for_viewer(READER, true).is_none());
    assert!(event.message.get().is_none());
    let participant = event.for_viewer(MENTIONED, false).unwrap();
    let delivery = participant.delivery(&[], |_| None).unwrap().unwrap();
    assert!(delivery.prepare(true, || true).await.unwrap().is_some());
    for (viewer, hidden) in [(MENTIONED, SENDER), (SENDER, MENTIONED)] {
        let participant = event.for_viewer(viewer, true).unwrap();
        assert!(
            participant
                .delivery(&[hidden.into()], |_| None)
                .unwrap()
                .is_none()
        );
    }
    assert!(event.for_viewer(READER, false).is_none());

    let staff = self::event(
        room(Room::Staff),
        Some(ChatVisibility {
            player_ids: vec![READER.into()],
        }),
    );
    let member = staff.for_viewer(READER, true).unwrap();
    let delivery = member.delivery(&[], |_| None).unwrap().unwrap();
    assert!(delivery.prepare(true, || true).await.unwrap().is_some());
    assert!(staff.for_viewer(READER, false).is_none());
    assert!(staff.for_viewer(MENTIONED, true).is_none());
}

#[tokio::test]
async fn a_slow_private_channel_preserves_other_channels_and_coalesces_all_missed_rows() {
    use crate::domains::chat::service::LIVE_ITEMS;
    use futures_util::FutureExt;

    let inbox = Inbox::default();
    let public = Arc::new(event(room(Room::Public), None));
    inbox.push(Channel::Public, &public);

    let private = ChatDestination {
        target: Some(Target::Recipient(player(READER))),
    };
    let last = i64::try_from(LIVE_ITEMS + 3).unwrap();
    for cursor in 2..=last {
        let mut event = event(private.clone(), None);
        Arc::get_mut(&mut event.committed).unwrap().cursor = cursor;
        inbox.push(Channel::Private, &Arc::new(event));
    }

    let Item::Gaps(gaps) = inbox.recv().await.unwrap() else {
        panic!("expected private gap")
    };
    assert_eq!(gaps.len(), 1);
    assert_eq!(gaps[0].0, "private");
    assert_eq!(gaps[0].1.cursor(), last);
    // Another private overflow while the client was handling the first notice must yield
    // to the already-buffered public message.
    let next = Arc::new(event(private, None));
    for _ in 0..=LIVE_ITEMS {
        inbox.push(Channel::Private, &next);
    }
    let Item::Event(event) = inbox.recv().await.unwrap() else {
        panic!("expected intact public event")
    };
    assert!(Arc::ptr_eq(&event, &public));
    assert!(matches!(inbox.recv().await.unwrap(), Item::Gaps(_)));
    assert!(inbox.recv().now_or_never().is_none());
}
