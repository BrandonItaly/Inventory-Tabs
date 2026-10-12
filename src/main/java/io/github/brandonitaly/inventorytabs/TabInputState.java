package io.github.brandonitaly.inventorytabs;

import java.util.HashSet;
import java.util.Set;

/** Retains ownership of a mouse gesture even if its press opens another screen. */
public final class TabInputState {
    private final Set<Integer> buttons = new HashSet<>();

    public void beginPress(int button) { buttons.remove(button); }
    public void capture(int button) { buttons.add(button); }
    public boolean dragging(int button) { return buttons.contains(button); }
    public boolean release(int button) { return buttons.remove(button); }
    public void clear() { buttons.clear(); }
}
