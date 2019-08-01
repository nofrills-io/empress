package io.nofrills.empress.test_support

import android.os.Bundle
import androidx.fragment.app.Fragment
import io.nofrills.empress.EmpressApi
import io.nofrills.empress.android.enthrone
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

class SampleFragment : Fragment(), WithEmpress {
    override lateinit var api: EmpressApi<Event, Patch>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val retainEmpressInstance = arguments?.getBoolean(RETAIN_EMPRESS_ARG) ?: false
        api = enthrone(
            SampleEmpress(),
            retainInstance = retainEmpressInstance,
            dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        )
    }

    companion object {
        private const val RETAIN_EMPRESS_ARG = "RETAIN_EMPRESS_ARG"

        fun argsBundle(retainEmpressInstance: Boolean): Bundle {
            return Bundle().apply {
                putBoolean(RETAIN_EMPRESS_ARG, retainEmpressInstance)
            }
        }
    }
}
