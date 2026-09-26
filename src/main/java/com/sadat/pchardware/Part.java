package com.sadat.pchardware;

import java.util.Map;

public record Part(String category, String name, String specs, double price, Map<String, String> attributes) {
    public Part(String category, String name, String specs, double price) {
        this(category, name, specs, price, Map.of());
    }

    public Part {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public String attribute(String key) {
        return attributes.getOrDefault(key, "");
    }

    @Override
    public String toString() {
        return name + " — " + String.format("৳%,.0f", price);
    }
}
