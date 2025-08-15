package com.example.scheduler;

import lombok.Getter;

@Getter
class ConfigurationChangeEvent {
    // getters
    private final String property;
    private final Object oldValue;
    private final Object newValue;
    private final java.time.LocalDateTime timestamp;

    public ConfigurationChangeEvent(String property, Object oldValue, Object newValue) {
        this.property = property;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.timestamp = java.time.LocalDateTime.now();
    }

    @Override
    public String toString() {
        return String.format("ConfigurationChangeEvent{property='%s', old='%s', new='%s', time=%s}",
                property, oldValue, newValue, timestamp);
    }
}
