use super::{
    BUILDING_ID, BUILDING_NAME, BUILDINGS, ELEVATOR_RULE, Eval, GameResult, INVENTORY,
    OTHER_INSTANCE, OWNER, PLOT_CONTEXT, PLOT_RULE, ToolFailure, VISITOR, WAIT, assert_excerpt,
    assert_failure, assert_reply, history_text, iron_blocks, lookup_failed, observed, paper_tool,
    reference,
};
use mik_protocol::wire::{
    AssistantAsk, AssistantSignal, AssistantToolLink, GameQueryEmpty, assistant_block::Content,
    assistant_command::Operation, assistant_failure::Reason, assistant_result::Outcome,
};
use serde_json::json;
use uuid::Uuid;

const SECOND_BUILDING: &str = "fedcba9876543210fedcba9876543210";

#[tokio::test]
async fn follow_up_can_confirm_a_displayed_reply_before_reading_history() {
    let mut eval = Eval::open().await;
    let question = "How do I craft sticks?";
    let answer = "Stack two planks vertically.";
    let turn = eval.ask(OWNER, question);
    eval.exchange(question).await.say(answer);
    let Some(Outcome::Reply(reply)) = eval.poll_done(&turn).await.outcome else {
        panic!("reply required")
    };
    assert_reply(&reply, answer);

    let follow_up = "How many sticks does that make?";
    let next = eval.submit(OWNER, follow_up, "en-US", Some(turn.id.clone()));
    let model = eval.exchange(follow_up).await;
    assert_eq!(
        model.input["previous_exchanges"],
        json!([{"question": question, "reply": answer}])
    );
    eval.delivered(&turn);
    model.say("Four sticks.");
    assert_reply(&eval.take_reply(&next).await, "Four sticks.");

    eval.reopen().await;
    let next = eval.submit(OWNER, follow_up, "en-US", Some(next.id));
    let model = eval.exchange(follow_up).await;
    assert!(
        model.input["previous_exchanges"]
            .as_array()
            .is_none_or(Vec::is_empty)
    );
    model.say("Two planks make four sticks.");
    assert_reply(
        &eval.take_reply(&next).await,
        "Two planks make four sticks.",
    );
    eval.close().await;
}

#[tokio::test]
async fn direct_replies_need_one_completion_and_keep_the_models_layout() {
    let mut eval = Eval::open().await;
    for (question, answer, visible) in [
        (
            "How do I craft sticks?",
            "Stack two planks vertically.",
            "Stack two planks vertically.",
        ),
        (
            "Can you help me build it?",
            "What would you like to build?",
            "What would you like to build?",
        ),
        (
            "Can you sketch a roof plan?",
            "[Roof plan]\nLeave one block of overhang.\n\n1. Lay out the ridge.\n2. Mirror the slope.",
            "[Roof plan] Leave one block of overhang.\n\n1. Lay out the ridge.\n2. Mirror the slope.",
        ),
    ] {
        let turn = eval.ask(OWNER, question);
        let model = eval.exchange(question).await;
        model.assert_no_tool_history();
        model.say(answer);
        assert_reply(&eval.take_reply(&turn).await, visible);
    }
    eval.close().await;
}

#[tokio::test]
async fn mixed_replies_preserve_text_evidence_and_plain_commands_in_order() {
    let mut eval = Eval::open().await;
    let question = "Can I create a plot to build on?";
    let turn = eval.ask(OWNER, question);
    let rules = eval.exchange(question).await.search("plots");
    let model = eval.exchange(question).await;
    assert_excerpt(&model.observation(&rules), "plots", PLOT_RULE);
    let plot = model.call(PLOT_CONTEXT, json!({}));
    eval.observe_game(
        &turn,
        &plot,
        observed(
            &json!({"standing":"outside","plot_name":null,"role_allows_plot_creation":true}),
            vec![],
        ),
    )
    .await;

    let model = eval.exchange(question).await;
    let plot = model.observation(&plot);
    assert_eq!(plot["data"]["standing"], "outside");
    assert!(plot["data"]["plot_name"].is_null());
    assert_eq!(plot["data"]["role_allows_plot_creation"], true);
    assert!(plot.get("references").is_none());
    model.say(&format!(
        "Your role can create a plot.\n\n> {PLOT_RULE} — <ref:r1>\n\nStart with `/plot`.",
    ));
    let reply = eval.take_reply(&turn).await;
    let [body, quote, command] = reply.blocks.as_slice() else {
        panic!("{reply:?}")
    };
    assert!(matches!(body.content, Some(Content::Paragraph(_))));
    assert!(matches!(quote.content, Some(Content::BlockQuote(_))));
    let Some(Content::Paragraph(command)) = &command.content else {
        panic!("{reply:?}")
    };
    assert!(command.parts.iter().all(|part| part.target.is_none()));
    let visible = history_text(&reply);
    assert!(visible.starts_with(&format!("Your role can create a plot.\n\n│ {PLOT_RULE} — ")));
    assert!(visible.ends_with("\n\nStart with /plot."));
    eval.close().await;
}

#[tokio::test]
async fn private_inventory_tools_can_support_material_planning() {
    let mut eval = Eval::open().await;
    let question = "For a six-block iron elevator, how many blocks am I missing?";
    let turn = eval.ask(OWNER, question);
    let rules = eval.exchange(question).await.search("elevator");
    let model = eval.exchange(question).await;
    assert_excerpt(&model.observation(&rules), "elevator", ELEVATOR_RULE);
    let inventory = model.call(INVENTORY, json!({}));
    let job = eval.game_call(&turn, &inventory).await;
    eval.game_reply(&turn, job, iron_blocks(2)).await;
    let model = eval.exchange(question).await;
    assert_eq!(
        model.observation(&inventory)["data"]["items"][0]["amount"],
        2
    );
    model.say("You are missing four iron blocks.");
    assert_reply(
        &eval.take_reply(&turn).await,
        "You are missing four iron blocks.",
    );
    eval.close().await;
}

#[tokio::test]
async fn game_tool_arguments_dont_change_the_envelope_identity_and_failures_are_observations() {
    let mut eval = Eval::open().await;
    let question = "How much iron am I carrying?";
    let turn = eval.ask(OWNER, question);
    let invalid = eval.exchange(question).await.call(
        INVENTORY,
        json!({"player_uuid": VISITOR.uuid, "turn_id": turn.id}),
    );
    eval.observe_game(
        &turn,
        &invalid,
        lookup_failed(ToolFailure::InvalidArguments, "no arguments expected"),
    )
    .await;
    let model = eval.exchange(question).await;
    assert_eq!(model.observation(&invalid)["status"], "invalid_request");
    let inventory = model.call(INVENTORY, json!({}));
    eval.observe_game(&turn, &inventory, GameResult::Failed(GameQueryEmpty {}))
        .await;
    let model = eval.exchange(question).await;
    let result = model.observation(&inventory);
    assert_eq!(result["status"], "unavailable");
    assert!(result.get("data").is_none());
    model.say("I can't read your inventory right now.");
    assert_reply(
        &eval.take_reply(&turn).await,
        "I can't read your inventory right now.",
    );
    eval.close().await;
}

#[tokio::test]
async fn paper_defines_new_tools_and_result_fields_per_turn() {
    const WEATHER: &str = "game_read_weather";
    let mut eval = Eval::open().await;
    let question = "What is the weather in my world?";
    let schema = json!({
        "type":"object", "properties":{"units":{"enum":["ticks","seconds"]}},
        "required":["units"], "additionalProperties":false
    });
    let tool = paper_tool(WEATHER, "Read the requester's weather", &schema);
    let duplicate = paper_tool(WEATHER, "Conflicting definition", &json!({"type":"object"}));
    let collision = paper_tool("search_knowledge", "Not a Paper name", &schema);
    let invalid = paper_tool(
        "game_bad_schema",
        "Invalid schema",
        &json!({"type":"string"}),
    );
    let turn = eval.submit_tools(
        OWNER,
        question,
        "en-US",
        None,
        vec![tool, duplicate, collision, invalid],
    );
    let model = eval.exchange(question).await;
    assert_eq!(
        model
            .tools()
            .into_iter()
            .filter(|name| name.starts_with("game_"))
            .collect::<Vec<_>>(),
        [WEATHER]
    );
    let definition = model.body["tools"]
        .as_array()
        .unwrap()
        .iter()
        .find(|definition| definition["function"]["name"] == WEATHER)
        .unwrap();
    assert_eq!(definition["function"]["parameters"], schema);
    assert_eq!(
        definition["function"]["description"],
        "Read the requester's weather"
    );

    let call = model.call(WEATHER, json!({"units":"seconds"}));
    let data = json!([{"precipitation": "snow", "expected_seconds": 36, "introduced_by_paper": [true, null]}]);
    eval.observe_game(
        &turn,
        &call,
        observed(
            &data,
            vec![
                AssistantToolLink {
                    label: "Private network".into(),
                    url: "https://127.0.0.1/".into(),
                },
                AssistantToolLink {
                    label: "Weather guide".into(),
                    url: "https://mcmik.top/weather".into(),
                },
            ],
        ),
    )
    .await;
    let model = eval.exchange(question).await;
    let result = model.observation(&call);
    assert_eq!(result["data"], data);
    assert_eq!(
        result["references"],
        json!([
            {"label":"Weather guide","url":"https://mcmik.top/weather","reference_id":"r1"}
        ])
    );
    model.say("Snow for about 36 seconds. <ref:r1>");
    assert_reply(
        &eval.take_reply(&turn).await,
        "Snow for about 36 seconds. Weather guide",
    );

    let turn = eval.submit_tools(OWNER, question, "en-US", None, vec![]);
    let model = eval.exchange(question).await;
    assert!(model.tools().iter().all(|name| !name.starts_with("game_")));
    model.say("I can't check the current weather.");
    assert_reply(
        &eval.take_reply(&turn).await,
        "I can't check the current weather.",
    );
    eval.close().await;
}

#[tokio::test]
async fn a_list_of_tool_results_is_a_reply_and_survives_delivery_for_followups() {
    let mut eval = Eval::open().await;
    let question = "What buildings are nearby?";
    let turn = eval.ask(OWNER, question);
    let lookup = eval.exchange(question).await.call(BUILDINGS, json!({}));
    eval.observe_game(
        &turn,
        &lookup,
        buildings(
            "",
            0,
            2,
            None,
            &[
                (BUILDING_ID, BUILDING_NAME, 28),
                (SECOND_BUILDING, BUILDING_NAME, 32),
            ],
        ),
    )
    .await;
    let model = eval.exchange(question).await;
    let result = model.observation(&lookup);
    model.say(&format!(
        "1. {} · 32 blocks away\n2. <ref:r99>\n3. {}",
        reference(&result["references"][1]["reference_id"]),
        reference(&result["references"][0]["reference_id"]),
    ));

    let visible = format!("1. {BUILDING_NAME} · 32 blocks away\n2. {BUILDING_NAME}");
    assert_reply(&eval.take_reply(&turn).await, &visible);
    eval.delivered(&turn);
    let follow_up = "How do I get to the first one?";
    let turn = eval.ask(OWNER, follow_up);
    let model = eval.exchange(follow_up).await;
    assert_eq!(
        model.input["previous_exchanges"],
        json!([{"question":question,"reply":visible,"references":[
            {"line":1, "label":BUILDING_NAME, "url":format!("https://mcmik.top/buildings/{SECOND_BUILDING}")},
            {"line":2, "label":BUILDING_NAME, "url":format!("https://mcmik.top/buildings/{BUILDING_ID}")}
        ]}])
    );
    let lookup = model.call(BUILDINGS, json!({"query":SECOND_BUILDING}));
    eval.observe_game(
        &turn,
        &lookup,
        buildings(
            SECOND_BUILDING,
            0,
            1,
            None,
            &[(SECOND_BUILDING, BUILDING_NAME, 130)],
        ),
    )
    .await;
    let model = eval.exchange(follow_up).await;
    let observed = model.observation(&lookup);
    assert_eq!(observed["data"]["buildings"][0]["distance_blocks"], 130);
    model.say(&format!(
        "<ref:{SECOND_BUILDING}>{}{} · 130 blocks away; no verified route.",
        reference(&result["references"][1]["reference_id"]),
        reference(&observed["references"][0]["reference_id"]),
    ));
    let reply = eval.take_reply(&turn).await;
    assert_reply(
        &reply,
        &format!("{BUILDING_NAME} · 130 blocks away; no verified route."),
    );
    assert_eq!(
        serde_json::to_value(super::paper_history(&reply)).unwrap()["references"][0]["url"],
        format!("https://mcmik.top/buildings/{SECOND_BUILDING}")
    );
    eval.close().await;
}

#[tokio::test]
async fn unavailable_catalog_zero_matches_and_empty_page_are_distinct() {
    let mut eval = Eval::open().await;
    let question = "Are there any observatories nearby?";
    for (found, offset, answer) in [
        (None, 0, "The building catalog is unavailable right now."),
        (Some(0), 0, "The catalog found no nearby observatories."),
        (Some(2), 8, "There are two matches, but this page is empty."),
    ] {
        let turn = eval.ask(OWNER, question);
        let search = eval
            .exchange(question)
            .await
            .call(BUILDINGS, json!({"query":"observatory","offset":offset}));
        let result = found.map_or_else(
            || {
                lookup_failed(
                    ToolFailure::Unavailable,
                    "the public building catalog is not currently verified",
                )
            },
            |total| buildings("observatory", offset, total, None, &[]),
        );
        eval.observe_game(&turn, &search, result).await;
        let model = eval.exchange(question).await;
        let observation = model.observation(&search);
        if let Some(total) = found {
            assert_eq!(observation["data"]["total_matches"], total);
            assert_eq!(observation["data"]["buildings"], json!([]));
        } else {
            assert_eq!(observation["status"], "unavailable");
            assert!(observation.get("data").is_none());
        }
        model.say(answer);
        assert_reply(&eval.take_reply(&turn).await, answer);
    }
    eval.close().await;
}

#[tokio::test]
async fn a_second_building_page_extends_evidence_without_changing_reply_shape() {
    let mut eval = Eval::open().await;
    let question = "Compare the two nearest public glasshouses.";
    let turn = eval.ask(OWNER, question);
    let first = eval
        .exchange(question)
        .await
        .call(BUILDINGS, json!({"query": "glasshouse", "limit": 1}));
    eval.observe_game(
        &turn,
        &first,
        buildings(
            "glasshouse",
            0,
            2,
            Some(1),
            &[(BUILDING_ID, BUILDING_NAME, 28)],
        ),
    )
    .await;
    let model = eval.exchange(question).await;
    let page = model.observation(&first);
    assert_eq!(page["data"]["total_matches"], 2);
    assert_eq!(page["data"]["world"], "world");
    let next = model.call(
        BUILDINGS,
        json!({
            "query": page["data"]["filter"], "offset": page["data"]["next_offset"], "limit": 1,
        }),
    );
    eval.observe_game(
        &turn,
        &next,
        buildings(
            "glasshouse",
            1,
            2,
            None,
            &[(SECOND_BUILDING, "Hilltop Glasshouse", 32)],
        ),
    )
    .await;
    let model = eval.exchange(question).await;
    let second_page = model.observation(&next);
    assert_eq!(second_page["data"]["offset"], 1);
    assert!(second_page["data"]["next_offset"].is_null());
    model.say(&format!(
        "| Building | Distance |\n| --- | ---: |\n| {} | 28 |\n| {} | 32 |",
        reference(&page["references"][0]["reference_id"]),
        reference(&second_page["references"][0]["reference_id"]),
    ));
    let reply = eval.take_reply(&turn).await;
    let [table] = reply.blocks.as_slice() else {
        panic!("{reply:?}")
    };
    assert!(matches!(&table.content, Some(Content::Table(table)) if table.rows.len() == 2));
    eval.close().await;
}

#[tokio::test]
async fn provider_failure_does_not_turn_intermediate_observations_into_an_answer() {
    let mut eval = Eval::open().await;
    let question = "Find guides about elevators and plots.";
    let turn = eval.ask(OWNER, question);
    let elevator = eval.exchange(question).await.search("elevator");
    let model = eval.exchange(question).await;
    assert_excerpt(&model.observation(&elevator), "elevator", ELEVATOR_RULE);
    drop(model);
    eval.expect_failure(&turn, Reason::GenerationFailed).await;
    eval.close().await;
}

#[tokio::test]
async fn provider_filter_cannot_deliver_a_well_formed_reply() {
    let mut eval = Eval::open().await;
    let question = "How can I decorate my shelter?";
    for content in ["Try lanterns.", "- **Try lanterns.**"] {
        let turn = eval.ask(OWNER, question);
        eval.exchange(question).await.respond(
            "content_filter",
            &json!({"role":"assistant", "content":content}),
        );
        assert_failure(&eval.poll_done(&turn).await, Reason::GenerationFailed);
    }
    eval.close().await;
}

#[tokio::test]
async fn output_limit_preserves_readable_parts_of_the_same_reply() {
    let mut eval = Eval::open().await;
    let question = "How can I decorate my shelter?";
    for (content, visible) in [
        (
            "Try lanterns.\nHang them from the ceiling.",
            "Try lanterns. Hang them from the ceiling.",
        ),
        ("- **Lanterns**\n- <ref:r99>", "• Lanterns"),
    ] {
        let turn = eval.ask(OWNER, question);
        eval.exchange(question)
            .await
            .respond("length", &json!({"role":"assistant", "content":content}));
        assert_reply(&eval.take_reply(&turn).await, visible);
    }

    eval.close().await;
}

#[tokio::test]
async fn interrupted_generation_does_not_execute_requested_tools() {
    let mut eval = Eval::open().await;
    let question = "How many iron blocks do I have?";
    for reason in ["content_filter", "length"] {
        let turn = eval.ask(OWNER, question);
        eval.exchange(question).await.respond(
            reason,
            &json!({"role":"assistant", "content":null, "tool_calls":[{
                "id":"call_inventory", "type":"function",
                "function":{"name":INVENTORY, "arguments":"{}"}
            }]}),
        );
        let next_game_job = async {
            let queue = eval.game.queries(false);
            let mut pending = None;
            loop {
                if let Some(job) = queue
                    .exchange(OWNER.instance, None, &mut pending, WAIT, true)
                    .await
                    .job
                {
                    break job;
                }
            }
        };
        tokio::select! {
            job = next_game_job => panic!("interrupted generation requested a game read: {job:?}"),
            () = eval.expect_failure(&turn, Reason::GenerationFailed) => {}
        }
    }
    eval.close().await;
}

#[tokio::test]
async fn cancelled_game_reply_cannot_cross_player_or_instance_scope_or_enter_history() {
    let mut eval = Eval::open().await;
    let question = "How many iron blocks am I carrying?";
    let turn = eval.ask(OWNER, question);
    let call = eval.exchange(question).await.call(INVENTORY, json!({}));
    let job = eval.game_call(&turn, &call).await;
    assert_eq!(
        eval.poll(&turn).outcome,
        Some(Outcome::Pending(AssistantSignal {}))
    );
    for wrong_scope in [VISITOR, OTHER_INSTANCE] {
        assert_failure(
            &eval.command(
                wrong_scope,
                &turn.id,
                Operation::PollResult(AssistantSignal {}),
            ),
            Reason::TurnUnavailable,
        );
        assert_failure(
            &eval.command(
                wrong_scope,
                &turn.id,
                Operation::ConfirmDelivery(AssistantSignal {}),
            ),
            Reason::TurnUnavailable,
        );
    }
    eval.discard(&turn);
    assert_failure(&eval.poll(&turn), Reason::TurnUnavailable);
    eval.game_reply(&turn, job, iron_blocks(42)).await;
    let next_question = "What fuel can I use?";
    let next = eval.ask(OWNER, next_question);
    let model = eval.exchange(next_question).await;
    assert_eq!(model.questions(), [next_question]);
    model.assert_no_tool_history();
    model.say("Coal works in a furnace.");
    assert_reply(&eval.take_reply(&next).await, "Coal works in a furnace.");
    eval.close().await;
}

#[tokio::test]
async fn a_turn_waiting_on_tools_keeps_its_capacity_until_the_reply_is_ready() {
    let mut eval = Eval::open_with(Some(1), None).await;
    let question = "How much iron do I have?";
    let first = eval.ask(OWNER, question);
    let inventory = eval.exchange(question).await.call(INVENTORY, json!({}));
    let job = eval.game_call(&first, &inventory).await;
    let other_question = "A different player's question";
    assert_failure(
        &eval.command(
            VISITOR,
            &Uuid::new_v4().to_string(),
            Operation::Ask(AssistantAsk {
                prompt: other_question.into(),
                ..Default::default()
            }),
        ),
        Reason::Busy,
    );
    eval.game_reply(&first, job, iron_blocks(2)).await;
    let model = eval.exchange(question).await;
    assert_eq!(
        model.observation(&inventory)["data"]["items"][0]["amount"],
        2
    );
    model.say("Two iron blocks.");
    let Some(Outcome::Reply(reply)) = eval.poll_done(&first).await.outcome else {
        panic!("reply required")
    };
    assert_reply(&reply, "Two iron blocks.");

    let second = eval.ask(VISITOR, other_question);
    eval.exchange(other_question).await.say("The other answer.");
    assert_reply(&eval.take_reply(&second).await, "The other answer.");
    eval.delivered(&first);
    eval.close().await;
}

#[tokio::test]
async fn missing_delivery_does_not_block_a_new_turn_or_insert_late_history() {
    let mut eval = Eval::open().await;
    let first = eval.ask(OWNER, "First question");
    eval.exchange("First question").await.say("First answer.");
    assert!(matches!(
        eval.poll_done(&first).await.outcome,
        Some(Outcome::Reply(_))
    ));

    let second = eval.ask(OWNER, "Second question");
    let model = eval.exchange("Second question").await;
    assert_eq!(model.questions(), ["Second question"]);
    model.say("Second answer.");
    assert_reply(&eval.take_reply(&second).await, "Second answer.");
    eval.delivered(&first);

    let third = eval.ask(OWNER, "Third question");
    let model = eval.exchange("Third question").await;
    assert_eq!(
        model.input["previous_exchanges"],
        json!([{"question": "Second question", "reply": "Second answer."}])
    );
    model.say("Third answer.");
    assert_reply(&eval.take_reply(&third).await, "Third answer.");
    eval.close().await;
}

fn buildings(
    filter: &str,
    offset: u32,
    total_matches: u32,
    next_offset: Option<u32>,
    entries: &[(&str, &str, u32)],
) -> GameResult {
    let data = entries
        .iter()
        .map(|(id, name, distance)| json!({"id":id,"name":name,"distance_blocks":distance}))
        .collect::<Vec<_>>();
    let links = entries
        .iter()
        .map(|(id, name, _)| AssistantToolLink {
            label: (*name).into(),
            url: format!("https://mcmik.top/buildings/{id}"),
        })
        .collect();
    observed(
        &json!({
            "world":"world","filter":filter,"offset":offset,"total_matches":total_matches,
            "next_offset":next_offset,"buildings":data
        }),
        links,
    )
}
