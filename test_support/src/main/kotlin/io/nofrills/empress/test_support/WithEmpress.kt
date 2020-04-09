package io.nofrills.empress.test_support

import io.nofrills.empress.base.EmpressApi
import kotlinx.coroutines.CoroutineDispatcher

interface WithEmpress {
    fun enthroneEmpress(
        dispatcher: CoroutineDispatcher,
        retainInstance: Boolean
    ): EmpressApi<SampleEmpress, Model, Signal>
}
