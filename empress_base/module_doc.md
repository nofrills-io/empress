# Package io.nofrills.empress.base

Base functionality.

## Usage

First, you need to define two (usually `sealed`) classes: `Model` and `Signal`
(though you can call them whatever you want).

- Model — represents application state;
    each subclass should be relatively small, and be related to a single aspect of the app;
    can implement `android.os.Parcelable` (in which case it will be restored when needed) 
- Signal — represents a one-time action to be performed; it is never persisted

Next, implement an [io.nofrills.empress.base.Empress] interface:

```kotlin
class MyEmpress : Empress {
    val myCounter by model(0)
    val mySignal by signal<Any>()

    override fun initialModels(): Collection<Model> {
        // return a collection of initial values for all of your model classes
        return listOf()
    }

    // Now, implement your event handlers, e.g.:
    suspend fun someEvent() = onEvent {
        // Here you can access and modify your models, send updates or signals,
        // issue requests, or cancel existing ones.
        // By default we're on the main thread.
        // The event handler should be quick, 
        // and cannot perform blocking actions (use requests for that).
    }

    // Define request handlers:
    suspend fun someRequest() = onRequest {
        // By default we're off the main thread,
        // so you can do some network requests, read files etc.
        // and when you're ready, call one or more of your event handlers.
        // You should not modify your models here.
    }
}
```

Once you have defined your [io.nofrills.empress.base.Empress], you need a way to run it.
If you want to use it with __Android__ activity or fragment, refer to [io.nofrills.empress.android].

Otherwise, for standalone usage (e.g. in a unit test)
you should use [io.nofrills.empress.base.EmpressBackend] like below:

```kotlin
val empress = MyEmpress()
val coroutineScope = ... // e.g. `TestCoroutineScope` or a scope of your activity/fragment
val api: EmpressApi<MyEmpress> = EmpressBackend(empress, scope, scope)
```

Finally you can send events and listen for updates using [EmpressApi]
(or [TestEmpressApi]) interface, which is implemented by [EmpressBackend].
