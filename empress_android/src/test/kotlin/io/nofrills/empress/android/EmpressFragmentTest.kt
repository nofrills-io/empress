package io.nofrills.empress.android

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragment
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.nofrills.empress.Model
import io.nofrills.empress.RequestId
import io.nofrills.empress.test_support.*
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EmpressFragmentTest {
    @Test
    fun activityUsageRetained() {
        val previousModel: Model<Patch> = Model(listOf(Patch.Counter(1)))
        val finalModel: Model<Patch> = Model(previousModel, listOf(Patch.Sender(RequestId(1))))
        testWithActivity(true, finalModel)
    }

    @Test
    fun activityUsageNotRetained() {
        val finalModel: Model<Patch> = Model(listOf(Patch.Counter(1), Patch.Sender(null)))
        testWithActivity(false, finalModel)
    }

    @Test(expected = IllegalStateException::class)
    fun failsWithMainDispatcher() {
        val intent = SampleActivity.newIntent(
            InstrumentationRegistry.getInstrumentation().context,
            false,
            SampleActivity.DispatcherType.MAIN
        )
        ActivityScenario.launch<SampleActivity>(intent)
    }

    @Test
    fun fragmentUsageRetained() {
        val previousModel: Model<Patch> = Model(listOf(Patch.Counter(1)))
        val finalModel: Model<Patch> = Model(previousModel, listOf(Patch.Sender(RequestId(1))))
        testWithFragment(true, finalModel)
    }

    @Test
    fun fragmentUsageNotRetained() {
        val finalModel: Model<Patch> = Model(listOf(Patch.Counter(1), Patch.Sender(null)))
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

                api.send(Event.GetFailure)
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
                api.interrupt()

                deferredUpdates.await()
            }
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
                val baseModel = Model(listOf(Patch.Counter(0), Patch.Sender(null)))
                assertEquals(baseModel, api.modelSnapshot())

                api.send(Event.Increment)
                val model1 = Model(baseModel, listOf(Patch.Counter(1)))
                assertEquals(model1, api.modelSnapshot())

                api.send(Event.SendCounter)
                val model2 = Model(model1, listOf(Patch.Sender(RequestId(1))))
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
    internal sealed class Scenario<T> {
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
}