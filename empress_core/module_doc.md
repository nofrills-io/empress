# Package io.nofrills.empress

Core functionality.

## Usage

First, you need to define three (sealed) classes: `Event`, `Patch` and `Request`
(though you can call them whatever you want).
Have a look at [io.nofrills.empress.Empress] interface, "_Parameters_" section for short
description about what each class is supposed to do.

Then, define and implement an `Empress` interface:

```kotlin
class MyEmpress : Empress<Event, Patch, Request> {
    // implementation
}
```

_Note_: Instead of implementing [io.nofrills.empress.Empress] interface,
you can also use an [Empress DSL][io.nofrills.empress.builder] builder function.

Once you have defined your [io.nofrills.empress.Empress], you need a way to run it.
If you want to use it with __Android__ activity or fragment, refer to [io.nofrills.empress.android].

Otherwise, for standalone usage (e.g. in a unit test)
you should use [io.nofrills.empress.EmpressBackend] like below:

```kotlin
val empress = MyEmpress()
val coroutineScope = ... // e.g. TestCoroutineScope or your activity's scope
val api: EmpressApi<Event, Patch> = EmpressBackend(empress, scope)
```

Finally you can send events and listen for updates using [EmpressApi] interface
(which is implemented by [EmpressBackend]).
