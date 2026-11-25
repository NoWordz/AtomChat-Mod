package com.atom.chat.config;

/** Graphics quality is a client policy independent of appearance snapshots. */
public enum GraphicsQuality {
    ULTRA("ultra"),
    BALANCED("balanced"),
    PERFORMANCE("performance");

    private final String id;

    GraphicsQuality(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}
