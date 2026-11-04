package dev.dlc.client.setting;

import java.util.List;
import java.util.function.BooleanSupplier;

public class MultiBooleanSetting implements Setting<List<BooleanSetting>> {
    private final String name;
    private final String hint;
    private final List<BooleanSetting> values;
    private final BooleanSupplier visible;

    public MultiBooleanSetting(String name, String hint, List<BooleanSetting> values) {
        this(name, hint, values, () -> true);
    }
    public MultiBooleanSetting(String name, String hint, List<BooleanSetting> values, BooleanSupplier visible) {
        this.name = name; this.hint = hint; this.values = values; this.visible = visible;
    }
    public String name() { return name; }
    public String hint() { return hint; }
    public boolean visible() { return visible.getAsBoolean(); }
    public List<BooleanSetting> values() { return values; }
}
