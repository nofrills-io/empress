package io.nofrills.empress.test_support

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import io.nofrills.empress.EmpressApi
import io.nofrills.empress.android.empress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

class SampleActivity : FragmentActivity(), WithEmpress {
    override lateinit var api: EmpressApi<Event, Patch>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val coroutineDispatcher =
            when (DispatcherType.valueOf(intent.getStringExtra(DISPATCHER_EXTRA))) {
                DispatcherType.MAIN -> Dispatchers.Main
                DispatcherType.SINGLE -> Executors.newSingleThreadExecutor().asCoroutineDispatcher()
            }
        val retainEmpressInstance = intent.getBooleanExtra(RETAIN_EMPRESS_EXTRA, false)
        api = empress(
            SampleEmpress(),
            retainInstance = retainEmpressInstance,
            dispatcher = coroutineDispatcher
        )
    }

    enum class DispatcherType {
        MAIN, SINGLE
    }

    companion object {
        private const val DISPATCHER_EXTRA = "DISPATCHER_EXTRA"
        private const val RETAIN_EMPRESS_EXTRA = "RETAIN_EMPRESS_EXTRA"

        fun newIntent(
            context: Context,
            retainEmpressInstance: Boolean,
            dispatcherType: DispatcherType
        ): Intent {
            return Intent(context, SampleActivity::class.java).apply {
                putExtra(DISPATCHER_EXTRA, dispatcherType.name)
                putExtra(RETAIN_EMPRESS_EXTRA, retainEmpressInstance)
            }
        }
    }
}
