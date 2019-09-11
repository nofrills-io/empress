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

package io.nofrills.empress.android.internal

import android.app.Activity
import androidx.fragment.app.Fragment
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragment
import androidx.test.core.app.ActivityScenario
import io.nofrills.empress.Models
import io.nofrills.empress.MutableEmpress
import io.nofrills.empress.RequestCommander
import io.nofrills.empress.android.enthrone
import io.nofrills.empress.test_support.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineDispatcher
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class RulerFragmentTest {
    @Test
    fun retainedActivity() {
        val scenario = activityScenario()
        recreationTest(scenario, true, 1)
    }

    @Test
    fun unretainedActivity() {
        val scenario = activityScenario()
        recreationTest(scenario, false, 0)
    }

    @Test
    fun retainedFragment() {
        val scenario = fragmentScenario()
        recreationTest(scenario, true, 1)
    }

    @Test
    fun unretainedFragment() {
        val scenario = fragmentScenario()
        recreationTest(scenario, false, 0)
    }

    @Test
    fun doubleEnthrone() {
        val scenario = launchFragment<Fragment>()
        scenario.onFragment { fragment ->
            val backend = fragment.enthrone(SampleMutableEmpress())
            assertSame(backend, fragment.enthrone(SampleMutableEmpress()))
        }
    }

    @Test(expected = IllegalStateException::class)
    fun doubleEnthroneWithInvalidMutableEmpressInFragment() {
        val scenario = launchFragment<Fragment>()
        scenario.onFragment { fragment ->
            fragment.enthrone(SampleMutableEmpress("my_empress"))
            fragment.enthrone(
                EmptyMutableEmpress(
                    "my_empress"
                )
            )
        }
    }

    @Test(expected = IllegalStateException::class)
    fun doubleEnthroneWithInvalidMutableEmpressInActivity() {
        val activityScenario = ActivityScenario.launch(SampleActivity::class.java)
        activityScenario.onActivity { activity ->
            activity.enthrone(SampleMutableEmpress())
            activity.enthrone(
                EmptyMutableEmpress(
                    SampleMutableEmpress::class.java.name
                )
            )
        }
    }

    @Test
    fun twoEmperorsWithDistinctIds() {
        val scenario = launchFragment<Fragment>()
        scenario.onFragment { fragment ->
            val api1 = fragment.enthrone(SampleMutableEmpress("empress1"))
            val api2 = fragment.enthrone(SampleMutableEmpress("empress2"))
            assertNotSame(api1, api2)
        }
    }

    private fun <T : WithRuler> recreationTest(
        scenario: CommonScenario<T>,
        retainInstance: Boolean,
        finalCounterValue: Int
    ) {
        val dispatcher = TestCoroutineDispatcher()

        scenario.onScenario {
            val empressApi = it.enthroneEmpress(dispatcher, retainInstance)
            val mutableEmpressApi = it.enthroneMutableEmpress(dispatcher, retainInstance)

            empressApi.post(Event.Increment)
            mutableEmpressApi.post(Event.Increment)

            runBlocking {
                assertEquals(1, empressApi.models()[Model.Counter::class].count)
                assertEquals(1, empressApi.models()[Model.ParcelableCounter::class].count)
            }

            assertEquals(1, mutableEmpressApi.models()[Model.Counter::class].count)
            assertEquals(1, mutableEmpressApi.models()[Model.ParcelableCounter::class].count)
        }

        scenario.recreate()

        scenario.onScenario {
            val empressApi = it.enthroneEmpress(dispatcher, retainInstance)
            val mutableEmpressApi = it.enthroneMutableEmpress(dispatcher, retainInstance)

            runBlocking {
                assertEquals(finalCounterValue, empressApi.models()[Model.Counter::class].count)
                assertEquals(1, empressApi.models()[Model.ParcelableCounter::class].count)
            }

            assertEquals(finalCounterValue, mutableEmpressApi.models()[Model.Counter::class].count)
            assertEquals(1, mutableEmpressApi.models()[Model.ParcelableCounter::class].count)
        }
    }

    private fun activityScenario(): CommonScenario<SampleActivity> {
        return CommonScenario.FromActivity(
            ActivityScenario.launch(SampleActivity::class.java)
        )
    }

    private fun fragmentScenario(): CommonScenario<SampleFragment> {
        return CommonScenario.FromFragment(
            launchFragment()
        )
    }

    private sealed class CommonScenario<T> {
        abstract fun onScenario(body: (T) -> Unit)
        abstract fun recreate()

        class FromActivity<A : Activity>(private val scenario: ActivityScenario<A>) :
            CommonScenario<A>() {

            override fun onScenario(body: (A) -> Unit) {
                scenario.onActivity(body)
            }

            override fun recreate() {
                scenario.recreate()
            }
        }

        class FromFragment<F : Fragment>(private val scenario: FragmentScenario<F>) :
            CommonScenario<F>() {

            override fun onScenario(body: (F) -> Unit) {
                scenario.onFragment(body)
            }

            override fun recreate() {
                scenario.recreate()
            }
        }
    }

    private class EmptyMutableEmpress(private val id: String? = null) :
        MutableEmpress<Event, Model, Request> {
        override fun id(): String {
            return id ?: super.id()
        }

        override fun initialize(): Collection<Model> = emptyList()

        override fun onEvent(
            event: Event,
            models: Models<Model>,
            requests: RequestCommander<Request>
        ) {
        }

        override suspend fun onRequest(request: Request): Event {
            error("mock")
        }
    }
}
