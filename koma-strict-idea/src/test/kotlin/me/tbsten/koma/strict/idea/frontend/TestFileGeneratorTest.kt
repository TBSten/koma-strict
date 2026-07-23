package me.tbsten.koma.strict.idea.frontend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test source-set selection logic (`ide-test-code.md`). Only the pure decision functions are unit-tested
 * here; the actual VFS write + source-root lookup (module structure, generated-root exclusion) is IDE
 * integration verified in a live IDE.
 */
class TestFileGeneratorTest {

    @Test
    fun `preferredTestSegments maps a main source set to its host unit-test set`() {
        assertEquals(listOf("test", "unitTest"), TestFileGenerator.preferredTestSegments("main"))
        assertEquals(listOf("commonTest"), TestFileGenerator.preferredTestSegments("commonMain"))
        assertEquals(listOf("jvmTest"), TestFileGenerator.preferredTestSegments("jvmMain"))
        assertEquals(listOf("iosSimulatorArm64Test"), TestFileGenerator.preferredTestSegments("iosSimulatorArm64Main"))
        // main セグメントが無ければ空 (呼び出し側は commonTest ランクへフォールバック)。
        assertEquals(emptyList<String>(), TestFileGenerator.preferredTestSegments("foo"))
    }

    @Test
    fun `androidMain targets the host unit-test set, never the instrumented androidTest`() {
        val segments = TestFileGenerator.preferredTestSegments("androidMain")
        assertEquals(listOf("androidUnitTest", "androidHostTest", "unitTest"), segments)
        assertFalse("androidTest" in segments)
    }

    @Test
    fun `isUnitTestSegment accepts host unit-test source sets`() {
        assertTrue(TestFileGenerator.isUnitTestSegment("test"))
        assertTrue(TestFileGenerator.isUnitTestSegment("commonTest"))
        assertTrue(TestFileGenerator.isUnitTestSegment("jvmTest"))
        assertTrue(TestFileGenerator.isUnitTestSegment("androidUnitTest"))
        assertTrue(TestFileGenerator.isUnitTestSegment("androidHostTest"))
    }

    @Test
    fun `isUnitTestSegment rejects instrumented, per-variant, and non-test source sets`() {
        // instrumented / device (計測テスト) は host unit test ではない。
        assertFalse(TestFileGenerator.isUnitTestSegment("androidTest"))
        assertFalse(TestFileGenerator.isUnitTestSegment("androidInstrumentedTest"))
        assertFalse(TestFileGenerator.isUnitTestSegment("androidDeviceTest"))
        // variant 固有の集約 source set (build/generated を引き込む元凶) は避ける。
        assertFalse(TestFileGenerator.isUnitTestSegment("debugUnitTest"))
        assertFalse(TestFileGenerator.isUnitTestSegment("releaseUnitTest"))
        assertFalse(TestFileGenerator.isUnitTestSegment("testDebugUnitTest"))
        // そもそも test source set でないもの。
        assertFalse(TestFileGenerator.isUnitTestSegment("main"))
        assertFalse(TestFileGenerator.isUnitTestSegment("commonMain"))
        assertFalse(TestFileGenerator.isUnitTestSegment("androidMain"))
    }

    @Test
    fun `testSegmentRank prefers the exact target, then commonTest, then any other`() {
        val preferred = TestFileGenerator.preferredTestSegments("androidMain") // [androidUnitTest, androidHostTest, unitTest]
        val rank = { segment: String -> TestFileGenerator.testSegmentRank(segment, preferred) }
        // preferred 内は宣言順に優先。
        assertTrue(rank("androidUnitTest") > rank("androidHostTest"))
        assertTrue(rank("androidHostTest") > rank("unitTest"))
        // preferred の次に commonTest、その次にそれ以外 (jvmTest 等)。
        assertTrue(rank("unitTest") > rank("commonTest"))
        assertTrue(rank("commonTest") > rank("jvmTest"))
    }
}
