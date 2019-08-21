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
    object CounterSent : Event()
}

// In empress your model is defined as a set of subclasses,
// where each subclass is responsible for single aspect of application state. 
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

2. Next, define your empress. You can either implement an [Empress](dokka/empress/io.nofrills.empress/-empress/index.html) interface directly,
or use [Empress DSL builder](dokka/empress/io.nofrills.empress.builder/index.html), like below:

```kotlin
val empress = Empress("sampleEmpress") {
    initializer { Patch.Counter(0) }
    initializer { Patch.Sender(null) }

    onEvent<Event.Decrement> {
        val counter = model.get<Patch.Counter>()
        // return a collection of patches that have changed
        listOf(counter.copy(count = counter.count - 1))
    }

    onEvent<Event.Increment> {
        val counter = model.get<Patch.Counter>()
        listOf(counter.copy(count = counter.count + 1))
    }
    
    onEvent<Event.SendCounter> {
        val sender = model.get<Patch.Sender>()
        if (sender.requestId != null) {
            // Counter value is already being sent,
            // so we return an empty collection, since there's nothing to be done.
            listOf()
            
            // Alternatively, we could cancel current request 
            // (using requests.cancel(sender.requestId)) 
            // and then create a new one.
        } else {
            // We create a request and queue it..
            val requestId = requests.post(Request.SendCounter(counter.count))
            
            // ..while returning an updated patch.
            listOf(Patch.Sender(requestId))
        }
    }
    
    onEvent<Event.CounterSent> {
        val sender = model.get<Patch.Sender>()
        if (sender.requestId == null) {
            listOf()
        } else {
            listOf(Patch.Sender(null))
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
private lateinit var api: EmpressApi<Event, Patch>

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // install our empress
    api = enthrone(empress)
    
    // pass events to empress
    decrement_button.setOnClickListener {
        api.send(Event.Decrement)
    }
    increment_button.setOnClickListener {
        api.send(Event.Decrement)
    }

    launch {
        // first, we can render the whole UI
        render(api.modelSnapshot().all())

        // then we listen for updates and render only the updated patches
        api.updates().collect { update ->
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

Empress library is stable and tested, but considered alpha,
and its public API may change without backwards compatibility.

## License

This project is published under Apache License, Version 2.0 (see the [LICENSE](https://github.com/nofrills-io/empress/blob/master/LICENSE) file for details).