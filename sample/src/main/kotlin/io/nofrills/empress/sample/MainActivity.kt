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
import android.os.Handler
import android.view.View
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import io.nofrills.empress.android.enthrone
import io.nofrills.empress.test_support.Event
import io.nofrills.empress.test_support.Patch
import io.nofrills.empress.test_support.SampleEmpress
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.*
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.flow.collect
import kotlin.coroutines.CoroutineContext

class MainActivity : AppCompatActivity(), CoroutineScope {
    private val job = Job()

    private val mainDispatcher: CoroutineDispatcher by lazy { Handler().asCoroutineDispatcher() }

    override val coroutineContext: CoroutineContext = mainDispatcher + job

    private val empressApi by lazy { enthrone(SampleEmpress()) }

    override fun onCreate(savedInstanceState: Bundle?) {
        allowDiskReads { super.onCreate(savedInstanceState) }
        setContentView(R.layout.activity_main)

        launch {
            render(empressApi.modelSnapshot().all())
            empressApi.updates().collect { update ->
                render(update.model.updated(), update.event)
            }
        }

        setupButtonListeners()
    }

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
    }

    private fun setupButtonListeners() {
        decrement_button.setOnClickListener {
            empressApi.send(Event.Decrement)
        }
        increment_button.setOnClickListener {
            empressApi.send(Event.Increment)
        }
        send_button.setOnClickListener {
            empressApi.send(Event.SendCounter)
        }
        cancel_button.setOnClickListener {
            empressApi.send(Event.CancelSendingCounter)
        }
    }

    private fun render(patches: Collection<Patch>, sourceEvent: Event? = null) {
        for (patch in patches) {
            when (patch) {
                is Patch.Counter -> renderCount(patch)
                is Patch.Sender -> renderProgress(patch, sourceEvent)
            }
        }
    }

    private fun renderProgress(patch: Patch.Sender, sourceEvent: Event? = null) {
        if (patch.requestId == null) {
            if (sourceEvent is Event.CounterSent) {
                showToast(R.string.counter_sent)
            } else if (sourceEvent is Event.CancelSendingCounter) {
                showToast(R.string.send_counter_cancelled)
            }
        }
        progress_bar.visibility = if (patch.requestId != null) {
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

    private fun renderCount(patch: Patch.Counter) {
        counter_value.text = patch.count.toString()
    }
}
