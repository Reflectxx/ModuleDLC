# JavaScript, Java и Kotlin

Все языки используют один и тот же `ScriptApi`, builder модулей, настройки и
`ScriptContext`. Скрипт регистрирует модуль при загрузке файла; callbacks
выполняются позже по мере работы клиента.

## JavaScript

JavaScript запускается встроенным Rhino. Глобальная переменная `api` доступна
сразу. Объекты, полученные из `ctx`, являются Java-объектами Minecraft; их
доступные методы можно вызывать из JavaScript.

```javascript
var greeting = api.module("greeting", "Greeting", "Misc");
greeting.onEnable(function (ctx) {
    ctx.message("Модуль включён");
});
greeting.onTick(function (ctx) {
    if (!ctx.inGame()) return;
    api.log("Игрок: " + ctx.playerName());
});
greeting.register();
```

Для нового `.js`-файла перезапуск клиента не нужен. Смотрите также готовый
[AutoSprint.js](../examples/AutoSprint.js).

## Java

Файл `.java` должен содержать один или несколько классов, реализующих
`ScriptEntrypoint`. Скрипт компилируется при загрузке с целевой Java 21,
поэтому для запуска игры нужен JDK с `javac`.

```java
import dev.dlc.client.script.ScriptApi;
import dev.dlc.client.script.ScriptEntrypoint;

public final class Greeting implements ScriptEntrypoint {
    @Override
    public void register(ScriptApi api) {
        var greeting = api.module("greeting", "Greeting", "Misc");
        greeting
                .onEnable(ctx -> ctx.message("Модуль включён"))
                .onTick(ctx -> {
                    if (ctx.inGame()) {
                        api.log("Игрок: " + ctx.playerName());
                    }
                })
                .register();
    }
}
```

Для `.java` имя публичного класса должно совпадать с именем файла.

## Kotlin

Файл `.kt` должен содержать класс, реализующий `ScriptEntrypoint`.
Компилятор `kotlinc` должен быть установлен и доступен через `PATH`;
используется цель JVM 21.

```kotlin
import dev.dlc.client.script.ScriptApi
import dev.dlc.client.script.ScriptEntrypoint

class Greeting : ScriptEntrypoint {
    override fun register(api: ScriptApi) {
        val greeting = api.module("greeting", "Greeting", "Misc")
        greeting
            .onEnable { ctx -> ctx.message("Модуль включён") }
            .onTick { ctx ->
                if (ctx.inGame()) {
                    api.log("Игрок: ${ctx.playerName()}")
                }
            }
            .register()
    }
}
```

## Отличия и совместимость

- Все три языка имеют одинаковые builder-методы и callback-контекст.
- Java и Kotlin могут импортировать типы Minecraft через обычные `import`.
- JavaScript получает клиентские объекты из `ctx` и вызывает их доступные методы
  через Rhino.
- API предоставляет Java-объекты клиента, а не стабильные обёртки для всех
  функций Minecraft. При обновлении Minecraft методы этих объектов могут
  измениться.
- Ошибки синтаксиса, компиляции и запуска попадают в игровой лог. Если новая
  версия файла не загрузилась, прежняя успешная версия продолжает работать.
