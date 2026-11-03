package app.wowforever.addons;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * One addon search over every source: the GitHub index, catalog entries the index does not list yet,
 * then WoWInterface. The order here is the source preference: GitHub first, WoWInterface only for
 * addons GitHub does not have (matched by folder or name). An empty query lists every addon by
 * {@link Popularity}.
 */
final class Search {

    /** Results of one page; warning is a source that failed while the others still answered. */
    static final class Page {
        final List<Model.Item> items = new ArrayList<>();
        boolean more;
        String warning;
    }

    static Page page(String query, int from, int count) throws IOException {
        return page(query, null, from, count);
    }

    /** A page of one category (a Categories key), or of everything when category is null. */
    static Page page(String query, String category, int from, int count) throws IOException {
        String q = query == null ? "" : query.trim();
        Page p = new Page();
        List<Model.Item> all = new ArrayList<>();
        IOException indexError = null;
        List<Model.Item> github;
        try {
            github = Index.search(q, 0, Integer.MAX_VALUE);
        } catch (IOException e) {
            indexError = e;
            github = new ArrayList<>();
        }
        Set<String> keys = new HashSet<>();
        Set<String> folders = new HashSet<>();
        Set<String> names = new HashSet<>();
        // Hand-picked entries the index does not list yet go first: they are known to work on Forever.
        for (Model.Item c : catalog(q)) if (Index.item(c.id) == null) add(all, c, keys, folders, names);
        for (Model.Item it : github) add(all, it, keys, folders, names);
        if (!q.isEmpty()) {
            try {
                for (Model.Item w : Sources.Wowi.search(q, 0, Integer.MAX_VALUE)) {
                    if (sameAddon(w, folders, names)) continue;
                    add(all, w, keys, folders, names);
                }
            } catch (IOException e) {
                p.warning = MainActivity.friendly(e);
            }
        }
        // Without a query the list is the most popular addons; a query keeps its relevance order.
        if (q.isEmpty()) Collections.sort(all, Popularity.ORDER);
        if (category != null) {
            List<Model.Item> in = new ArrayList<>();
            for (Model.Item it : all) if (Categories.matches(it, category)) in.add(it);
            all = in;
        }
        if (indexError != null) {
            if (all.isEmpty()) throw indexError;
            if (p.warning == null) p.warning = MainActivity.friendly(indexError);
        }
        if (from < all.size()) p.items.addAll(all.subList(from, Math.min(all.size(), from + count)));
        p.more = from + count < all.size();
        return p;
    }

    /** How many addons each category holds, without a query: the index and catalog entries it lacks. */
    static Map<String, Integer> counts() throws IOException {
        Map<String, Integer> n = new HashMap<>();
        for (String k : Categories.KEYS) n.put(k, 0);
        List<Model.Item> all = new ArrayList<>(Index.items(false));
        for (Model.Item c : Catalog.all()) if (Index.item(c.id) == null) all.add(c);
        for (Model.Item it : all) {
            String k = Categories.of(it);
            n.put(k, n.get(k) + 1);
        }
        return n;
    }

    private static void add(List<Model.Item> all, Model.Item it, Set<String> keys, Set<String> folders, Set<String> names) {
        if (!keys.add(it.key())) return;
        all.add(it);
        for (String f : it.folders) folders.add(f.toLowerCase(Locale.ROOT));
        names.add(norm(it.name));
    }

    private static boolean sameAddon(Model.Item w, Set<String> folders, Set<String> names) {
        for (String f : w.folders) if (folders.contains(f.toLowerCase(Locale.ROOT))) return true;
        return names.contains(norm(w.name));
    }

    private static String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private static List<Model.Item> catalog(String q) {
        String n = q.toLowerCase(Locale.ROOT);
        List<Model.Item> out = new ArrayList<>();
        for (Model.Item it : Catalog.all()) {
            if (!n.isEmpty() && !(it.name.toLowerCase(Locale.ROOT).contains(n) || it.summary.toLowerCase(Locale.ROOT).contains(n)
                    || it.author.toLowerCase(Locale.ROOT).contains(n) || it.id.toLowerCase(Locale.ROOT).contains(n))) continue;
            out.add(it);
        }
        return out;
    }

    private Search() {}
}
