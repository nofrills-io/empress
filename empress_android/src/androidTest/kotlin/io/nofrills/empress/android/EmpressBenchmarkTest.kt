/*
 * Copyright 2019 Mateusz Armatys
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.nofrills.empress.android

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.nofrills.empress.EmpressApi
import io.nofrills.empress.EmpressBackend
import io.nofrills.empress.test_support.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScope
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EmpressBenchmarkTest {
    @get:Rule
    val benchmarkRule = BenchmarkRule()

    @Test
    fun dslBuilder() {
        benchmarkRule.measureRepeated {
            buildEmpress()
        }
    }

    @Test
    fun annotationBuilder() {
        benchmarkRule.measureRepeated {
            Empress_AnnotatedEmpress(AnnotatedEmpress())
        }
    }

    @Test
    fun dslBuilderSending() {
        val empress = buildEmpress()
        benchmarkRule.measureRepeated {
            val scope = TestCoroutineScope()
            val api = EmpressBackend(empress, scope, null)
            runApiTest(api)
            scope.cleanupTestCoroutines()
        }
    }

    @Test
    fun annotationBuilderSending() {
        val empress = Empress_AnnotatedEmpress(AnnotatedEmpress())
        benchmarkRule.measureRepeated {
            val scope = TestCoroutineScope()
            val api = EmpressBackend(empress, scope, null)
            runApiTest(api)
            scope.cleanupTestCoroutines()
        }
    }

    private fun runApiTest(api: EmpressApi<Event, Patch>) {
        runBlocking {
            coroutineScope {
                api.send(Event.Increment)
                api.send(Event.Increment)
                api.send(Event.Decrement)
                api.send(Event.SendCounter)
                api.send(Event.CancelSendingCounter)
            }
            api.modelSnapshot()
        }
    }
}