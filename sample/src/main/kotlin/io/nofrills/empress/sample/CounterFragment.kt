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
import io.nofrills.empress.sample.databinding.FragmentCounterBinding
import kotlinx.coroutines.flow.*

class CounterFragment : Fragment() {
    private var binding: FragmentCounterBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentCounterBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // we call `enthrone` on activity,
        // since we want to share the Empress instance
        val empressApi = requireActivity().enthrone(MainActivity.EMPRESS_ID, ::SampleEmpress)

        empressApi.model { counter }
            .onEach { renderCount(it) }
            .launchIn(lifecycle.coroutineScope)
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private fun renderCount(counter: Counter) {
        binding?.counterTextView?.text = counter.count.toString()
    }
}
