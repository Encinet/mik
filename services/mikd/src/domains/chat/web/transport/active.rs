use super::super::{
    Lane,
    live::Item,
    session::{Lease, Registration, State, WebChat},
};
use super::{
    Result,
    framing::Output,
    presence::PresenceUpdates,
    progress::Progress,
    request::{execute, write_live_reply},
    scope::{guard_write, permits_delivery, scope_ended},
};
use crate::domains::chat::now;
use futures_util::{StreamExt, stream};
use serde_json::json;
use std::{sync::Arc, time::Duration};
use tokio::{
    sync::mpsc,
    time::{Instant, sleep_until},
};
use zeroize::Zeroizing;

pub(super) async fn live(
    web: &WebChat,
    session: &Registration<'_>,
    output: &Output,
    input: mpsc::Receiver<Zeroizing<Vec<u8>>>,
    progress: &Progress,
) -> Result<()> {
    let mut state = session.state.clone();
    let mut published = None;
    let mut presence = PresenceUpdates::default();
    let deflate = output.deflate;
    // Keep one live request in flight while dispatching events and lease changes.
    let requests = stream::unfold(input, |mut input| async {
        input.recv().await.map(|frame| (frame, input))
    })
    .then(|frame| async move { execute(web, session, deflate, Lane::Live, &frame).await });
    let mut requests = std::pin::pin!(requests);
    let mut changes = web.presence_changed.subscribe();
    changes.mark_changed();
    let mut roster_staff = None;
    loop {
        let current = state.borrow_and_update().clone();
        let lease = match current {
            State::Authorized(lease) if lease.grant.expires_at > now() => lease,
            state => {
                let reason = if let State::Closed(reason) = state {
                    reason
                } else {
                    "authorization_expired"
                };
                output
                    .json(&json!({"type":"closed","reason":reason}))
                    .await?;
                return Ok(());
            }
        };
        publish_access(output, &lease, &mut published).await?;
        if changes.has_changed()? || roster_staff != Some(lease.staff) || presence.needs_publish() {
            changes.borrow_and_update();
            roster_staff = Some(lease.staff);
            presence.publish(web, session, &lease, output).await?;
        }
        if !session.state.borrow().permits_reply(&lease, now()) {
            continue;
        }
        let expiry = Instant::now()
            + Duration::from_millis((lease.grant.expires_at - now()).max(0).unsigned_abs());
        progress.flush(output, session, &lease, web).await?;
        let deadline = progress
            .state
            .lock()
            .await
            .deadline(Instant::now())
            .map_or(expiry, |at| at.min(expiry));
        tokio::select! {
            state = state.changed() => state?,
            () = sleep_until(deadline) => {}
            () = progress.changed.notified() => {}
            change = changes.changed() => {change?; changes.mark_changed();}
            reply = requests.next() => {
                let Some(reply) = reply else { return Ok(()) };
                if let Some((admitted, value)) = reply? {
                    write_live_reply(web, session, output, &admitted, value).await?;
                }
            }
            item = session.inbox.recv() => {
                deliver(web, session, &lease, progress, &mut presence, output, item?).await?;
            }
        }
    }
}

async fn deliver(
    web: &WebChat,
    session: &Registration<'_>,
    lease: &Arc<Lease>,
    progress: &Progress,
    presence: &mut PresenceUpdates,
    output: &Output,
    item: Item,
) -> Result<()> {
    let Some(game) = web
        .chat
        .game()
        .filter(|_| session.state.borrow().permits_reply(lease, now()))
    else {
        progress.missed().await;
        presence.missed();
        return Ok(());
    };
    let viewer = &lease.grant.player_uuid;
    let hidden = presence.hidden_for(&game, viewer);
    let permits = || permits_delivery(web, session, lease, &game, &hidden);
    progress.state.lock().await.retain_scope(&hidden, lease);
    match item {
        Item::Gaps(gaps) => {
            let mut channels = Vec::new();
            let mut scope_changed = false;
            {
                let mut state = progress.state.lock().await;
                for (channel, last) in gaps {
                    state.missed_through(channel, last.cursor());
                    if let Some(recipient) = last.for_viewer(viewer, lease.staff) {
                        if recipient.visible_in(&game) {
                            channels.push(channel);
                        } else {
                            scope_changed = true;
                        }
                    }
                }
            }
            if scope_changed {
                // A generic scope refresh covers the backlog without identifying a now-hidden peer's channel.
                presence.missed();
                return Ok(());
            }
            if !channels.is_empty() {
                if !permits() {
                    presence.missed();
                    return Ok(());
                }
                let frame = json!({"type":"historyChanged","channels":channels});
                guard_write(
                    output.json(&frame),
                    scope_ended(web, session, lease, &game, &hidden),
                )
                .await?;
            }
        }
        Item::Event(event) => {
            let Some(recipient) = event.for_viewer(viewer, lease.staff) else {
                return Ok(());
            };
            let Some(delivery) =
                recipient.delivery(&hidden, |person| web.chat.current_mention_player(person))?
            else {
                return Ok(());
            };
            let channel = delivery.channel.name();
            if let Some(record) = delivery.prepare(output.deflate, permits).await? {
                guard_write(
                    output.record(&record),
                    scope_ended(web, session, lease, &game, &hidden),
                )
                .await?;
                progress
                    .state
                    .lock()
                    .await
                    .delivered(channel, delivery.cursor);
            } else {
                progress
                    .state
                    .lock()
                    .await
                    .missed_through(channel, delivery.cursor);
                presence.missed();
            }
        }
    }
    Ok(())
}

async fn publish_access(
    output: &Output,
    lease: &Lease,
    published: &mut Option<(i64, bool)>,
) -> Result<()> {
    let grant = &lease.grant;
    let remaining = (grant.expires_at - now()).clamp(0, 60_000);
    let record = if let Some((end, staff)) = *published {
        if end == grant.expires_at && staff == lease.staff {
            return Ok(());
        }
        let mut renewal = json!({"type":"lease", "validForMs":remaining});
        if staff != lease.staff {
            renewal["staff"] = json!(lease.staff);
        }
        renewal
    } else {
        json!({"type":"authorized", "playerUuid":grant.player_uuid, "staff":lease.staff, "validForMs":remaining})
    };
    output.json(&record).await?;
    *published = Some((grant.expires_at, lease.staff));
    Ok(())
}
