package org.encinet.mik.feature.community.social.platform.matrix.crypto

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MatrixCryptoSyncTest {
    @Test
    fun `default rotation is supplied without changing the received frame or explicit policy`() {
        val response = JsonParser.parseString("""
            {"next_batch":"next","rooms":{"join":{"!room:example.org":{"state_after":{"events":[
              {"type":"m.room.encryption","state_key":"","content":{"algorithm":"m.megolm.v1.aes-sha2","rotation_period_msgs":7}}
            ]}}}}}
        """).asJsonObject
        val copy = response.deepCopy()
        val normalized = cryptoSync(response, emptySet(), false)
        val settings = normalized.getAsJsonObject("rooms").getAsJsonObject("join").getAsJsonObject("!room:example.org")
            .getAsJsonObject("state_after").getAsJsonArray("events")[0].asJsonObject.getAsJsonObject("content")
        assertEquals(604_800_000L, settings.get("rotation_period_ms").asLong)
        assertEquals(7, settings.get("rotation_period_msgs").asInt)
        assertEquals(copy, response)
    }

    @Test
    fun `only authoritative full state can retire a room absent from the response`() {
        val response = JsonParser.parseString("""
            {"next_batch":"next","rooms":{"join":{"!joined:example.org":{
              "state_after":{"events":[]},
              "state":{"events":[{"type":"m.room.member","state_key":"@alice:example.org","content":{"membership":"leave"}}]},
              "timeline":{"events":[{"type":"m.room.member","state_key":"@alice:example.org","content":{"membership":"leave"}}]}
            }}}}
        """).asJsonObject
        val previous = setOf("!joined:example.org", "!forgotten:example.org")
        assertFalse(cryptoSync(response, previous, false).getAsJsonObject("rooms").has("leave"))
        val full = cryptoSync(response, previous, true).getAsJsonObject("rooms")
        assertEquals(setOf("!forgotten:example.org"), full.getAsJsonObject("leave").keySet())
        assertTrue(full.getAsJsonObject("join").getAsJsonObject("!joined:example.org")
            .getAsJsonObject("state_after").getAsJsonArray("events").isEmpty)
        assertFalse(full.getAsJsonObject("join").getAsJsonObject("!joined:example.org").has("state"))
        assertTrue(full.getAsJsonObject("join").getAsJsonObject("!joined:example.org")
            .getAsJsonObject("timeline").getAsJsonArray("events").isEmpty)
    }
}
