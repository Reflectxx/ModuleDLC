var autoSprintInfo = {
    id: "auto_sprint",
    name: "Auto Sprint",
    category: "Player",
    description: "Автоматически включает спринт при движении вперёд"
};

var autoSprint = api.module(
    autoSprintInfo.id,
    autoSprintInfo.name,
    autoSprintInfo.category,
    autoSprintInfo.description
);

var statusBackground = autoSprint.hudRectangle("status_background");
statusBackground.setPosition(5, 5);
statusBackground.setSize(92, 20);
statusBackground.setShape("rounded");
statusBackground.setRadius(10);
statusBackground.setOpacity(0.9);
statusBackground.setColor(20, 26, 32);
statusBackground.setDraggable(true);

var statusText = autoSprint.hudText("status_text");
statusText.setText("Auto Sprint: OFF");
statusText.setSize(8);
statusText.setColor(255, 255, 255);
statusText.setOpacity(1);
statusBackground.attach(statusText, 5, 5);

autoSprint.onTick(function (ctx) {
    var player = ctx.player();
    if (player === null) return;

    var shouldSprint =
        player.input.hasForwardImpulse() && !player.isShiftKeyDown();
    ctx.setSprinting(shouldSprint);
    statusText.setText(shouldSprint ? "Au Sprint: ON" : "Auto Sprint: OFF");
});

autoSprint.onDisable(function (ctx) {
    ctx.setSprinting(false);
});

autoSprint.register();
