# Package io.nofrills.empress.builder

Simple DSL for defining an Empress.

By using the [io.nofrills.empress.builder.Empress] or [io.nofrills.empress.builder.MutableEmpress]
builder functions, you can quickly define your custom implementation
of [io.nofrills.empress.Empress] or [io.nofrills.empress.MutableEmpress] interfaces. For example:

```kotlin
val empress = Empress("myEmpress") {
    initializer { /* return initial value for one of your Model subclasses */ }
    initializer { /* another initializer */ }

    onEvent<Event.MyEvent> {
        // handle an event
        // return a list of models that have changed (or an empty list if nothing's changed)
    }

    onEvent<Event.AnotherEvent> {
        // handle another event
    }

    onRequest<Request.SomeRequest> {
        // handle a request
        // you can use suspending functions here
        // return an event
    }
}
```
