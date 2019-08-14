[![API docs](https://img.shields.io/badge/API-docs-%2346C800.svg)](https://nofrills.io/empress/dokka/empress/index.html)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](https://github.com/nofrills-io/empress/blob/master/LICENSE)
[![Build Status](https://travis-ci.com/nofrills-io/empress.svg?branch=master)](https://travis-ci.com/nofrills-io/empress)
[![Release](https://jitpack.io/v/nofrills-io/empress.svg)](https://jitpack.io/#nofrills-io/empress)

# Empress — Android framework for ruling your app

Empress is a framework for managing application state and its representation in UI.
It's targeted to be used with Android's activities and fragments, but you can also use it standalone.

### Install

```kotlin
repositories {
    maven { url("https://jitpack.io") }
}

dependencies {
    implementation("com.github.nofrills-io:empress:<empress_version>")
}

// Note: skip `::class` if you're using Groovy instead of Kotlin
tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class).whenTaskAdded {
    kotlinOptions {
        jvmTarget = "1.8"
    }
}
```

### Sample usage

Let's say you need to build an app to count things, and send the final value to a server
(full example in `sample` app module).

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
        // Here, we return only patches that have changed.
        // If nothing has changed, we could return an empty list.
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
private lateinit var empress: EmpressApi<Event, Patch>

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // install our empress
    empress = enthrone(SampleEmpress())
    
    // pass events to empress
    decrement_button.setOnClickListener {
        empress.send(Event.Decrement)
    }
    increment_button.setOnClickListener {
        empress.send(Event.Decrement)
    }

    launch {
        // first, we can render the whole UI
        render(empress.modelSnapshot().all())

        // then we listen for updates and render only the updated patches
        empress.updates().collect { update ->
            render(update.model.updated(), update.event)
        }
    }
}

private fun render(patches: Collection<Patch>, sourceEvent: Event? = null) {
    for (patch in patches) {
        when (patch) {
            is Patch.Counter -> text_view.text = patch.count.toString()
            is Patch.Sender -> updateProgress(showLoader = patch.requestId != null)
        }
    }
}
```

### Status

The core modules (`empress_core` and `empress_android`) are stable and tested,
but considered alpha, and their public API may change without backwards compatibility.

Modules `empress_builder`, `empress_annotations` and `empress_compiler` are experimental
and are not published on jitpack repository.

## License

This project is published under Apache License, Version 2.0 (see the [LICENSE](https://github.com/nofrills-io/empress/blob/master/LICENSE) file for details).