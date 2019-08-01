# Empress

### Android framework for ruling your app

Empress is a framework for managing application state and its representation in UI.
It's targeted to be used with Android's activities and fragments, but you can also use it standalone.

### Install

TODO

### Sample usage

Let's say you need to build an app to count things, and send the final value to a server
(full example in [sample](sample)).

1. First, define your events, patches (model) and requests:

```kotlin

// Events represent events originating from your UI, and also results from performing Requests
sealed class Event {
    object Decrement : Event()
    object Increment : Event()
    object SendCounter : Event()
}

// Very often you'd use a single class to represent your model.
// However in empress your model is defined as a set of subclasses. 
sealed class Patch {
    // In case our process is temporarily killed by the OS, we can make sure
    // our state will be brought back, by implementing `Parcelable`
    @Parcelize
    data class Counter(val count: Int) : Patch(), Parcelable

    // In this case it doesn't make sense to implement `Parcelable`,
    // because if our process gets killed, our async request will also die
    data class Sender(val requestId: RequestId?) : Patch()
}

sealed class Request {
    // Represents an intent to send the counter value to a server
    class SendCounter(val counterValue: Int) : Request()
}
```

2. Next, define your empress, by implementing an interface:

```kotlin
class SampleEmpress : Empress<Event, Patch, Request> {
    override fun initializer(): Collection<Patch> {
        // return initial values for all of your patches — it represents starting state of your application
        return listOf(Patch.Counter(0), Patch.Sender(null))
    }
    
    override fun onEvent(
        event: Event,
        model: Model<Patch>,
        requests: Requests<Event, Request>
    ): Collection<Patch> {
        // return only patches that have changed (if nothing has changed, return an empty list)
        val counter: Patch.Counter = model.get()
        return when (event) {
            Event.Decrement -> listOf(counter.copy(count = counter.count - 1))
            Event.Increment -> listOf(counter.copy(count = counter.count + 1))
            Event.SendCounter -> run {
                // We need to make an asynchronous request to send the counter value.
                // For that we create an instance of `Request.SendCounter` and pass it down.
                val requestId = requests.post(Request.SendCounter(counter.count))
                listOf(Patch.Sender(requestId))
            }
        }
    }
    
    override suspend fun onRequest(request: Request): Event {
        // Handle the requests. This function is off the main thread,
        // and is suspendable, so you can call remote servers, read files etc.
        // At the end, you return an event (which can contain a payload, e.g. server response)
        return when (request) {
            is Request.SendCounter -> run {
                delay(abs(request.counterValue) * 1000L)
                Event.CounterSent
            }
        }
    }
}
```

3. In your `Activity` or `Fragment`, attach your empress, send events and listen for updates:

```kotlin
lateinit var empress: EmpressApi<Event, Patch>

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // install our empress
    empress = enthrone(CounterEmpress())
    
    // pass click events to empress
    decrement_button.setOnClickListener {
        empress.send(Event.Decrement)
    }
    increment_button.setOnClickListener {
        empress.send(Event.Decrement)
    }
    
    launch {
        // first, we can render the whole UI
        render(empressApi.modelSnapshot().all())

        // then we listen for updates and render only the updated patches
        empressApi.updates().collect { update ->
            render(update.model.updated(), update.event)
        }
    }
}

private fun render(patches: Collection<Patch>, sourceEvent: Event? = null) {
    counter_value.text = patch.count.toString()
    // ...
}
``` 
