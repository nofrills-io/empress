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

package io.nofrills.empress.test_support

import android.os.Bundle
import androidx.fragment.app.Fragment
import io.nofrills.empress.EmpressApi
import io.nofrills.empress.android.enthrone
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

class SampleFragment : Fragment(), WithEmpress {
    override lateinit var api: EmpressApi<Event, Patch>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val retainEmpressInstance = arguments?.getBoolean(RETAIN_EMPRESS_ARG) ?: false
        api = enthrone(
            SampleEmpress(),
            retainInstance = retainEmpressInstance,
            dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        )
    }

    companion object {
        private const val RETAIN_EMPRESS_ARG = "RETAIN_EMPRESS_ARG"

        fun argsBundle(retainEmpressInstance: Boolean): Bundle {
            return Bundle().apply {
                putBoolean(RETAIN_EMPRESS_ARG, retainEmpressInstance)
            }
        }
    }
}
