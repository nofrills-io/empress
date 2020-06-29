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
import io.nofrills.empress.android.enthrone
import io.nofrills.empress.test_support.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineDispatcher
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class EmpressFragmentTest {
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
            val backend = fragment.enthrone("test", SampleEmpress())
            assertSame(backend, fragment.enthrone("test", SampleEmpress()))
        }
    }

    @Test
    fun distinctEmpressIds() {
        val scenario = launchFragment<Fragment>()
        scenario.onFragment { fragment ->
            val api1 = fragment.enthrone("empress1", SampleEmpress())
            val api2 = fragment.enthrone("empress2", SampleEmpress())
            assertNotSame(api1, api2)
        }
    }

    private fun <T : WithEmpress> recreationTest(
        scenario: CommonScenario<T>,
        retainInstance: Boolean,
        finalCounterValue: Int
    ) {
        val dispatcher = TestCoroutineDispatcher()

        scenario.onScenario { s ->
            val empressApi = s.enthroneEmpress(dispatcher, retainInstance)
            empressApi.post { increment() }

            assertEquals(Model.Counter(1), empressApi.get(Model.Counter::class.java))
            assertEquals(Model.ParcelableCounter(1), empressApi.get(Model.ParcelableCounter::class.java))
        }

        scenario.recreate()

        scenario.onScenario { s ->
            val empressApi = s.enthroneEmpress(dispatcher, retainInstance)
            assertEquals(Model.Counter(finalCounterValue), empressApi.get(Model.Counter::class.java))
            assertEquals(Model.ParcelableCounter(1), empressApi.get(Model.ParcelableCounter::class.java))
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
}
