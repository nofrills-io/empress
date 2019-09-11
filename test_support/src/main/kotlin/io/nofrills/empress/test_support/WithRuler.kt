package io.nofrills.empress.test_support

import io.nofrills.empress.MutableEmpressApi
import io.nofrills.empress.EmpressApi
import kotlinx.coroutines.CoroutineDispatcher

interface WithRuler {
    fun enthroneEmpress(
        dispatcher: CoroutineDispatcher,
        retainInstance: Boolean
    ): EmpressApi<Event, Model>

    fun enthroneMutableEmpress(
        dispatcher: CoroutineDispatcher,
        retainInstance: Boolean
    ): MutableEmpressApi<Event, Model>
}
