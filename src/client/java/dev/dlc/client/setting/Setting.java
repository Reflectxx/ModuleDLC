package dev.dlc.client.setting;

public interface Setting<T> {
    String name();
    String hint();
    boolean visible();
}
