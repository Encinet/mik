use super::{ChatService, empty_web_roster, require_readable_recipient};
use crate::domains::chat::{
    BodyInput, HistoryError,
    history::{Admission, Append},
    mentions,
    model::{
        Destination, MAX_BODY_PARTS, destination, external_account, mentioned_person,
        normalize_visibility, player_principal, room, valid_runs, validate_event,
    },
};
use mik_protocol::wire::{
    ChatDestination, ChatEvent, ChatPublication, ChatRecord, ChatUserRecord, PaperChatDelivery,
    chat_destination::Target,
    chat_publication::{Subject, post},
    chat_record::Kind,
};
use prost::Message;
use sha2::{Digest, Sha256};
use std::collections::BTreeSet;

impl ChatService {
    pub(crate) fn paper_delivery(&self, event: &ChatEvent) -> PaperChatDelivery {
        let body = match event
            .record
            .as_ref()
            .and_then(|record| record.kind.as_ref())
        {
            Some(Kind::User(user)) => user.body.as_ref(),
            _ => None,
        };
        let mut players = BTreeSet::new();
        if let Some(body) = body {
            for part in &body.parts {
                if let Some(person) = part.content.as_ref().and_then(mentioned_person)
                    && let Some(player) = self.current_mention_player(person)
                {
                    players.insert(player);
                }
            }
        }
        PaperChatDelivery {
            event: Some(event.clone()),
            mention_player_ids: players.into_iter().map(|id| id.to_string()).collect(),
        }
    }

    pub(crate) async fn publish_paper(
        &self,
        mut publication: ChatPublication,
    ) -> Result<String, HistoryError> {
        normalize_visibility(publication.visibility_limit.as_mut())?;
        // The fingerprint covers the submission, not the current roster or bindings.
        let digest = Sha256::digest(publication.encode_to_vec()).into();
        let PaperSubmission {
            mut entry,
            requires_remote,
            parts,
        } = paper_submission(publication)?;
        // A direct post does not resolve offline candidates. Its current audience is evaluated
        // after the receipt lookup, and only for a genuinely new write.
        let game = if requires_remote { None } else { self.game() };
        if let Some(parts) = parts {
            let roster = if game.is_some()
                && parts
                    .iter()
                    .any(|part| matches!(part, BodyInput::OfflineMention { .. }))
            {
                self.web_roster()
            } else {
                empty_web_roster()
            };
            self.prepare_post(&mut entry, parts, game.as_deref(), &roster.players)?;
        } else if let Some(game) = &game {
            game.restrict(&mut entry);
        }
        if requires_remote {
            validate_event(&mut entry)?;
            if let Some(id) = self
                .history
                .paper_receipt(entry.message_id.clone(), digest)
                .await?
            {
                return Ok(id);
            }
            // Looking up the receipt can suspend. Check a new submission against the current
            // projections rather than the ones that were present when the request arrived.
            let view = self.game().ok_or(HistoryError::Busy)?;
            view.restrict(&mut entry);
            let Some(record) = &entry.record else {
                return Err(HistoryError::Invalid("message record required"));
            };
            let Destination::Private { author, recipient } = destination(record)? else {
                return Err(HistoryError::Invalid(
                    "remote delivery requires a direct post",
                ));
            };
            let roster = self.web_roster();
            if view
                .web_player(&author.id, &recipient.id, &roster.players)
                .is_none()
            {
                return Err(HistoryError::RecipientUnavailable);
            }
            require_readable_recipient(&entry)?;
        }
        self.commit(Append {
            entry,
            admission: Admission::Paper(digest),
        })
        .await
    }
}

struct PaperSubmission {
    entry: ChatEvent,
    requires_remote: bool,
    parts: Option<Vec<BodyInput>>,
}

fn paper_submission(publication: ChatPublication) -> Result<PaperSubmission, HistoryError> {
    let mut requires_remote = false;
    let mut parts = None;
    let kind = match publication.subject {
        Some(Subject::Presence(notice)) => Kind::Presence(notice),
        Some(Subject::Post(post)) => {
            let destination = match post.destination {
                Some(post::Destination::Room(value)) => room(
                    mik_protocol::wire::chat_destination::Room::try_from(value)
                        .map_err(|_| HistoryError::Invalid("invalid chat room"))?,
                ),
                Some(post::Destination::Direct(direct)) => {
                    requires_remote = direct.require_remote_recipient;
                    ChatDestination {
                        target: direct.recipient.map(Target::Recipient),
                    }
                }
                None => return Err(HistoryError::Invalid("chat destination required")),
            };
            if post.parts.len() > MAX_BODY_PARTS {
                return Err(HistoryError::Invalid("invalid chat body size"));
            }
            parts = Some(
                post.parts
                    .into_iter()
                    .map(paper_part)
                    .collect::<Result<Vec<_>, _>>()?,
            );
            Kind::User(ChatUserRecord {
                destination: Some(destination),
                author: post.author.map(player_principal),
                body: None,
            })
        }
        None => return Err(HistoryError::Invalid("Paper publication subject required")),
    };
    Ok(PaperSubmission {
        entry: ChatEvent {
            message_id: publication.message_id,
            record: Some(ChatRecord {
                platform: "game".into(),
                created_at_ms: publication.created_at_ms,
                kind: Some(kind),
            }),
            visibility: publication.visibility_limit,
        },
        requires_remote,
        parts,
    })
}

fn paper_part(part: post::Part) -> Result<BodyInput, HistoryError> {
    match part.input {
        Some(post::part::Input::Resolved(part)) => {
            if part
                .content
                .as_ref()
                .and_then(mentioned_person)
                .and_then(external_account)
                .is_some()
            {
                return Err(HistoryError::Invalid(
                    "Paper cannot assert external mention identities",
                ));
            }
            Ok(BodyInput::Resolved(part))
        }
        Some(post::part::Input::PlayerMention(mention))
            if mentions::valid_name(&mention.player_name) && valid_runs(&mention.runs) =>
        {
            Ok(BodyInput::OfflineMention {
                name: mention.player_name,
                runs: mention.runs,
            })
        }
        Some(post::part::Input::PlayerMention(_)) => {
            Err(HistoryError::Invalid("invalid player mention candidate"))
        }
        None => Err(HistoryError::Invalid("post part required")),
    }
}
