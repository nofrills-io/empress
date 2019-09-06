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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val empressApi by lazy { enthrone(SampleEmpress()) }

    override fun onCreate(savedInstanceState: Bundle?) {
        allowDiskReads { super.onCreate(savedInstanceState) }
        setContentView(R.layout.activity_main)

        lifecycle.coroutineScope.launch {
            render(empressApi.models().all())
            empressApi.updates().collect { update ->
                render(update.updated, update.event)
            }
        }

        setupButtonListeners()
    }

    private fun setupButtonListeners() {
        decrement_button.setOnClickListener {
            empressApi.post(Event.Decrement)
        }
        increment_button.setOnClickListener {
            empressApi.post(Event.Increment)
        }
        send_button.setOnClickListener {
            empressApi.post(Event.SendCounter)
        }
        cancel_button.setOnClickListener {
            empressApi.post(Event.CancelSendingCounter)
        }
    }

    private fun render(models: Collection<Model>, sourceEvent: Event? = null) {
        for (patch in models) {
            when (patch) {
                is Model.Counter -> renderCount(patch)
                is Model.Sender -> renderProgress(patch, sourceEvent)
            }
        }
    }

    private fun renderProgress(sender: Model.Sender, sourceEvent: Event? = null) {
        if (sender.requestId == null) {
            if (sourceEvent is Event.CounterSent) {
                showToast(R.string.counter_sent)
            } else if (sourceEvent is Event.CancelSendingCounter) {
                showToast(R.string.send_counter_cancelled)
            }
        }
        progress_bar.visibility = if (sender.requestId != null) {
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

    private fun renderCount(counter: Model.Counter) {
        counter_value.text = counter.count.toString()
    }
}
