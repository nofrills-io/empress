package io.nofrills.empress.test_support

import androidx.fragment.app.FragmentActivity
import io.nofrills.empress.android.enthrone
import io.nofrills.empress.base.TestEmpressApi
import kotlinx.coroutines.CoroutineDispatcher

class SampleActivity : FragmentActivity(), WithEmpress {
    override fun enthroneEmpress(
        dispatcher: CoroutineDispatcher,
        retainInstance: Boolean
    ): TestEmpressApi<SampleEmpress, Signal> {
        return enthrone(
            "sample_empress",
            SampleEmpress(),
            eventDispatcher = dispatcher,
            requestDispatcher = dispatcher,
            retainInstance = retainInstance
        ) as TestEmpressApi<SampleEmpress, Signal>
    }
}
