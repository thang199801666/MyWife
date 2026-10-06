package com.example.videoshield

import org.junit.Assert.*
import org.junit.Test

class SearchSuggestionClientTest {
    @Test fun parsesYouTubeCompletionsAndRejectsOtherQuery() {
        val json="""["new",["newjeans","new trick","new heart"],[],{}]"""
        assertEquals(listOf("newjeans","new trick","new heart"),SearchSuggestionClient.parse(json,"new"))
        assertTrue(SearchSuggestionClient.parse(json,"new jeans").isEmpty())
    }
    @Test fun boundsAndDeduplicatesUntrustedSuggestions() {
        assertEquals(listOf("nhạc chill"), SearchSuggestionClient.parse("""["nhạc",["nhạc chill",null,5,"","nhạc chill"]]""","nhạc"))
        val rows=org.json.JSONArray(); repeat(30) { rows.put("keyword $it") }
        assertEquals(10, SearchSuggestionClient.parse(org.json.JSONArray().put("keyword").put(rows).toString(),"keyword").size)
    }
}
