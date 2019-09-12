# Package io.nofrills.empress

Core functionality.

## Usage

First, you need to define three (usually `sealed`) classes: `Event`, `Model` and `Request`
(though you can call them whatever you want).

- Event — signals an event for which we need to take some action
- Model — represents application state;
    each subclass should be relatively small, and be related to a single aspect of the app;
    can implement `android.os.Parcelable` (in which case it will be restored when needed) 
- Request — represents a demand for obtaining a resource asynchronously

If your models are immutable (have immutable properties), define and implement an [io.nofrills.empress.Empress] interface:

```kotlin
class MyEmpress : Empress<Event, Model, Request> {
    // implementation
}
```

Alternatively, if you prefer to have models with mutable properties, then you should use
[io.nofrills.empress.MutableEmpress] interface.

_Note_: Instead of implementing an interface,
you can also use an [Empress DSL][io.nofrills.empress.builder] builder function.

Once you have defined your [io.nofrills.empress.Empress] / [io.nofrills.empress.MutableEmpress],
you need a way to run it.
If you want to use it with __Android__ activity or fragment, refer to [io.nofrills.empress.android].

Otherwise, for standalone usage (e.g. in a unit test)
you should use [io.nofrills.empress.backend.EmpressBackend] or [io.nofrills.empress.backend.MutableEmpressBackend] like below:

```kotlin
val empress = MyEmpress()
val coroutineScope = ... // e.g. TestCoroutineScope or scope of your activity
val api: EmpressApi<Event, Model> = EmpressBackend(empress, scope)
```

Finally you can send events and listen for updates using [EmpressApi] / [MutableEmpressApi] interfaces
(which are implemented by [EmpressBackend] and [MutableEmpressBackend] respectively).
