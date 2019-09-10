package io.nofrills.empress.test_support

import io.nofrills.empress.MutableEmpressApi
import io.nofrills.empress.EmpressApi

interface WithRuler {
    val mutableEmpressApi: MutableEmpressApi<Event, Model>
    val empressApi: EmpressApi<Event, Model>
}
