package io.nofrills.empress.test_support

import io.nofrills.empress.EmperorApi
import io.nofrills.empress.EmpressApi

interface WithRuler {
    val emperorApi: EmperorApi<Event, Model>
    val empressApi: EmpressApi<Event, Model>
}
