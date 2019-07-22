package io.nofrills.empress.sample

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import io.nofrills.empress.android.empress
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

class MainActivity : AppCompatActivity(), CoroutineScope {
    private val supervisorJob = SupervisorJob()

    override val coroutineContext: CoroutineContext = Dispatchers.Main + supervisorJob

    private val empressApi by lazy { empress(CounterEmpress()) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        launch {
            empressApi.updates().collect { update ->
                if (update.event == null) {
                    render(update.model.all())
                } else {
                    render(update.model.updated(), update.event)
                }
            }
        }

        setupButtonListeners()
    }

    override fun onDestroy() {
        supervisorJob.cancel()
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
        if (patch.isSending == null) {
            if (sourceEvent is Event.CounterSent) {
                Toast.makeText(this, R.string.counter_sent, Toast.LENGTH_LONG).show()
            } else if (sourceEvent is Event.CancelSendingCounter) {
                Toast.makeText(this, R.string.send_counter_cancelled, Toast.LENGTH_LONG).show()
            }
        }
        progress_bar.visibility = if (patch.isSending != null) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun renderCount(patch: Patch.Counter) {
        counter_value.text = patch.count.toString()
    }
}
