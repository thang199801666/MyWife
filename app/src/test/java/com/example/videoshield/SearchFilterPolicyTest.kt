package com.example.videoshield

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchFilterPolicyTest {
    @Test fun knownTypeTokensRemainCompatible() {
        assertEquals("EgIQAQ==", SearchFilterPolicy.encode(SearchFilterState(type = SearchResultType.VIDEOS)))
        assertEquals("EgIQAg==", SearchFilterPolicy.encode(SearchFilterState(type = SearchResultType.CHANNELS)))
        assertEquals("EgIQAw==", SearchFilterPolicy.encode(SearchFilterState(type = SearchResultType.PLAYLISTS)))
        assertEquals("EgIQCQ==", SearchFilterPolicy.encode(SearchFilterState(type = SearchResultType.SHORTS)))
    }

    @Test fun combinedStateRoundTrips() {
        val state = SearchFilterState(
            type = SearchResultType.VIDEOS,
            prioritize = SearchPrioritize.POPULARITY,
            uploadDate = SearchUploadDate.THIS_WEEK,
            duration = SearchDuration.THREE_TO_20_MIN
        )
        assertEquals(state, SearchFilterPolicy.decode(SearchFilterPolicy.encode(state)))
    }

    @Test fun defaultSearchDoesNotForceAType() {
        val url = SearchFilterPolicy.buildUrl("finite element tutorial", SearchFilterState())
        assertTrue(url.startsWith("https://m.youtube.com/results?search_query="))
        assertTrue(!url.contains("&sp="))
    }
}
