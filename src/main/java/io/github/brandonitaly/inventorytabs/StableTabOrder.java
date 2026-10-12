package io.github.brandonitaly.inventorytabs;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public final class StableTabOrder {
    private final List<String> keys = new ArrayList<>();

    public List<String> update(List<String> discovered) {
        var present = new HashSet<>(discovered);
        keys.removeIf(key -> !present.contains(key));
        var known = new HashSet<>(keys);
        for (String key : discovered) if (known.add(key)) keys.add(key);
        return List.copyOf(keys);
    }

    public void clear() { keys.clear(); }
}
