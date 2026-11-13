package app.wowforever.addons;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Unpacks addon zips into Interface/AddOns atomically, folder by folder. */
final class Installer {

    /** Messages shown to the user. The app swaps in its string resources; the JVM test keeps these English ones. */
    interface Texts {
        default String noAddons() { return "No addons found in the archive (no .toc files)"; }
        default String cannotCreate(Object path) { return "Could not create " + path; }
        default String noWrite(Object path) { return "No write access: " + path; }
        default String unpacking(String names) { return "Unpacking: " + names; }
        default String unpackingFiles(int index, int total) { return "Unpacking files: " + index + " of " + total; }
        default String unsafePath(String name) { return "Unsafe path in the archive: " + name; }
        default String installing() { return "Installing into AddOns"; }
        default String cannotWrite(String name) { return "Could not write " + name; }
        default String cannotDelete(Object path) { return "Could not delete " + path; }
    }

    static volatile Texts texts = new Texts() {};

    /** prefix inside the zip ("" or "Wrapper/Addon/") -> AddOns folder name */
    static Map<String, String> findRoots(List<String> names) throws IOException {
        Map<String, String> candidates = new LinkedHashMap<>();
        Map<String, String> loose = new LinkedHashMap<>(); // parent prefix -> base, for wrapped repos
        for (String raw : names) {
            String name = raw.replace('\\', '/');
            if (!name.toLowerCase(Locale.ROOT).endsWith(".toc")) continue;
            int slash = name.lastIndexOf('/');
            String parent = slash < 0 ? "" : name.substring(0, slash);
            String file = slash < 0 ? name : name.substring(slash + 1);
            String base = Toc.baseName(file);
            if (base.isEmpty()) continue;
            if (parent.isEmpty()) {
                loose.put("", base);
                continue;
            }
            String folder = parent.substring(parent.lastIndexOf('/') + 1);
            String plain = file.substring(0, file.length() - 4);
            if (base.equalsIgnoreCase(folder) || plain.equalsIgnoreCase(folder)) {
                candidates.put(parent + "/", folder);
            } else if (!loose.containsKey(parent + "/")) {
                loose.put(parent + "/", base);
            }
        }
        if (candidates.isEmpty()) {
            // Repository archives wrap the addon in "owner-repo-sha/": use the single TOC name.
            for (Map.Entry<String, String> e : loose.entrySet()) {
                String prefix = e.getKey();
                if (prefix.isEmpty() || prefix.indexOf('/') == prefix.length() - 1) {
                    candidates.put(prefix, e.getValue());
                    break;
                }
            }
        }
        // Drop roots nested inside another root (an addon that ships sub-addons inside itself).
        List<String> prefixes = new ArrayList<>(candidates.keySet());
        Collections.sort(prefixes, (a, b) -> a.length() - b.length());
        Map<String, String> roots = new LinkedHashMap<>();
        List<String> taken = new ArrayList<>();
        for (String p : prefixes) {
            boolean nested = false;
            for (String r : roots.keySet()) if (!r.isEmpty() && p.startsWith(r)) { nested = true; break; }
            if (nested) continue;
            String folder = candidates.get(p);
            if (taken.contains(folder.toLowerCase(Locale.ROOT))) continue;
            taken.add(folder.toLowerCase(Locale.ROOT));
            roots.put(p, folder);
        }
        if (roots.isEmpty()) throw new IOException(texts.noAddons());
        if (roots.containsKey("") && roots.size() > 1) roots.remove("");
        return roots;
    }

    interface Log { void step(String text); }

    /** Installs every addon folder of the zip into addonsDir; returns the folder names. */
    static List<String> install(File zip, File addonsDir, Log log) throws IOException {
        if (!addonsDir.exists() && !addonsDir.mkdirs()) throw new IOException(texts.cannotCreate(addonsDir));
        File interfaceDir = addonsDir.getParentFile();
        File staging = new File(interfaceDir, ".wow-addons-staging-" + System.currentTimeMillis());
        if (!staging.mkdirs()) throw new IOException(texts.noWrite(interfaceDir));
        ZipFile zf = new ZipFile(zip);
        try {
            List<String> names = new ArrayList<>();
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) names.add(en.nextElement().getName());
            Map<String, String> roots = findRoots(names);
            if (log != null) log.step(texts.unpacking(String.join(", ", roots.values())));
            String stagingCanon = staging.getCanonicalPath() + File.separator;
            byte[] buf = new byte[64 * 1024];
            en = zf.entries();
            int index = 0;
            int total = names.size();
            while (en.hasMoreElements()) {
                ZipEntry ze = en.nextElement();
                if (log != null && total > 200 && ++index % 150 == 0) log.step(texts.unpackingFiles(index, total));
                String name = ze.getName().replace('\\', '/');
                String root = null;
                for (String p : roots.keySet()) {
                    if (p.isEmpty() || name.startsWith(p)) { if (root == null || p.length() > root.length()) root = p; }
                }
                if (root == null) continue;
                String rel = name.substring(root.length());
                if (rel.isEmpty()) continue;
                File out = new File(new File(staging, roots.get(root)), rel);
                if (!out.getCanonicalPath().startsWith(stagingCanon)) throw new IOException(texts.unsafePath(name));
                if (ze.isDirectory() || name.endsWith("/")) {
                    out.mkdirs();
                    continue;
                }
                File parent = out.getParentFile();
                if (!parent.exists() && !parent.mkdirs()) throw new IOException(texts.cannotCreate(parent));
                InputStream in = zf.getInputStream(ze);
                OutputStream os = new FileOutputStream(out);
                try {
                    int n;
                    while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
                } finally {
                    os.close();
                    in.close();
                }
            }
            List<String> folders = new ArrayList<>(roots.values());
            if (log != null) log.step(texts.installing());
            for (String folder : folders) swapIn(new File(staging, folder), new File(addonsDir, folder), staging);
            return folders;
        } finally {
            zf.close();
            deleteRecursive(staging);
        }
    }

    private static void swapIn(File fresh, File target, File staging) throws IOException {
        File backup = null;
        if (target.exists()) {
            backup = new File(staging, ".old-" + target.getName());
            if (!target.renameTo(backup)) {
                deleteRecursive(target);
                backup = null;
            }
        }
        if (!fresh.renameTo(target)) {
            copyRecursive(fresh, target);
        }
        if (!target.exists()) {
            if (backup != null) backup.renameTo(target);
            throw new IOException(texts.cannotWrite(target.getName()));
        }
        if (backup != null) deleteRecursive(backup);
    }

    static void remove(File addonsDir, List<String> folders) throws IOException {
        for (String f : folders) {
            if (f == null || f.isEmpty() || f.contains("/") || f.contains("..")) continue;
            File d = new File(addonsDir, f);
            if (d.exists()) deleteRecursive(d);
            if (d.exists()) throw new IOException(texts.cannotDelete(f));
        }
    }

    static void deleteRecursive(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteRecursive(k);
        }
        f.delete();
    }

    static void copyRecursive(File src, File dst) throws IOException {
        if (src.isDirectory()) {
            if (!dst.exists() && !dst.mkdirs()) throw new IOException(texts.cannotCreate(dst));
            File[] kids = src.listFiles();
            if (kids != null) for (File k : kids) copyRecursive(k, new File(dst, k.getName()));
        } else {
            InputStream in = new FileInputStream(src);
            OutputStream out = new FileOutputStream(dst);
            try {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            } finally {
                out.close();
                in.close();
            }
        }
    }

    static String md5(File f) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            InputStream in = new FileInputStream(f);
            try {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            } finally {
                in.close();
            }
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    private Installer() {}
}
