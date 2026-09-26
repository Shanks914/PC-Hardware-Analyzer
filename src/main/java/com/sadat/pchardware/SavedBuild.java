package com.sadat.pchardware;

import java.util.List;

public record SavedBuild(long id, String name, List<Part> parts) {
    public SavedBuild {
        parts = List.copyOf(parts);
    }

    @Override
    public String toString() {
        return name + " (" + parts.size() + " parts)";
    }
}
