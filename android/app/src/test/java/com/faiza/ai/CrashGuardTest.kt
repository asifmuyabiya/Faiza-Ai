package com.faiza.ai

import com.faiza.ai.util.CrashReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashGuardTest {

    @Test fun guardedScopeSurvivesAFailingJob() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CrashReporter.guard)
        scope.launch { error("boom") }.join()
        var ran = false
        scope.launch { ran = true }.join()
        assertTrue("the scope keeps working after a failure", ran)
    }
}
