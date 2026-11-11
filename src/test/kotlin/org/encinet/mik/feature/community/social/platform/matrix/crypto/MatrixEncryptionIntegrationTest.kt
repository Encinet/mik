package org.encinet.mik.feature.community.social.platform.matrix.crypto

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import de.connect2x.trixnity.core.serialization.canonicalJsonString
import de.connect2x.trixnity.crypto.core.createAesHmacSha2MacFromKey
import de.connect2x.trixnity.crypto.key.encodeRecoveryKey
import de.connect2x.trixnity.crypto.key.encryptSecret
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.net.http.HttpClient
import java.security.MessageDigest
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Handler
import java.util.logging.LogRecord
import java.util.logging.Logger
import org.bukkit.configuration.file.YamlConfiguration
import org.encinet.mik.feature.community.social.platform.matrix.MatrixPlatformConfig
import org.encinet.mik.feature.community.social.platform.matrix.client.MatrixClient
import org.encinet.mik.feature.community.social.platform.matrix.sync.MatrixRoomDirectory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

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

    @ParameterizedTest
    @CsvSource("false,false", "true,false", "true,true")
    fun `SAS verifies exact transactions and requires a manual matching confirmation`(sameAccount: Boolean, delayedSignature: Boolean) {
        Homeserver(sameAccount, delayedSignature).use { server ->
            val alice = device(server, "alice", onlyVerified = true)
            var bob = device(server, "bob")
            try {
                alice.initialize(server.userId("alice"))
                bob.initialize(server.userId("bob"))
                alice.receiveSync(server.sync("alice", "a1"), true)
                bob.receiveSync(server.sync("bob", "b1"), true)
                val excluded = if (!sameAccount) {
                    val encrypted = alice.encrypt(ROOM, json("""{"msgtype":"m.text","body":"before verification"}""")).get(15, TimeUnit.SECONDS)
                    val withheld = server.pending("bob").single { it.get("type").asString == "m.room_key.withheld" }.getAsJsonObject("content")
                    assertEquals("m.unverified", withheld.get("code").asString)
                    assertEquals(encrypted.get("session_id"), withheld.get("session_id"))
                    assertEquals(ROOM, withheld.get("room_id").asString)
                    assertFalse(withheld.has("session_key"))
                    assertFalse(server.pending("bob").any { it.get("type").asString == "m.room.encrypted" })
                    encrypted
                } else null
                val request = alice.manage("verify", listOf(server.userId("bob"), "BOB")).get(10, TimeUnit.SECONDS)
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
                if (sameAccount) assertEquals(MatrixCryptoResult.Verification.UNVERIFIED, status(bob).verification())
                assertThrows(Exception::class.java) { bob.manage("confirm", listOf("wrong-transaction")).get(5, TimeUnit.SECONDS) }
                assertFalse(server.paths.any { it.contains("/m.key.verification.mac/") })
                alice.manage("confirm", listOf(transaction)).get(10, TimeUnit.SECONDS)
                bob.manage("confirm", listOf(transaction)).get(10, TimeUnit.SECONDS)
                repeat(4) { index ->
                    alice.receiveSync(server.sync("alice", "a-done-$index"), true)
                    bob.receiveSync(server.sync("bob", "b-done-$index"), true)
                }
                if (sameAccount) {
                    await("SAS must refresh our newly cross-signed device even without a device_lists notification") {
                        status(bob).verification() == MatrixCryptoResult.Verification.VERIFIED
                    }
                    if (delayedSignature) assertTrue(server.signatureQueries.get() >= 2)
                    assertEquals(MatrixCryptoResult.Verification.VERIFIED, status(alice).verification())
                } else {
                    assertTrue((alice.manage("status", listOf(server.userId("bob"), "BOB")).get(10, TimeUnit.SECONDS) as MatrixCryptoResult.Status).target().orElseThrow().verified())
                    assertTrue((bob.manage("status", listOf(server.userId("alice"), "ALICE")).get(10, TimeUnit.SECONDS) as MatrixCryptoResult.Status).target().orElseThrow().verified())
                }
                server.awaitOneTimeKeys("bob")
                val encrypted = alice.encrypt(ROOM, json("""{"msgtype":"m.text","body":"after verification"}""")).get(15, TimeUnit.SECONDS)
                if (excluded != null) assertNotEquals(excluded.get("session_id"), encrypted.get("session_id"))
                bob.receiveSync(server.sync("bob", "verified-message", event("\$verified", encrypted, server.userId("alice"))), true)
                assertEquals("after verification", bob.decrypt(ROOM, "\$verified").get(15, TimeUnit.SECONDS)
                    .getAsJsonObject("content").get("body").asString)
                if (sameAccount && !delayedSignature) {
                    assertTrue(status(bob).backupVersion().isEmpty)
                    val recovery = server.enableRecovery(includeInSync = false, includeMaster = false)
                    bob.close()
                    bob = TrixnityEncryption(config(server, "bob"), directory.resolve("bob"), recovery, Logger.getAnonymousLogger())
                    bob.initialize(server.userId("bob"))
                    bob.receiveSync(server.sync("bob", "after-sas-restart"), true)
                    await("an already verified device must still recover its missing backup secret") {
                        val result = status(bob)
                        result.verification() == MatrixCryptoResult.Verification.VERIFIED && result.backupVersion().orElse(null) == "1"
                    }
                }
            } finally {
                alice.close()
                bob.close()
            }
        }
    }

    @Test
    fun `enabling verified recipients discards a persisted session shared with an unverified device`() {
        Homeserver().use { server ->
            var alice = device(server, "alice")
            val bob = device(server, "bob")
            try {
                alice.initialize(server.userId("alice"))
                bob.initialize(server.userId("bob"))
                alice.receiveSync(server.sync("alice", "a1"), true)
                bob.receiveSync(server.sync("bob", "b1"), true)
                server.awaitOneTimeKeys("bob")
                val old = alice.encrypt(ROOM, json("""{"msgtype":"m.text","body":"previous policy"}""")).get(15, TimeUnit.SECONDS)
                bob.receiveSync(server.sync("bob", "b2", event("\$old-policy", old)), true)
                assertEquals("previous policy", bob.decrypt(ROOM, "\$old-policy").get(15, TimeUnit.SECONDS)
                    .getAsJsonObject("content").get("body").asString)
                alice.close()
                alice = device(server, "alice", onlyVerified = true)
                alice.initialize(server.userId("alice"))
                alice.receiveSync(server.sync("alice", "a2"), true)
                val restricted = alice.encrypt(ROOM, json("""{"msgtype":"m.text","body":"verified recipients only"}""")).get(15, TimeUnit.SECONDS)
                assertNotEquals(old.get("session_id"), restricted.get("session_id"))
                assertFalse(server.pending("bob").any { it.get("type").asString == "m.room.encrypted" })
                assertTrue(server.pending("bob").any { it.get("type").asString == "m.room_key.withheld" })
                alice.close()
                alice = device(server, "alice", onlyVerified = false)
                alice.initialize(server.userId("alice"))
                alice.receiveSync(server.sync("alice", "a3"), true)
                val reopened = alice.encrypt(ROOM, json("""{"msgtype":"m.text","body":"policy disabled"}""")).get(15, TimeUnit.SECONDS)
                assertNotEquals(restricted.get("session_id"), reopened.get("session_id"))
                bob.receiveSync(server.sync("bob", "b3", event("\$disabled-policy", reopened)), true)
                assertEquals("policy disabled", bob.decrypt(ROOM, "\$disabled-policy").get(15, TimeUnit.SECONDS)
                    .getAsJsonObject("content").get("body").asString)
            } finally {
                alice.close()
                bob.close()
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `recovery restores device trust and key backup with or without sync account data`(includeInSync: Boolean) {
        Homeserver(sameAccount = true).use { server ->
            val recoveryKey = server.enableRecovery(includeInSync)
            val messages = CopyOnWriteArrayList<String>()
            val logger = collectingLogger(messages)
            val bot = TrixnityEncryption(config(server, "bob"), directory.resolve("bob"), recoveryKey, logger)
            try {
                bot.initialize(server.userId("bob"))
                assertTrue(messages.none { it.contains("recovery", ignoreCase = true) })
                bot.receiveSync(server.sync("bob", "recovery"), true)
                await("recovery must verify the device and activate the existing backup") {
                    val result = status(bot)
                    result.verification() == MatrixCryptoResult.Verification.VERIFIED && result.backupVersion().orElse(null) == "1"
                }
                assertTrue(messages.none { it.contains("failed", ignoreCase = true) })
                assertTrue(messages.none { it.contains(recoveryKey) })
            } finally {
                bot.close()
            }
        }
    }

    @Test
    fun `missing remote secret storage is reported before recovery key validation`() {
        Homeserver(sameAccount = true).use { server ->
            val messages = CopyOnWriteArrayList<String>()
            val recoveryKey = encodeRecoveryKey(ByteArray(32))
            val bot = TrixnityEncryption(config(server, "bob"), directory.resolve("bob"), recoveryKey, collectingLogger(messages))
            try {
                bot.initialize(server.userId("bob"))
                bot.receiveSync(server.sync("bob", "missing-storage"), true)
                await("a missing remote default key must be distinguishable from a wrong recovery key") {
                    messages.any { it.contains("read default secret-storage key: HTTP 404 (NotFound)") }
                }
                assertEquals(MatrixCryptoResult.Verification.UNVERIFIED, status(bot).verification())
                assertTrue(messages.none { it.contains("validate the recovery key") || it.contains(recoveryKey) })
            } finally {
                bot.close()
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

    private fun device(server: Homeserver, name: String, onlyVerified: Boolean = false) =
        TrixnityEncryption(config(server, name, onlyVerified), directory.resolve(name), "", Logger.getAnonymousLogger())

    private fun status(device: MatrixEncryption) =
        device.manage("status", emptyList()).get(10, TimeUnit.SECONDS) as MatrixCryptoResult.Status

    private fun collectingLogger(messages: MutableList<String>) = Logger.getAnonymousLogger().apply {
        addHandler(object : Handler() {
            override fun publish(record: LogRecord) { messages.add(record.message) }
            override fun flush() = Unit
            override fun close() = Unit
        })
    }

    private fun await(message: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (System.nanoTime() < deadline) {
            if (condition()) return
            Thread.sleep(20)
        }
        assertTrue(condition(), message)
    }

    private fun config(server: Homeserver, name: String, onlyVerified: Boolean = false): MatrixPlatformConfig {
        val yaml = YamlConfiguration()
        yaml.set("homeserver-url", "http://127.0.0.1:${server.port}")
        yaml.set("access-token", name)
        yaml.set("encryption.only-verified-users", onlyVerified)
        yaml.set("membership.room-ids", listOf(ROOM))
        yaml.set("commands.room-ids", listOf(ROOM))
        return MatrixPlatformConfig.from(yaml)
    }

    private class Homeserver(private val sameAccount: Boolean = false, private val delayedSignature: Boolean = false) : AutoCloseable {
        private val executor = Executors.newVirtualThreadPerTaskExecutor()
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        private val devices = ConcurrentHashMap<String, JsonObject>()
        private val crossSigning = if (sameAccount) CrossSigning() else null
        private var accountData = JsonArray()
        private var syncAccountData = true
        @Volatile private var signaturePending = false
        val signatureQueries = AtomicInteger()
        private val oneTime = ConcurrentHashMap<String, MutableMap<String, com.google.gson.JsonElement>>()
        private val toDevice = ConcurrentHashMap<String, CopyOnWriteArrayList<JsonObject>>()
        val paths = CopyOnWriteArrayList<String>()
        val joined = ConcurrentHashMap.newKeySet<String>().apply { addAll(listOf("alice", "bob")) }
        @Volatile var uploaded: ByteArray = byteArrayOf()
        @Volatile var lastSent: JsonObject? = null
        val port get() = server.address.port

        fun userId(name: String) = if (sameAccount) "@bot:example.org" else "@$name:example.org"

        fun enableRecovery(includeInSync: Boolean = true, includeMaster: Boolean = true): String {
            val (key, data) = checkNotNull(crossSigning).recovery(includeMaster)
            accountData = data
            syncAccountData = includeInSync
            return key
        }

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
            val user = userId(name)
            val device = name.uppercase()
            val response = when {
                path.endsWith("/account/whoami") -> json("""{"user_id":"$user","device_id":"$device"}""")
                path.startsWith("/_matrix/client/v3/profile/") -> json("""{"displayname":"$name"}""")
                path.endsWith("/filter") -> json("""{"filter_id":"filter"}""")
                path.endsWith("/versions") -> json("""{"versions":["v1.19"],"unstable_features":{}}""")
                path.endsWith("/capabilities") -> json("""{"capabilities":{}}""")
                path.endsWith("/keys/upload") -> {
                    body.getAsJsonObject("device_keys")?.let {
                        devices[name] = it.deepCopy().also { keys ->
                            if (name == "alice") crossSigning?.signDevice(keys)
                        }
                    }
                    body.getAsJsonObject("one_time_keys")?.let { keys ->
                        oneTime.computeIfAbsent(name) { ConcurrentHashMap() }.putAll(keys.asMap())
                    }
                    json("""{"one_time_key_counts":{"signed_curve25519":${oneTime[name]?.size ?: 0}}}""")
                }
                path.endsWith("/keys/query") -> {
                    if (signaturePending && name == "bob" && signatureQueries.incrementAndGet() >= 2) {
                        devices.computeIfPresent("bob") { _, keys -> keys.deepCopy().also { crossSigning?.signDevice(it) } }
                        signaturePending = false
                    }
                    val keys = JsonObject()
                    body.getAsJsonObject("device_keys").keySet().forEach { id ->
                        val ownerKeys = JsonObject()
                        devices.forEach { (owner, keys) ->
                            if (userId(owner) == id) ownerKeys.add(owner.uppercase(), keys)
                        }
                        keys.add(id, ownerKeys)
                    }
                    json("""{"failures":{},"master_keys":{},"self_signing_keys":{},"user_signing_keys":{}}""")
                        .apply {
                            add("device_keys", keys)
                            crossSigning?.addToResponse(this)
                        }
                }
                path.endsWith("/keys/claim") -> {
                    val keys = JsonObject()
                    body.getAsJsonObject("one_time_keys").entrySet().forEach { (id, requested) ->
                        val ownerKeys = JsonObject()
                        requested.asJsonObject.keySet().forEach { deviceId ->
                            val owner = devices.keys.firstOrNull { userId(it) == id && it.uppercase() == deviceId }
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
                        messages.asJsonObject.entrySet().forEach { (target, content) ->
                            devices.keys.filter { userId(it) == id && (target == "*" || it.uppercase() == target) }.forEach { recipient ->
                                toDevice.computeIfAbsent(recipient) { CopyOnWriteArrayList() }.add(JsonObject().apply {
                                    addProperty("type", type)
                                    addProperty("sender", user)
                                    add("content", content)
                                })
                            }
                        }
                    }
                    JsonObject()
                }
                path.endsWith("/members") -> JsonObject().apply { add("chunk", members(joined, false)) }
                path.contains("/send/m.room.encrypted/") -> {
                    lastSent = body
                    json("""{"event_id":"${'$'}image"}""")
                }
                path.endsWith("/keys/signatures/upload") -> {
                    crossSigning?.mergeSignatures(body.getAsJsonObject("@bot:example.org"), devices)
                    // Model the trusted client's signature, with no device_lists change notification.
                    if (sameAccount && name == "alice") {
                        if (delayedSignature) signaturePending = true
                        else devices.computeIfPresent("bob") { _, keys -> keys.deepCopy().also { crossSigning?.signDevice(it) } }
                    }
                    json("""{"failures":{}}""")
                }
                path.contains("/account_data/") -> {
                    val type = path.substringAfter("/account_data/")
                    val content = accountData.firstOrNull { it.asJsonObject.get("type").asString == type }
                        ?.asJsonObject?.getAsJsonObject("content")
                    if (content == null) {
                        respond(exchange, 404, json("""{"errcode":"M_NOT_FOUND","error":"account data missing"}"""))
                        return
                    }
                    content
                }
                path.contains("/room_keys/version") -> {
                    if (exchange.requestMethod == "GET") checkNotNull(crossSigning).backup()
                    else json("""{"version":"1"}""")
                }
                path.contains("/room_keys/keys") -> json("""{"count":0,"etag":"0","rooms":{}}""")
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
            val state = (if (sameAccount) JsonArray().apply {
                add(state("m.room.member", userId(name), json("""{"membership":"join"}""")))
            } else members(joined, true)).apply {
                add(state("m.room.create", "", json("""{"creator":"@alice:example.org","room_version":"12"}""")))
                add(state("m.room.encryption", "", json("""{"algorithm":"m.megolm.v1.aes-sha2","rotation_period_msgs":100,"rotation_period_ms":604800000}""")))
                add(state("m.room.history_visibility", "", json("""{"history_visibility":"joined"}""")))
            }
            return JsonObject().apply {
                addProperty("next_batch", token)
                add("account_data", JsonObject().apply { add("events", if (syncAccountData) accountData else JsonArray()) })
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

        fun pending(name: String): List<JsonObject> = toDevice[name]?.map { it.deepCopy() }.orEmpty()

        fun advertised(name: String): JsonObject = checkNotNull(devices[name]).deepCopy()

        override fun close() {
            server.stop(0)
            executor.shutdownNow()
        }
    }

    /** Real Ed25519 cross-signatures, representing a separate trusted Matrix client. */
    private class CrossSigning {
        private val masterPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        private val selfPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        private val userPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        private val backupPair = KeyPairGenerator.getInstance("X25519").generateKeyPair()
        private fun publicKey(pair: java.security.KeyPair) = Base64.getEncoder().withoutPadding()
            .encodeToString(pair.public.encoded.takeLast(32).toByteArray())
        private val master = key("master", publicKey(masterPair)).also { sign(it, masterPair) }
        private val self = key("self_signing", publicKey(selfPair)).also { sign(it, masterPair) }
        private val user = key("user_signing", publicKey(userPair)).also { sign(it, masterPair) }

        fun recovery(includeMaster: Boolean): Pair<String, JsonArray> = runBlocking {
            val recovery = ByteArray(32) { (it + 1).toByte() }
            val iv = ByteArray(16)
            val mac = createAesHmacSha2MacFromKey(recovery, iv)
            val data = JsonArray()
            fun accountEvent(type: String, content: JsonObject) {
                data.add(JsonObject().apply { addProperty("type", type); add("content", content) })
            }
            accountEvent("m.secret_storage.default_key", json("""{"key":"recovery"}"""))
            accountEvent("m.secret_storage.key.recovery", json("""{"algorithm":"m.secret_storage.v1.aes-hmac-sha2","iv":"${Base64.getEncoder().withoutPadding().encodeToString(iv)}","mac":"$mac"}"""))
            mapOf("m.cross_signing.master" to masterPair, "m.cross_signing.self_signing" to selfPair,
                "m.cross_signing.user_signing" to userPair, "m.megolm_backup.v1" to backupPair).forEach { (type, pair) ->
                if (!includeMaster && type == "m.cross_signing.master") return@forEach
                val secret = Base64.getEncoder().withoutPadding().encodeToString(pair.private.encoded.takeLast(32).toByteArray())
                val encrypted = encryptSecret(recovery, "recovery", type, secret, Json)
                accountEvent(type, JsonObject().apply { add("encrypted", JsonParser.parseString(kotlinx.serialization.json.JsonObject(encrypted).toString())) })
            }
            encodeRecoveryKey(recovery) to data
        }

        fun backup() = json("""{"algorithm":"m.megolm_backup.v1.curve25519-aes-sha2","version":"1","count":0,"etag":"0"}""").apply {
            add("auth_data", json("""{"public_key":"${publicKey(backupPair)}"}""").also { sign(it, masterPair) })
        }

        private fun key(usage: String, value: String) = json("""{"user_id":"@bot:example.org","usage":["$usage"],"keys":{"ed25519:$value":"$value"}}""")

        @Synchronized
        fun signDevice(device: JsonObject) = sign(device, selfPair)

        private fun sign(value: JsonObject, pair: java.security.KeyPair) {
            val unsigned = value.deepCopy().apply { remove("signatures"); remove("unsigned") }
            val canonical = canonicalJsonString(Json.parseToJsonElement(unsigned.toString()))
            val signature = Signature.getInstance("Ed25519").run {
                initSign(pair.private)
                update(canonical.toByteArray())
                Base64.getEncoder().withoutPadding().encodeToString(sign())
            }
            val signatures = value.getAsJsonObject("signatures") ?: JsonObject().also { value.add("signatures", it) }
            val own = signatures.getAsJsonObject("@bot:example.org") ?: JsonObject().also { signatures.add("@bot:example.org", it) }
            own.addProperty("ed25519:${publicKey(pair)}", signature)
        }

        @Synchronized
        fun addToResponse(response: JsonObject) {
            response.add("master_keys", JsonObject().apply { add("@bot:example.org", master.deepCopy()) })
            response.add("self_signing_keys", JsonObject().apply { add("@bot:example.org", self.deepCopy()) })
            response.add("user_signing_keys", JsonObject().apply { add("@bot:example.org", user.deepCopy()) })
        }

        @Synchronized
        fun mergeSignatures(upload: JsonObject?, devices: ConcurrentHashMap<String, JsonObject>) {
            upload?.entrySet()?.forEach { (id, value) ->
                val incoming = value.asJsonObject.getAsJsonObject("signatures") ?: return@forEach
                fun merge(target: JsonObject) {
                    val signatures = target.getAsJsonObject("signatures") ?: JsonObject().also { target.add("signatures", it) }
                    incoming.entrySet().forEach { (owner, keys) ->
                        val existing = signatures.getAsJsonObject(owner) ?: JsonObject().also { signatures.add(owner, it) }
                        keys.asJsonObject.entrySet().forEach { (key, value) -> existing.add(key, value) }
                    }
                }
                if (id == publicKey(masterPair)) merge(master)
                else devices.computeIfPresent(id.lowercase()) { _, keys -> keys.deepCopy().also(::merge) }
            }
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

        private fun event(id: String, content: JsonObject, sender: String = "@alice:example.org") = JsonObject().apply {
            addProperty("event_id", id)
            addProperty("sender", sender)
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
