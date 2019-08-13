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

package io.nofrills.empress.sample

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.coroutineScope
import io.nofrills.empress.EmpressApi
import io.nofrills.empress.android.enthrone
import io.nofrills.empress.test_support.Event
import io.nofrills.empress.test_support.Patch
import io.nofrills.empress.test_support.SampleEmpress
import kotlinx.android.synthetic.main.fragment_counter.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class CounterFragment : Fragment() {
    private lateinit var api: EmpressApi<Event, Patch>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // we call `enthrone` on activity,
        // since we want to share the Empress instance
        api = requireActivity().enthrone(SampleEmpress())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_counter, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lifecycle.coroutineScope.launch {
            renderCount(api.modelSnapshot().get())
            api.updates().collect {
                it.model.updated()
                    .filterIsInstance<Patch.Counter>()
                    .forEach(this@CounterFragment::renderCount)
            }
        }
    }

    private fun renderCount(counter: Patch.Counter) {
        counter_text_view.text = counter.count.toString()
    }
}