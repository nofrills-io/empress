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

import android.annotation.SuppressLint
import android.os.Parcelable
import io.nofrills.empress.base.RequestId
import kotlinx.parcelize.Parcelize

sealed class Model

@Parcelize
@SuppressLint("ParcelCreator")
data class Counter(val count: Int) : Model(), Parcelable

sealed class Sender : Model() {
    object Idle : Sender()
    data class Sending(val requestId: RequestId) : Sender()
}

sealed class CounterSignal {
    object CounterSent : CounterSignal()
    object CounterSendCancelled : CounterSignal()
}

class OnEventFailure : Throwable()
class OnRequestFailure : Throwable()
