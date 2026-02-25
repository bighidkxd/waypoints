package com.waypoints;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Persists waypoint groups to &lt;configDir&gt;/waypoints_groups.json.
 * Uses an atomic write (write to .tmp then rename) to prevent corruption.
 * Adapted from the Notenoughfakepixel CHwaypoints.
 */
public class WaypointStorage {

    private static final WaypointStorage INSTANCE = new WaypointStorage();

    // groups keyed by lowercase name for case-insensitive lookups
    private final Map<String, WaypointGroup> groups = new LinkedHashMap<>();
    private final Gson gson    = new GsonBuilder().setPrettyPrinting().create();
    private final AtomicBoolean dirty = new AtomicBoolean(false);
    private File file;

    private WaypointStorage() {}

    public static WaypointStorage getInstance() { return INSTANCE; }

    // ------------------------------------------------------------------ init / load

    public void initFile(File configDir) {
        if (file != null) return;
        configDir.mkdirs();
        file = new File(configDir, "waypoints_groups.json");
    }

    public synchronized void load() {
        if (file == null || !file.exists()) return;
        try (Reader r = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            Type type = new TypeToken<Map<String, WaypointGroup>>() {}.getType();
            Map<String, WaypointGroup> loaded = gson.fromJson(r, type);
            if (loaded != null) {
                groups.clear();
                // Normalise keys to lowercase and ensure each group has a non-null waypoints list
                for (Map.Entry<String, WaypointGroup> entry : loaded.entrySet()) {
                    WaypointGroup g = entry.getValue();
                    if (g == null) continue;
                    if (g.waypoints == null) g.waypoints = new ArrayList<>();
                    if (g.name == null)      g.name = entry.getKey();
                    groups.put(entry.getKey().toLowerCase(), g);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        dirty.set(false);
    }

    // queries

    public synchronized Map<String, WaypointGroup> getGroups() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(groups));
    }

    public synchronized WaypointGroup getGroup(String name) {
        return name == null ? null : groups.get(name.toLowerCase());
    }

    //  mutations

    public synchronized void putGroup(WaypointGroup group) {
        if (group == null || group.name == null) return;
        groups.put(group.name.toLowerCase(), group);
        dirty.set(true);
    }

    public synchronized boolean removeGroup(String name) {
        if (name == null) return false;
        boolean removed = groups.remove(name.toLowerCase()) != null;
        if (removed) dirty.set(true);
        return removed;
    }

    public void markDirty() { dirty.set(true); }

    // persistence

    public synchronized void saveIfDirty() {
        if (!dirty.get()) return;
        saveForce();
    }

    public synchronized void saveForce() {
        if (file == null) return;
        File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
        try (Writer w = new BufferedWriter(new OutputStreamWriter(
                Files.newOutputStream(tmp.toPath(),
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING),
                StandardCharsets.UTF_8))) {
            gson.toJson(groups, w);
            w.flush();
            try {
                Files.move(tmp.toPath(), file.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            dirty.set(false);
        } catch (Exception e) {
            e.printStackTrace();
            try { Files.deleteIfExists(tmp.toPath()); } catch (Exception ignored) {}
        }
    }
}