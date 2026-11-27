package io.lifeos.focuslab

import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class FocusExchangeV1Test {
    @Test fun unpublishedIsFreeAndMalformedInitialStateIsRejected() {
        val raw = """{"schema_version":1,"revision":0,"state":"unpublished","generated_at":null,"timezone":"Asia/Singapore","valid_until":null,"tasks":[]}"""
        assertNull(FocusExchangeV1.publishedPlan(raw))
        assertTrue(runCatching { FocusExchangeV1.publishedPlan(raw.replace("\"tasks\":[]", "\"tasks\":[{}]")) }.isFailure)
    }
    @Test fun rawCellsKeepFormulaLikeTextAndNeverTruncate() {
        assertEquals("=sample", FocusExchangeV1.row(listOf("=sample")).single())
        assertTrue(runCatching { FocusExchangeV1.row(listOf("x".repeat(40001))) }.isFailure)
        assertTrue(FocusExchangeV1.sameRow(listOf("device", 100L, true, ""), JSONArray("[\"device\",100.0,true]")))
        assertFalse(FocusExchangeV1.sameRow(listOf("device", 100L), JSONArray("[\"other\",100]")))
    }
    @Test fun dailyColumnContractAndCategoryIdsAreStable() {
        assertEquals(16, FocusExchangeV1.headers.getValue("Usage Daily").size)
        assertEquals(15, FocusExchangeV1.headers.getValue("Sync State").size)
        assertEquals("education", FocusExchangeV1.category(AppCategory.STUDY))
        assertEquals("unknown", FocusExchangeV1.category(AppCategory.UNKNOWN))
    }
}
