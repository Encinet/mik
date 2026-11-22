mod delivery;
pub(super) mod matrix;
pub(super) mod qq;

use super::commands::Input;
pub(super) use delivery::{RenderedAi, ReplyDelivery};
use futures_util::{StreamExt, stream};
use tokio::sync::mpsc;

pub(super) async fn dispatch<Raw, Event, Task: Future<Output = ()>>(
    mut incoming: mpsc::Receiver<Raw>,
    mut prepare: impl FnMut(Raw) -> Option<(Input, Event)>,
    handle: impl Fn(Input, Event) -> Task,
) {
    let (chat, posts) = mpsc::channel(64);
    let (command, commands) = mpsc::channel(8);
    let (assistant, questions) = mpsc::channel(8);
    tokio::join!(
        async move {
            while let Some(raw) = incoming.recv().await {
                let Some((input, event)) = prepare(raw) else {
                    continue;
                };
                let (queue, lane) = match input {
                    Input::Chat => (&chat, "chat input"),
                    Input::Command(ref command) if command.is_ai() => (&assistant, "AI commands"),
                    Input::Command(_) => (&command, "commands"),
                };
                if queue.try_send((input, event)).is_err() {
                    eprintln!("mikd social {lane} is backpressured");
                }
            }
        },
        consume(posts, 16, |(input, event)| handle(input, event)),
        consume(commands, 4, |(input, event)| handle(input, event)),
        consume(questions, 4, |(input, event)| handle(input, event)),
    );
}

async fn consume<Event, Task: Future<Output = ()>>(
    input: mpsc::Receiver<Event>,
    limit: usize,
    handle: impl Fn(Event) -> Task,
) {
    stream::unfold(input, |mut queue| async {
        queue.recv().await.map(|message| (message, queue))
    })
    .for_each_concurrent(limit, handle)
    .await;
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::domains::social::{
        commands,
        config::{CommandPrefix, Conversation},
    };
    use std::{future::pending, time::Duration};

    #[tokio::test]
    async fn stalled_assistant_requests_do_not_hold_up_chat_or_commands() {
        let route = Conversation {
            commands: true,
            ai: true,
            ..Default::default()
        };
        let (input, messages) = mpsc::channel(64);
        let (processed, mut posts) = mpsc::channel(2);
        for _ in 0..32 {
            input.try_send("/ai prompt").unwrap();
        }
        input.try_send("ordinary message").unwrap();
        input.try_send("/help").unwrap();
        tokio::select! {
            received = tokio::time::timeout(Duration::from_secs(3), async {
                [posts.recv().await.unwrap(), posts.recv().await.unwrap()]
            }) => {
                let mut received = received.unwrap();
                received.sort_unstable();
                assert_eq!(received, ["/help", "ordinary message"]);
            }
            () = dispatch(
                messages,
                |text| Some((commands::classify(text, true, &route, CommandPrefix::Slash)?, text)),
                |input, text| {
                    let processed = &processed;
                    async move {
                        match input {
                            Input::Command(command) if command.is_ai() => pending().await,
                            _ => processed.try_send(text).unwrap(),
                        }
                    }
                },
            ) => panic!("input dispatch stopped"),
        }
    }
}
