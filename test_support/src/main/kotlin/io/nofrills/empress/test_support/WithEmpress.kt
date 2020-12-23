package io.nofrills.empress.test_support

import io.nofrills.empress.base.TestEmpressApi
import kotlinx.coroutines.CoroutineDispatcher

interface WithEmpress {
    fun enthroneEmpress(
        dispatcher: CoroutineDispatcher,
        retainInstance: Boolean
    ): TestEmpressApi<SampleEmpress>
}