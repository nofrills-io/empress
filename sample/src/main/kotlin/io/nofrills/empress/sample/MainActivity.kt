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
import android.view.View
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.coroutineScope
import io.nofrills.empress.android.enthrone
import io.nofrills.empress.sample.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val empressApi by lazy { enthrone(EMPRESS_ID, ::SampleEmpress) }

    override fun onCreate(savedInstanceState: Bundle?) {
        allowDiskReads { super.onCreate(savedInstanceState) }
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        empressApi.signal { counterSignal }
            .onEach { onSignal(it) }
            .launchIn(lifecycle.coroutineScope)

        empressApi.model { counter }
            .onEach { renderCount(it.count) }
            .launchIn(lifecycle.coroutineScope)

        empressApi.model { sender }
            .onEach { renderProgress(it) }
            .launchIn(lifecycle.coroutineScope)

        setupButtonListeners()
    }

    private fun setupButtonListeners() {
        binding.decrementButton.setOnClickListener {
            empressApi.post { decrement() }
        }
        binding.incrementButton.setOnClickListener {
            empressApi.post { increment() }
        }
        binding.sendButton.setOnClickListener {
            empressApi.post { sendCounter() }
        }
        binding.cancelButton.setOnClickListener {
            empressApi.post { cancelSendingCounter() }
        }
    }

    private fun onSignal(signal: CounterSignal) {
        when (signal) {
            CounterSignal.CounterSent -> showToast(R.string.counter_sent)
            CounterSignal.CounterSendCancelled -> showToast(R.string.send_counter_cancelled)
        }
    }

    private fun renderProgress(sender: Sender) {
        binding.progressBar.visibility = if (sender is Sender.Sending) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun showToast(@StringRes msg: Int) {
        allowDiskReads {
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        }
    }

    private fun renderCount(count: Int) {
        binding.counterValue.text = count.toString()
    }

    companion object {
        internal const val EMPRESS_ID = "sampleEmpress"
    }
}
