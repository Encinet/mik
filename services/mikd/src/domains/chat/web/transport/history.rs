use super::super::{
    Lane,
    session::{Registration, State, WebChat},
};
use super::{IO_TIMEOUT, Result, framing::Output, invalid, progress::Progress, request};
use crate::domains::chat::now;
use futures_util::{
    StreamExt,
    future::{AbortHandle, Abortable},
    stream::FuturesUnordered,
};
use std::collections::HashMap;
use tokio::{sync::mpsc, time::timeout};
use zeroize::Zeroizing;

pub(super) enum Action {
    Read(u32, Zeroizing<Vec<u8>>),
    Cancel(u32),
}

/// The connection owns up to two concurrent history exchanges.
pub(super) async fn serve(
    web: &WebChat,
    session: &Registration<'_>,
    writer: &Output,
    mut requests: mpsc::Receiver<Action>,
    progress: &Progress,
) -> Result<()> {
    let mut exchanges = FuturesUnordered::new();
    let mut pending = HashMap::<u32, AbortHandle>::new();
    loop {
        tokio::select! {
            biased;
            Some((id, result)) = exchanges.next() => {
                pending.remove(&id);
                if let Ok(result) = result {
                    result?;
                }
            }
            action = requests.recv() => match action {
                None => return Ok(()),
                Some(Action::Cancel(id)) => {
                    if let Some(handle) = pending.remove(&id) {
                        handle.abort();
                    }
                }
                Some(Action::Read(id, frame)) => {
                    let output = writer.history(id);
                    if pending.len() == 2 {
                        output.history_end(false).await?;
                        continue;
                    }
                    let (handle, registration) = AbortHandle::new_pair();
                    pending.insert(id, handle);
                    exchanges.push(async move {
                        let result = Abortable::new(
                            exchange(web, session, &output, id, progress, &frame),
                            registration,
                        ).await;
                        (id, result)
                    });
                }
            }
        }
    }
}

async fn exchange(
    web: &WebChat,
    session: &Registration<'_>,
    output: &Output,
    id: u32,
    progress: &Progress,
    frame: &[u8],
) -> Result<()> {
    let State::Authorized(admitted) = session.state.borrow().clone() else {
        return output.history_end(false).await;
    };
    let Some(game) = web.chat.game() else {
        return output.history_end(false).await;
    };
    let hidden = game.hidden_from(&admitted.grant.player_uuid);
    let mut gaps = progress.gaps.subscribe();
    let transfer = async {
        let Some((lease, replies)) =
            request::execute(web, session, output.deflate, Lane::History, frame).await?
        else {
            return Err(invalid());
        };
        let commits =
            request::write_history_replies(web, session, output, &lease, progress, replies).await?;
        output.history_end(true).await?;
        Ok::<_, super::Failure>(commits)
    };
    let commits = tokio::select! {
        biased;
        _ = gaps.changed() => None,
        () = super::scope::scope_ended(web, session, &admitted, &game, &hidden) => None,
        result = timeout(IO_TIMEOUT * 2, transfer) => result.ok().and_then(Result::ok),
    };
    let Some(commits) = commits else {
        return output.history_end(false).await;
    };
    let mut state = progress.state.lock().await;
    if let Some(hidden) = web.chat.hidden_from(&admitted.grant.player_uuid)
        && session.state.borrow().permits_reply(&admitted, now())
        && !gaps.has_changed().unwrap_or(true)
    {
        state.retain_scope(&hidden, &admitted);
        for page in commits {
            if page.hidden == hidden {
                state.commit(page, &admitted, u64::from(id));
            }
        }
        progress.changed.notify_one();
    }
    Ok(())
}
