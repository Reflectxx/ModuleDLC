# Примеры

## Auto Sprint

Готовый рабочий файл: [AutoSprint.js](../examples/AutoSprint.js).
Скопируйте его в `.minecraft/config/moduledlc/scripts/`, затем включите модуль
`Auto Sprint` в категории `Player`. Он включает спринт при движении вперёд,
отключает его при остановке или нажатом шифте и сбрасывает состояние при
выключении модуля.

## Сообщение при включении

```javascript
var greeting = api.module("greeting", "Greeting", "Misc");
greeting.onEnable(function (ctx) {
    ctx.message("Привет, " + ctx.playerName());
});
greeting.register();
```

## Настраиваемый порог расстояния

```javascript
var nearby = api.module("nearby", "Nearby players", "Player");
var distance = nearby.numberSetting(
    "Distance", "Максимальное расстояние", 2, 64, 1, 16, " blocks"
);

nearby.onTick(function (ctx) {
    if (!ctx.inGame()) return;

    var local = ctx.player();
    var limit = distance.value();
    var players = ctx.players();

    for (var i = 0; i < players.size(); i++) {
        var other = players.get(i);
        if (other.getUUID().toString() === local.getUUID().toString()) continue;
        if (local.distanceTo(other) <= limit) {
            // Здесь можно обработать другого игрока.
        }
    }
});

nearby.register();
```

## Логирование

```javascript
api.log("Сообщение в игровом логе");
```

Не записывайте сообщение в лог на каждом тике без необходимости: `onTick`
выполняется часто. Для API callbacks, данных игрока и ограничений см.
[основное руководство](./Script-API.md), [настройки](./modules-and-settings.md),
и [игровые данные](./game-data.md).
