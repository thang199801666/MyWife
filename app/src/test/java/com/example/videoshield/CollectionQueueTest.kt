package com.example.videoshield

import org.junit.Assert.assertEquals
import org.junit.Test

class CollectionQueueTest {
    private fun video(id: String) = VideoItem(id, id, "", "https://m.youtube.com/watch?v=$id", 0)
    @Test fun firstVideoPlaysImmediatelyAndRemainingVideosPrecedeExistingQueue() {
        assertEquals(listOf("b", "c", "x"), LibraryPolicy.collectionQueue(listOf(video("a"), video("b"), video("c")),
            listOf(video("x"), video("b"), video("a"))).map { it.videoId })
    }
    @Test fun duplicatesDoNotRepeatAndEmptyCollectionPreservesQueue() {
        assertEquals(listOf("b", "x"), LibraryPolicy.collectionQueue(listOf(video("a"), video("b"), video("b")), listOf(video("x"))).map { it.videoId })
        assertEquals(listOf("x"), LibraryPolicy.collectionQueue(emptyList(), listOf(video("x"))).map { it.videoId })
    }
}
