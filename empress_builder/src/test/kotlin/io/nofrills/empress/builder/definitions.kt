package io.nofrills.empress.builder

import io.nofrills.empress.Models
import io.nofrills.empress.RequestCommander
import io.nofrills.empress.RequestId
import kotlin.reflect.KClass


internal sealed class Event {
    object Click : Event()
    data class Submit(val payload: Long) : Event()
    object Sent : Event()
}

internal sealed class Model {
    data class Counter(var count: Int) : Model()
    data class Sender(var requestId: RequestId?) : Model()
}

internal sealed class Request {
    data class Send(val payload: Long) : Request()
    object Unhandled : Request()
}

internal class MockModels<M : Any>(private val models: Collection<M>) : Models<M> {
    override fun all(): Collection<M> {
        return models
    }

    override fun <T : M> get(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return models.find { it::class.java == modelClass } as T?
            ?: error("Could not find $modelClass")
    }

    override fun <T : M> get(modelClass: KClass<T>): T {
        return get(modelClass.java)
    }
}

internal data class MockRequestId(private val id: Int) : RequestId

internal class MockRequests : RequestCommander<Request> {
    private var nextRequestNum = 0

    override fun cancel(requestId: RequestId?): Boolean {
        return true
    }

    override fun post(request: Request): RequestId {
        nextRequestNum += 1
        return MockRequestId(nextRequestNum)
    }
}
