package io.nofrills.empress.test_support

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import io.nofrills.empress.EmperorApi
import io.nofrills.empress.EmpressApi
import io.nofrills.empress.android.enthrone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

class SampleActivity : FragmentActivity(), WithRuler {
    override lateinit var emperorApi: EmperorApi<Event, Model>
    override lateinit var empressApi: EmpressApi<Event, Model>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val retainInstance = if (intent.hasExtra(RETAIN_EXTRA)) {
            intent.getBooleanExtra(RETAIN_EXTRA, false)
        } else {
            null
        }

        enthroneEmpressApi(retainInstance)
        enthroneEmperorApi(retainInstance)
    }

    private fun enthroneEmpressApi(retainInstance: Boolean?) {
        empressApi = if (retainInstance != null) {
            enthrone(
                SampleEmpress(),
                retainInstance = retainInstance,
                eventDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher(),
                requestDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
            )
        } else {
            enthrone(SampleEmpress())
        }
    }

    private fun enthroneEmperorApi(retainInstance: Boolean?) {
        emperorApi = if (retainInstance != null) {
            enthrone(
                SampleEmperor(),
                retainInstance = retainInstance,
                eventDispatcher = Dispatchers.Main,
                requestDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
            )
        } else {
            enthrone(SampleEmperor())
        }
    }

    companion object {
        private const val RETAIN_EXTRA = "RETAIN_EXTRA"

        fun newIntent(
            context: Context,
            retainInstance: Boolean? = null
        ): Intent {
            return Intent(context, SampleActivity::class.java).apply {
                if (retainInstance != null) {
                    putExtra(RETAIN_EXTRA, retainInstance)
                }
            }
        }
    }
}
