# Игрок, мир и другие игроки

## ScriptContext

Каждый callback получает контекст текущего клиента:

| Метод | Результат |
| --- | --- |
| `ctx.inGame()` | `true`, если одновременно доступны игрок и мир. |
| `ctx.player()` | Локальный `LocalPlayer` либо `null`. |
| `ctx.world()` | Текущий клиентский `Level` либо `null`. |
| `ctx.players()` | Неизменяемый снимок игроков в загруженном клиентском мире; пустой список, если мир не открыт. |
| `ctx.effects()` | Снимок активных эффектов локального игрока (`MobEffectInstance`); пустой список, если игрок недоступен. |
| `ctx.targetedEntity()` | Сущность под прицелом либо `null`. |
| `ctx.targetedBlock()` | Позиция блока под прицелом (`BlockPos`) либо `null`. |
| `ctx.blockId(x, y, z)` | Registry ID блока по координатам, например `minecraft:stone`; `null`, если мир не открыт. |
| `ctx.minecraft()` | Экземпляр Minecraft-клиента. |
| `ctx.module()` | Модуль, чей callback сейчас выполняется. |
| `ctx.playerName()` | Имя локального игрока либо пустая строка. |
| `ctx.health()` | Здоровье локального игрока (`Float`) либо `null`. |
| `ctx.foodLevel()` | Уровень сытости (`Integer`) либо `null`. |
| `ctx.selectedSlot()` | Выбранный слот хотбара `0..8` либо `null`. |
| `ctx.message(text)` | Локальное сообщение в чате. |
| `ctx.sendChat(text)` | Отправка обычного сообщения от лица игрока. |
| `ctx.setSprinting(value)` | Изменение состояния спринта локального игрока. |
| `ctx.setSelectedSlot(slot)` | Выбрать слот хотбара `0..8`; возвращает `false`, если игрок недоступен. |
| `ctx.swingMainHand()` | Анимация удара основной рукой. |
| `ctx.useMainHand()` | Использовать предмет в основной руке; возвращает `InteractionResult`. |
| `ctx.interactWithTarget()` | Взаимодействовать с блоком/сущностью под прицелом; если цель не выбрана, использовать предмет в руке. |

Вне мира `player()` и `world()` могут быть `null`. В игровом callback сначала
проверяйте `ctx.inGame()`. `health()`, `foodLevel()` и `selectedSlot()` в этом
случае возвращают `null`. `blockId()` обращается к данным клиентского мира;
используйте координаты загруженных областей.

`setSelectedSlot()` выбрасывает `IllegalArgumentException`, если слот вне
диапазона `0..8`. `swingMainHand()`, `useMainHand()` и `interactWithTarget()`
требуют активный мир и выбрасывают `IllegalStateException`, если его нет.
Действия предметом идут через vanilla `gameMode` клиента; сервер может их
отклонить или ограничить.

`ctx.effects()` можно использовать для чтения активных эффектов игрока из
JavaScript. Уровень эффекта в Minecraft начинается с нуля (`getAmplifier()`),
поэтому для привычного отображения прибавьте единицу. Длительность указана в
тиках; 20 тиков равны примерно одной секунде:

```javascript
var effectsModule = api.module("effect_list", "Effect List", "Player");
var previousEffects = null;

effectsModule.onTick(function (ctx) {
    var effects = ctx.effects();
    var descriptions = [];

    for (var i = 0; i < effects.size(); i++) {
        var effect = effects.get(i);
        var name = effect.getEffect().value().getDisplayName().getString();
        var duration = effect.isInfiniteDuration()
            ? "∞"
            : Math.ceil(effect.getDuration() / 20) + " сек.";

        descriptions.push(
            name + " " + (effect.getAmplifier() + 1) + " (" + duration + ")"
        );
    }

    var snapshot = descriptions.join(", ") || "нет";
    if (snapshot !== previousEffects) {
        ctx.message("Эффекты: " + snapshot);
        previousEffects = snapshot;
    }
});

effectsModule.register();
```

Сообщение появляется только при изменении списка эффектов.

## Чтение других игроков

`ctx.players()` возвращает снимок списка игроков, сущности которых сейчас
известны клиенту; список может включать локального игрока. Пример для
JavaScript:

```javascript
var previousPlayers = null;
var playerList = api.module("player_list", "Player List", "Player");

playerList.onTick(function (ctx) {
    if (!ctx.inGame()) return;

    var localId = ctx.player().getUUID().toString();
    var players = ctx.players();
    var names = [];

    for (var i = 0; i < players.size(); i++) {
        var other = players.get(i);
        if (other.getUUID().toString() === localId) continue;

        names.push(
            other.getName().getString()
            + " ("
            + Math.round(other.getHealth())
            + " HP)"
        );
    }

    var snapshot = names.join(", ");
    if (snapshot !== previousPlayers) {
        api.log("Видимые игроки: " + (snapshot || "нет"));
        previousPlayers = snapshot;
    }
});

playerList.register();
```

## Цель прицела и действие

```javascript
var targetInfo = api.module("target_info", "Target Info", "Visuals");
var lastTarget = "";

targetInfo.onTick(function (ctx) {
    if (!ctx.inGame()) return;

    var entity = ctx.targetedEntity();
    if (entity !== null) {
        var name = entity.getName().getString();
        if (name !== lastTarget) {
            ctx.message("Цель: " + name);
            lastTarget = name;
        }
    } else {
        lastTarget = "";
    }
});

targetInfo.register();
```

Для одноразового vanilla-взаимодействия из callback можно вызвать
`ctx.interactWithTarget()`. Используйте его только когда действительно нужно
выполнить действие: например, в `onEnable`, а не каждый тик.

У объектов игроков доступны Java-методы Minecraft: например, UUID,
отображаемое имя, координаты, здоровье, расстояние и состояние сущности.
Актуальность конкретных значений зависит от данных, синхронизированных
сервером. Список `world.players()` ограничен тем, что клиентский мир в данный
момент загрузил; для поиска рядом используйте проверку расстояния или bounding
box.

## Пример на Java

```java
import net.minecraft.world.entity.player.Player;
import dev.dlc.client.script.ScriptApi;
import dev.dlc.client.script.ScriptEntrypoint;

public final class NearbyPlayers implements ScriptEntrypoint {
    @Override
    public void register(ScriptApi api) {
        var nearby = api.module("nearby", "Nearby players", "Player");
        nearby.onTick(ctx -> {
            if (!ctx.inGame()) return;

            Player local = ctx.player();
            for (Player other : ctx.players()) {
                if (!other.getUUID().equals(local.getUUID())) {
                    api.log(other.getName().getString());
                }
            }
        });
        nearby.register();
    }
}
```

В Java и Kotlin типы Minecraft можно импортировать обычными `import`.
JavaScript получает Java-объекты из `ctx` и может вызывать их доступные методы.

## Границы клиентских данных

Это клиентский API, а не доступ к серверной базе. Скрипт видит только данные,
которые игра получила и загрузила: например, сущностей в клиентском мире.
Нельзя через этот API читать скрытый инвентарь другого игрока, его данные,
которые сервер не отправлял, или сведения об игроках вне загруженного мира.
Сервер также может отклонять или ограничивать действия клиента.

## Безопасность действий

`sendChat(text)` отправляет обычное сообщение в чат и не обходит ограничения
сервера. Любые изменения движения и состояния применяются локально и не
гарантируют, что сервер их примет.
