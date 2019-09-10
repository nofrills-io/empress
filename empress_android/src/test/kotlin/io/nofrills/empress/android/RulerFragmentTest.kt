package io.nofrills.empress.android

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.nofrills.empress.MutableEmpress
import io.nofrills.empress.Models
import io.nofrills.empress.RequestCommander
import io.nofrills.empress.test_support.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RulerFragmentTest {
    @Test
    fun retainedActivity() {
        val scenario = activityScenario(true)
        recreationTest(scenario, 1)
    }

    @Test
    fun unretainedActivity() {
        val scenario = activityScenario(false)
        recreationTest(scenario, 0)
    }

    @Test
    fun retainedFragment() {
        val scenario = fragmentScenario(true)
        recreationTest(scenario, 1)
    }

    @Test
    fun unretainedFragment() {
        val scenario = fragmentScenario(false)
        recreationTest(scenario, 0)
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
            fragment.enthrone(EmptyMutableEmpress("my_empress"))
        }
    }

    @Test(expected = IllegalStateException::class)
    fun doubleEnthroneWithInvalidMutableEmpressInActivity() {
        val intent = SampleActivity.newIntent(InstrumentationRegistry.getInstrumentation().context)
        val activityScenario: ActivityScenario<SampleActivity> = ActivityScenario.launch(intent)
        activityScenario.onActivity { activity ->
            activity.enthrone(EmptyMutableEmpress(SampleMutableEmpress::class.java.name))
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
        finalCounterValue: Int
    ) {
        scenario.onScenario {
            it.mutableEmpressApi.post(Event.Increment)
            it.empressApi.post(Event.Increment)
            assertEquals(1, it.mutableEmpressApi.models()[Model.Counter::class].count)
            assertEquals(1, it.mutableEmpressApi.models()[Model.ParcelableCounter::class].count)

            runBlocking {
                assertEquals(1, it.empressApi.models()[Model.Counter::class].count)
                assertEquals(1, it.empressApi.models()[Model.ParcelableCounter::class].count)
            }
        }
        scenario.recreate()
        scenario.onScenario {
            assertEquals(finalCounterValue, it.mutableEmpressApi.models()[Model.Counter::class].count)
            assertEquals(1, it.mutableEmpressApi.models()[Model.ParcelableCounter::class].count)

            runBlocking {
                assertEquals(finalCounterValue, it.empressApi.models()[Model.Counter::class].count)
                assertEquals(1, it.empressApi.models()[Model.ParcelableCounter::class].count)
            }
        }
    }

    private fun activityScenario(retainInstance: Boolean): CommonScenario<SampleActivity> {
        val intent = SampleActivity.newIntent(
            InstrumentationRegistry.getInstrumentation().context,
            retainInstance
        )
        return CommonScenario.FromActivity(intent)
    }

    private fun fragmentScenario(retainInstance: Boolean): CommonScenario<SampleFragment> {
        val args = SampleFragment.argsBundle(retainInstance)
        return CommonScenario.FromFragment(SampleFragment::class.java, args)
    }

    private sealed class CommonScenario<T> {
        abstract fun onScenario(body: (T) -> Unit)
        abstract fun recreate()

        class FromActivity<A : Activity>(private val scenario: ActivityScenario<A>) :
            CommonScenario<A>() {

            constructor(intent: Intent) : this(ActivityScenario.launch(intent))

            override fun onScenario(body: (A) -> Unit) {
                scenario.onActivity(body)
            }

            override fun recreate() {
                scenario.recreate()
            }
        }

        class FromFragment<F : Fragment>(private val scenario: FragmentScenario<F>) :
            CommonScenario<F>() {

            constructor(fragmentClass: Class<F>, args: Bundle) : this(
                FragmentScenario.launch(fragmentClass, args)
            )

            override fun onScenario(body: (F) -> Unit) {
                scenario.onFragment(body)
            }

            override fun recreate() {
                scenario.recreate()
            }
        }
    }

    private class EmptyMutableEmpress(private val id: String? = null) : MutableEmpress<Event, Model, Request> {
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
