use super::super::{
    Lane, application,
    record::{self, APPLICATION, Record},
    session::{Lease, Registration, State, WebChat},
};
use super::{
    Result,
    framing::Output,
    invalid,
    progress::{HistoryCommit, Progress},
    scope::{guard_write, scope_ended},
};
use crate::domains::chat::now;
use serde_json::json;
use std::sync::Arc;

pub(super) async fn execute(
    web: &WebChat,
    session: &Registration<'_>,
    deflate: bool,
    lane: Lane,
    body: &[u8],
) -> Result<Option<(Arc<Lease>, application::Replies)>> {
    let state = session.state.borrow().clone();
    let State::Authorized(lease) = state else {
        return Ok(None);
    };
    if lease.grant.expires_at <= now() {
        return Ok(None);
    }
    let Some((&kind, body)) = body.split_first() else {
        return Err(invalid());
    };
    let expanded;
    let body = match kind {
        APPLICATION => body,
        record::COMPRESSED if deflate && lane == Lane::Live => {
            expanded = record::inflate_request(body)?;
            &expanded
        }
        _ => return Err(invalid()),
    };
    let state = session.state.clone();
    let admitted = lease.clone();
    let reply = application::request(
        web,
        &lease,
        std::str::from_utf8(body)?,
        kind == record::COMPRESSED,
        lane,
        move || state.has_changed().is_ok() && state.borrow().permits_reply(&admitted, now()),
    )
    .await;
    Ok(Some((lease, reply)))
}

pub(super) async fn write_live_reply(
    web: &WebChat,
    session: &Registration<'_>,
    output: &Output,
    lease: &Arc<Lease>,
    reply: application::Replies,
) -> Result<()> {
    if !session.state.borrow().permits_reply(lease, now()) {
        return Ok(());
    }
    match reply {
        application::Replies::Single(application::Reply::Ordinary(reply)) => {
            if reply["type"] == "accepted" {
                web.activity(&session.id);
            }
            output.json(&reply).await
        }
        application::Replies::Single(application::Reply::Receipts(mut items)) => {
            let Some(game) = web.chat.game() else {
                return output.json(&application::error("", "chat_busy")).await;
            };
            let hidden = game.hidden_from(&lease.grant.player_uuid);
            items.retain(|receipt| receipt.visible_to(&hidden));
            let reply = json!({"type":"receipts", "items":items});
            guard_write(
                output.json(&reply),
                scope_ended(web, session, lease, &game, &hidden),
            )
            .await
        }
        _ => Err(invalid()),
    }
}

pub(super) async fn write_history_replies(
    web: &WebChat,
    session: &Registration<'_>,
    output: &Output,
    lease: &Arc<Lease>,
    progress: &Progress,
    replies: application::Replies,
) -> Result<Vec<HistoryCommit>> {
    let (batch, pages) = match replies {
        application::Replies::Single(page) => (None, vec![page]),
        application::Replies::Resume { id, pages } => (Some(id), pages),
    };
    let mut commits = Vec::new();
    let mut current = Vec::new();
    for reply in pages {
        if !session.state.borrow().permits_reply(lease, now()) {
            return Err(invalid());
        }
        let application::Reply::History {
            output: mut body,
            compressible,
            hidden,
            anchor,
            channel,
            continuation,
        } = reply
        else {
            if let application::Reply::Ordinary(error) = reply {
                output.json(&error).await?;
                continue;
            }
            return Err(invalid());
        };
        let commit = channel.map(|channel| HistoryCommit {
            channel,
            hidden: hidden.clone(),
            anchor,
            continuation,
            next_page: body.fields["nextPage"].as_str().map(str::to_owned),
            checkpoint: body.fields["checkpoint"].as_str().map(str::to_owned),
            reset: body.fields["reset"] == true,
        });
        if let Some(commit) = commit.as_ref()
            && progress.state.lock().await.aligned(commit, lease)
        {
            body.fields["aligned"] = json!(true);
        }
        if batch.is_some() && body.fields["type"] == "historyCurrent" {
            current.push(commit.ok_or_else(invalid)?);
            continue;
        }
        let record = Record::prepare(
            serde_json::to_string(&body)?,
            output.deflate && compressible,
        )
        .await?;
        if !session.state.borrow().permits_reply(lease, now()) {
            return Err(invalid());
        }
        if web.chat.hidden_from(&lease.grant.player_uuid).as_ref() != Some(&hidden) {
            let id = body.fields["requestId"].as_str().ok_or_else(invalid)?;
            output
                .json(&application::error(id, "history_unavailable"))
                .await?;
            continue;
        }
        output.record(&record).await?;
        commits.extend(commit);
    }
    if let Some(id) = batch
        && !current.is_empty()
    {
        if !session.state.borrow().permits_reply(lease, now()) {
            return Err(invalid());
        }
        let hidden = web.chat.hidden_from(&lease.grant.player_uuid);
        if current
            .iter()
            .all(|page| hidden.as_ref() == Some(&page.hidden))
        {
            let channels: Vec<_> = current.iter().map(|page| page.channel).collect();
            output
                .json(&json!({"type":"historyCurrent","requestId":id,"channels":channels}))
                .await?;
            commits.extend(current);
        } else {
            for page in current {
                let channel = page.channel;
                output
                    .json(&application::error(
                        &format!("{id}-{channel}"),
                        "history_unavailable",
                    ))
                    .await?;
            }
        }
    }
    Ok(commits)
}
