# Модули и настройки

## Регистрация модуля

```javascript
var module = api.module(
    "unique_id",
    "Название в меню",
    "Player",
    "Описание модуля"
);

module.onEnable(function (ctx) {});
module.onDisable(function (ctx) {});
module.onTick(function (ctx) {});
module.onFrame(function (ctx) {});
module.register();
```

`onFrame` вызывается на каждом кадре для включённого модуля. Как и остальные
callbacks ModuleDLC, ошибки в нём логируются и отключают модуль, не завершая
клиент.

`id` должен быть уникальным среди всех загруженных модулей. `name` и
`category` не должны быть пустыми. Категория — одна строка: модули с одинаковым
написанием попадут в один раздел. Допустимы только категории `Combat`,
`Movement`, `Player`, `Visuals`, `UI` и `Misc`; регистр букв учитывается.
Модуль с неизвестной категорией не регистрируется, а в чат отправляется
`this category not exists`.

Созданные модули изначально выключены.

## HUD-объекты

Модуль может создавать текст, прямоугольники и изображения до `register()`.
Объекты отображаются, пока модуль включён. Координаты задаются в GUI-пикселях
от левого верхнего угла экрана. Методы
`x()`, `y()` и `setPosition(x, y)` позволяют читать и менять координаты;
`setDraggable(true)` разрешает перемещение конкретного объекта. При открытом
чате доступные для перемещения объекты подсвечиваются и перетаскиваются мышью.

```javascript
var panel = module.hudRectangle();
panel.setPosition(5, 5);
panel.setSize(80, 20);
panel.setColor(20, 26, 32);
panel.setOpacity(0.85);
panel.setShape("rounded");
panel.setRadius(5);
panel.setDraggable(true);

var label = module.hudText();
label.setText("Hello");
label.setSize(8);
label.setColor(255, 255, 255);

panel.attach(label, 5, 5);

var icon = module.hudImage();
icon.setResource("minecraft:textures/item/diamond.png");
icon.setPosition(10, 30);
icon.setSize(16, 16);
icon.setTint(0x80FFFFFF);
```

`hudText()`, `hudRectangle()` и `hudImage()` создают элемент со значениями по
умолчанию. После создания настрой его отдельными методами. Можно передать
стабильный ID, например `hudText("status_label")`, чтобы позиция сохранялась
даже если в скрипт добавились другие HUD-элементы. Без ID он генерируется
автоматически, но тогда зависит от порядка создания элементов.

Позиции сохраняются в `config/moduledlc/hud-layouts.json`, восстанавливаются
при перезагрузке скрипта и после перезапуска игры. В hot reload используются
ID модуля и HUD-элемента; при создании элемента с новым ID позиция для него
начинается со значения из скрипта.

`parent.attach(child, offsetX, offsetY)` groups a HUD element under another one.
The offset is relative to the parent; children render together with the parent,
and moving/selecting the parent moves the whole group as one object. Attached
elements must belong to the same module and cannot be attached more than once.

`hudText(id, text, x, y, size, color, draggable)` возвращает текстовый объект.
У него доступны `text()`, `setText(text)`, `size()`, `setSize(size)`,
`color()`, `setColor(color)`, `opacity()` и `setOpacity(0..1)`. Прямоугольник
`hudRectangle(id, x, y, width, height, color, draggable)` поддерживает
`setSize(width, height)`, `setColor(color)`, `setShape("rectangle"|"rounded"|"circle")`,
`shape()`, `setRadius(pixels)` и `radius()`. Изображение
`hudImage(id, resource, x, y, width, height, tint, draggable)` принимает ресурс
в формате `namespace:path` (без namespace используется `minecraft`) и
поддерживает `setSize`, `setTint` и `setOpacity`. Все объекты можно перемещать
через `setX`, `setY` и `setPosition`. Идентификаторы объектов должны быть
уникальны внутри модуля.

Цвет задаётся целым ARGB `0xAARRGGBB`; прозрачность цвета умножается на
`setOpacity(0..1)`. Например, `setColor(0x80FFFFFF)` задаёт белый цвет с
половинной прозрачностью. Для RGB есть короткая форма `setColor(red, green,
blue)` с каналами от `0` до `255`; она задаёт непрозрачный цвет, прозрачность
после этого регулируется через `setOpacity`. Для изображений доступны такие же
варианты `setTint(color)` и `setTint(red, green, blue)`.

## Бинды клавиш

Назначьте клавишу кнопкой бинда в карточке модуля. Нажатие назначенной клавиши
переключает модуль, когда игровое окно открыто и ни один экран не перехватывает
ввод. Повторное срабатывание при удержании клавиши не происходит. `Backspace`
или `Delete` снимает бинд, `Escape` отменяет переназначение. Настройки биндов
сохраняются в `config/moduledlc/binds.json` и переживают перезагрузку скриптов и
клиента.

## Типы настроек

Настройки нужно добавить до вызова `register()`. Методы с суффиксом `Setting`
возвращают объект настройки, чтобы скрипт мог читать и менять его значение.
Короткие варианты `toggle`, `number` и `choice` только добавляют настройку и
возвращают builder модуля.

### Переключатель

```javascript
var enabled = module.toggleSetting("Only forward", "Условие движения", true);
// enabled.enabled()
// enabled.set(false)
```

### Число

```javascript
var speed = module.numberSetting(
    "Speed", "Скорость", 0.1, 2.0, 0.1, 1.0, "x"
);
// speed.value()
// speed.set(1.2) — значение ограничивается диапазоном min..max
```

`min` должен быть меньше `max`, `step` — больше нуля, а начальное значение
должно находиться внутри диапазона. Все числа должны быть конечными.

### Выбор режима

```javascript
var mode = module.choiceSetting(
    "Mode", "Режим работы", "Legit,Strict,Custom", "Legit"
);
// mode.value()
// mode.set("Strict")
```

Можно передать варианты строкой через запятую или списком строк из Java/Kotlin.
Начальное значение обязано совпадать с одним из вариантов.

### Несколько переключателей

```javascript
var targets = module.multiToggleSetting(
    "Targets", "Какие сущности учитывать", "Players,Animals,Hostile"
);
// targets.values() возвращает список BooleanSetting
```

Все варианты `multiToggleSetting` изначально выключены. Чтобы выбрать варианты
по умолчанию, вызовите `set(true)` у нужных элементов списка до `register()`.

### Цвет

```javascript
var tint = module.colorSetting("Tint", "Цвет эффекта", 0xFF8FAFC0);
// tint.argb()
// tint.setHsb(hue, saturation, brightness)
// tint.setAlpha(alpha)
```

Цвет задаётся целым числом ARGB (`0xAARRGGBB`). Значения HSB и alpha — числа
с плавающей точкой.

## Видимость настройки

У настроек есть метод `visible()`, но публичного API для задания условия
видимости из скрипта сейчас нет. Поэтому создаваемые через builder настройки
всегда видимы.

## Сохранение значений

Настройки пока не сохраняются на диск. Они существуют в памяти и сбрасываются
при перезагрузке скрипта или перезапуске клиента.

## Java и Kotlin

В Java/Kotlin доступны те же builder и объекты настроек. Например:

```java
var module = api.module("example", "Example", "Misc");
var enabled = module.toggleSetting("Enabled", "", true);
module.onTick(ctx -> {
    if (enabled.enabled() && ctx.inGame()) {
        ctx.message("Работает");
    }
}).register();
```
