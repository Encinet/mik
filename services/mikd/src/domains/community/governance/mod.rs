mod delivery;
mod petitions;
mod players;
mod rules;
mod tenures;
#[cfg(test)]
mod tests;
mod votes;

use super::{CommunityStore, Error, store::now_ms};
use mik_protocol::wire::{
    GovernanceActionRequest, GovernanceCommand, GovernanceQuery, GovernanceResult,
    governance_action_request::Action, governance_command::Operation, governance_query::View,
    governance_result::Outcome, governance_vote_view::Termination,
};
use tokio_rusqlite::rusqlite::{Connection, TransactionBehavior, params};
use uuid::Uuid;

type DbResult<T> = Result<T, Error>;

impl CommunityStore {
    pub(crate) async fn community_role(&self, player: String) -> DbResult<&'static str> {
        self.call(move |db| {
            Ok(db.query_row("SELECT CASE
                  WHEN EXISTS(SELECT 1 FROM gov_tenures WHERE player_id=?1 AND ended_ms IS NULL) THEN 'moderator'
                  WHEN EXISTS(SELECT 1 FROM gov_players WHERE player_id=?1 AND member_since_ms IS NOT NULL) THEN 'member'
                  ELSE 'default' END", [&player], |row| Ok(match row.get_ref(0)?.as_str()? {
                      "moderator" => "moderator", "member" => "member", _ => "default",
                  }))?)
        }).await
    }

    pub(crate) async fn governance(
        &self,
        command: GovernanceCommand,
    ) -> DbResult<GovernanceResult> {
        let operation = command
            .operation
            .ok_or(Error::Invalid("governance operation required"))?;
        self.call(move |db| {
            let now = now_ms()?;
            if let Operation::Query(request) = operation {
                return query(db, &request, now);
            }
            if let Operation::PollDeliveries(request) = operation {
                return Ok(GovernanceResult {
                    grants: delivery::pending(db, &request.deferred_players)?,
                    votes: votes::list(db, votes::Selection::Announcements, "", 0, "", 32, 0)?,
                    ..GovernanceResult::default()
                });
            }
            let transaction = db.transaction_with_behavior(TransactionBehavior::Immediate)?;
            let mut result = GovernanceResult::default();
            match operation {
                Operation::Observe(batch) => {
                    if batch.players.len() > 256 {
                        return Err(Error::Invalid("too many observations"));
                    }
                    if let Some(snapshot) = batch.ban_snapshot {
                        players::observe_bans(&transaction, snapshot, now)?;
                    }
                    for observation in batch.players {
                        players::observe(&transaction, observation, now)?;
                    }
                }
                Operation::Act(action) => result = act(&transaction, &action, now)?,
                Operation::Confirm(receipt) => {
                    result.grants = delivery::confirm(&transaction, receipt, now)?;
                }
                Operation::ReservePasswordRemoval(intent) => {
                    votes::settle(&transaction, now)?;
                    result.outcome =
                        players::password_removal(&transaction, &intent, false)?.into();
                }
                Operation::FinalizePasswordRemoval(intent) => {
                    result.outcome = players::password_removal(&transaction, &intent, true)?.into();
                }
                Operation::Query(_) | Operation::PollDeliveries(_) => unreachable!(),
            }
            transaction.commit()?;
            Ok(result)
        })
        .await
    }

    pub(crate) async fn advance_governance(&self) -> DbResult<()> {
        self.call(|db| {
            let now = now_ms()?;
            let transaction = db.transaction_with_behavior(TransactionBehavior::Immediate)?;
            votes::settle(&transaction, now)?;
            tenures::review(&transaction, now)?;
            transaction.commit()?;
            Ok(())
        })
        .await
    }
}

fn canonical(id: &str) -> DbResult<String> {
    Uuid::parse_str(id)
        .map(|id| id.to_string())
        .map_err(|_| Error::Invalid("invalid governance identity"))
}

fn optional_player(id: &str) -> DbResult<String> {
    if id.is_empty() {
        Ok(String::new())
    } else {
        canonical(id)
    }
}

fn query(db: &Connection, request: &GovernanceQuery, now: i64) -> DbResult<GovernanceResult> {
    let mut response = GovernanceResult::default();
    let limit = if request.limit == 0 {
        32
    } else {
        request.limit.min(100)
    };
    let viewer = optional_player(&request.viewer_id)?;
    let subject = optional_player(&request.player_id)?;
    let view =
        View::try_from(request.view).map_err(|_| Error::Invalid("invalid governance view"))?;
    if (view == View::FindPlayers
        || (view == View::Profile && !viewer.is_empty() && viewer != subject))
        && !request.local_operator
        && !can_moderate(db, &viewer, now)?
    {
        response.outcome = Outcome::NotAuthorized.into();
        return Ok(response);
    }
    match view {
        View::Profile => {
            response.profile = players::profile(db, &canonical(&subject)?, now)?;
            if response.profile.is_none() {
                response.outcome = Outcome::UnknownPlayer.into();
            }
        }
        View::Roster => response.staff = players::roster(db)?,
        View::Matters => {
            response.votes = votes::list(
                db,
                votes::Selection::Open,
                &subject,
                0,
                &viewer,
                limit,
                request.offset,
            )?;
            response.petitions =
                petitions::list_views(db, &subject, &viewer, request.item_id, now, limit)?;
            if !viewer.is_empty() {
                response.profile = players::profile(db, &viewer, now)?;
            }
        }
        View::Vote => {
            response.votes = votes::list(
                db,
                votes::Selection::Vote,
                "",
                request.item_id,
                &viewer,
                1,
                0,
            )?;
            if response.votes.is_empty() {
                response.outcome = Outcome::VoteUnknown.into();
            }
        }
        View::History => {
            response.votes = votes::list(
                db,
                votes::Selection::Finished,
                &subject,
                0,
                &viewer,
                limit,
                request.offset,
            )?;
            response.total = db.query_row(
                "SELECT count(*) FROM gov_votes WHERE status<>1 AND (?1='' OR player_id=?1)",
                [&subject],
                |row| row.get(0),
            )?;
        }
        View::GrantTargets => response.grants = delivery::targets(db, &canonical(&subject)?)?,
        View::FindPlayers => response.players = players::search(db, &request.player_search, limit)?,
        View::Unspecified => return Err(Error::Invalid("governance view required")),
    }
    Ok(response)
}

fn can_moderate(db: &Connection, actor: &str, now: i64) -> DbResult<bool> {
    Ok(players::profile(db, actor, now)?.is_some_and(|profile| profile.governance_authorized))
}

fn act(db: &Connection, request: &GovernanceActionRequest, now: i64) -> DbResult<GovernanceResult> {
    let action = Action::try_from(request.action)
        .map_err(|_| Error::Invalid("invalid governance action"))?;
    let actor = if request.local_operator && request.actor_id.is_empty() {
        String::new()
    } else {
        canonical(&request.actor_id)?
    };
    let mut response = GovernanceResult::default();
    votes::settle(db, now)?;
    let outcome = match action {
        Action::Apply if request.local_operator => Outcome::CandidateIneligible,
        Action::Apply => {
            if let Some(credential) = request.credential.as_ref() {
                players::update_credential(db, &actor, credential)?;
            }
            match votes::apply(db, &actor, now)? {
                Ok(id) => {
                    response.votes = votes::list(db, votes::Selection::Vote, "", id, &actor, 1, 0)?;
                    Outcome::Done
                }
                Err(outcome) => outcome,
            }
        }
        Action::Withdraw => votes::withdraw(db, &actor, request.item_id, now)?,
        Action::Vote => {
            let outcome = votes::cast(db, request.item_id, &actor, request.ballot, now)?;
            if outcome == Outcome::Done {
                response.votes = votes::list(
                    db,
                    votes::Selection::Vote,
                    "",
                    request.item_id,
                    &actor,
                    1,
                    0,
                )?;
            }
            outcome
        }
        Action::Sponsor => match petitions::sponsor(
            db,
            &canonical(&request.player_id)?,
            &actor,
            request.item_id,
            now,
        )? {
            Ok(petition) => {
                if let Some(vote) = petition.vote_id {
                    response.votes =
                        votes::list(db, votes::Selection::Vote, "", vote, &actor, 1, 0)?;
                }
                response.petitions.push(petition);
                Outcome::Done
            }
            Err(outcome) => outcome,
        },
        Action::Resign => {
            if let Some(tenure) = tenures::current(db, &actor)? {
                tenures::end(
                    db,
                    tenure,
                    &actor,
                    "resignation",
                    Some(Termination::ModeratorResigned),
                    now,
                    None,
                )?;
                Outcome::Done
            } else {
                Outcome::RemovalTargetNotModerator
            }
        }
        Action::PausePromotion | Action::ResumePromotion => {
            if request.local_operator || can_moderate(db, &actor, now)? {
                update_promotion(db, &actor, request, action, &mut response, now)?
            } else {
                Outcome::NotAuthorized
            }
        }
        Action::RegisterMaintainer | Action::RetireMaintainer => {
            if !request.local_operator || !actor.is_empty() {
                Outcome::NotAuthorized
            } else {
                update_maintainer(db, request, action, now)?
            }
        }
        Action::Unspecified => return Err(Error::Invalid("governance action required")),
    };
    response.outcome = outcome.into();
    Ok(response)
}

fn update_maintainer(
    db: &Connection,
    request: &GovernanceActionRequest,
    action: Action,
    now: i64,
) -> DbResult<Outcome> {
    let player = canonical(&request.player_id)?;
    if action == Action::RetireMaintainer {
        db.execute(
            "UPDATE gov_maintainers SET ended_ms=?2 WHERE player_id=?1 AND ended_ms IS NULL",
            params![player, now],
        )?;
        return Ok(Outcome::Done);
    }
    let responsibility = request.reason.trim();
    let name = request.player_name.trim();
    if responsibility.is_empty()
        || responsibility.len() > 1024
        || name.is_empty()
        || name.len() > 64
    {
        return Err(Error::Invalid(
            "maintainer name and public responsibility required",
        ));
    }
    db.execute(
        "INSERT INTO gov_players(player_id,display_name) VALUES (?1,?2) ON CONFLICT DO NOTHING",
        params![player, name],
    )?;
    db.execute("INSERT INTO gov_maintainers(player_id,responsibility,source,started_ms) VALUES (?1,?2,'console',?3)
                ON CONFLICT(player_id) WHERE ended_ms IS NULL DO UPDATE SET responsibility=excluded.responsibility",
        params![player, responsibility, now])?;
    Ok(Outcome::Done)
}

fn update_promotion(
    db: &Connection,
    actor: &str,
    request: &GovernanceActionRequest,
    action: Action,
    response: &mut GovernanceResult,
    now: i64,
) -> DbResult<Outcome> {
    let subject = canonical(&request.player_id)?;
    let Some(profile) = players::profile(db, &subject, now)? else {
        return Ok(Outcome::UnknownPlayer);
    };
    let actor = if actor.is_empty() { "console" } else { actor };
    let reason = request.reason.trim();
    if reason.is_empty() || reason.len() > 1024 {
        return Err(Error::Invalid("invalid promotion reason"));
    }
    if action == Action::ResumePromotion {
        if let Some(pause) = profile.pause {
            db.execute("UPDATE gov_promotion_pauses SET lifted_ms=?2,lifted_by=?3,lift_reason=?4 WHERE id=?1", params![pause.id, now, actor, reason])?;
            response.pause = Some(pause);
        }
        return Ok(Outcome::Done);
    }
    if request.pause_until_ms <= now {
        return Ok(Outcome::PauseEndInvalid);
    }
    if profile.member_since_ms.is_some() {
        return Ok(Outcome::PauseAlreadyMember);
    }
    if profile.pause.is_some() {
        return Ok(Outcome::PauseAlreadyActive);
    }
    let appeal = request.appeal.trim();
    if appeal.is_empty() || appeal.len() > 1024 {
        return Err(Error::Invalid("invalid promotion appeal"));
    }
    db.execute("INSERT INTO gov_promotion_pauses(player_id,created_ms,ends_ms,actor,reason,appeal) VALUES (?1,?2,?3,?4,?5,?6)",
        params![subject, now, request.pause_until_ms, actor, reason, appeal])?;
    response.pause = players::active_pause(db, &subject, now)?;
    Ok(Outcome::Done)
}
