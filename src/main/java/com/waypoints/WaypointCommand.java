package com.waypoints;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import java.lang.reflect.Type;
import java.util.*;


public class WaypointCommand extends CommandBase {

    private static final Minecraft mc   = Minecraft.getMinecraft();
    private static final Gson      GSON = new GsonBuilder().create();

    private static final List<String> SUBCOMMANDS = Arrays.asList(
            "list", "load", "unload", "clear", "setup", "reset",
            "skip", "unskip", "skipto", "enable", "disable",
            "create", "delete", "add", "insert", "remove", "rename",
            "export", "import", "range", "time", "save", "info"
    );

    // ------------------------------------------------------------------ ICommand

    @Override public String getCommandName()  { return "waypoints"; }
    @Override public String getCommandUsage(ICommandSender s) { return "/waypoints <subcommand>"; }
    @Override public boolean canCommandSenderUseCommand(ICommandSender s) { return true; }

    @Override
    public List<String> getCommandAliases() {
        return Collections.singletonList("w");
    }

    // ------------------------------------------------------------------ dispatch

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        WaypointState   state   = WaypointState.getInstance();
        WaypointStorage storage = WaypointStorage.getInstance();

        if (args.length == 0) { showGroupList(sender); return; }

        switch (args[0].toLowerCase()) {

            // navigation

            case "guide": {
                msg(sender, "&2 Waypoints Guide ");
                msg(sender, "&e/w list &7- Show all waypoint groups");
                msg(sender, "&e/w create <name>  &7- Create a new group");
                msg(sender, "&e/w delete <name> &7- Delete a group");
                msg(sender, "&e/w rename <old> <new> &7- Rename a group");
                msg(sender, "&e/w load <name> &7- Load a group");
                msg(sender, "&e/w add [name] &7- Add waypoint at your position");
                msg(sender, "&e/w insert <index> [name] &7- Insert waypoint at index");
                msg(sender, "&e/w remove <index> &7- Remove waypoint");
                msg(sender, "&e/w skip [n] &7- Skip forward");
                msg(sender, "&e/w unskip [n] &7- Go backward");
                msg(sender, "&e/w skipto <index> &7- Jump to waypoint");
                msg(sender, "&e/w reset &7- Reset to first waypoint");
                msg(sender, "&e/w enable &7- Enable rendering");
                msg(sender, "&e/w disable &7- Disable rendering");
                msg(sender, "&e/w export [name] &7- Copy route to clipboard");
                msg(sender, "&e/w import <name> &7- Import from clipboard");
                msg(sender, "&e/w range <blocks> &7- Set auto-advance range");
                msg(sender, "&e/w time <ms> &7- Set auto-advance delay");
                msg(sender, "&e/w info &7- Show current route info");
                msg(sender, "&e/w save &7- Force save to disk");
                msg(sender, "&e/w setup &7- Display all waypoints in loaded group at once.");
                break;
            }
            case "list":
                showGroupList(sender);
                break;

            case "load": {
                if (args.length < 2) { msg(sender, "&cUsage: /w load <name>"); return; }
                WaypointGroup g = storage.getGroup(args[1]);
                if (g == null) { msg(sender, "&cGroup '&e" + args[1] + "&c' not found."); return; }
                state.load(g);
                msg(sender, "&aLoaded group &e" + g.name + " &a(&e" + g.waypoints.size() + " waypoints&a).");
                break;
            }

            case "unload":
            case "clear":
                state.unload();
                msg(sender, "&aWaypoints unloaded.");
                break;

            case "setup":
                state.setupMode = !state.setupMode;
                msg(sender, "&aSetup mode: " + (state.setupMode ? "&2ON" : "&4OFF") + "&a.");
                break;

            case "reset":
                if (!state.hasGroup()) { msg(sender, "&cNo group loaded."); return; }
                state.reset();
                msg(sender, "&aReset to waypoint 1.");
                break;

            case "enable":
                state.enabled = true;
                msg(sender, "&aWaypoints &2enabled&a.");
                break;

            case "disable":
                state.enabled = false;
                msg(sender, "&aWaypoints &4disabled&a.");
                break;

            case "skip": {
                if (!state.hasGroup()) { msg(sender, "&cNo group loaded."); return; }
                int n = args.length >= 2 ? parseIntSafe(args[1], 1) : 1;
                state.skip(n);
                msg(sender, "&aSkipped &e" + n + "&a. Now at: &e" + (state.currentIndex + 1) + "&a/&e" + state.size());
                break;
            }

            case "unskip": {
                if (!state.hasGroup()) { msg(sender, "&cNo group loaded."); return; }
                int n = args.length >= 2 ? parseIntSafe(args[1], 1) : 1;
                state.skip(-n);
                msg(sender, "&aWent back &e" + n + "&a. Now at: &e" + (state.currentIndex + 1) + "&a/&e" + state.size());
                break;
            }

            case "skipto": {
                if (!state.hasGroup()) { msg(sender, "&cNo group loaded."); return; }
                if (args.length < 2) { msg(sender, "&cUsage: /w skipto <number>"); return; }
                int n = parseIntSafe(args[1], -1);
                if (n < 1 || n > state.size()) {
                    msg(sender, "&cIndex out of range (1–" + state.size() + ")."); return;
                }
                state.skipTo(n - 1);
                msg(sender, "&aJumped to waypoint &e" + n + "&a.");
                break;
            }

            //  group management

            case "create": {
                if (args.length < 2) { msg(sender, "&cUsage: /w create <name> [description]"); return; }
                String name = args[1].toLowerCase();
                if (storage.getGroup(name) != null) {
                    msg(sender, "&cGroup '&e" + name + "&c' already exists. Delete it first."); return;
                }
                String desc = args.length > 2 ? joinFrom(args, 2) : "";
                storage.putGroup(new WaypointGroup(name, desc));
                storage.saveIfDirty();
                msg(sender, "&aCreated group &e" + name + "&a. Use &e/w load " + name + "&a then &e/w add&a to populate.");
                break;
            }

            case "delete": {
                if (args.length < 2) { msg(sender, "&cUsage: /w delete <name>"); return; }
                String name = args[1].toLowerCase();
                if (state.loadedGroup != null && state.loadedGroup.name.equalsIgnoreCase(name))
                    state.unload();
                if (storage.removeGroup(name)) {
                    storage.saveIfDirty();
                    msg(sender, "&aDeleted group &e" + name + "&a.");
                } else {
                    msg(sender, "&cGroup '&e" + name + "&c' not found.");
                }
                break;
            }

            case "rename": {
                if (args.length < 3) { msg(sender, "&cUsage: /w rename <oldname> <newname>"); return; }
                String oldName = args[1].toLowerCase(), newName = args[2].toLowerCase();
                WaypointGroup g = storage.getGroup(oldName);
                if (g == null) { msg(sender, "&cGroup '&e" + oldName + "&c' not found."); return; }
                storage.removeGroup(oldName);
                g.name = newName;
                storage.putGroup(g);
                storage.saveIfDirty();
                if (state.loadedGroup != null && state.loadedGroup.name.equalsIgnoreCase(oldName))
                    state.loadedGroup.name = newName;
                msg(sender, "&aRenamed &e" + oldName + "&a → &e" + newName + "&a.");
                break;
            }

            //  waypoint editing (operates on the loaded group)

            case "add": {
                WaypointGroup target = state.loadedGroup;
                if (target == null) {
                    // Allow /w add <groupname> [wpname] without loading first
                    if (args.length >= 2) target = storage.getGroup(args[1]);
                    if (target == null) { msg(sender, "&cNo group loaded. Use /w load <name> first."); return; }
                    String wpName = args.length >= 3 ? args[2] : String.valueOf(target.waypoints.size() + 1);
                    addWaypoint(sender, target, wpName);
                    storage.markDirty(); storage.saveIfDirty();
                    return;
                }
                String wpName = args.length >= 2 ? joinFrom(args, 1) : String.valueOf(target.waypoints.size() + 1);
                addWaypoint(sender, target, wpName);
                storage.markDirty(); storage.saveIfDirty();
                break;
            }

            case "insert": {
                if (!state.hasGroup()) { msg(sender, "&cNo group loaded."); return; }
                if (args.length < 2) { msg(sender, "&cUsage: /w insert <index> [name]"); return; }
                int idx = parseIntSafe(args[1], -1);
                if (idx < 1 || idx > state.size() + 1) {
                    msg(sender, "&cIndex out of range (1–" + (state.size() + 1) + ")."); return;
                }
                String wpName = args.length >= 3 ? args[2] : String.valueOf(idx);
                double bx = Math.floor(mc.thePlayer.posX);
                double by = Math.floor(mc.thePlayer.posY) - 1;
                double bz = Math.floor(mc.thePlayer.posZ);
                state.loadedGroup.waypoints.add(idx - 1, new WaypointPoint(bx, by, bz, wpName));
                renumberNumericNames(state.loadedGroup, idx);   // shift numeric labels after insertion
                storage.markDirty(); storage.saveIfDirty();
                msg(sender, "&aInserted &e" + wpName + "&a at index &e" + idx
                        + "&a (" + (int)bx + ", " + (int)by + ", " + (int)bz + ").");
                break;
            }

            case "remove": {
                if (!state.hasGroup()) { msg(sender, "&cNo group loaded."); return; }
                if (args.length < 2) { msg(sender, "&cUsage: /w remove <index>"); return; }
                int idx = parseIntSafe(args[1], -1);
                if (idx < 1 || idx > state.size()) {
                    msg(sender, "&cIndex out of range (1–" + state.size() + ")."); return;
                }
                WaypointPoint removed = state.loadedGroup.waypoints.remove(idx - 1);
                storage.markDirty(); storage.saveIfDirty();
                msg(sender, "&aRemoved waypoint &e" + (removed.name != null ? removed.name : idx) + "&a.");
                break;
            }

            //  import / export

            case "export": {
                WaypointGroup g;
                if (args.length >= 2) {
                    g = storage.getGroup(args[1]);
                    if (g == null) { msg(sender, "&cGroup '&e" + args[1] + "&c' not found."); return; }
                } else {
                    if (!state.hasGroup()) {
                        msg(sender, "&cNo group loaded and no name given. Use /w export <name>."); return;
                    }
                    g = state.loadedGroup;
                }
                GuiScreen.setClipboardString(exportSoopy(g));
                msg(sender, "&aCopied &e" + g.waypoints.size() + "&a waypoints (&e" + g.name + "&a) to clipboard.");
                break;
            }

            case "import": {
                if (args.length < 2) { msg(sender, "&cUsage: /w import <groupname>"); return; }
                String name = args[1].toLowerCase();
                String clip = GuiScreen.getClipboardString();
                if (clip == null || clip.trim().isEmpty()) { msg(sender, "&cClipboard is empty."); return; }
                List<WaypointPoint> wps = parseSoopy(clip.trim());
                if (wps == null) { msg(sender, "&cCould not parse clipboard as soopy waypoints. Copy a soopy/coleweight route first."); return; }
                WaypointGroup g = storage.getGroup(name);
                if (g == null) g = new WaypointGroup(name);
                g.waypoints = wps;
                storage.putGroup(g);
                storage.saveIfDirty();
                msg(sender, "&aImported &e" + wps.size() + "&a waypoints into group &e" + name + "&a.");
                break;
            }

            //  settings

            case "range": {
                if (args.length < 2) {
                    msg(sender, "&aCurrent advance range: &e" + state.advanceRange + " blocks&a. Usage: /w range <blocks>");
                    return;
                }
                double r = parseDoubleSafe(args[1], -1);
                if (r <= 0) { msg(sender, "&cInvalid range."); return; }
                state.advanceRange = r;
                msg(sender, "&aAdvance range set to &e" + r + " blocks&a.");
                break;
            }

            case "time": {
                if (args.length < 2) {
                    msg(sender, "&aCurrent advance delay: &e" + state.advanceDelayMs + "ms&a. Usage: /w time <ms>");
                    return;
                }
                long t = parseLongSafe(args[1], -1);
                if (t <= 0) { msg(sender, "&cInvalid delay."); return; }
                state.advanceDelayMs = t;
                msg(sender, "&aAdvance delay set to &e" + t + "ms&a.");
                break;
            }

            case "save":
                storage.saveForce();
                msg(sender, "&aSaved all groups to disk.");
                break;

            case "info": {
                if (!state.hasGroup()) { msg(sender, "&cNo group loaded."); return; }
                WaypointGroup g = state.loadedGroup;
                msg(sender, "&aGroup: &e" + g.name
                        + " &a| At: &e" + (state.currentIndex + 1) + "/" + g.waypoints.size()
                        + " &a| Setup: &e" + state.setupMode
                        + " &a| Range: &e" + state.advanceRange + "m"
                        + " &a| Delay: &e" + state.advanceDelayMs + "ms");
                // Also show current, next waypoint names
                WaypointPoint cur = state.getCurrent();
                WaypointPoint nxt = state.getNext();
                if (cur != null) msg(sender, "&aCurrent: &e" + cur);
                if (nxt != null) msg(sender, "&aNext:    &e" + nxt);
                break;
            }

            default:
                msg(sender, "&cUnknown subcommand '&e" + args[0] + "&c'. Try /w list for help.");
        }
    }

    // group list UI

    private void showGroupList(ICommandSender sender) {
        Map<String, WaypointGroup> groups = WaypointStorage.getInstance().getGroups();

        sender.addChatMessage(new ChatComponentText(
                color(WaypointsMod.PREFIX + "&2===== Waypoint Groups =====")));

        if (groups.isEmpty()) {
            msg(sender, "&eNo groups saved. Use &a/w create <name>&e to make one.");
            return;
        }

        for (Map.Entry<String, WaypointGroup> e : groups.entrySet()) {
            WaypointGroup g = e.getValue();

            ChatComponentText root = new ChatComponentText("");

            // Name + count
            ChatComponentText nameText = new ChatComponentText(
                    EnumChatFormatting.YELLOW + g.name
                            + EnumChatFormatting.GRAY + " (" + g.waypoints.size() + " wps)");
            if (g.description != null && !g.description.isEmpty())
                nameText.appendText(EnumChatFormatting.DARK_GRAY + " – " + g.description);
            root.appendSibling(nameText);

            // [LOAD]
            ChatComponentText load = new ChatComponentText(
                    " " + EnumChatFormatting.GREEN + EnumChatFormatting.BOLD + "[LOAD]");
            load.getChatStyle()
                    .setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/waypoints load " + g.name))
                    .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            new ChatComponentText("Load " + g.name)));
            root.appendSibling(load);

            // [EXPORT]
            ChatComponentText export = new ChatComponentText(
                    " " + EnumChatFormatting.AQUA + EnumChatFormatting.BOLD + "[EXPORT]");
            export.getChatStyle()
                    .setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/waypoints export " + g.name))
                    .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            new ChatComponentText("Export " + g.name + " to clipboard")));
            root.appendSibling(export);

            // [DEL]
            ChatComponentText del = new ChatComponentText(
                    " " + EnumChatFormatting.RED + EnumChatFormatting.BOLD + "[DEL]");
            del.getChatStyle()
                    .setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/waypoints delete " + g.name))
                    .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            new ChatComponentText("Delete " + g.name)));
            root.appendSibling(del);

            sender.addChatMessage(root);
        }

        msg(sender, "&7/w create | /w load | /w add | /w export | /w import | /w setup | /w info");
    }

    // waypoint helpers

    private void addWaypoint(ICommandSender sender, WaypointGroup group, String name) {
        double bx = Math.floor(mc.thePlayer.posX);
        double by = Math.floor(mc.thePlayer.posY) - 1; // block under feet
        double bz = Math.floor(mc.thePlayer.posZ);
        group.waypoints.add(new WaypointPoint(bx, by, bz, name));
        msg(sender, "&aAdded &e" + name + "&a at (" + (int)bx + ", " + (int)by + ", " + (int)bz
                + ") to group &e" + group.name + "&a. Total: &e" + group.waypoints.size());
    }

    /**
     * After inserting at {@code fromOneBasedIndex}, bump every purely numeric label
     * at or after that position by 1 so labels stay sequential.
     */
    private void renumberNumericNames(WaypointGroup g, int fromOneBasedIndex) {
        for (int i = fromOneBasedIndex; i < g.waypoints.size(); i++) {
            WaypointPoint wp = g.waypoints.get(i);
            try {
                int n = Integer.parseInt(wp.name);
                if (n == i) wp.name = String.valueOf(i + 1); // only bump if it was already correct
            } catch (NumberFormatException ignored) {}
        }
    }

    //  soopy serialisation

    /**
     * Export a group in soopy format — compatible with coleweight's getWaypoints() / load().
     * Format: [{x, y, z, r, g, b, options: {name}}, ...]
     */
    private String exportSoopy(WaypointGroup g) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (WaypointPoint wp : g.waypoints) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("x", wp.x);
            m.put("y", wp.y);
            m.put("z", wp.z);
            m.put("r", 0);
            m.put("g", 1);
            m.put("b", 0);
            Map<String, Object> opts = new LinkedHashMap<>();
            opts.put("name", wp.name != null ? wp.name : "");
            m.put("options", opts);
            list.add(m);
        }
        return GSON.toJson(list);
    }

    /**
     * Parse soopy JSON from clipboard into an ordered list of WaypointPoints.
     * Handles both array format ([{x,y,z,options:{name}},...]) and plain
     * x y z rows as a fallback. Returns null on failure.
     */
    private List<WaypointPoint> parseSoopy(String json) {
        try {
            // Try soopy array format
            if (json.startsWith("[")) {
                Type type = new TypeToken<List<Map<String, Object>>>() {}.getType();
                List<Map<String, Object>> raw = GSON.fromJson(json, type);
                List<WaypointPoint> wps = new ArrayList<>();
                for (int i = 0; i < raw.size(); i++) {
                    Map<String, Object> m = raw.get(i);
                    double x = toDouble(m.get("x"));
                    double y = toDouble(m.get("y"));
                    double z = toDouble(m.get("z"));
                    String name = String.valueOf(i + 1);
                    if (m.containsKey("options")) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> opts = (Map<String, Object>) m.get("options");
                        if (opts != null && opts.containsKey("name"))
                            name = String.valueOf(opts.get("name"));
                    }
                    wps.add(new WaypointPoint(x, y, z, name));
                }
                // Sort by numeric name if applicable (matches coleweight's bubble-sort)
                wps.sort((a, b) -> {
                    try { return Integer.compare(Integer.parseInt(a.name), Integer.parseInt(b.name)); }
                    catch (NumberFormatException e) { return 0; }
                });
                return wps;
            }

            // Fallback: plain "x y z" rows
            if (json.matches("(?s).*\\d.*")) {
                List<WaypointPoint> wps = new ArrayList<>();
                String[] rows = json.split("[\r\n]+");
                for (int i = 0; i < rows.length; i++) {
                    String[] parts = rows[i].trim().split("\\s+");
                    if (parts.length >= 3) {
                        wps.add(new WaypointPoint(
                                Double.parseDouble(parts[0]),
                                Double.parseDouble(parts[1]),
                                Double.parseDouble(parts[2]),
                                String.valueOf(i + 1)));
                    }
                }
                return wps.isEmpty() ? null : wps;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private double toDouble(Object o) {
        if (o instanceof Number) return ((Number) o).doubleValue();
        return Double.parseDouble(String.valueOf(o));
    }

    //  tab completion

    @Override
    @SuppressWarnings("unchecked")
    public List addTabCompletionOptions(ICommandSender sender, String[] args, BlockPos pos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, SUBCOMMANDS);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("load") || sub.equals("delete") || sub.equals("export")
                    || sub.equals("rename") || sub.equals("import")) {
                return getListOfStringsMatchingLastWord(args,
                        new ArrayList<>(WaypointStorage.getInstance().getGroups().keySet()));
            }
        }
        return Collections.emptyList();
    }

    //  utilities

    private void msg(ICommandSender sender, String text) {
        sender.addChatMessage(new ChatComponentText(color(WaypointsMod.PREFIX + text)));
    }

    private String color(String s) { return s.replace("&", "\u00a7"); }

    private int    parseIntSafe   (String s, int    def) { try { return Integer.parseInt(s);   } catch (Exception e) { return def; } }
    private double parseDoubleSafe(String s, double def) { try { return Double.parseDouble(s); } catch (Exception e) { return def; } }
    private long   parseLongSafe  (String s, long   def) { try { return Long.parseLong(s);     } catch (Exception e) { return def; } }

    private String joinFrom(String[] args, int from) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < args.length; i++) {
            if (i > from) sb.append(' ');
            sb.append(args[i]);
        }
        return sb.toString();
    }
}