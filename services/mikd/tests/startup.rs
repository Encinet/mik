#[cfg(test)]
mod tests {
    use mik_protocol::{
        CAPABILITIES, VERSION, read_frame,
        wire::{
            ChatGameRoster, ConnectionRole, Envelope, GameRoster, GameStatusView, Hello,
            PaperStateUpdate, PlayerSnapshot, Request, Status, StatusRequest, envelope::Body,
            request::Operation, response::Result as Reply, status::DomainProblem,
        },
        write_frame,
    };
    use std::{
        fs,
        io::{BufRead, BufReader, Write},
        net::{Ipv4Addr, TcpListener},
        os::unix::{
            fs::{MetadataExt, PermissionsExt},
            net::UnixStream,
        },
        path::Path,
        process::{Child, Command, Stdio},
        thread,
        time::{Duration, Instant},
    };
    use uuid::Uuid;

    const SOCKET: &str = "mikd.sock";
    const WAIT: Duration = Duration::from_secs(10);
    const QQ_GROUPS: &str = "qq:\n  app_id: test-app\n  groups:";
    const MATRIX_ROOM: &str = "matrix:\n  homeserver: https://matrix.example.org\n  user: '@bot:example.org'\n  rooms:\n    '!room:example.org':";

    fn private_directory() -> tempfile::TempDir {
        tempfile::Builder::new()
            .permissions(fs::Permissions::from_mode(0o700))
            .tempdir()
            .unwrap()
    }

    fn command(root: &Path, action: &str) -> Command {
        let mut command = Command::new(env!("CARGO_BIN_EXE_mikd"));
        command
            .env_clear()
            .arg(action)
            .arg("--instance")
            .arg(root.join(SOCKET))
            .arg(fs::metadata(root).unwrap().uid().to_string())
            .arg(root)
            .env("TOKIO_WORKER_THREADS", "2");
        command
    }

    fn http() -> reqwest::Client {
        // Reqwest is built with rustls-no-provider, even for loopback HTTP fixtures.
        drop(rustls::crypto::ring::default_provider().install_default());
        reqwest::Client::builder()
            .no_proxy()
            .timeout(Duration::from_secs(2))
            .build()
            .unwrap()
    }

    fn assert_core_ready(status: &Status) {
        for domain in ["identity", "chat", "governance"] {
            assert!(
                status.ready_domains.iter().any(|ready| ready == domain),
                "{domain} unavailable: {status:?}"
            );
        }
    }

    struct Daemon(Child);

    impl Daemon {
        fn start(root: &Path, command: &mut Command) -> Self {
            Self(
                command
                    .stdout(Stdio::null())
                    .stderr(fs::File::create(root.join("daemon.log")).unwrap())
                    .spawn()
                    .unwrap(),
            )
        }

        fn connect(&mut self, root: &Path, role: ConnectionRole) -> (UnixStream, String) {
            let deadline = Instant::now() + WAIT;
            let socket = root.join(SOCKET);
            let mut stream = loop {
                if let Ok(stream) = UnixStream::connect(&socket) {
                    break stream;
                }
                let exited = self.0.try_wait().unwrap();
                assert!(
                    exited.is_none() && Instant::now() < deadline,
                    "mikd did not start: {:?}; {}",
                    exited,
                    fs::read_to_string(root.join("daemon.log")).unwrap()
                );
                thread::sleep(Duration::from_millis(25));
            };
            stream.set_read_timeout(Some(WAIT)).unwrap();
            stream.set_write_timeout(Some(WAIT)).unwrap();
            write_frame(
                &mut stream,
                &Envelope {
                    body: Some(Body::Hello(Hello {
                        protocol_version: VERSION,
                        role: role.into(),
                        game_instance: "startup-test".into(),
                        startup_id: Uuid::new_v4().to_string(),
                        connection_id: Uuid::new_v4().to_string(),
                        capabilities: CAPABILITIES.iter().map(|value| (*value).into()).collect(),
                        console: None,
                    })),
                    ..Default::default()
                },
            )
            .unwrap();
            let welcome = read_frame(&mut stream).unwrap();
            assert!(matches!(welcome.body, Some(Body::Welcome(_))));
            (stream, welcome.session_id)
        }

        fn status(&mut self, root: &Path) -> Status {
            let (mut stream, session) = self.connect(root, ConnectionRole::Control);
            let Reply::Status(status) = request(
                &mut stream,
                &session,
                1,
                Operation::Status(StatusRequest {}),
            ) else {
                panic!("status result required");
            };
            status
        }
    }

    fn request(stream: &mut UnixStream, session: &str, id: u64, operation: Operation) -> Reply {
        write_frame(
            stream,
            &Envelope {
                request_id: id,
                session_id: session.into(),
                body: Some(Body::Request(Request {
                    timeout_ms: 1_000,
                    operation: Some(operation),
                })),
            },
        )
        .unwrap();
        let Some(Body::Response(response)) = read_frame(stream).unwrap().body else {
            panic!("response required");
        };
        response.result.expect("response result required")
    }

    impl Drop for Daemon {
        fn drop(&mut self) {
            drop(self.0.kill());
            drop(self.0.wait());
        }
    }

    fn admin(root: &Path, args: &[&str]) -> std::process::Output {
        Command::new(env!("CARGO_BIN_EXE_mikd"))
            .env_clear()
            .args(["admin", "--socket"])
            .arg(root.join("mikd.admin.sock"))
            .args(args)
            .output()
            .unwrap()
    }

    fn admin_json(root: &Path, args: &[&str]) -> serde_json::Value {
        let output = admin(root, args);
        assert!(
            output.status.success(),
            "{}",
            String::from_utf8_lossy(&output.stderr)
        );
        serde_json::from_slice(&output.stdout).unwrap()
    }

    #[test]
    fn local_admin_reports_live_minecraft_status_until_the_game_disconnects() {
        let directory = private_directory();
        let root = directory.path();
        let mut daemon = Daemon::start(root, &mut command(root, "serve"));
        let (mut stream, session) = daemon.connect(root, ConnectionRole::PaperState);
        let status = || admin_json(root, &["status"])["data"]["minecraft"].clone();
        let unavailable = serde_json::json!({"state": "no_recent_sample"});
        assert_eq!(status(), unavailable);
        let server = GameStatusView {
            version: "1.21.11".into(),
            max_players: 60,
            tps: 19.98,
            mspt: 18.4,
            uptime_seconds: 90_600,
        };
        assert!(matches!(
            request(
                &mut stream,
                &session,
                1,
                Operation::PaperState(PaperStateUpdate {
                    version: 1,
                    server: Some(server.clone()),
                    public_roster: Some(GameRoster {
                        players: vec![PlayerSnapshot {
                            player_id: Uuid::new_v4().to_string(),
                            name: "TestPlayer".into(),
                            ..Default::default()
                        }],
                    }),
                    chat_roster: Some(ChatGameRoster::default()),
                    ..Default::default()
                }),
            ),
            Reply::PaperState(accepted) if accepted.version == 1
        ));
        assert_eq!(
            status(),
            serde_json::json!({
                "state": "live",
                "version": "1.21.11",
                "visible_players": 1,
                "max_players": 60,
                "tps": 19.98,
                "mspt": 18.4,
                "uptime_seconds": 90_600,
            })
        );
        assert!(matches!(
            request(
                &mut stream,
                &session,
                2,
                Operation::PaperState(PaperStateUpdate {
                    version: 2,
                    server: Some(GameStatusView {
                        tps: 0.0,
                        mspt: f64::NAN,
                        ..server
                    }),
                    ..Default::default()
                }),
            ),
            Reply::PaperState(accepted) if accepted.version == 2
        ));
        let updated = status();
        assert_eq!(updated["tps"], 0.0);
        assert!(updated.get("mspt").is_some_and(serde_json::Value::is_null));
        assert_eq!(updated["visible_players"], 1);

        drop(stream);
        let deadline = Instant::now() + Duration::from_secs(2);
        while status() != unavailable {
            assert!(
                Instant::now() < deadline,
                "disconnected sample remained live"
            );
            thread::sleep(Duration::from_millis(25));
        }
    }

    #[test]
    fn local_admin_manages_subscriptions_without_a_paper_session() {
        let directory = private_directory();
        let root = directory.path();
        let imports = root.join("music/sources/imports");
        fs::create_dir_all(&imports).unwrap();
        let script = imports.join("demo.js");
        fs::write(
            &script,
            r"
            function logInfo(...args) { console.info(...args); }
            logInfo('initializing LX source', { ready: false });
            window.console.debug('debug');
            console.log('log');
            console.warn('warn');
            console.error('error');
            console.trace('trace');
            const { on, send, EVENT_NAMES, utils: { buffer } } = lx;
            const bytes = Uint8Array.of(251, 255);
            if (buffer.bufToString(bytes, 'base64url') !== '-_8') {
                throw new Error('invalid Base64URL encoding');
            }
            for (const encoded of ['-_8', '+/8=', ' +/8=\n']) {
                if (buffer.bufToString(buffer.from(encoded, 'base64url'), 'hex') !== 'fbff') {
                    throw new Error('invalid Base64URL decoding');
                }
            }
            const recovered = Promise.reject(new Error('recoverable startup error'));
            Promise.resolve().then(() => recovered.catch(() => {}));
            on(EVENT_NAMES.request, () => 'https://example.invalid/audio');
            setTimeout(() => send(EVENT_NAMES.inited, {
                status: true, name: 'Demo',
                sources: { wy: { type: 'music', actions: ['musicUrl'], qualitys: ['128k'] } },
            }), 5);
        ",
        )
        .unwrap();
        let _daemon = Daemon::start(root, &mut command(root, "serve"));
        wait_for_music_sources(root);
        let socket = root.join("mikd.admin.sock");
        let mode = fs::metadata(socket).unwrap().permissions().mode() & 0o777;
        assert_eq!(mode, 0o600);
        let url = url::Url::from_file_path(&script).unwrap();
        let import = admin_json(root, &["music", "sources", "import", url.as_str()]);
        assert_eq!(import["data"]["added"], true);
        let id = import["data"]["id"].as_str().unwrap();
        assert!(id.starts_with("remote-"));
        let listed = admin(root, &["music", "sources", "list"]);
        assert!(listed.status.success());
        assert!(String::from_utf8_lossy(&listed.stdout).contains(id));
        let info = admin_json(root, &["music", "sources", "inspect", id]);
        assert_eq!(info["data"]["channels"], serde_json::json!(["wy"]));
        assert_eq!(info["data"]["available"], true);
        fs::write(
            script,
            r"
            lx.on(lx.EVENT_NAMES.request, () => null);
            setTimeout(() => Promise.reject(new Error(
                'Music service denied https://example.invalid/private?token=fixture-url-secret; api_key=fixture-api-secret'
            )), 5);
        ",
        )
        .unwrap();
        let refresh = admin(root, &["music", "sources", "refresh"]);
        assert!(!refresh.status.success());
        let summary: serde_json::Value = serde_json::from_slice(&refresh.stdout).unwrap();
        assert_eq!(summary["data"]["failed"], 1);
        let info = admin_json(root, &["music", "sources", "inspect", id]);
        assert_eq!(info["data"]["available"], true);
        let error = info["data"]["last_error"].as_str().unwrap();
        assert!(
            error.starts_with("LX initialization failed: Music service denied"),
            "{error}"
        );
        assert!(
            !error.contains("fixture-url-secret") && !error.contains("fixture-api-secret"),
            "{error}"
        );
        assert_eq!(
            admin_json(root, &["music", "sources", "remove", id])["data"]["id"],
            id
        );
        assert!(
            !admin(root, &["music", "sources", "inspect", id])
                .status
                .success()
        );
    }

    #[test]
    fn local_admin_tests_only_the_selected_source_and_leaves_the_cache_empty() {
        let directory = private_directory();
        let root = directory.path();
        let local = root.join("music/sources/local");
        fs::create_dir_all(&local).unwrap();
        let http = TcpListener::bind((Ipv4Addr::LOCALHOST, 0)).unwrap();
        let address = http.local_addr().unwrap();
        let fixture = r"
            const { on, send, EVENT_NAMES } = lx;
            on(EVENT_NAMES.request, ({ action, info }) => {
                if (action === 'musicSearch') return [{ name: 'Probe Track', songmid: 'example' }];
                if (info.musicInfo.meta.songId !== 'example' || info.type !== '128k') {
                    throw new Error('missing track or quality');
                }
                __RESULT__
            });
            send(EVENT_NAMES.inited, {
                sources: { demo: { type: 'music', actions: ['musicUrl', 'musicSearch'], qualitys: ['128k'] } },
            });
        ";
        fs::write(
            local.join("good.js"),
            fixture.replace("__RESULT__", &format!("return 'http://{address}/audio';")),
        )
        .unwrap();
        fs::write(
            local.join("bad.js"),
            fixture.replace("__RESULT__", "throw new Error('selected source failed');"),
        )
        .unwrap();
        let _daemon = Daemon::start(root, &mut command(root, "serve"));
        wait_for_music_sources(root);
        let search = admin_json(root, &["music", "catalog", "search", "Probe Track"]);
        let track_id = search["data"]["entries"][0]["id"].as_str().unwrap();
        let server = thread::spawn(move || {
            let (mut stream, _) = http.accept().unwrap();
            stream.set_read_timeout(Some(WAIT)).unwrap();
            let mut request = String::new();
            BufReader::new(&mut stream).read_line(&mut request).unwrap();
            assert!(request.starts_with("GET /audio "));
            stream
                .write_all(b"HTTP/1.1 200 OK\r\nContent-Type: audio/wav\r\nContent-Length: 4\r\nConnection: close\r\n\r\nRIFF")
                .unwrap();
        });
        let failed = admin(
            root,
            &["music", "sources", "test", "local:bad.js", track_id],
        );
        assert!(!failed.status.success());
        assert!(String::from_utf8_lossy(&failed.stderr).contains("selected source failed"));
        let tested = admin_json(
            root,
            &["music", "sources", "test", "local:good.js", track_id],
        );
        assert_eq!(tested["data"]["source_id"], "local:good.js");
        assert_eq!(tested["data"]["track_id"], track_id);
        server.join().unwrap();
        let cache = admin_json(root, &["music", "cache", "list"]);
        assert_eq!(cache["data"]["entries"], serde_json::json!([]));
        admin_json(root, &["music", "sources", "disable", "local:good.js"]);
        let disabled = admin(
            root,
            &["music", "sources", "test", "local:good.js", track_id],
        );
        assert!(!disabled.status.success());
        assert!(String::from_utf8_lossy(&disabled.stderr).contains("LX source is disabled"));
    }

    fn wait_for_music_sources(root: &Path) {
        let deadline = Instant::now() + WAIT;
        let socket = root.join("mikd.admin.sock");
        loop {
            if socket.exists()
                && admin_json(root, &["status"])["data"]["music_source_registry"] == true
            {
                return;
            }
            assert!(
                Instant::now() < deadline,
                "{}",
                fs::read_to_string(root.join("daemon.log")).unwrap()
            );
            thread::sleep(Duration::from_millis(25));
        }
    }

    fn assert_admin_music_available(root: &Path) {
        let music_status = admin_json(root, &["status"]);
        assert_eq!(music_status["data"]["paper_music"], false);
        assert_eq!(music_status["data"]["music_catalog"], true);

        // The service is still available through the host-only admin socket.
        let list = admin_json(root, &["music", "library", "list"]);
        assert_eq!(list["data"], serde_json::json!([]));
        let file = root.join("track.nbs");
        let mut bytes = vec![1, 0, 1, 0]; // legacy header, one tick and layer
        bytes.extend_from_slice(&[0; 16]); // four empty text fields
        bytes.extend_from_slice(&1000u16.to_le_bytes());
        bytes.extend_from_slice(&[0; 23 + 4 + 2]);
        fs::write(&file, bytes).unwrap();
        let imported = admin_json(
            root,
            &["music", "library", "import", file.to_str().unwrap()],
        );
        let id = imported["data"]["id"].as_str().unwrap();
        assert_eq!(
            admin_json(root, &["music", "track", "inspect", id])["data"]["media"]["format"],
            "nbs"
        );
        assert_eq!(
            admin_json(root, &["music", "track", "retire", id])["data"]["id"],
            id
        );
    }

    #[tokio::test]
    async fn optional_startup_failures_leave_identity_online_and_announcements_heal() {
        let directory = private_directory();
        let root = directory.path();
        let announcements = root.join("announcements");
        fs::write(&announcements, b"not a directory").unwrap();
        fs::write(root.join("knowledge"), b"not a directory").unwrap();
        let config_dir = root.join("config");
        fs::create_dir(&config_dir).unwrap();
        fs::write(
            config_dir.join("ai.yml"),
            include_str!("../../../deploy/mikd/ai.example.yml"),
        )
        .unwrap();
        // A failed music socket bind must not take the control listener down.
        fs::write(root.join("mikd.sock.music.sock"), b"not a socket").unwrap();
        let mut daemon = Daemon::start(root, command(root, "serve").arg("0"));
        let status = daemon.status(root);
        assert_core_ready(&status);
        assert_eq!(
            status.domain_problems.get("announcements"),
            Some(&DomainProblem::Unavailable.into())
        );
        assert!(
            !status
                .ready_domains
                .iter()
                .any(|domain| domain == "assistant")
        );
        let log_path = root.join("daemon.log");
        tokio::time::timeout(WAIT, async {
            loop {
                let log = fs::read_to_string(&log_path).unwrap();
                if log.contains("AI assistant disabled until restart:")
                    && log.contains("must be an absolute knowledge directory")
                {
                    break;
                }
                tokio::time::sleep(Duration::from_millis(20)).await;
            }
        })
        .await
        .unwrap();
        let client = http();
        let url = format!("http://127.0.0.1:{}/api/announcements", status.web_api_port);
        let response = client.get(&url).send().await.unwrap();
        assert_eq!(response.status(), reqwest::StatusCode::SERVICE_UNAVAILABLE);
        assert_eq!(
            response.headers()["x-mik-announcements-state"],
            "unavailable"
        );
        // Observe an actual failed refresh, not just the state before the first tick.
        tokio::time::timeout(WAIT, async {
            loop {
                let log = fs::read_to_string(&log_path).unwrap();
                if log.contains("mikd announcement catalog")
                    && log.contains("mikd Paper music listener")
                    && log.contains("music IPC")
                {
                    break;
                }
                tokio::time::sleep(Duration::from_millis(25)).await;
            }
        })
        .await
        .unwrap_or_else(|_| panic!("{}", fs::read_to_string(&log_path).unwrap()));
        assert_admin_music_available(root);

        fs::remove_file(&announcements).unwrap();
        fs::create_dir(&announcements).unwrap();
        fs::write(announcements.join("catalog.json"), b"[]").unwrap();
        let recovered = tokio::time::timeout(WAIT, async {
            loop {
                let response = client.get(&url).send().await.unwrap();
                if response.status() == reqwest::StatusCode::OK {
                    break response;
                }
                tokio::time::sleep(Duration::from_millis(50)).await;
            }
        })
        .await
        .unwrap();
        assert_eq!(recovered.headers()["x-mik-announcements-state"], "ready");
        assert_eq!(recovered.text().await.unwrap(), "[]");
        let status = daemon.status(root);
        assert!(!status.domain_problems.contains_key("announcements"));
        assert!(
            status
                .ready_domains
                .iter()
                .any(|domain| domain == "announcements")
        );
    }

    #[test]
    fn occupied_api_port_does_not_start_websocket_or_stop_ipc() {
        let directory = private_directory();
        let root = directory.path();
        let occupied = TcpListener::bind((Ipv4Addr::LOCALHOST, 0)).unwrap();
        let port = occupied.local_addr().unwrap().port().to_string();
        let key = root.join("web-chat.key");
        let configured = |action| {
            let mut command = command(root, action);
            command.args([&port, "0"]);
            command
        };

        // Validation parses configuration but never probes listener ports or generates keys.
        assert!(configured("validate").status().unwrap().success());
        assert_eq!(fs::read_dir(root).unwrap().count(), 0);

        let mut daemon = Daemon::start(root, &mut configured("serve"));
        let status = daemon.status(root);
        assert_core_ready(&status);
        assert_eq!((status.web_api_port, status.web_chat_port), (0, 0));
        assert!(!key.exists());
        let log = fs::read_to_string(root.join("daemon.log")).unwrap();
        assert!(log.contains("Web API unavailable"), "{log}");
        assert!(log.contains("WebSocket not started"), "{log}");
    }

    #[tokio::test]
    async fn websocket_bind_and_key_failures_leave_http_and_ipc_online() {
        let occupied = TcpListener::bind((Ipv4Addr::LOCALHOST, 0)).unwrap();
        let client = http();
        for (port, cause) in [
            (occupied.local_addr().unwrap().port(), "bind TCP port"),
            (0, "signing key"),
        ] {
            let directory = private_directory();
            let root = directory.path();
            let key = root.join("web-chat.key");
            let corrupt_key = b"invalid DER";
            if port == 0 {
                fs::write(&key, corrupt_key).unwrap();
                fs::set_permissions(&key, fs::Permissions::from_mode(0o600)).unwrap();
            }
            let mut daemon =
                Daemon::start(root, command(root, "serve").arg("0").arg(port.to_string()));
            let status = daemon.status(root);
            assert_core_ready(&status);
            assert!(status.web_api_port > 0);
            assert_eq!(status.web_chat_port, 0);
            if port == 0 {
                assert_eq!(fs::read(key).unwrap(), corrupt_key);
            } else {
                assert!(!key.exists());
            }
            let log = fs::read_to_string(root.join("daemon.log")).unwrap();
            assert!(
                log.contains("WebSocket unavailable") && log.contains(cause),
                "{log}"
            );
            let response = client
                .get(format!(
                    "http://127.0.0.1:{}/api/announcements",
                    status.web_api_port
                ))
                .send()
                .await
                .unwrap();
            assert_eq!(response.status(), reqwest::StatusCode::SERVICE_UNAVAILABLE);
            assert_eq!(
                response.headers()["x-mik-announcements-state"],
                "unavailable"
            );
        }
    }

    #[test]
    fn serve_rejects_corrupt_required_community_data() {
        let directory = private_directory();
        let root = directory.path();
        let database = root.join("community.db");
        let log = root.join("daemon.log");
        fs::write(&database, b"not a sqlite database").unwrap();
        fs::set_permissions(&database, fs::Permissions::from_mode(0o600)).unwrap();
        let mut daemon = Daemon::start(root, &mut command(root, "serve"));
        let deadline = Instant::now() + WAIT;
        let status = loop {
            if let Some(status) = daemon.0.try_wait().unwrap() {
                break status;
            }
            assert!(
                Instant::now() < deadline,
                "mikd served corrupt community data"
            );
            thread::sleep(Duration::from_millis(25));
        };
        assert!(!status.success());
        assert!(fs::read_to_string(log).unwrap().contains("not a database"));
        assert!(!root.join(SOCKET).exists());
    }

    #[test]
    fn validate_reports_invalid_optional_deployment_input_without_creating_state() {
        let directory = private_directory();
        let root = directory.path();
        let mut validate = command(root, "validate");
        assert!(validate.status().unwrap().success());
        let catalog = root.join("announcements/catalog.json");
        fs::create_dir(catalog.parent().unwrap()).unwrap();
        fs::write(&catalog, "invalid JSON").unwrap();
        let output = validate.output().unwrap();
        assert!(!output.status.success());
        assert!(String::from_utf8_lossy(&output.stderr).contains("announcement catalog"));

        fs::write(catalog, "[]").unwrap();
        let sources = root.join("music/sources");
        fs::create_dir(sources.parent().unwrap()).unwrap();
        fs::write(sources, "not a directory").unwrap();
        let output = validate.output().unwrap();
        assert!(!output.status.success());
        assert!(String::from_utf8_lossy(&output.stderr).contains("music configuration: sources"));
        assert_eq!(fs::read_dir(root).unwrap().count(), 2);
    }

    #[test]
    fn validate_loads_ai_from_a_fixed_name_only_when_present() {
        let directory = private_directory();
        let root = directory.path();
        let mut validate = command(root, "validate");
        let config_dir = root.join("config");
        fs::create_dir(&config_dir).unwrap();
        fs::write(config_dir.join("ai.example.yml"), "not a configuration").unwrap();
        assert!(validate.status().unwrap().success());

        let config = config_dir.join("ai.yml");
        fs::write(&config, "not a configuration").unwrap();
        let output = validate.output().unwrap();
        let diagnosis = String::from_utf8_lossy(&output.stderr);
        assert!(!output.status.success());
        assert!(diagnosis.contains("AI assistant configuration") && diagnosis.contains("ai.yml"));

        fs::write(&config, include_str!("../../../deploy/mikd/ai.example.yml")).unwrap();
        assert!(validate.status().unwrap().success());
        assert_eq!(fs::read_dir(root).unwrap().count(), 1);
    }

    #[test]
    fn serve_opens_social_state_beside_history_without_a_path_setting() {
        let directory = private_directory();
        let root = directory.path();
        let config_dir = root.join("config");
        fs::create_dir(&config_dir).unwrap();
        fs::write(config_dir.join("social.yml"), format!("{QQ_GROUPS} {{}}\n")).unwrap();
        let mut daemon = Daemon::start(root, &mut command(root, "serve"));
        assert_core_ready(&daemon.status(root));

        let deadline = Instant::now() + WAIT;
        let state = root.join("social/owner.lock");
        while !state.is_file() {
            assert!(
                Instant::now() < deadline,
                "social state must open in {state:?}"
            );
            thread::sleep(Duration::from_millis(20));
        }
        assert!(root.join("chat-messages-v6.sqlite").is_file());
        assert!(!config_dir.join("social").exists());
    }

    #[test]
    fn validate_checks_moderation_keys_only_when_qq_is_configured() {
        let directory = private_directory();
        let root = directory.path();
        let state = root.join("social");
        fs::create_dir(root.join("config")).unwrap();
        let config = root.join("config/social.yml");

        for (platforms, uses_qq) in [
            (format!("{MATRIX_ROOM} {{}}\n"), false),
            (format!("{QQ_GROUPS} {{}}\n"), true),
            (
                format!(
                    "{QQ_GROUPS}\n    first: {{}}\n    second: {{ outbound: {{}} }}\n{MATRIX_ROOM} {{}}\n"
                ),
                true,
            ),
        ] {
            fs::write(&config, platforms).unwrap();
            let mut validate = command(root, "validate");
            let output = validate.output().unwrap();
            assert!(
                output.status.success(),
                "{}",
                String::from_utf8_lossy(&output.stderr)
            );

            for key in ["MIKD_QQ_OVH_API_KEY", "MIKD_QQ_EDEN_API_KEY"] {
                let output = validate.env(key, "bad key").output().unwrap();
                validate.env_remove(key);
                let diagnosis = String::from_utf8_lossy(&output.stderr);
                assert_eq!(output.status.success(), !uses_qq, "{diagnosis}");
                if uses_qq {
                    assert!(diagnosis.contains(&format!("invalid {key}")), "{diagnosis}");
                }
            }
            assert!(!state.exists());
        }
    }

    #[test]
    fn validate_rejects_removed_social_path_and_moderation_switches() {
        let directory = private_directory();
        let root = directory.path();
        fs::create_dir(root.join("config")).unwrap();
        let config = root.join("config/social.yml");
        for (yaml, field) in [
            (format!("state: /old/social\n{QQ_GROUPS} {{}}\n"), "state"),
            (
                format!("{QQ_GROUPS}\n    group: {{ moderate: false }}\n"),
                "moderate",
            ),
            (format!("{MATRIX_ROOM} {{ moderate: false }}\n"), "moderate"),
        ] {
            fs::write(&config, yaml).unwrap();
            let output = command(root, "validate").output().unwrap();
            let diagnosis = String::from_utf8_lossy(&output.stderr);
            assert!(!output.status.success());
            assert!(
                diagnosis.contains(&format!("unknown field `{field}`")),
                "{diagnosis}"
            );
        }
    }
}
