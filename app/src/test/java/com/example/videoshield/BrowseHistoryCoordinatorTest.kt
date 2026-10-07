package com.example.videoshield

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowseHistoryCoordinatorTest {
    @Test fun ringKeepsRecentEntriesAndTracksAdjacentBackForwardCommits() {
        val history = BrowseHistoryCoordinator(maxEntries = 3)
        history.commit("a")
        history.commit("b")
        history.commit("c")
        history.commit("d")
        assertEquals(listOf("b", "c", "d"), history.snapshot().entries)
        assertEquals("c", history.backTarget())

        history.commit("c")
        assertEquals("b", history.backTarget())
        assertEquals("d", history.forwardTarget())
        history.commit("d")
        assertEquals("c", history.backTarget())
        assertNull(history.forwardTarget())
    }

    @Test fun divergentCommitDropsForwardBranch() {
        val history = BrowseHistoryCoordinator()
        history.commit("home")
        history.commit("search")
        history.commit("subs")
        history.commit("search")
        history.commit("channel")
        assertEquals(listOf("home", "search", "channel"), history.snapshot().entries)
        assertNull(history.forwardTarget())
    }

    @Test fun replaceCurrentKeepsOneLogicalSlotForFastMovingFeeds() {
        val history = BrowseHistoryCoordinator()
        history.commit("home")
        history.commit("shorts/one")
        history.replaceCurrent("shorts/two")
        history.replaceCurrent("shorts/three")
        assertEquals(listOf("home", "shorts/three"), history.snapshot().entries)
        assertEquals("home", history.backTarget())
    }

    @Test fun compactedFlagSurvivesSnapshotRestore() {
        val history = BrowseHistoryCoordinator()
        history.commit("home")
        history.commit("search")
        history.markCompacted()
        val restored = BrowseHistoryCoordinator()
        restored.restore(history.snapshot())
        assertTrue(restored.compacted)
        assertEquals("search", restored.current())
    }
    @Test fun oversizedSnapshotTranslatesIndexAfterDroppingOldPrefix() {
        val restored = BrowseHistoryCoordinator(maxEntries = 3)
        restored.restore(
            BrowseHistoryCoordinator.Snapshot(
                entries = listOf("a", "b", "c", "d", "e"),
                index = 3,
                compacted = true
            )
        )
        assertEquals(listOf("c", "d", "e"), restored.snapshot().entries)
        assertEquals("d", restored.current())
        assertEquals("c", restored.backTarget())
        assertEquals("e", restored.forwardTarget())
    }

}
