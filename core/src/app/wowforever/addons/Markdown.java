package app.wowforever.addons;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns release-notes Markdown into readable text: headings, bullet lists, links as their text,
 * no emphasis markers, images or HTML. Plain Java, so test/ can run it; the UI adds the styles.
 */
final class Markdown {

    /** Rendered text and the character ranges to show as headings or bold. */
    static final class Doc {
        final StringBuilder text = new StringBuilder();
        final List<int[]> headings = new ArrayList<>();   // {start, end}
        final List<int[]> bold = new ArrayList<>();
    }

    private static final Pattern IMAGE = Pattern.compile("!\\[([^\\]]*)\\]\\([^)]*\\)");
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]+)\\]\\([^)]*\\)");
    private static final Pattern REF_LINK = Pattern.compile("\\[([^\\]]+)\\]\\[[^\\]]*\\]");
    private static final Pattern AUTOLINK = Pattern.compile("<(https?://[^>\\s]+)>");
    private static final Pattern HTML = Pattern.compile("</?[A-Za-z][^>]*>");
    private static final Pattern BOLD = Pattern.compile("(\\*\\*|__)(?=\\S)(.+?)(?<=\\S)\\1");
    private static final Pattern ITALIC = Pattern.compile("(?<![\\w*])([*_])(?=\\S)(.+?)(?<=\\S)\\1(?![\\w*])");
    private static final Pattern CODE = Pattern.compile("`([^`]+)`");
    private static final Pattern STRIKE = Pattern.compile("~~(.+?)~~");
    private static final Pattern HEADING = Pattern.compile("^\\s{0,3}(#{1,6})\\s+(.*?)\\s*#*\\s*$");
    private static final Pattern BULLET = Pattern.compile("^(\\s*)[-*+]\\s+(.*)$");
    private static final Pattern NUMBERED = Pattern.compile("^(\\s*)(\\d+)[.)]\\s+(.*)$");
    private static final Pattern RULE = Pattern.compile("^\\s{0,3}([-*_])(\\s*\\1){2,}\\s*$");
    private static final Pattern LINK_DEF = Pattern.compile("^\\s{0,3}\\[[^\\]]+\\]:\\s*\\S+.*$");
    private static final Pattern SETEXT = Pattern.compile("^\\s{0,3}(=+|-+)\\s*$");

    static Doc render(String md) {
        Doc d = new Doc();
        if (md == null) return d;
        String[] lines = md.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        boolean blank = true;     // the text so far ends a paragraph
        boolean fence = false;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.trim().startsWith("```") || line.trim().startsWith("~~~")) { fence = !fence; continue; }
            if (fence) { appendLine(d, line); blank = false; continue; }
            if (line.trim().isEmpty() || RULE.matcher(line).matches() || LINK_DEF.matcher(line).matches()) {
                if (!blank) { d.text.append('\n'); blank = true; }
                continue;
            }
            // "Title\n=====" headings
            boolean setext = i + 1 < lines.length && SETEXT.matcher(lines[i + 1]).matches() && !BULLET.matcher(line).matches();
            Matcher h = HEADING.matcher(line);
            if (h.matches() || setext) {
                if (!blank && d.text.length() > 0) d.text.append('\n');
                int start = d.text.length();
                inline(d, h.matches() ? h.group(2) : line.trim());
                if (d.text.length() > start) d.headings.add(new int[]{start, d.text.length()});
                d.text.append('\n');
                blank = false;
                if (setext) i++;
                continue;
            }
            Matcher b = BULLET.matcher(line), n = NUMBERED.matcher(line);
            if (b.matches()) {
                indent(d, b.group(1));
                d.text.append("• ");
                inline(d, b.group(2));
            } else if (n.matches()) {
                indent(d, n.group(1));
                d.text.append(n.group(2)).append(". ");
                inline(d, n.group(3));
            } else {
                String t = line.trim();
                if (t.startsWith(">")) t = t.replaceFirst("^(>\\s?)+", "");
                int before = d.text.length();
                inline(d, t);
                if (d.text.toString().substring(before).trim().isEmpty()) {   // an image or a tag alone
                    d.text.setLength(before);
                    continue;
                }
            }
            d.text.append('\n');
            blank = false;
        }
        while (d.text.length() > 0 && Character.isWhitespace(d.text.charAt(d.text.length() - 1))) d.text.setLength(d.text.length() - 1);
        for (List<int[]> l : java.util.Arrays.asList(d.headings, d.bold)) {
            for (int[] r : l) r[1] = Math.min(r[1], d.text.length());
        }
        return d;
    }

    private static void indent(Doc d, String ws) {
        int depth = ws.replace("\t", "    ").length() / 2;
        for (int k = 0; k < Math.min(depth, 3); k++) d.text.append("   ");
    }

    private static void appendLine(Doc d, String line) {
        d.text.append(line).append('\n');
    }

    /** Inline markup: links and images to text, emphasis markers out, bold ranges recorded. */
    private static void inline(Doc d, String s) {
        s = IMAGE.matcher(s).replaceAll("");
        s = LINK.matcher(s).replaceAll("$1");
        s = REF_LINK.matcher(s).replaceAll("$1");
        s = AUTOLINK.matcher(s).replaceAll("$1");
        s = HTML.matcher(s).replaceAll("");
        s = CODE.matcher(s).replaceAll("$1");
        s = STRIKE.matcher(s).replaceAll("$1");
        Matcher m = BOLD.matcher(s);
        int last = 0;
        StringBuilder out = new StringBuilder();
        List<int[]> spans = new ArrayList<>();
        while (m.find()) {
            out.append(s, last, m.start());
            int st = out.length();
            out.append(m.group(2));
            spans.add(new int[]{st, out.length()});
            last = m.end();
        }
        out.append(s.substring(last));
        // Italic markers are dropped without a style; shifting the bold ranges is not worth it, so
        // only strip italics when there is no bold in the line.
        String text = spans.isEmpty() ? ITALIC.matcher(out).replaceAll("$2") : out.toString();
        text = text.replace("\\*", "*").replace("\\_", "_").replace("\\#", "#").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">");
        int base = d.text.length();
        d.text.append(text);
        if (text.length() == out.length()) for (int[] r : spans) d.bold.add(new int[]{base + r[0], base + r[1]});
    }

    private Markdown() {}
}
