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

package io.nofrills.empress.test_support

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import io.nofrills.empress.EmpressApi
import io.nofrills.empress.android.enthrone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

class SampleActivity : FragmentActivity(), WithEmpress {
    override lateinit var api: EmpressApi<Event, Patch>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val coroutineDispatcher =
            when (DispatcherType.valueOf(intent.getStringExtra(DISPATCHER_EXTRA))) {
                DispatcherType.MAIN -> Dispatchers.Main
                DispatcherType.SINGLE -> Executors.newSingleThreadExecutor().asCoroutineDispatcher()
            }
        val retainEmpressInstance = intent.getBooleanExtra(RETAIN_EMPRESS_EXTRA, false)
        api = enthrone(
            SampleEmpress(),
            retainInstance = retainEmpressInstance,
            dispatcher = coroutineDispatcher
        )
    }

    enum class DispatcherType {
        MAIN, SINGLE
    }

    companion object {
        private const val DISPATCHER_EXTRA = "DISPATCHER_EXTRA"
        private const val RETAIN_EMPRESS_EXTRA = "RETAIN_EMPRESS_EXTRA"

        fun newIntent(
            context: Context,
            retainEmpressInstance: Boolean,
            dispatcherType: DispatcherType
        ): Intent {
            return Intent(context, SampleActivity::class.java).apply {
                putExtra(DISPATCHER_EXTRA, dispatcherType.name)
                putExtra(RETAIN_EMPRESS_EXTRA, retainEmpressInstance)
            }
        }
    }
}
