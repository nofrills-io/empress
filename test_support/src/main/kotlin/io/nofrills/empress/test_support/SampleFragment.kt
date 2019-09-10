package io.nofrills.empress.test_support

import android.os.Bundle
import androidx.fragment.app.Fragment
import io.nofrills.empress.MutableEmpressApi
import io.nofrills.empress.EmpressApi
import io.nofrills.empress.android.enthrone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

class SampleFragment : Fragment(), WithRuler {
    override lateinit var mutableEmpressApi: MutableEmpressApi<Event, Model>
    override lateinit var empressApi: EmpressApi<Event, Model>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val retainInstance = arguments?.getBoolean(RETAIN_ARG) ?: false

        enthroneEmpress(retainInstance)
        enthroneMutableEmpress(retainInstance)
    }

    private fun enthroneEmpress(retainInstance: Boolean?) {
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

    private fun enthroneMutableEmpress(retainInstance: Boolean?) {
        mutableEmpressApi = if (retainInstance != null) {
            enthrone(
                SampleMutableEmpress(),
                retainInstance = retainInstance,
                eventDispatcher = Dispatchers.Main,
                requestDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
            )
        } else {
            enthrone(SampleMutableEmpress())
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
