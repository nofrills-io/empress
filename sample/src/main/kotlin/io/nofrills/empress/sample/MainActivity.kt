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
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.coroutineScope
import io.nofrills.empress.RulerApi
import io.nofrills.empress.Update
import io.nofrills.empress.android.enthrone
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var activeRuler: RulerApi<Event, *>

    private val empressApi by lazy { enthrone(EMPRESS_ID, sampleEmpress) }
    private val mutableEmpressApi by lazy { enthrone(MUTABLE_EMPRESS_ID, sampleMutableEmpress) }

    override fun onCreate(savedInstanceState: Bundle?) {
        allowDiskReads { super.onCreate(savedInstanceState) }
        setContentView(R.layout.activity_main)

        activeRuler = if (savedInstanceState?.getString(ACTIVE_RULER_EXTRA) == MUTABLE_EMPRESS_ID) {
            mutableEmpressApi
        } else {
            empressApi
        }

        updateActiveRulerTitle()

        empressApi.updates()
            .map { it.updated }
            .onStart { emit(empressApi.models().all()) }
            .filter { activeRuler == empressApi }
            .onEach { render(it) }
            .launchIn(lifecycle.coroutineScope)

        mutableEmpressApi.events()
            .map { mutableEmpressApi.models().all() }
            .onStart { emit(mutableEmpressApi.models().all()) }
            .filter { activeRuler == mutableEmpressApi }
            .onEach { renderMutable(it) }
            .launchIn(lifecycle.coroutineScope)

        setupButtonListeners()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        MenuInflater(this).inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.toggle_active_ruler -> {
                toggleActiveRuler()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val rulerId = if (activeRuler == empressApi) EMPRESS_ID else MUTABLE_EMPRESS_ID
        outState.putString(ACTIVE_RULER_EXTRA, rulerId)
    }

    private fun setupButtonListeners() {
        decrement_button.setOnClickListener {
            empressApi.post(Event.Decrement)
            mutableEmpressApi.post(Event.Decrement)
        }
        increment_button.setOnClickListener {
            empressApi.post(Event.Increment)
            mutableEmpressApi.post(Event.Increment)
        }
        send_button.setOnClickListener {
            empressApi.post(Event.SendCounter)
            mutableEmpressApi.post(Event.SendCounter)
        }
        cancel_button.setOnClickListener {
            empressApi.post(Event.CancelSendingCounter)
            mutableEmpressApi.post(Event.CancelSendingCounter)
        }
    }

    private fun toggleActiveRuler() {
        activeRuler = if (activeRuler == empressApi) {
            mutableEmpressApi
        } else {
            empressApi
        }

        updateActiveRulerTitle()
    }

    private fun updateActiveRulerTitle() {
        title = if (activeRuler == empressApi) "Empress" else "MutableEmpress"
    }

    private fun render(models: Collection<Model>) {
        for (model in models) {
            when (model) {
                is Model.Counter -> renderCount(model.count)
                is Model.Sender -> renderProgress(model.consumableState.consume(empressApi))
            }
        }
    }

    private fun renderMutable(models: Collection<MutModel>) {
        for (model in models) {
            when (model) {
                is MutModel.Counter -> renderCount(model.count)
                is MutModel.Sender -> renderProgress(model.consumableState.consume(mutableEmpressApi))
            }
        }
    }

    private fun renderProgress(senderState: SenderState) {
        when (senderState) {
            is SenderState.Sent -> showToast(R.string.counter_sent)
            is SenderState.Cancelled -> showToast(R.string.send_counter_cancelled)
        }

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
        private const val ACTIVE_RULER_EXTRA = "ACTIVE_RULER_EXTRA"
        internal const val EMPRESS_ID = "sampleEmpress"
        internal const val MUTABLE_EMPRESS_ID = "sampleMutableEmpress"
    }
}
