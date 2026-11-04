package dev.dlc.client.script;

import dev.dlc.client.module.Module;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

public final class ScriptContext {
    private final Minecraft minecraft;
    private final Module module;
    private final String source;
    private final ScriptStorage storage;

    public ScriptContext(Minecraft minecraft, Module module) {
        this(minecraft, module, module == null ? "api" : module.source());
    }

    public ScriptContext(Minecraft minecraft, Module module, String source) {
        this.minecraft = minecraft;
        this.module = module;
        this.source = source;
        storage = ScriptStorage.forScript(source);
    }

    public ScriptContext(Minecraft minecraft) {
        this(minecraft, null);
    }

    public Minecraft minecraft() {
        return minecraft;
    }

    public LocalPlayer player() {
        return minecraft.player;
    }

    public Level world() {
        return minecraft.level;
    }

    public List<Player> players() {
        return minecraft.level instanceof ClientLevel level
                ? List.copyOf(level.players())
                : List.of();
    }

    public List<MobEffectInstance> effects() {
        return minecraft.player == null
                ? List.of()
                : List.copyOf(minecraft.player.getActiveEffects());
    }

    public Entity targetedEntity() {
        return minecraft.hitResult instanceof EntityHitResult hit ? hit.getEntity() : null;
    }

    public BlockPos targetedBlock() {
        return minecraft.hitResult instanceof BlockHitResult hit ? hit.getBlockPos() : null;
    }

    public String blockId(int x, int y, int z) {
        if (minecraft.level == null) return null;
        var state = minecraft.level.getBlockState(new BlockPos(x, y, z));
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    public ScriptStorage storage() {
        return storage;
    }

    public List<BlockPos> findBlocks(String blockId, int radius) {
        if (!(minecraft.level instanceof ClientLevel level)) return List.of();
        if (radius < 0 || radius > 16)
            throw new IllegalArgumentException("Block search radius must be between 0 and 16");
        Identifier identifier = Identifier.tryParse(blockId);
        if (identifier == null)
            throw new IllegalArgumentException("Invalid block identifier: " + blockId);
        var target = BuiltInRegistries.BLOCK.getOptional(identifier)
                .orElseThrow(() -> new IllegalArgumentException("Unknown block: " + blockId));
        if (minecraft.player == null) return List.of();

        BlockPos center = minecraft.player.blockPosition();
        List<BlockPos> found = new java.util.ArrayList<>();
        int radiusSquared = radius * radius;
        for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
            for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                if (!level.hasChunk(x >> 4, z >> 4)) continue;
                for (int y = Math.max(level.getMinY(), center.getY() - radius);
                        y <= Math.min(level.getMaxY() - 1, center.getY() + radius);
                        y++) {
                    int dx = x - center.getX(), dy = y - center.getY(), dz = z - center.getZ();
                    if (dx * dx + dy * dy + dz * dz > radiusSquared) continue;
                    BlockPos pos = new BlockPos(x, y, z);
                    if (level.getBlockState(pos).is(target)) {
                        found.add(pos.immutable());
                        if (found.size() == 4096) return List.copyOf(found);
                    }
                }
            }
        }
        return List.copyOf(found);
    }

    public Module module() {
        return module;
    }

    public boolean inGame() {
        return minecraft.player != null && minecraft.level != null;
    }

    public String playerName() {
        return inGame() ? minecraft.player.getName().getString() : "";
    }

    public void message(String text) {
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal(text), false);
        }
    }

    public void sendChat(String text) {
        if (minecraft.player != null && !text.isBlank()) minecraft.player.connection.sendChat(text);
    }

    public void setSprinting(boolean sprinting) {
        if (minecraft.player != null) minecraft.player.setSprinting(sprinting);
    }

    public Float health() {
        return minecraft.player == null ? null : minecraft.player.getHealth();
    }

    public Integer foodLevel() {
        return minecraft.player == null ? null : minecraft.player.getFoodData().getFoodLevel();
    }

    public Integer selectedSlot() {
        return minecraft.player == null ? null : minecraft.player.getInventory().getSelectedSlot();
    }

    public boolean setSelectedSlot(int slot) {
        if (slot < 0 || slot > 8) {
            throw new IllegalArgumentException("Hotbar slot must be between 0 and 8");
        }
        if (minecraft.player == null) return false;
        minecraft.player.getInventory().setSelectedSlot(slot);
        return true;
    }

    public void swingMainHand() {
        requireInGame();
        minecraft.player.swing(InteractionHand.MAIN_HAND);
    }

    public InteractionResult useMainHand() {
        requireInGame();
        if (minecraft.gameMode == null) {
            throw new IllegalStateException(
                    "Cannot use an item while the game mode is unavailable");
        }
        return minecraft.gameMode.useItem(minecraft.player, InteractionHand.MAIN_HAND);
    }

    public InteractionResult interactWithTarget() {
        requireInGame();
        if (minecraft.gameMode == null) {
            throw new IllegalStateException(
                    "Cannot interact while the game mode is unavailable");
        }
        if (minecraft.hitResult instanceof EntityHitResult hit) {
            return minecraft.gameMode.interact(
                    minecraft.player, hit.getEntity(), InteractionHand.MAIN_HAND);
        }
        if (minecraft.hitResult instanceof BlockHitResult hit) {
            return minecraft.gameMode.useItemOn(
                    minecraft.player, InteractionHand.MAIN_HAND, hit);
        }
        return useMainHand();
    }

    private void requireInGame() {
        if (!inGame()) throw new IllegalStateException("This action requires an active game world");
    }
}
