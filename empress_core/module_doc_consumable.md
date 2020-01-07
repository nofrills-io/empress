# Package io.nofrills.empress.consumable

Consumable values with effects.

A [Consumable] is a value that can be [consumed][Consumable.consume]. When consumed for the
first time, an [Effect] is executed, which produces an event that will further update your model.

## Sample

Let's say you want to display an alert, but you want to show it only once
(and not every time the view is rendered, since that would create a stack of alert messages).

In your model, put a [Consumable] property with the state of the alert:

```kotlin
data class MyModel(val shouldShowAlert: Consumable<Boolean, MyEvent>)

sealed class MyEvent {
    object ShowProgress : MyEvent()
    object ProgressShown : MyEvent()
}
``` 

In your (Mutable)Empress, define the behaviour:

```kotlin
class MyEmpress: Empress<MyEvent, MyModel, MyRequest> {
    override fun initialize(): Collection<Model> {
        return listOf(MyModel(consumableOf(false)))
    }
    
    override fun onEvent(
        event: Event,
        models: Models<Model>,
        requests: RequestCommander<Request>
    ): Collection<Model> {
        return when (event) {
            MyEvent.ShowProgress -> {
                listOf(
                    // when the `shouldShowAlert` property is consumed
                    // an event `MyEvent.ProgressShown` will be pushed
                    MyModel(shouldShowAlert = consumableOf(true) { MyEvent.ProgressShown })
                )
            }
            MyEvent.ProgressShown -> listOf(MyModel(shouldShowAlert = consumableOf(false)))
        }
    }
}
```

Finally, in your UI, consume the value:

```kotlin
fun renderUI(empressApi: EmpressApi<MyEvent, MyModel>, model: MyModel) {
    if (model.shouldShowAlert.consume(empressApi)) {
        // show an alert
        // once consumed, the `shouldShowAlert` value will be automatically set to `false`
    }
}
```
