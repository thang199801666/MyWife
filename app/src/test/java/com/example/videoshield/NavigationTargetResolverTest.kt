package com.example.videoshield

import org.junit.Assert.*
import org.junit.Test

class NavigationTargetResolverTest {
    @Test fun searchReturnsUnfilteredResultsWithEncodedKeyword() {
        assertEquals("https://m.youtube.com/results?search_query=rick+astley",
            NavigationTargetResolver.resolveAddressInput(" rick astley "))
        assertEquals("https://m.youtube.com/results?search_query=nh%E1%BA%A1c+%26+chill",
            NavigationTargetResolver.resolveAddressInput("nhạc & chill"))
    }
    @Test fun explicitUrlsAndEmptyInputKeepTheirBehavior() {
        val url="https://m.youtube.com/watch?v=dQw4w9WgXcQ"
        assertEquals(url, NavigationTargetResolver.resolveAddressInput(url))
        assertEquals("https://youtu.be/dQw4w9WgXcQ", NavigationTargetResolver.resolveAddressInput("youtu.be/dQw4w9WgXcQ"))
        assertNull(NavigationTargetResolver.resolveAddressInput("  "))
    }
}
