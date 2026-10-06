package com.example.videoshield

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunitySegmentClientTest {
    private val videoId = "aqz-KE-bpKQ"

    private fun parse(payload: String, categories: Set<String> = setOf("outro")): List<CommunitySegment> {
        val client = CommunitySegmentClient()
        return try { client.parseCandidates(payload, videoId, requestedCategories = categories) }
        finally { client.close() }
    }

    @Test fun currentPrefixResponseWithoutHashIsAccepted() {
        val result = parse("""[{"videoID":"$videoId","segments":[{"category":"outro","actionType":"skip","segment":[494.6179,618.71356]}]}]""")
        assertEquals(listOf(CommunitySegment(494617L, 618713L, "outro")), result)
    }

    @Test fun suppliedMismatchedHashAndOtherVideoAreRejected() {
        assertTrue(parse("""[{"videoID":"$videoId","hash":"wrong","segments":[{"category":"outro","segment":[1,2]}]}]""").isEmpty())
        assertTrue(parse("""[{"videoID":"dQw4w9WgXcQ","segments":[{"category":"outro","segment":[1,2]}]}]""").isEmpty())
    }

    @Test fun suppliedValidHashStillWorks() {
        val hash = CommunitySegmentClient.sha256(videoId).uppercase()
        assertEquals(1, parse("""[{"videoID":"$videoId","hash":"$hash","segments":[{"category":"outro","segment":[1,2]}]}]""").size)
    }

    @Test fun onlyRequestedSkipCategoriesAndValidTimeRangesAreAccepted() {
        val payload = """[{"videoID":"$videoId","segments":[
            {"category":"sponsor","segment":[1,2]},
            {"category":"outro","actionType":"mute","segment":[1,2]},
            {"category":"outro","segment":[-1,2]},
            {"category":"outro","segment":[3,2]},
            {"category":"outro","segment":[1,999999]},
            {"category":"outro","segment":[5,6]}
        ]}]"""
        assertEquals(listOf(CommunitySegment(5000L, 6000L, "outro")), parse(payload))
        assertTrue(parse("broken json").isEmpty())
    }
}
