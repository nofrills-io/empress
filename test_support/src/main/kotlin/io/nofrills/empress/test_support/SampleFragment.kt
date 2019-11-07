package io.nofrills.empress.test_support

import androidx.fragment.app.Fragment
import io.nofrills.empress.EmpressApi
import io.nofrills.empress.MutableEmpressApi
import io.nofrills.empress.android.enthrone
import kotlinx.coroutines.CoroutineDispatcher

class SampleFragment : Fragment(), WithRuler {
    override fun enthroneEmpress(
        dispatcher: CoroutineDispatcher,
        retainInstance: Boolean
    ): EmpressApi<Event, Model> {
        return enthrone(
            "sample_empress",
            SampleEmpress(),
            eventDispatcher = dispatcher,
            requestDispatcher = dispatcher,
            retainInstance = retainInstance
        )
    }

    override fun enthroneMutableEmpress(
        dispatcher: CoroutineDispatcher,
        retainInstance: Boolean
    ): MutableEmpressApi<Event, Model> {
        return enthrone(
            "sample_mutable_empress",
            SampleMutableEmpress(),
            eventDispatcher = dispatcher,
            requestDispatcher = dispatcher,
            retainInstance = retainInstance
        )
    }
}
