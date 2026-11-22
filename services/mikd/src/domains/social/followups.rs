use crate::domains::ai::Conversation;
use std::{
    collections::HashMap,
    sync::Mutex,
    time::{Duration, Instant},
};
use uuid::Uuid;

const LIFETIME: Duration = Duration::from_secs(120);
const CAPACITY: usize = 512;

#[derive(Clone, Copy, PartialEq, Eq)]
pub(super) struct FollowUpKey(pub(super) Uuid);

/// The reviewed questions from a particular turn, in the order they are shown.
pub(super) struct QuestionOffer {
    generation: Uuid,
    questions: Vec<String>,
}

impl QuestionOffer {
    pub(super) fn new(generation: Uuid, questions: Vec<String>) -> Option<Self> {
        (!questions.is_empty()).then_some(Self {
            generation,
            questions,
        })
    }

    pub(super) fn questions(&self) -> &[String] {
        &self.questions
    }
}

#[derive(Default)]
pub(super) struct QuestionOffers {
    active: Mutex<HashMap<Conversation, Active>>,
}

struct Active {
    generation: Uuid,
    expires_at: Instant,
    choices: Option<Vec<(FollowUpKey, String)>>,
}

impl QuestionOffers {
    pub(super) fn begin(&self, scope: Conversation, now: Instant) -> Option<Uuid> {
        let mut active = self.active.lock().ok()?;
        active.retain(|_, entry| entry.expires_at > now);
        if active.len() >= CAPACITY && !active.contains_key(&scope) {
            return None;
        }
        let generation = Uuid::new_v4();
        active.insert(
            scope,
            Active {
                generation,
                expires_at: now + LIFETIME,
                choices: None,
            },
        );
        Some(generation)
    }

    pub(super) fn offer(
        &self,
        scope: &Conversation,
        offer: &QuestionOffer,
        keys: Vec<FollowUpKey>,
        now: Instant,
    ) -> bool {
        if keys.len() != offer.questions.len()
            || keys
                .iter()
                .enumerate()
                .any(|(i, key)| keys[..i].contains(key))
        {
            return false;
        }
        let Ok(mut active) = self.active.lock() else {
            return false;
        };
        let Some(entry) = active.get_mut(scope) else {
            return false;
        };
        if entry.expires_at <= now
            || entry.generation != offer.generation
            || entry.choices.is_some()
        {
            return false;
        }
        entry.choices = Some(
            keys.into_iter()
                .zip(offer.questions.iter().cloned())
                .collect(),
        );
        entry.expires_at = now + LIFETIME;
        true
    }

    pub(super) fn claim(
        &self,
        scope: &Conversation,
        key: &FollowUpKey,
        now: Instant,
    ) -> Option<String> {
        let mut active = self.active.lock().ok()?;
        let entry = active.get(scope)?;
        if entry.expires_at <= now {
            active.remove(scope);
            return None;
        }
        let index = entry
            .choices
            .as_ref()?
            .iter()
            .position(|(id, _)| id == key)?;
        // Consume the whole set: another click cannot submit a sibling from the same answer.
        active
            .remove(scope)?
            .choices?
            .into_iter()
            .nth(index)
            .map(|(_, question)| question)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn scope() -> Conversation {
        Conversation {
            platform: "qq",
            account: "assistant-app".into(),
            room: "redstone-group".into(),
            thread: None,
            sender: "member".into(),
        }
    }

    fn key() -> FollowUpKey {
        FollowUpKey(Uuid::new_v4())
    }

    #[test]
    fn only_the_original_actor_and_room_can_submit_the_exact_question_once() {
        let offers = QuestionOffers::default();
        let now = Instant::now();
        let player = scope();
        let question = "红石中继器能延迟多久？";
        let offer = QuestionOffer::new(
            offers.begin(player.clone(), now).unwrap(),
            vec!["红石中继器要放在哪？".into(), question.into()],
        )
        .unwrap();
        let sibling = key();
        let selected = key();
        assert!(offers.offer(&player, &offer, vec![sibling, selected], now));
        for outsider in [
            Conversation {
                sender: "other-member".into(),
                ..player.clone()
            },
            Conversation {
                room: "other-group".into(),
                ..player.clone()
            },
            Conversation {
                account: "other-app".into(),
                ..player.clone()
            },
        ] {
            assert_eq!(offers.claim(&outsider, &selected, now), None);
        }
        assert_eq!(offers.claim(&player, &key(), now), None);
        assert_eq!(
            offers.claim(&player, &selected, now).as_deref(),
            Some(question)
        );
        assert_eq!(offers.claim(&player, &selected, now), None);
        assert_eq!(offers.claim(&player, &sibling, now), None);
    }

    #[test]
    fn a_new_turn_invalidates_older_deliveries_and_expiration_blocks_clicks() {
        let offers = QuestionOffers::default();
        let now = Instant::now();
        let player = scope();
        let old = QuestionOffer::new(
            offers.begin(player.clone(), now).unwrap(),
            vec!["Old?".into()],
        )
        .unwrap();
        let stale = key();
        assert!(offers.offer(&player, &old, vec![stale], now));

        let current = QuestionOffer::new(
            offers.begin(player.clone(), now).unwrap(),
            vec!["New?".into()],
        )
        .unwrap();
        assert_eq!(offers.claim(&player, &stale, now), None);
        assert!(!offers.offer(&player, &old, vec![stale], now));
        let current_key = key();
        assert!(offers.offer(&player, &current, vec![current_key], now));
        assert_eq!(offers.claim(&player, &current_key, now + LIFETIME), None);
    }
}
