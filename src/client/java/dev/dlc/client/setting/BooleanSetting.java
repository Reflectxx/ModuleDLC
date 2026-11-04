package dev.dlc.client.setting;

import java.util.function.BooleanSupplier;

public class BooleanSetting implements Setting<Boolean> {
    private final String name;
    private final String hint;
    private boolean value;
    private final BooleanSupplier visible;

    public BooleanSetting(String name, String hint, boolean value) {
        this(name, hint, value, () -> true);
    }
    public BooleanSetting(String name, String hint, boolean value, BooleanSupplier visible) {
        this.name = name; this.hint = hint; this.value = value; this.visible = visible;
    }
    public String name() { return name; }
    public String hint() { return hint; }
    public boolean visible() { return visible.getAsBoolean(); }
    public boolean enabled() { return value; }
    public void set(boolean v) { value = v; }
}
