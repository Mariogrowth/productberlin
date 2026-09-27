package net.productberlin.presentation.designsystem.showcase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.productberlin.presentation.designsystem.showcase.DesignSystemShowcase
import net.productberlin.presentation.designsystem.testing.DesignSystemTest
import react.create

class DesignSystemShowcaseTest : DesignSystemTest() {
    @Test
    fun catalogueMountsAllComponentSections() {
        render(DesignSystemShowcase.create())
        assertEquals(3, container.querySelectorAll("section").length)
        assertEquals(4, container.querySelectorAll(".pb-result").length)
        assertTrue(input("[type='checkbox']").checked)
    }
}
