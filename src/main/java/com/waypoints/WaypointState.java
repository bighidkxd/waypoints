package com.waypoints;

/**
 * Holds all runtime state for the currently loaded waypoint group.
 * Thread-access: advance logic runs on the render thread; commands run on the game thread.
 * Keep mutations simple – no heavy locking needed for a client-side mod.
 */
public class WaypointState {

    private static final WaypointState INSTANCE = new WaypointState();

    // ---- loaded data ----
    public WaypointGroup loadedGroup = null;

    /**
     * Index of the waypoint the player is CURRENTLY AT (just mined / arrived at).
     * "next" = currentIndex + 1, "prev" = currentIndex - 1 (wrapped).
     */
    public int currentIndex = 0;

    // display settings
    public boolean setupMode = false;   // show ALL waypoints in the loaded group
    public boolean enabled   = true;

    // advance settings
    /** How close (blocks, 3-D) the player must be to the NEXT waypoint to start the timer. */
    public double advanceRange   = 5.0;
    /** How long (ms) the player must stay within advanceRange before we auto-advance. */
    public long   advanceDelayMs = 2000L;

    // advance timer (set by renderer, never persisted)
    public long advanceTimerStart = -1L;

    private WaypointState() {}

    public static WaypointState getInstance() { return INSTANCE; }

    // queries

    public boolean hasGroup() {
        return loadedGroup != null && !loadedGroup.waypoints.isEmpty();
    }

    public int size() {
        return loadedGroup == null ? 0 : loadedGroup.waypoints.size();
    }

    /** The waypoint the player is currently at / just finished. */
    public WaypointPoint getCurrent() {
        if (!hasGroup()) return null;
        return loadedGroup.waypoints.get(Math.floorMod(currentIndex, size()));
    }

    /** The waypoint the player is heading toward next. */
    public WaypointPoint getNext() {
        if (!hasGroup()) return null;
        return loadedGroup.waypoints.get(Math.floorMod(currentIndex + 1, size()));
    }

    /** The waypoint the player just came from. */
    public WaypointPoint getPrev() {
        if (!hasGroup()) return null;
        return loadedGroup.waypoints.get(Math.floorMod(currentIndex - 1, size()));
    }

    public int getNextIndex()  { return Math.floorMod(currentIndex + 1, size()); }
    public int getPrevIndex()  { return Math.floorMod(currentIndex - 1, size()); }

    // ------------------------------------------------------------------ mutations

    public void load(WaypointGroup group) {
        this.loadedGroup        = group;
        this.currentIndex       = 0;
        this.advanceTimerStart  = -1L;
    }

    public void unload() {
        this.loadedGroup        = null;
        this.currentIndex       = 0;
        this.advanceTimerStart  = -1L;
    }

    /** Reset pointer back to the first waypoint without unloading. */
    public void reset() {
        this.currentIndex      = 0;
        this.advanceTimerStart = -1L;
    }

    /** Auto-advance: next becomes current. Called by renderer when timer fires. */
    public void advance() {
        if (!hasGroup()) return;
        currentIndex       = Math.floorMod(currentIndex + 1, size());
        advanceTimerStart  = -1L;
    }

    /** Skip forward (positive) or backward (negative) by n steps. */
    public void skip(int n) {
        if (!hasGroup()) return;
        currentIndex       = Math.floorMod(currentIndex + n, size());
        advanceTimerStart  = -1L;
    }

    /** Jump directly to a 0-based index. */
    public void skipTo(int index) {
        if (!hasGroup() || index < 0 || index >= size()) return;
        currentIndex       = index;
        advanceTimerStart  = -1L;
    }
}