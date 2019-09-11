package io.nofrills.empress.test_support

import androidx.fragment.app.FragmentActivity
import io.nofrills.empress.EmpressApi
import io.nofrills.empress.MutableEmpressApi
import io.nofrills.empress.android.enthrone
import kotlinx.coroutines.CoroutineDispatcher

class SampleActivity : FragmentActivity(), WithRuler {
    override fun enthroneEmpress(
        dispatcher: CoroutineDispatcher,
        retainInstance: Boolean
    ): EmpressApi<Event, Model> {
        return enthrone(
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
            SampleMutableEmpress(),
            eventDispatcher = dispatcher,
            requestDispatcher = dispatcher,
            retainInstance = retainInstance
        )
    }
}
