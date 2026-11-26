package io.lifeos.focuslab

import org.junit.Assert.*
import org.junit.Test

class AppClassifierTest {
    @Test fun manualCategoryBeatsEveryAutomaticSourceOnRepeatedScans() {
        repeat(3) {
            val result = AppClassifier.classify("tv.danmaku.bili", "视频", 0, AppCategory.STUDY)
            assertEquals(AppCategory.STUDY, result.category)
            assertEquals(ClassificationSource.USER, result.source)
        }
    }
    @Test fun exactPackageBeatsPlatformAndPlatformBeatsKeywords() {
        assertEquals(AppCategory.MEDIA, AppClassifier.classify("tv.danmaku.bili", "游戏", 0).category)
        val platform = AppClassifier.classify("example.unknown", "购物游戏", 7)
        assertEquals(AppCategory.PRODUCTIVITY, platform.category)
        assertEquals(ClassificationSource.ANDROID, platform.source)
    }
    @Test fun keywordsStayTentativeAndUnknownAppsAreNotInvented() {
        val guessed = AppClassifier.classify("example.notes", "我的笔记", -1)
        assertEquals(AppCategory.PRODUCTIVITY, guessed.category)
        assertEquals(ClassificationConfidence.LOW, guessed.confidence)
        assertEquals(AppCategory.UNKNOWN, AppClassifier.classify("example.zzz", "ZZZ", -1).category)
    }
    @Test fun protectedAndAppExceptionsHaveTheRequiredPrecedence() {
        assertTrue(CategoryAccessPolicy.allows(true, false, false))
        assertFalse(CategoryAccessPolicy.allows(false, false, true))
        assertTrue(CategoryAccessPolicy.allows(false, true, false))
        assertTrue(CategoryAccessPolicy.allows(false, null, true))
        assertFalse(CategoryAccessPolicy.allows(false, null, false))
    }
}
