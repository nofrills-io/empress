package io.nofrills.empress.test_support

import android.os.Bundle
import androidx.fragment.app.Fragment
import io.nofrills.empress.EmperorApi
import io.nofrills.empress.EmpressApi
import io.nofrills.empress.android.enthrone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

class SampleFragment : Fragment(), WithRuler {
    override lateinit var emperorApi: EmperorApi<Event, Model>
    override lateinit var empressApi: EmpressApi<Event, Model>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val retainInstance = arguments?.getBoolean(RETAIN_ARG) ?: false

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
        private const val RETAIN_ARG = "RETAIN_ARG"

        fun argsBundle(retainInstance: Boolean): Bundle {
            return Bundle().apply {
                putBoolean(RETAIN_ARG, retainInstance)
            }
        }
    }
}
