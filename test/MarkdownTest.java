package app.wowforever.addons;
/** Run: javac -d /tmp/m src/app/wowforever/addons/Markdown.java test/MarkdownTest.java && java -cp /tmp/m app.wowforever.addons.MarkdownTest */
public class MarkdownTest {
  static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); System.out.println("ok  " + what); }
  public static void main(String[] a) {
    String md = "## Questie 12.4.0\r\n\r\n### Bug fixes\n\n- Fixed [quest #123](https://github.com/Questie/Questie/issues/123) by **someone**\n"
        + "  * nested _item_ with `code`\n1. first\n\n---\n![shot](https://x/y.png)\n<details>hidden tag</details>\n"
        + "Title\n=====\n> quoted\n```\nraw *stays*\n```\n[ref]: https://example.org\n";
    Markdown.Doc d = Markdown.render(md);
    String t = d.text.toString();
    System.out.println(t + "\n----");
    check(!t.contains("#") || t.contains("#123"), "heading markers removed");
    check(t.startsWith("Questie 12.4.0\n\nBug fixes\n"), "headings become lines");
    check(!t.contains("shot"), "images dropped");
    check(t.contains("• Fixed quest #123 by someone"), "bullet, link text and bold markers");
    check(t.contains("   • nested item with code"), "nested bullet, italics and code");
    check(t.contains("1. first"), "numbered list");
    check(!t.contains("](") && !t.contains("https://"), "no link targets or link definitions");
    check(!t.contains("<details>") && t.contains("hidden tag"), "HTML tags dropped, text kept");
    check(t.contains("Title") && !t.contains("==="), "setext heading");
    check(t.contains("quoted") && !t.contains(">"), "quote marker removed");
    check(t.contains("raw *stays*"), "fenced code kept verbatim");
    check(!t.contains("\n\n\n"), "no runs of blank lines");
    check(d.headings.size() == 3 && t.substring(d.headings.get(0)[0], d.headings.get(0)[1]).equals("Questie 12.4.0"), "heading ranges");
    check(d.bold.size() == 1 && t.substring(d.bold.get(0)[0], d.bold.get(0)[1]).equals("someone"), "bold range");
    check(Markdown.render("plain text, nothing else").text.toString().equals("plain text, nothing else"), "plain text unchanged");
    check(Markdown.render(null).text.length() == 0 && Markdown.render("").text.length() == 0, "empty input");
  }
}
