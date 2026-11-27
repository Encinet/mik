use super::{HistoryError, links};
use crate::domains::community::{BindingIndex, ExternalKey};
use mik_protocol::wire::{
    ChatBody, ChatDescription, ChatDestination, ChatEvent, ChatExternalActor, ChatExternalIdentity,
    ChatGamePeer, ChatIdentity, ChatPlayer, ChatRecord, ChatReference, ChatTextRun, ChatTextStyle,
    ChatVisibility, chat_description,
    chat_destination::{Room, Target},
    chat_identity::Principal,
    chat_part::Content,
    chat_presence_notice,
    chat_record::Kind,
    chat_reference::{Link, Target as ReferenceTarget},
};
use std::{borrow::Cow, collections::HashSet};
use uuid::Uuid;

#[derive(Clone, Copy, PartialEq, Eq)]
pub(super) enum Channel {
    Public,
    Staff,
    Private,
}

impl Channel {
    pub(super) fn name(self) -> &'static str {
        match self {
            Self::Public => "public",
            Self::Staff => "staff",
            Self::Private => "private",
        }
    }
}

pub(super) enum Destination<'a> {
    Public,
    Staff,
    Private {
        author: &'a ChatPlayer,
        recipient: &'a ChatPlayer,
    },
}

impl Destination<'_> {
    pub(super) fn channel(&self) -> Channel {
        match self {
            Self::Public => Channel::Public,
            Self::Staff => Channel::Staff,
            Self::Private { .. } => Channel::Private,
        }
    }

    pub(super) fn includes(&self, player: &str, staff: bool) -> bool {
        match self {
            Self::Public => true,
            Self::Staff => staff,
            Self::Private { author, recipient } => player == author.id || player == recipient.id,
        }
    }
}

pub(crate) fn room(room: Room) -> ChatDestination {
    ChatDestination {
        target: Some(Target::Room(room.into())),
    }
}

pub(super) fn destination(record: &ChatRecord) -> Result<Destination<'_>, HistoryError> {
    let user = match record.kind.as_ref() {
        Some(Kind::User(user)) => user,
        Some(Kind::Presence(_)) => return Ok(Destination::Public),
        None => return Err(HistoryError::Invalid("chat record required")),
    };
    match user
        .destination
        .as_ref()
        .and_then(|destination| destination.target.as_ref())
    {
        Some(Target::Room(room)) if *room == i32::from(Room::Public) => Ok(Destination::Public),
        Some(Target::Room(room)) if *room == i32::from(Room::Staff) => Ok(Destination::Staff),
        Some(Target::Recipient(recipient)) => {
            let author = user
                .author
                .as_ref()
                .and_then(|author| match &author.principal {
                    Some(Principal::Player(player)) => Some(player),
                    _ => None,
                })
                .filter(|author| author.id != recipient.id)
                .ok_or(HistoryError::Invalid(
                    "private destination requires two distinct players",
                ))?;
            Ok(Destination::Private { author, recipient })
        }
        _ => Err(HistoryError::Invalid("invalid chat destination")),
    }
}

pub(crate) fn person_mention(identity: ChatIdentity, runs: Vec<ChatTextRun>) -> Content {
    Content::Reference(ChatReference {
        target: Some(ReferenceTarget::Person(identity)),
        runs,
    })
}

pub(crate) fn link_reference(
    uri: impl Into<String>,
    runs: Vec<ChatTextRun>,
    automatic: bool,
) -> ChatReference {
    ChatReference {
        runs,
        target: Some(ReferenceTarget::Link(Link {
            uri: uri.into(),
            automatic,
        })),
    }
}

pub(crate) fn link_target(reference: &ChatReference) -> Option<&Link> {
    match reference.target.as_ref()? {
        ReferenceTarget::Link(link) => Some(link),
        _ => None,
    }
}

pub(crate) fn link_uri(content: &Content) -> Option<&str> {
    match content {
        Content::Reference(reference) => Some(&link_target(reference)?.uri),
        _ => None,
    }
}

pub(crate) fn mentioned_person(content: &Content) -> Option<&ChatIdentity> {
    match content {
        Content::Reference(ChatReference {
            target: Some(ReferenceTarget::Person(person)),
            ..
        }) => Some(person),
        _ => None,
    }
}

pub(crate) fn binding_key(identity: &ChatExternalIdentity) -> Option<ExternalKey> {
    ExternalKey::new(
        &identity.platform,
        identity.issuer.clone(),
        identity.scope.clone(),
        identity.subject.clone(),
    )
    .ok()
    .filter(|key| key.parts()[0] == identity.platform)
}

pub(crate) fn player_principal(player: ChatPlayer) -> ChatIdentity {
    ChatIdentity {
        principal: Some(Principal::Player(player)),
    }
}

pub(crate) fn external_principal(
    account: ChatExternalIdentity,
    bound_player: Option<ChatPlayer>,
) -> ChatIdentity {
    ChatIdentity {
        principal: Some(Principal::External(ChatExternalActor {
            account: Some(account),
            bound_player,
        })),
    }
}

pub(crate) fn player_snapshot(person: &ChatIdentity) -> Option<&ChatPlayer> {
    match person.principal.as_ref()? {
        Principal::Player(player) => Some(player),
        Principal::External(actor) => actor.bound_player.as_ref(),
    }
}

pub(crate) fn external_account(person: &ChatIdentity) -> Option<&ChatExternalIdentity> {
    match person.principal.as_ref()? {
        Principal::External(actor) => actor.account.as_ref(),
        Principal::Player(_) => None,
    }
}

// The stored player is a snapshot, never permission to notify a newly linked account.
pub(crate) fn current_player(
    person: &ChatIdentity,
    bindings: Option<&BindingIndex>,
) -> Option<Uuid> {
    match person.principal.as_ref()? {
        Principal::Player(player) => Uuid::parse_str(&player.id).ok(),
        Principal::External(actor) => {
            let id = Uuid::parse_str(&actor.bound_player.as_ref()?.id).ok()?;
            let binding = bindings?.find(&binding_key(actor.account.as_ref()?)?)?;
            (binding.account == id).then_some(id)
        }
    }
}

fn validate_person(person: &ChatIdentity) -> Result<(), HistoryError> {
    match person.principal.as_ref() {
        Some(Principal::Player(player)) => validate_player(player),
        Some(Principal::External(actor)) => {
            if actor.account.as_ref().and_then(binding_key).is_none() {
                return Err(HistoryError::Invalid("invalid external identity"));
            }
            if let Some(player) = &actor.bound_player {
                validate_player(player)?;
            }
            Ok(())
        }
        None => Err(HistoryError::Invalid("chat identity required")),
    }
}

#[cfg(test)]
pub(crate) fn text_part(
    text: impl Into<String>,
    style: Option<ChatTextStyle>,
) -> mik_protocol::wire::ChatPart {
    mik_protocol::wire::ChatPart {
        content: Some(Content::Text(ChatTextRun {
            text: text.into(),
            style,
        })),
    }
}

pub(super) fn validate_event(event: &mut ChatEvent) -> Result<(), HistoryError> {
    if event.message_id.trim().is_empty() || event.message_id.len() > 2048 {
        return Err(HistoryError::Invalid("invalid message id"));
    }
    normalize_visibility(event.visibility.as_mut())?;
    let record = event
        .record
        .as_ref()
        .ok_or(HistoryError::Invalid("message record required"))?;
    if record.platform.trim().is_empty() {
        return Err(HistoryError::Invalid("chat origin required"));
    }
    let destination = destination(record)?;
    if let Destination::Private { recipient, .. } = &destination {
        validate_player(recipient)?;
    }
    match &record.kind {
        Some(Kind::User(user)) => {
            let author = user
                .author
                .as_ref()
                .ok_or(HistoryError::Invalid("message author required"))?;
            validate_person(author)?;
            let body = user
                .body
                .as_ref()
                .ok_or(HistoryError::Invalid("message body required"))?;
            validate_body(body)?;
        }
        Some(Kind::Presence(presence))
            if presence.kind() != chat_presence_notice::Kind::Unspecified =>
        {
            validate_player(
                presence
                    .player
                    .as_ref()
                    .ok_or(HistoryError::Invalid("presence player required"))?,
            )?;
        }
        _ => return Err(HistoryError::Invalid("invalid chat record kind")),
    }
    Ok(())
}

pub(super) fn visibility_allows(visibility: Option<&ChatVisibility>, player: &str) -> bool {
    visibility.is_none_or(|scope| scope.player_ids.iter().any(|id| id == player))
}

pub(super) fn normalize_visibility(
    visibility: Option<&mut ChatVisibility>,
) -> Result<(), HistoryError> {
    if let Some(visibility) = visibility {
        if visibility.player_ids.len() > 4096 {
            return Err(HistoryError::Invalid("visibility scope too large"));
        }
        for id in &visibility.player_ids {
            uuid(id)?;
        }
        visibility.player_ids.sort_unstable();
        visibility.player_ids.dedup();
    }
    Ok(())
}

pub(super) const MAX_BODY_PARTS: usize = 1024;

fn validate_body(body: &ChatBody) -> Result<(), HistoryError> {
    if body.parts.is_empty() || body.parts.len() > MAX_BODY_PARTS {
        return Err(HistoryError::Invalid("invalid chat body size"));
    }
    for part in &body.parts {
        match part.content.as_ref() {
            Some(Content::Text(run)) if valid_run(run) => {}
            Some(Content::Reference(reference)) if valid_runs(&reference.runs) => {
                match &reference.target {
                    Some(ReferenceTarget::Link(link))
                        if links::link_destination(&link.uri).is_some() => {}
                    Some(ReferenceTarget::Person(person)) => validate_person(person)?,
                    Some(ReferenceTarget::Audience(_)) => {}
                    _ => return Err(HistoryError::Invalid("invalid reference target")),
                }
            }
            Some(Content::Description(description))
                if valid_style(description.style)
                    && match &description.label {
                        Some(chat_description::Label::Kind(kind)) => {
                            chat_description::Kind::try_from(*kind)
                                .is_ok_and(|kind| kind != chat_description::Kind::Unspecified)
                        }
                        Some(chat_description::Label::Name(name)) => {
                            !name.trim().is_empty() && valid_text(name)
                        }
                        None => false,
                    } => {}
            _ => return Err(HistoryError::Invalid("invalid chat part")),
        }
    }
    Ok(())
}

pub(super) fn valid_runs(runs: &[ChatTextRun]) -> bool {
    (1..=256).contains(&runs.len()) && runs.iter().all(valid_run)
}

fn valid_run(run: &ChatTextRun) -> bool {
    valid_style(run.style) && valid_text(&run.text)
}

fn valid_text(text: &str) -> bool {
    !text.is_empty()
        && !text
            .chars()
            .any(|ch| ch.is_control() && !matches!(ch, '\n' | '\t'))
}

fn valid_style(style: Option<ChatTextStyle>) -> bool {
    style.is_none_or(|style| {
        style.color.is_none_or(|color| color <= 0xff_ff_ff)
            && style
                .gradient_end_color
                .is_none_or(|color| style.color.is_some() && color <= 0xff_ff_ff)
    })
}

fn validate_player(player: &ChatPlayer) -> Result<(), HistoryError> {
    uuid(&player.id)?;
    if player.name.trim().is_empty()
        || player.name.encode_utf16().count() > 64
        || player.name.chars().any(char::is_control)
    {
        return Err(HistoryError::Invalid("invalid player name"));
    }
    Ok(())
}

pub(super) fn uuid(value: &str) -> Result<(), HistoryError> {
    if !Uuid::parse_str(value).is_ok_and(|id| id.to_string() == value) {
        return Err(HistoryError::Invalid("invalid UUID"));
    }
    Ok(())
}

pub(crate) fn valid_game_roster(peers: &[ChatGamePeer]) -> bool {
    let mut unique = HashSet::with_capacity(peers.len());
    peers.iter().all(|peer| {
        peer.player
            .as_ref()
            .is_some_and(|player| validate_player(player).is_ok() && unique.insert(&player.id))
            && peer.visible_to.as_ref().is_none_or(|bits| {
                let full_bytes = peers.len() / 8;
                let remainder = peers.len() % 8;
                bits.len() <= peers.len().div_ceil(8)
                    && bits
                        .get(full_bytes)
                        .is_none_or(|last| u16::from(*last) < (1 << remainder))
            })
    })
}

pub(crate) fn social_platform_label(id: &str) -> &str {
    match id {
        "qq" => "QQ",
        "matrix" => "Matrix",
        _ => id,
    }
}

pub(crate) fn description_label(description: &ChatDescription) -> Cow<'_, str> {
    match description.label.as_ref() {
        Some(chat_description::Label::Name(name)) => Cow::Borrowed(name),
        Some(chat_description::Label::Kind(kind)) => Cow::Owned(description_kind_label(
            chat_description::Kind::try_from(*kind).unwrap_or_default(),
        )),
        None => Cow::Borrowed(""),
    }
}

pub(crate) fn description_kind_label(kind: chat_description::Kind) -> String {
    format!("[{}]", kind.as_str_name().to_ascii_lowercase())
}

pub(crate) fn external_name(identity: &ChatExternalIdentity) -> &str {
    if identity.display_name.trim().is_empty() {
        &identity.subject
    } else {
        &identity.display_name
    }
}
