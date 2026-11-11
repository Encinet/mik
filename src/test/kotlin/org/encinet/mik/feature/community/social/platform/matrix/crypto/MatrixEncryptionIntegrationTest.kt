package org.encinet.mik.feature.community.social.platform.matrix.crypto

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.net.http.HttpClient
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.logging.Logger
import org.bukkit.configuration.file.YamlConfiguration
import org.encinet.mik.feature.community.social.platform.matrix.MatrixPlatformConfig
import org.encinet.mik.feature.community.social.platform.matrix.client.MatrixClient
import org.encinet.mik.feature.community.social.platform.matrix.sync.MatrixRoomDirectory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class MatrixEncryptionIntegrationTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `devices exchange encrypted messages and retain keys and replay protection after restart`() {
        Homeserver().use { server ->
            val alice = device(server, "alice")
            var bob = device(server, "bob")
            try {
                alice.initialize("@alice:example.org")
                bob.initialize("@bob:example.org")
                alice.receiveSync(server.sync("alice", "a1"), true)
                bob.receiveSync(server.sync("bob", "b1"), true)
                server.awaitOneTimeKeys("bob")
                val text = json("""{"msgtype":"m.text","body":"private !绑定 000123","m.mentions":{"user_ids":["@bob:example.org"]}}""")
                val encrypted = alice.encrypt(ROOM, text).get(15, TimeUnit.SECONDS)
                assertEquals("m.megolm.v1.aes-sha2", encrypted.get("algorithm").asString)
                assertFalse(encrypted.toString().contains(text.get("body").asString))
                bob.receiveSync(server.sync("bob", "b2", event("\$first", encrypted)), true)
                val decoded = bob.decrypt(ROOM, "\$first").get(15, TimeUnit.SECONDS)
                assertEquals(text, decoded.getAsJsonObject("content"))

                val pixels = byteArrayOf(1, 2, 3, 4, 5)
                HttpClient.newHttpClient().use { http ->
                    val transport = MatrixClient(http, config(server, "alice"), { java.util.concurrent.CompletableFuture.completedFuture(MatrixRoomDirectory.Security.ENCRYPTED) }, alice)
                    transport.replyImage(ROOM, "\$first", java.util.Optional.of("\$thread"), "image/png", pixels,
                        1, 1, "private image", "private caption", "<b>private caption</b>").get(15, TimeUnit.SECONDS)
                }
                assertFalse(pixels.contentEquals(server.uploaded))
                val imageCipher = checkNotNull(server.lastSent)
                assertFalse(imageCipher.toString().contains("private caption"))
                bob.receiveSync(server.sync("bob", "b-image", event("\$image", imageCipher)), true)
                val image = bob.decrypt(ROOM, "\$image").get(15, TimeUnit.SECONDS).getAsJsonObject("content")
                assertEquals("private caption", image.get("body").asString)
                assertEquals("\$thread", image.getAsJsonObject("m.relates_to").get("event_id").asString)
                assertFalse(image.has("url"))
                val file = image.getAsJsonObject("file")
                assertEquals("v2", file.get("v").asString)
                assertEquals("mxc://example.org/encrypted", file.get("url").asString)
                assertArrayEquals(MessageDigest.getInstance("SHA-256").digest(server.uploaded),
                    Base64.getDecoder().decode(file.getAsJsonObject("hashes").get("sha256").asString))
                val cipher = Cipher.getInstance("AES/CTR/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE,
                    SecretKeySpec(Base64.getUrlDecoder().decode(file.getAsJsonObject("key").get("k").asString), "AES"),
                    IvParameterSpec(Base64.getDecoder().decode(file.get("iv").asString)))
                assertArrayEquals(pixels, cipher.doFinal(server.uploaded))
                bob.commit("b2")
                val fingerprint = (bob.manage("status", emptyList()).get(5, TimeUnit.SECONDS) as MatrixCryptoResult.Status).signingKey()
                bob.close()

                bob = device(server, "bob")
                bob.initialize("@bob:example.org")
                assertEquals("b2", bob.cursor().orElseThrow())
                assertEquals(fingerprint, (bob.manage("status", emptyList()).get(5, TimeUnit.SECONDS) as MatrixCryptoResult.Status).signingKey())
                val next = alice.encrypt(ROOM, json("""{"msgtype":"m.text","body":"after restart"}""")).get(15, TimeUnit.SECONDS)
                bob.receiveSync(server.sync("bob", "b3", event("\$second", next)), true)
                assertEquals("after restart", bob.decrypt(ROOM, "\$second").get(15, TimeUnit.SECONDS)
                    .getAsJsonObject("content").get("body").asString)
                bob.receiveSync(server.sync("bob", "b4", event("\$replayed", next)), true)
                assertThrows(Exception::class.java) { bob.decrypt(ROOM, "\$replayed").get(15, TimeUnit.SECONDS) }
                assertFalse(server.paths.any { it.startsWith("/_matrix/client/v3/sync") }, "the SDK must not start another /sync consumer")
                assertTrue(Files.walk(directory).use { paths -> paths.anyMatch { it.fileName.toString() == "crypto.mv.db" } })
            } finally {
                alice.close()
                bob.close()
            }
        }
    }

    @Test
    fun `SAS verifies exact transactions and requires a manual matching confirmation`() {
        Homeserver().use { server ->
            val alice = device(server, "alice")
            val bob = device(server, "bob")
            try {
                alice.initialize("@alice:example.org")
                bob.initialize("@bob:example.org")
                alice.receiveSync(server.sync("alice", "a1"), true)
                bob.receiveSync(server.sync("bob", "b1"), true)
                val request = alice.manage("verify", listOf("@bob:example.org", "BOB")).get(10, TimeUnit.SECONDS)
                val transaction = (request as MatrixCryptoResult.Requested).transactionId()
                bob.receiveSync(server.sync("bob", "b2"), true)
                bob.manage("accept", listOf(transaction)).get(10, TimeUnit.SECONDS)
                alice.receiveSync(server.sync("alice", "a2"), true)
                alice.manage("accept", listOf(transaction)).get(10, TimeUnit.SECONDS)
                bob.receiveSync(server.sync("bob", "b3"), true)
                bob.manage("accept", listOf(transaction)).get(10, TimeUnit.SECONDS)
                var comparisonAlice = emptyList<Int>()
                var comparisonBob = emptyList<Int>()
                repeat(6) { index ->
                    alice.receiveSync(server.sync("alice", "a-sas-$index"), true)
                    bob.receiveSync(server.sync("bob", "b-sas-$index"), true)
                    comparisonAlice = (alice.manage("status", emptyList()).get(5, TimeUnit.SECONDS) as MatrixCryptoResult.Status).transaction().orElse(null)?.decimals().orEmpty()
                    comparisonBob = (bob.manage("status", emptyList()).get(5, TimeUnit.SECONDS) as MatrixCryptoResult.Status).transaction().orElse(null)?.decimals().orEmpty()
                    if (comparisonAlice.isNotEmpty() && comparisonBob.isNotEmpty()) return@repeat
                }
                assertTrue(comparisonAlice.isNotEmpty())
                assertEquals(comparisonAlice, comparisonBob)
                assertThrows(Exception::class.java) { bob.manage("confirm", listOf("wrong-transaction")).get(5, TimeUnit.SECONDS) }
                assertFalse(server.paths.any { it.contains("/m.key.verification.mac/") })
                alice.manage("confirm", listOf(transaction)).get(10, TimeUnit.SECONDS)
                bob.manage("confirm", listOf(transaction)).get(10, TimeUnit.SECONDS)
                repeat(4) { index ->
                    alice.receiveSync(server.sync("alice", "a-done-$index"), true)
                    bob.receiveSync(server.sync("bob", "b-done-$index"), true)
                }
                assertTrue((alice.manage("status", listOf("@bob:example.org", "BOB")).get(10, TimeUnit.SECONDS) as MatrixCryptoResult.Status).target().orElseThrow().verified())
                assertTrue((bob.manage("status", listOf("@alice:example.org", "ALICE")).get(10, TimeUnit.SECONDS) as MatrixCryptoResult.Status).target().orElseThrow().verified())
            } finally {
                alice.close()
                bob.close()
            }
        }
    }

    @Test
    fun `leaving a room rotates the group session and excludes departed devices`() {
        Homeserver().use { server ->
            val alice = device(server, "alice")
            val bob = device(server, "bob")
            try {
                alice.initialize("@alice:example.org")
                bob.initialize("@bob:example.org")
                alice.receiveSync(server.sync("alice", "a1"), true)
                bob.receiveSync(server.sync("bob", "b1"), true)
                server.awaitOneTimeKeys("bob")
                val first = alice.encrypt(ROOM, json("""{"msgtype":"m.text","body":"before leave"}""")).get(15, TimeUnit.SECONDS)
                val deliveredKeys = server.toDeviceCount("bob")
                assertTrue(deliveredKeys > 0)
                server.joined.remove("bob")
                alice.receiveSync(server.sync("alice", "a2"), true)
                val second = alice.encrypt(ROOM, json("""{"msgtype":"m.text","body":"after leave"}""")).get(15, TimeUnit.SECONDS)
                assertNotEquals(first.get("session_id"), second.get("session_id"))
                assertEquals(deliveredKeys, server.toDeviceCount("bob"))
            } finally {
                alice.close()
                bob.close()
            }
        }
    }

    @Test
    fun `a wiped local account cannot overwrite the server device identity`() {
        Homeserver().use { server ->
            val first = device(server, "alice")
            first.initialize("@alice:example.org")
            val identity = server.advertised("alice")
            first.close()
            MatrixCryptoStorage.open(directory.resolve("alice"), config(server, "alice").homeserverUri().toString(),
                "@alice:example.org", "ALICE").use { store ->
                java.sql.DriverManager.getConnection("jdbc:h2:file:${store.database()};CIPHER=AES", "sa",
                    "${store.password()} ${store.password()}").use { connection ->
                    connection.createStatement().use { it.execute("DROP ALL OBJECTS") }
                }
            }
            val restored = device(server, "alice")
            try {
                val failure = assertThrows(IllegalStateException::class.java) { restored.initialize("@alice:example.org") }
                assertTrue(failure.message.orEmpty().contains("identity does not match"))
                assertEquals(identity, server.advertised("alice"))
            } finally {
                restored.close()
            }
        }
    }

    @Test
    fun `an existing device cannot have its encryption identity overwritten`() {
        Homeserver().use { server ->
            val first = device(server, "alice")
            try {
                first.initialize("@alice:example.org")
                val fresh = TrixnityEncryption(config(server, "alice"), directory.resolve("another-install"), "", Logger.getAnonymousLogger())
                try {
                    val error = assertThrows(IllegalStateException::class.java) { fresh.initialize("@alice:example.org") }
                    assertTrue(error.message.orEmpty().contains("already has encryption keys"))
                } finally {
                    fresh.close()
                }
            } finally {
                first.close()
            }
        }
    }

    private fun device(server: Homeserver, name: String) =
        TrixnityEncryption(config(server, name), directory.resolve(name), "", Logger.getAnonymousLogger())

    private fun config(server: Homeserver, name: String): MatrixPlatformConfig {
        val yaml = YamlConfiguration()
        yaml.set("homeserver-url", "http://127.0.0.1:${server.port}")
        yaml.set("access-token", name)
        yaml.set("membership.room-ids", listOf(ROOM))
        yaml.set("commands.room-ids", listOf(ROOM))
        return MatrixPlatformConfig.from(yaml)
    }

    private class Homeserver : AutoCloseable {
        private val executor = Executors.newVirtualThreadPerTaskExecutor()
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        private val devices = ConcurrentHashMap<String, JsonObject>()
        private val oneTime = ConcurrentHashMap<String, MutableMap<String, com.google.gson.JsonElement>>()
        private val toDevice = ConcurrentHashMap<String, CopyOnWriteArrayList<JsonObject>>()
        val paths = CopyOnWriteArrayList<String>()
        val joined = ConcurrentHashMap.newKeySet<String>().apply { addAll(listOf("alice", "bob")) }
        @Volatile var uploaded: ByteArray = byteArrayOf()
        @Volatile var lastSent: JsonObject? = null
        val port get() = server.address.port

        init {
            server.executor = executor
            server.createContext("/") { exchange ->
                try {
                    handle(exchange)
                } catch (failure: Throwable) {
                    failure.printStackTrace()
                    runCatching { respond(exchange, 500, json("""{"errcode":"M_UNKNOWN","error":"test fixture failed"}""")) }
                }
            }
            server.start()
        }

        private fun handle(exchange: HttpExchange) {
            val path = exchange.requestURI.path
            paths.add(path)
            val name = exchange.requestHeaders.getFirst("Authorization").orEmpty().removePrefix("Bearer ")
            if (path == "/_matrix/media/v3/upload") {
                assertEquals("application/octet-stream", exchange.requestHeaders.getFirst("Content-Type"))
                uploaded = exchange.requestBody.readAllBytes()
                respond(exchange, 200, json("""{"content_uri":"mxc://example.org/encrypted"}"""))
                return
            }
            val body = exchange.requestBody.readAllBytes().decodeToString().let { if (it.isBlank()) JsonObject() else json(it) }
            val user = "@$name:example.org"
            val device = name.uppercase()
            val response = when {
                path.endsWith("/account/whoami") -> json("""{"user_id":"$user","device_id":"$device"}""")
                path.startsWith("/_matrix/client/v3/profile/") -> json("""{"displayname":"$name"}""")
                path.endsWith("/filter") -> json("""{"filter_id":"filter"}""")
                path.endsWith("/versions") -> json("""{"versions":["v1.19"],"unstable_features":{}}""")
                path.endsWith("/capabilities") -> json("""{"capabilities":{}}""")
                path.endsWith("/keys/upload") -> {
                    body.getAsJsonObject("device_keys")?.let { devices[name] = it.deepCopy() }
                    body.getAsJsonObject("one_time_keys")?.let { keys ->
                        oneTime.computeIfAbsent(name) { ConcurrentHashMap() }.putAll(keys.asMap())
                    }
                    json("""{"one_time_key_counts":{"signed_curve25519":${oneTime[name]?.size ?: 0}}}""")
                }
                path.endsWith("/keys/query") -> {
                    val keys = JsonObject()
                    body.getAsJsonObject("device_keys").keySet().forEach { id ->
                        val owner = id.removePrefix("@").substringBefore(':')
                        val ownerKeys = JsonObject()
                        devices[owner]?.let { ownerKeys.add(owner.uppercase(), it) }
                        keys.add(id, ownerKeys)
                    }
                    json("""{"failures":{},"master_keys":{},"self_signing_keys":{},"user_signing_keys":{}}""")
                        .apply { add("device_keys", keys) }
                }
                path.endsWith("/keys/claim") -> {
                    val keys = JsonObject()
                    body.getAsJsonObject("one_time_keys").entrySet().forEach { (id, requested) ->
                        val owner = id.removePrefix("@").substringBefore(':')
                        val ownerKeys = JsonObject()
                        requested.asJsonObject.keySet().forEach { deviceId ->
                            val claimed = JsonObject()
                            oneTime[owner]?.entries?.firstOrNull()?.let { entry ->
                                claimed.add(entry.key, entry.value)
                                oneTime[owner]?.remove(entry.key)
                            }
                            ownerKeys.add(deviceId, claimed)
                        }
                        keys.add(id, ownerKeys)
                    }
                    json("""{"failures":{}}""").apply { add("one_time_keys", keys) }
                }
                path.contains("/sendToDevice/") -> {
                    val type = path.substringAfter("/sendToDevice/").substringBefore('/')
                    body.getAsJsonObject("messages").entrySet().forEach { (id, messages) ->
                        val recipient = id.removePrefix("@").substringBefore(':')
                        messages.asJsonObject.entrySet().forEach { (_, content) ->
                            toDevice.computeIfAbsent(recipient) { CopyOnWriteArrayList() }.add(JsonObject().apply {
                                addProperty("type", type)
                                addProperty("sender", user)
                                add("content", content)
                            })
                        }
                    }
                    JsonObject()
                }
                path.endsWith("/members") -> JsonObject().apply { add("chunk", members(joined, false)) }
                path.contains("/send/m.room.encrypted/") -> {
                    lastSent = body
                    json("""{"event_id":"${'$'}image"}""")
                }
                path.endsWith("/keys/signatures/upload") -> json("""{"failures":{}}""")
                else -> {
                    respond(exchange, 404, json("""{"errcode":"M_NOT_FOUND","error":"not configured"}"""))
                    return
                }
            }
            respond(exchange, 200, response)
        }

        fun sync(name: String, token: String, event: JsonObject? = null): JsonObject {
            val pending = toDevice[name]?.toList().orEmpty()
            toDevice[name]?.removeAll(pending.toSet())
            val state = members(joined, true).apply {
                add(state("m.room.create", "", json("""{"creator":"@alice:example.org","room_version":"12"}""")))
                add(state("m.room.encryption", "", json("""{"algorithm":"m.megolm.v1.aes-sha2","rotation_period_msgs":100,"rotation_period_ms":604800000}""")))
                add(state("m.room.history_visibility", "", json("""{"history_visibility":"joined"}""")))
            }
            return JsonObject().apply {
                addProperty("next_batch", token)
                add("device_one_time_keys_count", json("""{"signed_curve25519":${oneTime[name]?.size ?: 0}}"""))
                add("device_unused_fallback_key_types", JsonArray())
                add("to_device", JsonObject().apply { add("events", JsonArray().apply { pending.forEach(::add) }) })
                add("rooms", JsonObject().apply {
                    add("join", JsonObject().apply {
                        add(ROOM, JsonObject().apply {
                            add("state_after", JsonObject().apply { add("events", state) })
                            add("timeline", JsonObject().apply {
                                addProperty("prev_batch", "before-$token")
                                addProperty("limited", false)
                                add("events", JsonArray().apply { event?.let(::add) })
                            })
                        })
                    })
                })
            }
        }

        fun awaitOneTimeKeys(name: String) {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while (oneTime[name].isNullOrEmpty() && System.nanoTime() < deadline) Thread.sleep(10)
            assertFalse(oneTime[name].isNullOrEmpty(), "SDK must publish signed one-time keys")
        }

        fun toDeviceCount(name: String): Int = toDevice[name]?.size ?: 0

        fun advertised(name: String): JsonObject = checkNotNull(devices[name]).deepCopy()

        override fun close() {
            server.stop(0)
            executor.shutdownNow()
        }
    }

    companion object {
        private const val ROOM = "!encrypted:example.org"
        private fun json(value: String): JsonObject = JsonParser.parseString(value).asJsonObject

        private fun members(joined: Set<String>, includeLeft: Boolean) = JsonArray().apply {
            listOf("alice", "bob").forEach { name ->
                if (includeLeft || name in joined) add(state("m.room.member", "@$name:example.org",
                    json("""{"membership":"${if (name in joined) "join" else "leave"}","displayname":"$name"}""")))
            }
        }

        private fun state(type: String, stateKey: String, content: JsonObject) = JsonObject().apply {
            addProperty("room_id", ROOM)
            addProperty("type", type)
            addProperty("state_key", stateKey)
            addProperty("sender", "@alice:example.org")
            addProperty("event_id", "\$${type}-$stateKey-${content.hashCode()}")
            addProperty("origin_server_ts", 1000)
            add("content", content)
        }

        private fun event(id: String, content: JsonObject) = JsonObject().apply {
            addProperty("event_id", id)
            addProperty("sender", "@alice:example.org")
            addProperty("origin_server_ts", 2000)
            addProperty("type", "m.room.encrypted")
            add("content", content)
        }

        private fun respond(exchange: HttpExchange, status: Int, body: JsonObject) {
            exchange.responseHeaders.set("Content-Type", "application/json")
            val bytes = body.toString().toByteArray()
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
    }
}
