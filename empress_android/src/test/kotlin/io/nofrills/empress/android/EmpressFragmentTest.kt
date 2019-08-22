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

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragment
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.nofrills.empress.Empress
import io.nofrills.empress.Model
import io.nofrills.empress.RequestId
import io.nofrills.empress.Requests
import io.nofrills.empress.test_support.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.lang.IllegalStateException

@RunWith(RobolectricTestRunner::class)
class EmpressFragmentTest {
    @Test
    fun activityUsageRetained() {
        val previousModel: Model<Patch> = Model.from(listOf(Patch.Counter(1)))
        val finalModel: Model<Patch> = Model.from(previousModel, listOf(Patch.Sender(RequestId(1))))
        testWithActivity(true, finalModel)
    }

    @Test
    fun activityUsageNotRetained() {
        val finalModel: Model<Patch> = Model.from(listOf(Patch.Counter(1), Patch.Sender(null)))
        testWithActivity(false, finalModel)
    }

    @Test
    fun fragmentUsageRetained() {
        val previousModel: Model<Patch> = Model.from(listOf(Patch.Counter(1)))
        val finalModel: Model<Patch> = Model.from(previousModel, listOf(Patch.Sender(RequestId(1))))
        testWithFragment(true, finalModel)
    }

    @Test
    fun fragmentUsageNotRetained() {
        val finalModel: Model<Patch> = Model.from(listOf(Patch.Counter(1), Patch.Sender(null)))
        testWithFragment(false, finalModel)
    }

    @Test(expected = OnEventFailure::class)
    fun onEventExceptionInFragment() {
        val fragmentScenario =
            launchFragment<SampleFragment>(SampleFragment.argsBundle(false))
        val scenario = Scenario.FromFragment(fragmentScenario)

        scenario.onScenario {
            val api = it.api

            runBlocking {
                val deferredUpdates = async {
                    api.updates().toList()
                }

                api.send(Event.GetFailure).join()
                api.interrupt()

                deferredUpdates.await()
            }
        }
    }

    @Test(expected = OnRequestFailure::class)
    fun onRequestExceptionInActivity() {
        val intent = SampleActivity.newIntent(
            InstrumentationRegistry.getInstrumentation().context,
            false,
            SampleActivity.DispatcherType.SINGLE
        )
        val activityScenario: ActivityScenario<SampleActivity> = ActivityScenario.launch(intent)
        val scenario = Scenario.FromActivity(activityScenario)

        scenario.onScenario {
            val api = it.api

            runBlocking {
                val deferredUpdates = async {
                    api.updates().toList()
                }
                api.send(Event.GetFailureWithRequest)
                deferredUpdates.await()
            }
        }
    }

    @Test
    fun doubleEnthrone() {
        val scenario = launchFragment<Fragment>()
        scenario.onFragment { fragment ->
            val backend = fragment.enthrone(SampleEmpress())
            assertSame(backend, fragment.enthrone(SampleEmpress()))
        }
    }

    @Test(expected = IllegalStateException::class)
    fun doubleEnthroneWithInvalidEmpressInFragment() {
        val scenario = launchFragment<Fragment>()
        scenario.onFragment { fragment ->
            fragment.enthrone(SampleEmpress("my_empress"))
            fragment.enthrone(EmptyEmpress("my_empress"))
        }
    }

    @Test(expected = IllegalStateException::class)
    fun doubleEnthroneWithInvalidEmpressInActivity() {
        val intent = SampleActivity.newIntent(
            InstrumentationRegistry.getInstrumentation().context,
            false,
            SampleActivity.DispatcherType.SINGLE
        )
        val activityScenario: ActivityScenario<SampleActivity> = ActivityScenario.launch(intent)
        val scenario = Scenario.FromActivity(activityScenario)
        scenario.onScenario { activity ->
            activity.enthrone(EmptyEmpress(SampleEmpress::class.java.name))
        }
    }

    @Test
    fun twoEmpressesWithDistinctIds() {
        val scenario = launchFragment<Fragment>()
        scenario.onFragment { fragment ->
            val api1 = fragment.enthrone(SampleEmpress("empress1"))
            val api2 = fragment.enthrone(SampleEmpress("empress2"))
            assertNotSame(api1, api2)
        }
    }

    private fun testWithActivity(retainEmpressInstance: Boolean, finalModel: Model<Patch>) {
        val intent = SampleActivity.newIntent(
            InstrumentationRegistry.getInstrumentation().context,
            retainEmpressInstance,
            SampleActivity.DispatcherType.SINGLE
        )
        val activityScenario: ActivityScenario<SampleActivity> = ActivityScenario.launch(intent)
        val scenario = Scenario.FromActivity(activityScenario)

        testWithScenario(scenario, finalModel)
    }

    private fun testWithFragment(retainEmpressInstance: Boolean, finalModel: Model<Patch>) {
        val fragmentScenario =
            launchFragment<SampleFragment>(SampleFragment.argsBundle(retainEmpressInstance))
        val scenario = Scenario.FromFragment(fragmentScenario)

        testWithScenario(scenario, finalModel)
    }

    private fun <T : WithEmpress> testWithScenario(
        scenario: Scenario<T>,
        finalModel: Model<Patch>
    ) {
        scenario.onScenario {
            val api = it.api

            runBlocking {
                val baseModel = Model.from(listOf(Patch.Counter(0), Patch.Sender(null)))
                assertEquals(baseModel, api.modelSnapshot())

                api.send(Event.Increment).join()
                val model1 = Model.from(baseModel, listOf(Patch.Counter(1)))
                assertEquals(model1, api.modelSnapshot())

                api.send(Event.SendCounter).join()
                val model2 = Model.from(model1, listOf(Patch.Sender(RequestId(1))))
                assertEquals(model2, api.modelSnapshot())
            }
        }

        scenario.recreate()

        scenario.onScenario {
            val api = it.api

            runBlocking {
                assertEquals(finalModel, api.modelSnapshot())
                scenario.moveToState(Lifecycle.State.DESTROYED)
            }
        }
    }

    /** Generic scenario that can be used with Activities and Fragments. */
    private sealed class Scenario<T> {
        abstract fun moveToState(newState: Lifecycle.State)
        abstract fun onScenario(fn: (T) -> Unit)
        abstract fun recreate()

        class FromFragment<F : Fragment>(private val fragmentScenario: FragmentScenario<F>) :
            Scenario<F>() {
            override fun moveToState(newState: Lifecycle.State) {
                fragmentScenario.moveToState(newState)
            }

            override fun onScenario(fn: (F) -> Unit) {
                fragmentScenario.onFragment(fn)
            }

            override fun recreate() {
                fragmentScenario.recreate()
            }
        }

        class FromActivity<A : FragmentActivity>(private val activityScenario: ActivityScenario<A>) :
            Scenario<A>() {
            override fun moveToState(newState: Lifecycle.State) {
                activityScenario.moveToState(newState)
            }

            override fun onScenario(fn: (A) -> Unit) {
                activityScenario.onActivity(fn)
            }

            override fun recreate() {
                activityScenario.recreate()
            }
        }
    }

    private class EmptyEmpress(private val id: String? = null) : Empress<Event, Patch, Request> {
        override fun id(): String {
            return id ?: super.id()
        }

        override fun initializer(): Collection<Patch> = emptyList()

        override fun onEvent(
            event: Event,
            model: Model<Patch>,
            requests: Requests<Event, Request>
        ): Collection<Patch> {
            return emptyList()
        }

        override suspend fun onRequest(request: Request): Event {
            error("mock")
        }
    }
}