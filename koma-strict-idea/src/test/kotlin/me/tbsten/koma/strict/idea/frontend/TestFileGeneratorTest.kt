package me.tbsten.koma.strict.idea.frontend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Test source-set derivation (`ide-test-code.md`). The actual file write + source-root lookup is IDE
 * integration (module structure) and is verified in a live IDE, not here.
 */
class TestFileGeneratorTest {

    @Test
    fun `deriveTestModuleName swaps the main source-set suffix for test`() {
        assertEquals("proj.test", TestFileGenerator.deriveTestModuleName("proj.main"))
        assertEquals("proj.commonTest", TestFileGenerator.deriveTestModuleName("proj.commonMain"))
        assertEquals("proj.jvmTest", TestFileGenerator.deriveTestModuleName("proj.jvmMain"))
        assertEquals("proj.iosSimulatorArm64Test", TestFileGenerator.deriveTestModuleName("proj.iosSimulatorArm64Main"))
        // main セグメントが無ければ null (呼び出し側は別のフォールバックへ)。
        assertNull(TestFileGenerator.deriveTestModuleName("proj.foo"))
    }
}
