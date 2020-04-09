package io.nofrills.empress.test_support

import androidx.fragment.app.Fragment
import io.nofrills.empress.android.enthrone
import io.nofrills.empress.base.EmpressApi
import kotlinx.coroutines.CoroutineDispatcher

class SampleFragment : Fragment(), WithEmpress {
    override fun enthroneEmpress(
        dispatcher: CoroutineDispatcher,
        retainInstance: Boolean
    ): EmpressApi<SampleEmpress, Model, Signal> {
        return enthrone(
            "sample_empress",
            SampleEmpress(),
            eventDispatcher = dispatcher,
            requestDispatcher = dispatcher,
            retainInstance = retainInstance
        )
    }
}
