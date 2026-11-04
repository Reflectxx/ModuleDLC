package dev.dlc.client.setting;

import java.util.List;
import java.util.function.BooleanSupplier;

public class ModeSetting implements Setting<String> {
    private final String name;
    private final String hint;
    private final List<String> modes;
    private String value;
    private final BooleanSupplier visible;

    public ModeSetting(String name, String hint, List<String> modes, String value) {
        this(name, hint, modes, value, () -> true);
    }
    public ModeSetting(String name, String hint, List<String> modes, String value, BooleanSupplier visible) {
        this.name = name; this.hint = hint; this.modes = modes; this.value = value; this.visible = visible;
    }
    public String name() { return name; }
    public String hint() { return hint; }
    public boolean visible() { return visible.getAsBoolean(); }
    public List<String> modes() { return modes; }
    public String value() { return value; }
    public void set(String v) { value = v; }
}
