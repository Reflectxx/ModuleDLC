package dev.dlc.client.ui.panel;

import dev.dlc.client.ClientCore;
import dev.dlc.client.module.ModuleCategories;
import dev.dlc.client.render.ui.AnimatedFloat;
import dev.dlc.client.render.ui.SliderModel;
import dev.dlc.client.setting.BooleanSetting;
import dev.dlc.client.setting.ColorSetting;
import dev.dlc.client.setting.ModeSetting;
import dev.dlc.client.setting.MultiBooleanSetting;
import dev.dlc.client.setting.NumberSetting;

import java.util.ArrayList;
import java.util.List;

final class PanelModel {
    public record Category(String name, List<Module> modules) {
        public Category {
            modules = List.copyOf(modules);
        }
    }

    public static final class Module {
        public final String name, description;
        final String searchText;
        public final List<Setting> settings;
        public final AnimatedFloat animation;
        public boolean enabled;
        private final dev.dlc.client.module.Module backing;

        Module(dev.dlc.client.module.Module backing, List<Setting> settings) {
            this.backing = backing;
            name = backing.name();
            description = backing.description();
            searchText = (name + " " + description).toLowerCase(java.util.Locale.ROOT);
            enabled = backing.enabled();
            this.settings = List.copyOf(settings);
            animation = new AnimatedFloat(enabled ? 1 : 0);
        }

        public String id() {
            return backing.id();
        }

        public void toggle() {
            enabled = !enabled;
            backing.setEnabled(enabled);
            animation.setTarget(enabled ? 1 : 0);
        }

        void syncEnabled() {
            enabled = backing.enabled();
            animation.setTarget(enabled ? 1 : 0);
        }

        public boolean hasError() {
            return backing.hasError();
        }
    }

    public sealed interface Setting permits Toggle, Slider, Range, Choice, Group, ColorValue {
        String name();

        String hint();

        default boolean visible() {
            return true;
        }
    }

    public static final class Toggle implements Setting {
        private final BooleanSetting backing;
        public final AnimatedFloat animation;
        public boolean value;

        Toggle(BooleanSetting backing) {
            this.backing = backing;
            value = backing.enabled();
            animation = new AnimatedFloat(value ? 1 : 0);
        }

        public String name() {
            return backing.name();
        }

        public String hint() {
            return backing.hint();
        }

        public boolean visible() {
            return backing.visible();
        }

        public void toggle() {
            value = !value;
            backing.set(value);
            animation.setTarget(value ? 1 : 0);
        }
    }

    public static final class Slider implements Setting {
        private final NumberSetting backing;
        private final String suffix;
        private final NumericText label;
        public final SliderModel model;

        Slider(NumberSetting backing) {
            this.backing = backing;
            suffix = backing.suffix();
            label = new NumericText(backing.step());
            model = new SliderModel(backing.min(), backing.max(), backing.value());
        }

        public String name() {
            return backing.name();
        }

        public String hint() {
            return backing.hint();
        }

        public boolean visible() {
            return backing.visible();
        }

        public String value() {
            return label.format(model.value()) + suffix;
        }

        public void sync() {
            backing.set(model.value());
            model.setValue(backing.value());
        }
    }

    /** A genuine two-value control backed by two existing numeric settings with a shared domain. */
    public static final class Range implements Setting {
        private final NumberSetting lower;
        private final NumberSetting upper;
        final AnimatedFloat lowProgress;
        final AnimatedFloat highProgress;
        private final NumericText lowLabel;
        private final NumericText highLabel;
        private float lastLow = Float.NaN;
        private float lastHigh = Float.NaN;
        private String display;

        Range(NumberSetting lower, NumberSetting upper) {
            if (lower.min() != upper.min()
                    || lower.max() != upper.max()
                    || lower.step() != upper.step()
                    || !lower.suffix().equals(upper.suffix())
                    || lower.value() > upper.value()
                    || lower.step() <= 0
                    || !Float.isFinite(lower.step())
                    || !Float.isFinite(lower.min())
                    || !Float.isFinite(lower.max())
                    || !Float.isFinite(lower.value())
                    || !Float.isFinite(upper.value())
                    || lower.max() <= lower.min()) {
                throw new IllegalArgumentException(
                        "Range endpoints must share an ordered numeric domain");
            }
            this.lower = lower;
            this.upper = upper;
            lowLabel = new NumericText(lower.step());
            highLabel = new NumericText(upper.step());
            lowProgress = new AnimatedFloat(progress(lower.value()));
            highProgress = new AnimatedFloat(progress(upper.value()));
        }

        public String name() {
            return lower.name();
        }

        public String hint() {
            return lower.hint();
        }

        public boolean visible() {
            return lower.visible() && upper.visible();
        }

        public String value() {
            float low = lower.value(), high = upper.value();
            if (lastLow != low || lastHigh != high) {
                display = lowLabel.format(low) + " - " + highLabel.format(high) + lower.suffix();
                lastLow = low;
                lastHigh = high;
            }
            return display;
        }

        private float progress(float value) {
            return (value - lower.min()) / (lower.max() - lower.min());
        }

        void update(float dt) {
            lowProgress.setTarget(progress(lower.value()));
            highProgress.setTarget(progress(upper.value()));
            lowProgress.update(dt, 40);
            highProgress.update(dt, 40);
        }

        void setFromMouse(float mx, float x, float width, boolean high) {
            float p = Math.max(0, Math.min(1, (mx - x) / width));
            float value = lower.min() + p * (lower.max() - lower.min());
            if (high) upper.set(Math.max(lower.value(), value));
            else lower.set(Math.min(upper.value(), value));
        }
    }

    public static final class Choice implements Setting {
        private final ModeSetting backing;
        public final List<String> values;
        public int selected;

        Choice(ModeSetting backing) {
            this.backing = backing;
            values = backing.modes();
            selected = Math.max(0, values.indexOf(backing.value()));
        }

        public String name() {
            return backing.name();
        }

        public String hint() {
            return backing.hint();
        }

        public boolean visible() {
            return backing.visible();
        }

        public String value() {
            return values.get(selected);
        }

        public void select(int index) {
            selected = Math.max(0, Math.min(values.size() - 1, index));
            backing.set(value());
        }
    }

    public static final class Group implements Setting {
        private final MultiBooleanSetting backing;
        public final List<Toggle> values;

        Group(MultiBooleanSetting backing) {
            this.backing = backing;
            values = backing.values().stream().map(Toggle::new).toList();
        }

        public String name() {
            return backing.name();
        }

        public String hint() {
            return backing.hint();
        }

        public boolean visible() {
            return backing.visible();
        }

        public String summary() {
            long enabled = values.stream().filter(value -> value.value).count();
            return enabled + "/" + values.size() + " enabled";
        }
    }

    public static final class ColorValue implements Setting {
        private final ColorSetting backing;

        ColorValue(ColorSetting backing) {
            this.backing = backing;
        }

        public String name() {
            return backing.name();
        }

        public String hint() {
            return backing.hint();
        }

        public boolean visible() {
            return backing.visible();
        }

        public int argb() {
            return backing.argb();
        }

        public String value() {
            return backing.hex();
        }

        public float hue() {
            return backing.hue();
        }

        public float saturation() {
            return backing.saturation();
        }

        public float brightness() {
            return backing.brightness();
        }

        public float alpha() {
            return backing.alpha();
        }

        public void setHsb(float hue, float saturation, float brightness) {
            backing.setHsb(hue, saturation, brightness);
        }

        public void setAlpha(float alpha) {
            backing.setAlpha(alpha);
        }
    }

    public static List<Category> create() {
        List<dev.dlc.client.module.Module> source = ClientCore.getInstance().modules().all();
        java.util.Map<String, List<dev.dlc.client.module.Module>> grouped =
                source.stream()
                        .collect(
                                java.util.stream.Collectors.groupingBy(
                                        dev.dlc.client.module.Module::category,
                                        java.util.TreeMap::new,
                                        java.util.stream.Collectors.toList()));
        return ModuleCategories.ALL.stream()
                .map(
                        name ->
                                new Category(
                                        name,
                                        grouped.getOrDefault(name, List.of()).stream()
                                                .map(PanelModel::live)
                                                .toList()))
                .toList();
    }

    private static Module live(dev.dlc.client.module.Module core) {
        List<Setting> settings = new ArrayList<>();
        for (dev.dlc.client.setting.Setting<?> setting : core.settings()) {
            if (setting instanceof BooleanSetting value) {
                settings.add(new Toggle(value));
            } else if (setting instanceof NumberSetting value) {
                settings.add(new Slider(value));
            } else if (setting instanceof ModeSetting value) {
                settings.add(new Choice(value));
            } else if (setting instanceof MultiBooleanSetting value) {
                settings.add(new Group(value));
            } else if (setting instanceof ColorSetting value) {
                settings.add(new ColorValue(value));
            }
        }
        return new Module(core, settings);
    }

    /** Format only on value changes. Decimal precision follows the actual setting step. */
    private static final class NumericText {
        private final int digits;
        private float previous = Float.NaN;
        private String label;

        NumericText(float step) {
            if (!Float.isFinite(step) || step <= 0)
                throw new IllegalArgumentException("Invalid numeric step");
            digits =
                    Math.max(
                            0,
                            new java.math.BigDecimal(Float.toString(step))
                                    .stripTrailingZeros()
                                    .scale());
        }

        String format(float value) {
            if (previous != value) {
                label =
                        new java.math.BigDecimal(Float.toString(value))
                                .setScale(digits, java.math.RoundingMode.HALF_UP)
                                .stripTrailingZeros()
                                .toPlainString();
                previous = value;
            }
            return label;
        }
    }

    private PanelModel() {}
}
