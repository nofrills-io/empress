package io.nofrills.empress.test_support

import io.nofrills.empress.EmpressApi

interface WithEmpress {
    val api: EmpressApi<Event, Patch>
}