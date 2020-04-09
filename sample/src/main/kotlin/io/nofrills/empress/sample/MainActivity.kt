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
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart

class MainActivity : AppCompatActivity() {
    private val empressApi by lazy { enthrone(EMPRESS_ID, SampleEmpress()) }

    override fun onCreate(savedInstanceState: Bundle?) {
        allowDiskReads { super.onCreate(savedInstanceState) }
        setContentView(R.layout.activity_main)

        empressApi.signals()
            .onEach { onSignal(it) }
            .launchIn(lifecycle.coroutineScope)

        empressApi.updates()
            .onEach { render(it) }
            .launchIn(lifecycle.coroutineScope)

        setupButtonListeners()
    }

    private fun setupButtonListeners() {
        decrement_button.setOnClickListener {
            empressApi.post { decrement() }
        }
        increment_button.setOnClickListener {
            empressApi.post { increment() }
        }
        send_button.setOnClickListener {
            empressApi.post { sendCounter() }
        }
        cancel_button.setOnClickListener {
            empressApi.post { cancelSendingCounter() }
        }
    }

    private fun onSignal(signal: Signal) {
        when (signal) {
            Signal.CounterSent -> showToast(R.string.counter_sent)
            Signal.CounterSendCancelled -> showToast(R.string.send_counter_cancelled)
        }
    }

    private fun render(model: Model) {
        when (model) {
            is Model.Counter -> renderCount(model.count)
            is Model.Sender -> renderProgress(model.state)
        }
    }

    private fun renderProgress(senderState: SenderState) {
        progress_bar.visibility = if (senderState is SenderState.Sending) {
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
        counter_value.text = count.toString()
    }

    companion object {
        internal const val EMPRESS_ID = "sampleEmpress"
    }
}
