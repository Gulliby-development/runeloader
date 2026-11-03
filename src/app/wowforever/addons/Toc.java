package app.wowforever.addons;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Reads addon TOC files the way the WoW Forever client picks them. */
final class Toc {

    /** Forever is the Mainline family: _Forever, then its codename _Camelot, then _Mainline, then the base TOC. */
    static final String[] FOREVER_CHAIN = {"_Forever", "_Camelot", "_Mainline", ""};

    /** Every client suffix, for stripping a TOC name back to its folder name. */
    static final List<String> ALL_SUFFIXES = Arrays.asList(
            "_forever", "_camelot", "_mainline", "_standard", "_classic", "_vanilla", "_tbc", "_bcc",
            "_wrath", "_wotlkc", "_cata", "_mists", "_titan",
            "-forever", "-camelot", "-mainline", "-classic", "-bcc", "-wotlkc", "-cata", "-mists", "-vanilla", "-tbc", "-wrath");

    static final String FOREVER_INTERFACE_PREFIX = "160";

    /** "Addon_Forever.toc" -> "Addon" ; "Addon.toc" -> "Addon". */
    static String baseName(String tocFileName) {
        String n = tocFileName;
        if (n.toLowerCase(Locale.ROOT).endsWith(".toc")) n = n.substring(0, n.length() - 4);
        String lower = n.toLowerCase(Locale.ROOT);
        for (String s : ALL_SUFFIXES) {
            if (lower.endsWith(s) && n.length() > s.length()) return n.substring(0, n.length() - s.length());
        }
        return n;
    }

    /** The TOC file the Forever client would load from an addon folder, or null. */
    static File pick(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return null;
        Map<String, File> byLower = new HashMap<>();
        for (File f : files) if (f.isFile()) byLower.put(f.getName().toLowerCase(Locale.ROOT), f);
        String folder = dir.getName().toLowerCase(Locale.ROOT);
        for (String suffix : FOREVER_CHAIN) {
            File f = byLower.get(folder + suffix.toLowerCase(Locale.ROOT) + ".toc");
            if (f != null) return f;
        }
        // Old style "Addon-Classic.toc" or a TOC that only matches after stripping
        for (File f : files) {
            if (f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".toc")
                    && baseName(f.getName()).equalsIgnoreCase(dir.getName())) return f;
        }
        return null;
    }

    static boolean hasForeverToc(File dir) {
        String folder = dir.getName().toLowerCase(Locale.ROOT);
        return new File(dir, dir.getName() + "_Forever.toc").exists()
                || new File(dir, dir.getName() + "_Camelot.toc").exists()
                || existsIgnoreCase(dir, folder + "_forever.toc")
                || existsIgnoreCase(dir, folder + "_camelot.toc");
    }

    private static boolean existsIgnoreCase(File dir, String lowerName) {
        String[] names = dir.list();
        if (names == null) return false;
        for (String n : names) if (n.toLowerCase(Locale.ROOT).equals(lowerName)) return true;
        return false;
    }

    static Map<String, String> parse(File toc) throws IOException {
        Map<String, String> m = new HashMap<>();
        BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(toc), StandardCharsets.UTF_8));
        try {
            String line;
            int count = 0;
            while ((line = r.readLine()) != null && count++ < 400) {
                if (line.startsWith("﻿")) line = line.substring(1);
                if (!line.startsWith("##")) continue;
                int colon = line.indexOf(':');
                if (colon < 0) continue;
                String key = line.substring(2, colon).trim();
                String value = line.substring(colon + 1).trim();
                if (!key.isEmpty() && !m.containsKey(key)) m.put(key, value);
            }
        } finally {
            r.close();
        }
        return m;
    }

    /** Removes WoW color codes and textures from TOC titles. */
    static String clean(String s) {
        if (s == null) return "";
        s = s.replaceAll("\\|c[0-9a-fA-F]{8}", "").replaceAll("\\|r", "").replaceAll("\\|T[^|]*\\|t", "")
             .replaceAll("\\|A[^|]*\\|a", "").replaceAll("\\|n", " ");
        return s.trim();
    }

    static Model.LocalAddon read(File dir) {
        Model.LocalAddon a = new Model.LocalAddon();
        a.folder = dir.getName();
        a.title = dir.getName();
        File toc = pick(dir);
        if (toc == null) return null;
        a.tocFile = toc.getName();
        try {
            Map<String, String> m = parse(toc);
            String title = clean(m.get("Title"));
            String locTitle = clean(firstNonEmpty(m.get("Title-ruRU")));
            if (!locTitle.isEmpty()) title = locTitle;
            if (!title.isEmpty()) a.title = title;
            a.version = clean(firstNonEmpty(m.get("Version")));
            if (a.version.startsWith("@")) a.version = "";
            a.author = clean(firstNonEmpty(m.get("Author")));
            String notes = clean(firstNonEmpty(m.get("Notes-ruRU")));
            if (notes.isEmpty()) notes = clean(firstNonEmpty(m.get("Notes")));
            a.notes = notes;
            a.interfaceVersion = firstNonEmpty(m.get("Interface"));
            a.loadOnDemand = "1".equals(firstNonEmpty(m.get("LoadOnDemand")));
            a.wowiId = emptyToNull(m.get("X-WoWI-ID"));
            a.wagoId = emptyToNull(m.get("X-Wago-ID"));
            a.website = emptyToNull(m.get("X-Website"));
            String deps = firstNonEmpty(m.get("RequiredDeps"));
            if (deps.isEmpty()) deps = firstNonEmpty(m.get("Dependencies"));
            if (deps.isEmpty()) deps = firstNonEmpty(m.get("Dep"));
            for (String d : deps.split(",")) {
                d = d.trim();
                if (!d.isEmpty()) a.deps.add(d);
            }
        } catch (IOException ignored) {
        }
        String tocLower = a.tocFile.toLowerCase(Locale.ROOT);
        a.foreverToc = tocLower.contains("_forever") || tocLower.contains("_camelot") || interfaceMatchesForever(a.interfaceVersion);
        return a;
    }

    static boolean interfaceMatchesForever(String iface) {
        if (iface == null) return false;
        for (String p : iface.split(",")) {
            if (p.trim().startsWith(FOREVER_INTERFACE_PREFIX)) return true;
        }
        return false;
    }

    /** All addon folders in AddOns (skips Blizzard_ folders and dot folders). */
    static List<Model.LocalAddon> scan(File addonsDir) {
        List<Model.LocalAddon> out = new ArrayList<>();
        File[] dirs = addonsDir.listFiles();
        if (dirs == null) return out;
        for (File d : dirs) {
            if (!d.isDirectory()) continue;
            String n = d.getName();
            if (n.startsWith(".") || n.startsWith("Blizzard_")) continue;
            Model.LocalAddon a = read(d);
            if (a == null) {
                a = new Model.LocalAddon();
                a.folder = n;
                a.title = n;
                a.tocFile = "";
            }
            out.add(a);
        }
        Collections.sort(out, (x, y) -> x.title.compareToIgnoreCase(y.title));
        return out;
    }

    private static String firstNonEmpty(String s) { return s == null ? "" : s.trim(); }

    private static String emptyToNull(String s) {
        if (s == null) return null;
        s = s.trim();
        return s.isEmpty() || s.startsWith("@") ? null : s;
    }

    private Toc() {}
}
