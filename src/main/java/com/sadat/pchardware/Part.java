package com.sadat.pchardware;

public record Part(String category, String name, String specs, double price) {
    @Override
    public String toString() {
        return name + " — " + String.format("৳%,.0f", price);
    }
}
