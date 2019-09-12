/*
 * Copyright 2019 Mateusz Armatys
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.nofrills.empress.sample

import android.os.Parcelable
import io.nofrills.empress.RequestId
import kotlinx.android.parcel.Parcelize

sealed class Event {
    object Decrement : Event()
    object Increment : Event()
    object SendCounter : Event()
    object CancelSendingCounter : Event()
    object CounterSent : Event()
    object GetFailure : Event()
    object GetFailureWithRequest : Event()
}

sealed class Model {
    @Parcelize
    data class Counter(val count: Int) : Model(), Parcelable

    data class Sender(val requestId: RequestId?) : Model()
}

sealed class MutModel {
    @Parcelize
    data class Counter(var count: Int) : MutModel(), Parcelable

    data class Sender(var requestId: RequestId?) : MutModel()
}

sealed class Request {
    class SendCounter(val counterValue: Int) : Request()
    object GetFailure : Request()
}

class OnEventFailure : Throwable()
class OnRequestFailure : Throwable()
