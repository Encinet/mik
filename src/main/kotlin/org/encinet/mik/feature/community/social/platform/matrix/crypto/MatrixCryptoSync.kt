package org.encinet.mik.feature.community.social.platform.matrix.crypto

import com.google.gson.JsonArray
import com.google.gson.JsonObject

/** Supplies Matrix's default rotation policy and reconciles rooms omitted by an authoritative full sync. */
internal fun cryptoSync(response: JsonObject, previouslyJoined: Set<String>, fullState: Boolean): JsonObject {
    val prepared = response.deepCopy()
    val rooms = prepared.getAsJsonObject("rooms") ?: JsonObject().also { prepared.add("rooms", it) }
    val joined = rooms.getAsJsonObject("join") ?: JsonObject()
    if (fullState) {
        val absent = previouslyJoined - joined.keySet()
        if (absent.isNotEmpty()) {
            val left = rooms.getAsJsonObject("leave") ?: JsonObject().also { rooms.add("leave", it) }
            absent.forEach { roomId ->
                if (!left.has(roomId)) left.add(roomId, JsonObject())
            }
        }
    }
    listOf("join", "leave").forEach { group ->
        rooms.getAsJsonObject(group)?.asMap()?.values?.forEach { room ->
            if (room.asJsonObject.has("state_after")) {
                // The SDK emitter also consumes timeline state. Only the authoritative final state may reach it.
                room.asJsonObject.remove("state")
                room.asJsonObject.getAsJsonObject("timeline")?.let { timeline ->
                    timeline.getAsJsonArray("events")?.let { events ->
                        timeline.add("events", JsonArray().apply {
                            events.filterNot { it.asJsonObject.has("state_key") }.forEach(::add)
                        })
                    }
                }
            }
            listOf("state", "state_after", "timeline").forEach { section ->
                val events = room.asJsonObject.getAsJsonObject(section)?.getAsJsonArray("events") ?: JsonArray()
                events.forEach { raw ->
                    val event = raw.asJsonObject
                    if (event.get("type")?.asString == "m.room.encryption") {
                        val content = event.getAsJsonObject("content")
                        if (content?.get("algorithm")?.asString == "m.megolm.v1.aes-sha2") {
                            if (!content.has("rotation_period_ms")) content.addProperty("rotation_period_ms", 604_800_000L)
                            if (!content.has("rotation_period_msgs")) content.addProperty("rotation_period_msgs", 100)
                        }
                    }
                }
            }
        }
    }
    return prepared
}
