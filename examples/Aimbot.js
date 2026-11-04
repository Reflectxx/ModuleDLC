var aimbot = api.module(
    "aimbot",
    "Aimbot",
    "Combat",
    "Помогает наводиться каждый кадр, плавно подтягивая прицел к цели"
);

var targets = aimbot.multiToggleSetting(
    "Targets",
    "По кому бить",
    "Players,Mobs"
);

var rangeSetting = aimbot.numberSetting(
    "Range",
    "Дальность",
    0,
    6,
    0.1,
    6,
    " blocks"
);

var strengthSetting = aimbot.numberSetting(
    "Strength",
    "Сила притягивания",
    0,
    50,
    1,
    10,
    ""
);

var verticalSetting = aimbot.toggleSetting(
    "Vertical Aim",
    "Вертикальный аим",
    true
);

var AABB = Packages.net.minecraft.world.phys.AABB;
var Player = Packages.net.minecraft.world.entity.player.Player;
var LivingEntity = Packages.net.minecraft.world.entity.LivingEntity;
var Predicate = Packages.java.util.function.Predicate;

function aim(ctx) {
    if (!ctx.inGame()) return;

    var player = ctx.player();
    var world = ctx.world();

    var range = rangeSetting.value();
    if (range <= 0) return;

    var strength = strengthSetting.value();
    var factor = strength / 50.0;
    if (factor <= 0) return;
    if (factor > 1) factor = 1;

    var aimPlayers = targets.values().get(0).enabled();
    var aimMobs = targets.values().get(1).enabled();
    var vertical = verticalSetting.enabled();

    var eye = player.getEyePosition();
    var box = new AABB(
        eye.x - range, eye.y - range, eye.z - range,
        eye.x + range, eye.y + range, eye.z + range
    );

    var alivePredicate = new Predicate({
        test: function (entity) {
            return entity.isAlive();
        }
    });

    var entities = world.getEntities(player, box, alivePredicate);

    var best = null;
    var bestDistance = range + 1;

    for (var i = 0; i < entities.size(); i++) {
        var entity = entities.get(i);
        if (entity === player) continue;

        var isPlayer = entity instanceof Player;
        var isMob = (entity instanceof LivingEntity) && !(entity instanceof Player);

        if (isPlayer && !aimPlayers) continue;
        if (isMob && !aimMobs) continue;
        if (!isPlayer && !isMob) continue;

        var distance = player.distanceTo(entity);
        if (distance <= range && distance < bestDistance) {
            bestDistance = distance;
            best = entity;
        }
    }

    if (best === null) return;

    var targetEye = best.getEyePosition();

    var dx = targetEye.x - eye.x;
    var dy = targetEye.y - eye.y;
    var dz = targetEye.z - eye.z;
    var horizontalDistance = Math.sqrt(dx * dx + dz * dz);

    var radiansToDegrees = 180.0 / Math.PI;
    var desiredYaw = Math.atan2(dz, dx) * radiansToDegrees - 90.0;
    var desiredPitch = -Math.atan2(dy, horizontalDistance) * radiansToDegrees;

    var currentYaw = player.getYRot();
    var yawDelta = desiredYaw - currentYaw;
    while (yawDelta > 180) yawDelta -= 360;
    while (yawDelta < -180) yawDelta += 360;
    player.setYRot(currentYaw + yawDelta * factor);

    if (vertical) {
        var currentPitch = player.getXRot();
        var pitchDelta = desiredPitch - currentPitch;
        var newPitch = currentPitch + pitchDelta * factor;
        if (newPitch > 90) newPitch = 90;
        if (newPitch < -90) newPitch = -90;
        player.setXRot(newPitch);
    }
}

aimbot.onFrame(aim);

aimbot.register();
