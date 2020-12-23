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
- uses `kotlinx.coroutines.flow.Flow` for propagating changes
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

1. First, define your model and signal classes:

```kotlin
// In Empress, your models can be defined as a set of classes,
// where each class is responsible for single aspect of application state.

// In case our process is temporarily killed by the OS, we can make sure
// our state will be brought back, by implementing `android.os.Parcelable`
@Parcelize
data class Counter(val count: Int) : Parcelable

// In this case it doesn't make sense to implement `Parcelable`,
// because if our process gets killed, our async request will also die
data class Sender(val requestId: RequestId?)

// A signal raised when the counter has been successfully sent.
object CounterSentSignal
```

2. Next, define your empress.

Implement [Empress](https://nofrills.io/empress/dokka/empress/io.nofrills.empress.base/-empress/index.html)
interface:

```kotlin
class SampleEmpress : Empress() {
    val counter by model(Counter(0))
    val sender by model<Sender>(Sender(null))
    val counterSignal by signal<CounterSentSignal>()

    suspend fun increment() = onEvent {
        val count = counter.get().count
        counter.update(Counter(count + 1))
    }

    suspend fun sendCounter() = onEvent {
        // If request is already in progress, return early.
        if (sender.get().requestId != null) return@onEvent
    
        val count = counter.get().count
        val requestId = request { sendCounter(count) } // schedule a request
        sender.update(Sender(requestId))
    }

    private suspend fun sendCounter(count: Int) = onRequest {
        delay(count * 1000L) // emulate sending the value
        onCounterSent() // call an event handler
    }

    private suspend fun onCounterSent() = onEvent {
        counterSignal.push(CounterSentSignal)
        update(Sender(null))
    }
}
```

3. In your `Activity` or `Fragment`, attach your empress, send events and listen for updates:

```kotlin
private lateinit var api: EmpressApi<SampleEmpress>

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // install our empress
    api = enthrone("sampleApi", ::SampleEmpress)
    
    // pass events to empress
    increment_button.setOnClickListener {
        api.post { increment() }
    }
    send_button.setOnClickListener {
        api.post { sendCounter() }
    }

    // Listen for updated models and signals:
    lifecycle.coroutineScope.launch {
        api.model { counter }.collect {
            text_view.text = it.count.toString()
        }
        api.model { sender }.collect {
            text_view.text = loader_view.visibility = if (it.requestId != null) View.VISIBLE else View.GONE
        }
        api.signal { counterSignal }.collect {
            Toast.makeText(context, "Counter has been sent!", Toast.LENGTH_LONG).show()
        }
    }
}
```

### Status

Empress library is stable and tested, but considered alpha,
and its public API may change without backwards compatibility.

## License

This project is published under Apache License, Version 2.0 (see the [LICENSE](https://github.com/nofrills-io/empress/blob/master/LICENSE) file for details).
