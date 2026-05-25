package com.m8droid

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class RuntimeRestartPolicyTest {
    @Test
    fun `manual runtime restart preserves the current project`() {
        assertFalse(RuntimeRestartPolicy.restoresStartupProjectOnManualRestart)
    }
}
