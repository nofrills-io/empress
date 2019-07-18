package io.nofrills.empress.sample

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import io.nofrills.empress.Update
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
                when (update) {
                    is Update.Initial -> render(update.model.all())
                    is Update.FromEvent -> render(update.model.updated(), showFeedback = true)
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
    }

    private fun render(patches: Collection<Patch>, showFeedback: Boolean = false) {
        for (patch in patches) {
            when (patch) {
                is Patch.Counter -> renderCount(patch)
                is Patch.Sender -> renderProgress(patch, showFeedback)
            }
        }
    }

    private fun renderProgress(patch: Patch.Sender, showFeedback: Boolean) {
        if (showFeedback && !patch.isSending) {
            Toast.makeText(this, R.string.counter_sent, Toast.LENGTH_LONG).show()
        }
        progress_bar.visibility = if (patch.isSending) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun renderCount(patch: Patch.Counter) {
        counter_value.text = patch.count.toString()
    }
}
