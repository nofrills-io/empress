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
import io.nofrills.empress.android.enthrone
import io.nofrills.empress.android.enthronement
import kotlinx.android.synthetic.main.fragment_counter.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class CounterFragment : Fragment() {
//    private val empress by requireActivity().enthronement(sampleEmpress)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_counter, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // we call `enthrone` on activity,
        // since we want to share the Empress instance
        val empress = requireActivity().enthrone(sampleEmpress)

        lifecycle.coroutineScope.launch {
            renderCount(empress.models()[Model.Counter::class])
            empress.updates().collect {
                it.updated
                    .filterIsInstance<Model.Counter>()
                    .forEach(this@CounterFragment::renderCount)
            }
        }
    }

    private fun renderCount(counter: Model.Counter) {
        counter_text_view.text = counter.count.toString()
    }
}
