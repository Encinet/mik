use super::{ChatService, WebPost};
use crate::domains::{
    chat::{
        BodyInput, Committed, ConversationAddress, HistoryError, HistoryStore, NativeEvent,
        history::{Admission, Append, Audience, SyncRequest},
        link_uri, links, mentioned_person,
        model::{
            Channel, Destination, binding_key, destination, external_principal, player_snapshot,
            text_part,
        },
    },
    community::CommunityStore,
};
use mik_protocol::wire::{
    ChatBody, ChatEvent, ChatExternalIdentity, ChatGamePeer, ChatPart, ChatPlayer,
    ChatPresenceNotice, ChatPublication, ChatRecord, ChatTextRun, ChatWebRoster,
    chat_destination::Room,
    chat_part::{self, Content},
    chat_presence_notice,
    chat_publication::{Direct, Post, Subject, post},
    chat_record::Kind,
    chat_reference::Target as ReferenceTarget,
};
use std::{fs::Permissions, os::unix::fs::PermissionsExt, sync::Arc};
use tokio::sync::broadcast;
use tokio_rusqlite::rusqlite::Connection;
use uuid::Uuid;

async fn chat_fixture() -> (tempfile::TempDir, ChatService) {
    let directory = tempfile::Builder::new()
        .permissions(Permissions::from_mode(0o700))
        .tempdir()
        .unwrap();
    let history = HistoryStore::open(&directory.path().join("chat.db"))
        .await
        .unwrap();
    (directory, ChatService::new(history, None))
}

const ALICE: &str = "00000000-0000-4000-8000-000000000001";
const BOB: &str = "00000000-0000-4000-8000-000000000002";
const OTHER: &str = "00000000-0000-4000-8000-000000000003";

fn player(id: &str, name: &str) -> ChatPlayer {
    ChatPlayer {
        id: id.into(),
        name: name.into(),
    }
}

fn text(value: &str) -> ChatPart {
    text_part(value, None)
}

fn notice(id: &str) -> ChatPublication {
    ChatPublication {
        message_id: id.into(),
        subject: Some(Subject::Presence(joined())),
        ..Default::default()
    }
}

fn joined() -> ChatPresenceNotice {
    ChatPresenceNotice {
        kind: chat_presence_notice::Kind::Joined.into(),
        player: Some(player(ALICE, "Alice")),
    }
}

fn append_notice(id: &str) -> Append {
    Append {
        entry: ChatEvent {
            message_id: id.into(),
            record: Some(ChatRecord {
                platform: "game".into(),
                kind: Some(Kind::Presence(joined())),
                ..Default::default()
            }),
            ..Default::default()
        },
        admission: Admission::Presence,
    }
}

fn native(scope: &str) -> NativeEvent {
    NativeEvent {
        source: ConversationAddress {
            namespace: scope.into(),
            id: "room".into(),
        },
        event_scope: scope.into(),
        event_id: "event-1".into(),
    }
}

fn external(platform: &str) -> ChatExternalIdentity {
    ChatExternalIdentity {
        platform: platform.into(),
        issuer: "example.org".into(),
        subject: "alice".into(),
        ..Default::default()
    }
}

fn plain_input(text: &str) -> BodyInput {
    BodyInput::Text(ChatTextRun {
        text: text.into(),
        style: None,
    })
}

#[tokio::test]
async fn web_receipt_and_history_share_the_normalized_body() {
    let (_directory, chat) = chat_fixture().await;
    let sender = uuid::Uuid::new_v4().to_string();
    let mut live = chat.subscribe();
    chat.project_game(Some(Vec::new()));
    let post = || WebPost {
        sender: ChatPlayer {
            id: sender.clone(),
            name: "Alice".into(),
        },
        staff: false,
        channel: Channel::Public,
        target: None,
        text: "看 MC-12345",
        request_id: "retry-token",
    };
    let sent = chat.publish_web(post(), || true).await.ok().unwrap();
    chat.clear_game();
    assert_eq!(
        chat.publish_web(post(), || true).await.ok().as_deref(),
        Some(sent.as_str())
    );
    let published = live.try_recv().unwrap();
    assert!(matches!(
        live.try_recv(),
        Err(broadcast::error::TryRecvError::Empty)
    ));

    let page = chat
        .history()
        .sync(SyncRequest {
            audience: Audience {
                viewer: sender,
                staff: false,
                channel: Channel::Public,
                target: None,
            },
            since: None,
            continuation: None,
            hidden: Vec::new(),
        })
        .await
        .unwrap();
    assert_eq!(page.rows.len(), 1);
    let row = &page.rows[0];
    assert_eq!(row.message_id, sent);
    assert_eq!(Some(&row.record), published.entry.record.as_ref());
    let Some(Kind::User(user)) = &row.record.kind else {
        panic!("expected a user record")
    };
    assert!(matches!(
        user.body.as_ref().unwrap().parts[1].content.as_ref(),
        Some(Content::Reference(reference))
            if matches!(&reference.target, Some(ReferenceTarget::Link(link))
                if link.uri == "https://mojira.dev/MC-12345" && link.automatic)
                && links::label_text(&reference.runs) == "Mojira: MC-12345"
    ));
    chat.close().await.unwrap();
}

#[tokio::test]
async fn social_chat_keeps_literal_text_and_parses_both_wiki_links() {
    use chat_part::Content::{Reference, Text};

    let (_directory, chat) = chat_fixture().await;
    let mut live = chat.subscribe();
    chat.publish_native(
        external("matrix"),
        native("room"),
        vec![
            BodyInput::Resolved(text("MC-12345 ")),
            plain_input("看minecraft.wiki/w/Diamond；再看minecraft.wiki/w/Iron_Ingot"),
        ],
    )
    .await
    .unwrap();

    let published = live.try_recv().unwrap();
    let Some(Kind::User(user)) = &published.entry.record.as_ref().unwrap().kind else {
        panic!("expected a user record")
    };
    let parts = &user.body.as_ref().unwrap().parts;
    let content: Vec<_> = parts
        .iter()
        .map(|part| part.content.as_ref().unwrap())
        .collect();
    assert!(
        matches!(
            content.as_slice(),
            [Text(literal), Text(prefix), diamond @ Reference(_), Text(between), iron @ Reference(_)]
                if literal.text == "MC-12345 " && prefix.text == "看" && between.text == "；再看"
                    && link_uri(diamond) == Some("https://minecraft.wiki/w/Diamond")
                    && link_uri(iron) == Some("https://minecraft.wiki/w/Iron_Ingot")
        ),
        "{content:?}"
    );
    chat.close().await.unwrap();
}

#[tokio::test]
async fn paper_delivery_rechecks_mentions_without_rewriting_the_committed_event() {
    use crate::domains::{
        chat::person_mention,
        community::{Membership, Redemption, UnlinkAuthority},
    };

    let (directory, mut chat) = chat_fixture().await;
    let store = Arc::new(
        CommunityStore::open(&directory.path().join("community.db"))
            .await
            .unwrap(),
    );
    chat.community = Some(store.clone());
    let account = external("qq");
    let target = ChatExternalIdentity {
        subject: "bob".into(),
        ..account.clone()
    };
    let bob = Uuid::parse_str(BOB).unwrap();
    let key = binding_key(&target).unwrap();
    let code = store
        .issue_link_code(bob, "Bob".into(), "qq", || true)
        .await
        .unwrap()
        .code;
    let Redemption::Applied(binding) = store
        .redeem_link_code(Uuid::new_v4(), key, "Bobby", "", Some(code), |_| {
            Some(Membership::Joined)
        })
        .await
        .unwrap()
    else {
        panic!("expected a binding")
    };
    let mention = ChatPart {
        content: Some(person_mention(
            external_principal(target, None),
            vec![ChatTextRun {
                text: "@Bobby".into(),
                style: None,
            }],
        )),
    };
    let mut live = chat.subscribe();
    chat.publish_native(
        account,
        native("app"),
        vec![
            BodyInput::Resolved(mention.clone()),
            BodyInput::Resolved(mention),
        ],
    )
    .await
    .unwrap();
    let committed = live.try_recv().unwrap();
    let event = committed.paper_event().unwrap();
    assert_eq!(chat.paper_delivery(event).mention_player_ids, [BOB]);

    assert!(
        store
            .unlink_binding(
                Uuid::new_v4(),
                binding.binding_id,
                UnlinkAuthority::Owner(bob),
                || true,
            )
            .await
            .unwrap()
            .is_some()
    );
    let delivered = chat.paper_delivery(event);
    assert!(delivered.mention_player_ids.is_empty());
    assert_eq!(delivered.event.as_ref(), Some(event));
    chat.close().await.unwrap();
    store.close().await.unwrap();
}

#[tokio::test]
async fn social_event_deduplication_respects_the_platform_scope() {
    let (_directory, chat) = chat_fixture().await;
    let mut live = chat.subscribe();
    for scope in ["app-a", "app-a", "app-b"] {
        chat.publish_native(external("qq"), native(scope), vec![plain_input("hello")])
            .await
            .unwrap();
    }
    let first = live.try_recv().unwrap();
    let second = live.try_recv().unwrap();
    assert_ne!(first.entry.message_id, second.entry.message_id);
    assert_eq!(first.source.as_ref().unwrap().namespace, "app-a");
    assert_eq!(second.source.as_ref().unwrap().namespace, "app-b");
    assert!(matches!(
        live.try_recv(),
        Err(broadcast::error::TryRecvError::Empty)
    ));
    chat.close().await.unwrap();
}

#[tokio::test]
async fn paper_retries_are_idempotent_but_reusing_an_id_for_a_different_event_conflicts() {
    let (_directory, chat) = chat_fixture().await;
    let mut live = chat.subscribe();
    let original = notice("same-id");
    assert_eq!(
        chat.publish_paper(original.clone()).await.unwrap(),
        "same-id"
    );
    let event = live.try_recv().unwrap();
    // A change in the visibility projection does not change submission identity.
    chat.project_game(Some(vec![ChatGamePeer {
        player: Some(player(ALICE, "Alice")),
        visible_to: Some(Vec::new()),
        ..Default::default()
    }]));
    assert_eq!(
        chat.publish_paper(original.clone()).await.unwrap(),
        "same-id"
    );
    assert!(event.entry.visibility.is_none());

    let mut changed = original;
    let Some(Subject::Presence(presence)) = &mut changed.subject else {
        panic!("expected presence");
    };
    presence.kind = chat_presence_notice::Kind::Left.into();
    assert!(matches!(
        chat.publish_paper(changed.clone()).await,
        Err(HistoryError::Conflict)
    ));
    changed.subject = None;
    assert!(matches!(
        chat.publish_paper(changed).await,
        Err(HistoryError::Invalid(_))
    ));
    assert!(matches!(
        live.try_recv(),
        Err(broadcast::error::TryRecvError::Empty)
    ));
    chat.close().await.unwrap();
}

async fn bind_bob(chat: &mut ChatService, directory: &tempfile::TempDir) -> Arc<CommunityStore> {
    use crate::domains::community::{ExternalKey, Membership, Redemption};
    let store = Arc::new(
        CommunityStore::open(&directory.path().join("community.db"))
            .await
            .unwrap(),
    );
    for (platform, issuer, subject) in [
        ("qq", "test-app", "member"),
        ("matrix", "example.org", "@member:example.org"),
    ] {
        let code = store
            .issue_link_code(
                Uuid::parse_str(BOB).unwrap(),
                "Bob".into(),
                platform,
                || true,
            )
            .await
            .unwrap()
            .code;
        let key = ExternalKey::new(platform, issuer.into(), String::new(), subject.into()).unwrap();
        assert!(matches!(
            store
                .redeem_link_code(
                    Uuid::new_v4(),
                    key,
                    "External nickname",
                    "",
                    Some(code),
                    |_| { Some(Membership::Joined) }
                )
                .await
                .unwrap(),
            Redemption::Applied(_)
        ));
    }
    chat.community = Some(store.clone());
    store
}

#[tokio::test]
async fn paper_only_completes_public_candidates_and_keeps_the_visible_label_and_style() {
    use mik_protocol::wire::{ChatTextStyle, ChatVisibility};

    let (directory, mut chat) = chat_fixture().await;
    let store = bind_bob(&mut chat, &directory).await;
    chat.project_game(Some(Vec::new()));
    let mut live = chat.subscribe();
    let style = Some(ChatTextStyle {
        bold: true,
        ..Default::default()
    });
    let publication = ChatPublication {
        message_id: "paper-mention".into(),
        subject: Some(Subject::Post(Post {
            author: Some(player(ALICE, "Alice")),
            destination: Some(post::Destination::Room(Room::Public.into())),
            parts: vec![
                post::Part {
                    input: Some(post::part::Input::Resolved(text("@bob "))),
                },
                post::Part {
                    input: Some(post::part::Input::PlayerMention(
                        post::part::PlayerMention {
                            player_name: "bOb".into(),
                            runs: vec![
                                ChatTextRun {
                                    text: "@ b".into(),
                                    style: None,
                                },
                                ChatTextRun {
                                    text: "Ob".into(),
                                    style,
                                },
                            ],
                        },
                    )),
                },
            ],
        })),
        ..Default::default()
    };
    chat.publish_paper(publication.clone()).await.unwrap();

    let event = live.try_recv().unwrap();
    let record = event.entry.record.as_ref().unwrap();
    assert_eq!(record.platform, "game");
    let Some(Kind::User(user)) = &record.kind else {
        panic!("expected post")
    };
    let parts = &user.body.as_ref().unwrap().parts;
    assert_eq!(parts[0], text("@bob "));
    let Some(Content::Reference(mention)) = &parts[1].content else {
        panic!("expected player mention")
    };
    assert_eq!(
        mention.runs,
        [
            ChatTextRun {
                text: "@ b".into(),
                style: None
            },
            ChatTextRun {
                text: "Ob".into(),
                style
            },
        ]
    );
    assert!(matches!(&mention.target,
        Some(ReferenceTarget::Person(person)) if player_snapshot(person).is_some_and(|player| player.id == BOB)
    ));
    let mut restricted = publication;
    restricted.message_id = "restricted-mention".into();
    restricted.visibility_limit = Some(ChatVisibility {
        player_ids: vec![ALICE.into()],
    });
    chat.publish_paper(restricted).await.unwrap();
    let event = live.try_recv().unwrap();
    assert!(event.public_post().is_none());
    let parts = &post_body(&event).parts;
    assert_eq!(parts, &[text("@bob "), text("@ b"), text_part("Ob", style)]);
    chat.close().await.unwrap();
    store.close().await.unwrap();
}

fn post_body(event: &Committed) -> &ChatBody {
    let Some(Kind::User(user)) = &event.entry.record.as_ref().unwrap().kind else {
        panic!("expected post")
    };
    user.body.as_ref().unwrap()
}

#[tokio::test]
async fn public_web_and_native_posts_mention_bound_offline_players_by_account_name() {
    let (directory, mut chat) = chat_fixture().await;
    let store = bind_bob(&mut chat, &directory).await;
    let mut live = chat.subscribe();
    chat.project_game(Some(Vec::new()));
    let input = "你好@bOb在吗 https://example.org/@Bob @Bob:example.org @Bobé @Bob@server";
    let post = |request_id| WebPost {
        sender: player(ALICE, "Alice"),
        staff: false,
        channel: Channel::Public,
        target: None,
        text: input,
        request_id,
    };
    chat.publish_web(post("offline"), || true)
        .await
        .ok()
        .unwrap();
    for platform in ["qq", "matrix"] {
        chat.publish_native(
            external(platform),
            native("room"),
            vec![plain_input(input), BodyInput::Resolved(text(" @Bob"))],
        )
        .await
        .unwrap();
    }
    for origin in ["web", "qq", "matrix"] {
        let event = live.try_recv().unwrap();
        assert_eq!(event.public_post().unwrap().1, origin);
        let mentions: Vec<_> = post_body(&event)
            .parts
            .iter()
            .filter_map(|part| match part.content.as_ref()? {
                Content::Reference(reference) => {
                    let ReferenceTarget::Person(person) = reference.target.as_ref()? else {
                        return None;
                    };
                    Some((
                        player_snapshot(person).unwrap().id.as_str(),
                        links::label_text(&reference.runs),
                    ))
                }
                _ => None,
            })
            .collect();
        assert_eq!(mentions, [(BOB, "@bOb".into())]);
        assert_eq!(chat.paper_delivery(&event.entry).mention_player_ids, [BOB]);
    }
    chat.project_game(Some(vec![ChatGamePeer {
        player: Some(player(BOB, "Bob")),
        eligible: true,
        visible_to: Some(vec![0]),
        ..Default::default()
    }]));
    chat.publish_web(post("hidden"), || true)
        .await
        .ok()
        .unwrap();
    chat.publish_native(external("qq"), native("another"), vec![plain_input(input)])
        .await
        .unwrap();
    for _ in 0..2 {
        assert!(
            chat.paper_delivery(&live.try_recv().unwrap().entry)
                .mention_player_ids
                .is_empty()
        );
    }
    chat.close().await.unwrap();
    store.close().await.unwrap();
}

#[tokio::test]
async fn active_names_take_precedence_over_stale_bindings_in_every_ingress() {
    use mik_protocol::wire::ChatWebPeer;

    let (directory, mut chat) = chat_fixture().await;
    let store = bind_bob(&mut chat, &directory).await;
    let mut live = chat.subscribe();
    chat.project_game(Some(vec![ChatGamePeer {
        player: Some(player(BOB, "Robert")),
        eligible: true,
        ..Default::default()
    }]));
    let roster = Arc::new(ChatWebRoster {
        players: vec![ChatWebPeer {
            player: Some(player(OTHER, "Bob")),
            connections: 1,
            ..Default::default()
        }],
        ..Default::default()
    });
    chat.attach_web(move || roster.clone(), |_| {}).unwrap();
    let source = "@Bob @Robert";
    chat.publish_web(
        WebPost {
            sender: player(ALICE, "Alice"),
            staff: false,
            channel: Channel::Public,
            target: None,
            text: source,
            request_id: "active-names",
        },
        || true,
    )
    .await
    .ok()
    .unwrap();
    for platform in ["qq", "matrix"] {
        chat.publish_native(
            external(platform),
            native("room"),
            vec![plain_input(source)],
        )
        .await
        .unwrap();
    }
    for platform in ["web", "qq", "matrix"] {
        let event = live.try_recv().unwrap();
        assert_eq!(event.public_post().unwrap().1, platform);
        let targets: Vec<_> = post_body(&event)
            .parts
            .iter()
            .filter_map(|part| player_snapshot(mentioned_person(part.content.as_ref()?)?))
            .map(|player| player.id.as_str())
            .collect();
        assert_eq!(targets, [OTHER, BOB]);
    }

    // Even after the old Bob goes offline, a pending name cannot bypass its active owner.
    chat.project_game(Some(Vec::new()));
    let publication = ChatPublication {
        message_id: "pending".into(),
        subject: Some(Subject::Post(Post {
            author: Some(player(ALICE, "Alice")),
            destination: Some(post::Destination::Room(Room::Public.into())),
            parts: vec![post::Part {
                input: Some(post::part::Input::PlayerMention(
                    post::part::PlayerMention {
                        player_name: "Bob".into(),
                        runs: vec![ChatTextRun {
                            text: "@Bob".into(),
                            style: None,
                        }],
                    },
                )),
            }],
        })),
        ..Default::default()
    };
    chat.publish_paper(publication).await.unwrap();
    assert_eq!(post_body(&live.try_recv().unwrap()).parts, [text("@Bob")]);
    chat.close().await.unwrap();
    store.close().await.unwrap();
}

fn private_publication(alice: &ChatPlayer, bob: &ChatPlayer) -> ChatPublication {
    use mik_protocol::wire::ChatVisibility;
    ChatPublication {
        message_id: "private".into(),
        visibility_limit: Some(ChatVisibility {
            player_ids: vec![ALICE.into(), OTHER.into()],
        }),
        subject: Some(Subject::Post(Post {
            author: Some(alice.clone()),
            destination: Some(post::Destination::Direct(Direct {
                recipient: Some(bob.clone()),
                require_remote_recipient: false,
            })),
            parts: vec![post::Part {
                input: Some(post::part::Input::Resolved(text("for Bob"))),
            }],
        })),
        ..Default::default()
    }
}

fn direct(publication: &mut ChatPublication) -> &mut Direct {
    let Some(Subject::Post(post)) = &mut publication.subject else {
        panic!("expected player post");
    };
    let Some(post::Destination::Direct(direct)) = &mut post.destination else {
        panic!("expected direct post");
    };
    direct
}

#[tokio::test]
async fn private_history_and_live_delivery_use_the_same_destination_and_intersect_visibility() {
    let (_directory, chat) = chat_fixture().await;
    let mut live = chat.subscribe();
    let alice = player(ALICE, "Alice");
    let bob = player(BOB, "Bob");
    // Bob allows Alice to see him, but not the third player.
    chat.project_game(Some(vec![
        ChatGamePeer {
            player: Some(alice.clone()),
            ..Default::default()
        },
        ChatGamePeer {
            player: Some(bob.clone()),
            visible_to: Some(vec![1]),
            ..Default::default()
        },
        ChatGamePeer {
            player: Some(player(OTHER, "Other")),
            ..Default::default()
        },
    ]));
    let mut publication = private_publication(&alice, &bob);
    chat.publish_paper(publication.clone()).await.unwrap();
    let event = live.try_recv().unwrap();
    for (viewer, expected) in [(ALICE, true), (BOB, false), (OTHER, false)] {
        assert_eq!(event.visible_to(viewer, true), expected);
        let page = chat
            .history()
            .sync(SyncRequest {
                audience: Audience {
                    viewer: viewer.into(),
                    staff: true,
                    channel: Channel::Private,
                    target: None,
                },
                since: None,
                continuation: None,
                hidden: Vec::new(),
            })
            .await
            .unwrap();
        assert_eq!(page.rows.len(), usize::from(expected));
        if let Some(row) = page.rows.first() {
            assert_eq!(row.expires_at, event.expires_at);
            assert!(matches!(destination(&row.record).unwrap(),
                Destination::Private { author, recipient } if *author == alice && *recipient == bob));
        }
    }
    direct(&mut publication).require_remote_recipient = true;
    publication.message_id = "send".into();
    let web = Arc::new(ChatWebRoster {
        players: vec![mik_protocol::wire::ChatWebPeer {
            player: Some(bob),
            connections: 1,
            ..Default::default()
        }],
        ..Default::default()
    });
    let current = Arc::new(std::sync::Mutex::new(web.clone()));
    chat.attach_web(
        {
            let current = current.clone();
            move || current.lock().unwrap().clone()
        },
        |_| {},
    )
    .unwrap();
    assert!(matches!(
        chat.publish_paper(publication.clone()).await,
        Err(HistoryError::RecipientUnavailable)
    ));
    publication.visibility_limit = None;
    *current.lock().unwrap() = super::empty_web_roster();
    assert!(matches!(
        chat.publish_paper(publication.clone()).await,
        Err(HistoryError::RecipientUnavailable)
    ));
    *current.lock().unwrap() = web;
    assert_eq!(
        chat.publish_paper(publication.clone()).await.unwrap(),
        "send"
    );
    *current.lock().unwrap() = super::empty_web_roster();
    chat.clear_game();
    assert_eq!(
        chat.publish_paper(publication.clone()).await.unwrap(),
        "send"
    );
    assert_eq!(live.try_recv().unwrap().entry.message_id, "send");
    assert!(matches!(
        live.try_recv(),
        Err(broadcast::error::TryRecvError::Empty)
    ));
    direct(&mut publication).recipient = Some(alice);
    assert!(matches!(
        chat.publish_paper(publication).await,
        Err(HistoryError::Invalid(_))
    ));
    chat.close().await.unwrap();
}

#[tokio::test]
async fn shutdown_drains_already_queued_messages_in_order() {
    let (_directory, chat) = chat_fixture().await;
    let mut live = chat.subscribe();
    let first = chat.enqueue(append_notice("first")).unwrap();
    let second = chat.enqueue(append_notice("second")).unwrap();

    chat.close().await.unwrap();

    let first = first.await.unwrap().unwrap();
    let second = second.await.unwrap().unwrap();
    let first_live = live.try_recv().unwrap();
    let second_live = live.try_recv().unwrap();
    assert!(first_live.order_key < second_live.order_key);
    assert_eq!(first_live.entry.message_id, first);
    assert_eq!(second_live.entry.message_id, second);
}

#[tokio::test]
async fn commit_failure_is_reported_to_every_sender_without_broadcast() {
    let (directory, chat) = chat_fixture().await;
    let database = Connection::open(directory.path().join("chat.db")).unwrap();
    database
        .execute_batch(
            "CREATE TABLE fixture_parent(id INTEGER PRIMARY KEY);
             CREATE TABLE fixture_child(id INTEGER REFERENCES fixture_parent(id)
                DEFERRABLE INITIALLY DEFERRED);
             CREATE TRIGGER fail_commit AFTER INSERT ON chat_messages
             WHEN NEW.message_id = 'invalid' BEGIN INSERT INTO fixture_child VALUES(1); END;",
        )
        .unwrap();
    let mut live = chat.subscribe();
    let first = chat.enqueue(append_notice("valid")).unwrap();
    let second = chat.enqueue(append_notice("invalid")).unwrap();

    chat.close().await.unwrap();

    for receipt in [first, second] {
        assert!(matches!(
            receipt.await.unwrap(),
            Err(HistoryError::Database(_))
        ));
    }
    assert!(matches!(
        live.try_recv(),
        Err(broadcast::error::TryRecvError::Empty)
    ));
    assert_eq!(
        database
            .query_row("SELECT count(*) FROM chat_messages", [], |row| row
                .get::<_, i64>(0))
            .unwrap(),
        0
    );
}

#[tokio::test]
async fn undrained_worker_is_reported_without_skipping_history_close() {
    let (_directory, chat) = chat_fixture().await;
    chat.worker.lock().unwrap().as_ref().unwrap().abort();

    assert!(chat.close().await.is_err());
    assert!(matches!(
        chat.prune_history().await,
        Err(HistoryError::Busy)
    ));
}
