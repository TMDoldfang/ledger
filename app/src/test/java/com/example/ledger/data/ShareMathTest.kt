package com.example.ledger.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ShareMathTest {
    @Test fun splitEvenly() = assertEquals(listOf(300L, 300L, 300L), ShareMath.split(900, 3))

    @Test fun splitRemainderGoesToFirst() = assertEquals(listOf(334L, 333L, 333L), ShareMath.split(1000, 3))

    @Test fun splitNothing() {
        assertEquals(emptyList<Long>(), ShareMath.split(900, 0))
        assertEquals(listOf(0L, 0L), ShareMath.split(0, 2))
    }

    @Test fun splitWithMeGivesMeTheRemainder() {
        // 1200 / 4 份 = 300,3 個朋友各 300,我 300
        assertEquals(listOf(300L, 300L, 300L), ShareMath.splitWithMe(1200, 3))
        // 1000 / 3 份:我 334,兩個朋友各 333
        assertEquals(listOf(333L, 333L), ShareMath.splitWithMe(1000, 2))
    }

    @Test fun normalizeDropsNonPositiveAndMergesSamePerson() {
        val out = ShareMath.normalize(listOf(ShareInput(1, 200), ShareInput(2, 0), ShareInput(1, 100), ShareInput(3, -5)), 900)
        assertEquals(listOf(ShareInput(1, 300)), out)
    }

    @Test fun normalizeCapsTotalAtOwed() {
        val out = ShareMath.normalize(listOf(ShareInput(1, 500), ShareInput(2, 500)), 900)
        assertEquals(listOf(ShareInput(1, 500), ShareInput(2, 400)), out)
    }

    @Test fun normalizeNothingOwed() = assertEquals(emptyList<ShareInput>(), ShareMath.normalize(listOf(ShareInput(1, 100)), 0))

    @Test fun unassignedRemainder() {
        assertEquals(900L, ShareMath.unassigned(1200, 300, 0))
        assertEquals(600L, ShareMath.unassigned(1200, 300, 300))
        assertEquals(0L, ShareMath.unassigned(1200, 300, 900))
        assertEquals(0L, ShareMath.unassigned(1200, null, 0))
        assertEquals(0L, ShareMath.unassigned(1200, 300, 5000))
    }
}
