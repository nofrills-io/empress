[![API docs](https://img.shields.io/badge/API-docs-%2346C800.svg)](https://nofrills.io/empress/dokka/empress/index.html)
[![Release](https://jitpack.io/v/nofrills-io/empress.svg)](https://jitpack.io/#nofrills-io/empress)
[![Build Status](https://travis-ci.com/nofrills-io/empress.svg?branch=master)](https://travis-ci.com/nofrills-io/empress)
[![Codecov](https://img.shields.io/codecov/c/github/nofrills-io/empress)](https://codecov.io/gh/nofrills-io/empress)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](https://github.com/nofrills-io/empress/blob/master/LICENSE)

# Empress — Android framework for ruling your app

Empress is a framework for managing application state and its representation in UI.
It's targeted to be used with Android's activities and fragments, but you can also use it standalone.

Empress is similar to androidx ViewModel library in a way that it:
- can survive configuration changes
- can be shared among multiple clients (e.g. fragments)

Additionally:
- supports "save & restore" flow (it's automatic, as long as your model is `Parcelable`)
- supports long-running suspending requests
- uses `kotlinx.coroutines.flow.Flow` for propagating changes (for immutable models)
- aims for compatibility with Jetpack Compose (with mutable models)

### Install

```kotlin
repositories {
    maven { url("https://jitpack.io") }
}

dependencies {
    implementation("com.github.nofrills-io:empress:empress_version")
}

// Note: skip `::class` if you're using Groovy instead of Kotlin
tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class).configureEach {
    kotlinOptions {
        jvmTarget = "1.8"
    }
}
```

### Sample usage

Let's say you need to build an app to count things, and send the final value to a server
(full example in `sample` app module).

1. First, define your events, model and requests:

```kotlin

// Events represent events originating from your UI, and also results from performing Requests
sealed class Event {
    object Decrement : Event()
    object Increment : Event()
    object SendCounter : Event()
    object CounterSent : Event()
}

// In Empress, your model can be defined as a set of subclasses,
// where each subclass is responsible for single aspect of application state.
// Your model should be either fully immutable, or fully mutable.
sealed class Model {
    // In case our process is temporarily killed by the OS, we can make sure
    // our state will be brought back, by implementing `android.os.Parcelable`
    @Parcelize
    data class Counter(val count: Int) : Model(), Parcelable

    // In this case it doesn't make sense to implement `Parcelable`,
    // because if our process gets killed, our async request will also die
    data class Sender(val requestId: RequestId?) : Model()
}

sealed class Request {
    // Represents an intent to send the counter value to a server
    class SendCounter(val counterValue: Int) : Request()
}
```

2. Next, define your empress.

For __immutable__ models, implement [Empress](dokka/empress/io.nofrills.empress/-empress/index.html)
interface. Alternatively, for __mutable__ models, use [MutableEmpress](dokka/empress/io.nofrills.empress/-mutable-empress/index.html)

You can also use an [Empress DSL builder](dokka/empress/io.nofrills.empress.builder/index.html), like below:

```kotlin
val empress = Empress<Event, Model, Request>("sampleEmpress") {
    initializer { Model.Counter(0) }
    initializer { Model.Sender(null) }

    onEvent<Event.Decrement> {
        val counter = models[Model.Counter::class]
        // return a collection of models that have changed
        listOf(counter.copy(count = counter.count - 1))
    }

    onEvent<Event.Increment> {
        val counter = models[Model.Counter::class]
        listOf(counter.copy(count = counter.count + 1))
    }

    onEvent<Event.SendCounter> {
        val sender = models[Model.Sender::class]
        if (sender.requestId != null) {
            // Counter value is already being sent,
            // so we return an empty collection, since there's nothing to be done.
            listOf()

            // Alternatively, we could cancel current request 
            // (using requests.cancel(sender.requestId)) 
            // and then create a new one.
        } else {
            // We create a request and queue it..
            val counter = models[Model.Counter::class]
            val requestId = requests.post(Request.SendCounter(counter.count))

            // ..while returning an updated model.
            listOf(Model.Sender(requestId))
        }
    }

    onEvent<Event.CounterSent> {
        val sender = models[Model.Sender::class]
        if (sender.requestId == null) {
            listOf()
        } else {
            listOf(Model.Sender(null))
        }
    }

    onRequest<Request.SendCounter> {
        delay(abs(request.counterValue) * 1000L)
        Event.CounterSent
    }
}
```

3. In your `Activity` or `Fragment`, attach your empress, send events and listen for updates:

```kotlin
private lateinit var api: EmpressApi<Event, Model>

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // install our empress
    api = enthrone(empress)
    
    // pass events to empress
    decrement_button.setOnClickListener {
        api.post(Event.Decrement)
    }
    increment_button.setOnClickListener {
        api.post(Event.Increment)
    }

    lifecycle.coroutineScope.launch {
        // first, we can render the whole UI
        render(api.models().all())

        // then we listen for updates and render only the updated models
        api.updates().collect { update ->
            render(update.updated, update.event)
        }
    }
}

private fun render(models: Collection<Model>, sourceEvent: Event? = null) {
    for (model in models) {
        when (model) {
            is Model.Counter -> text_view.text = model.count.toString()
            is Model.Sender -> updateProgress(showLoader = model.requestId != null)
        }
    }
}
```

### Status

Empress library is stable and tested, but considered alpha,
and its public API may change without backwards compatibility.

## License

This project is published under Apache License, Version 2.0 (see the [LICENSE](https://github.com/nofrills-io/empress/blob/master/LICENSE) file for details).
