package fortconquer.build;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal, strict applier for unified diffs as produced by {@code diff -ruN a/ b/}.
 *
 * <p>Deliberately has no fuzz factor and no offset search: every hunk must match the
 * pinned apktool decode of the reference APK exactly, otherwise the build fails. This
 * guarantees that the shipped APK is exactly "reference APK + reviewed patches".
 */
public final class UnifiedDiff {
    private static final Pattern HUNK = Pattern.compile("^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@.*$");

    private UnifiedDiff() {}

    /** One file section of a patch. */
    static final class FilePatch {
        String oldPath;
        String newPath;
        final List<Hunk> hunks = new ArrayList<>();
    }

    static final class Hunk {
        int oldStart, oldCount, newStart, newCount;
        final List<String> lines = new ArrayList<>(); // each prefixed with ' ', '-', '+'
        boolean oldNoNewlineAtEnd, newNoNewlineAtEnd;
    }

    static List<FilePatch> parse(String patchText, String patchName) {
        String[] lines = patchText.split("\n", -1);
        List<FilePatch> result = new ArrayList<>();
        FilePatch cur = null;
        Hunk hunk = null;
        int remOld = 0, remNew = 0;
        char last = ' ';
        for (int i = 0; i < lines.length; i++) {
            String l = lines[i];
            if (hunk != null && (remOld > 0 || remNew > 0)) {
                if (l.startsWith("\\")) { // "\ No newline at end of file"
                    markNoNewline(hunk, last);
                    continue;
                }
                char c = l.isEmpty() ? ' ' : l.charAt(0);
                String body = l.isEmpty() ? "" : l.substring(1);
                if (c == ' ') { remOld--; remNew--; }
                else if (c == '-') remOld--;
                else if (c == '+') remNew--;
                else throw new IllegalStateException(patchName + ":" + (i + 1) + ": unexpected hunk line: " + l);
                hunk.lines.add(c + body);
                last = c;
                if (remOld < 0 || remNew < 0)
                    throw new IllegalStateException(patchName + ":" + (i + 1) + ": hunk longer than its header");
                continue;
            }
            if (l.startsWith("\\") && hunk != null) { markNoNewline(hunk, last); continue; }
            if (l.startsWith("--- ")) {
                cur = new FilePatch();
                cur.oldPath = stripPath(l.substring(4));
                result.add(cur);
                hunk = null;
            } else if (l.startsWith("+++ ")) {
                if (cur == null) throw new IllegalStateException(patchName + ": '+++' without '---'");
                cur.newPath = stripPath(l.substring(4));
            } else if (l.startsWith("@@")) {
                Matcher m = HUNK.matcher(l);
                if (!m.matches() || cur == null) throw new IllegalStateException(patchName + ":" + (i + 1) + ": bad hunk header");
                hunk = new Hunk();
                hunk.oldStart = Integer.parseInt(m.group(1));
                hunk.oldCount = m.group(2) == null ? 1 : Integer.parseInt(m.group(2));
                hunk.newStart = Integer.parseInt(m.group(3));
                hunk.newCount = m.group(4) == null ? 1 : Integer.parseInt(m.group(4));
                remOld = hunk.oldCount;
                remNew = hunk.newCount;
                cur.hunks.add(hunk);
            }
            // anything else ("diff -ruN ..." headers, comments) is ignored
        }
        return result;
    }

    private static void markNoNewline(Hunk h, char last) {
        if (last == '-') h.oldNoNewlineAtEnd = true;
        else if (last == '+') h.newNoNewlineAtEnd = true;
        else { h.oldNoNewlineAtEnd = true; h.newNoNewlineAtEnd = true; }
    }

    /** "a/smali/Foo.smali\t2024-..." -> "smali/Foo.smali"; "/dev/null" -> null. */
    private static String stripPath(String raw) {
        int tab = raw.indexOf('\t');
        String p = (tab >= 0 ? raw.substring(0, tab) : raw).trim();
        if (p.equals("/dev/null")) return null;
        int slash = p.indexOf('/');
        return slash >= 0 ? p.substring(slash + 1) : p;
    }

    /** Applies all file sections of {@code patchFile} to the tree rooted at {@code root}. */
    public static List<String> apply(Path patchFile, Path root) throws IOException {
        String text = new String(Files.readAllBytes(patchFile), StandardCharsets.UTF_8);
        List<String> touched = new ArrayList<>();
        for (FilePatch fp : parse(text, patchFile.getFileName().toString())) {
            String rel = fp.newPath != null ? fp.newPath : fp.oldPath;
            if (rel == null || rel.contains("..")) throw new IllegalStateException("Unsafe path in " + patchFile + ": " + rel);
            Path target = root.resolve(rel).normalize();
            if (!target.startsWith(root)) throw new IllegalStateException("Path escapes tree: " + rel);
            boolean creating = fp.hunks.size() == 1 && fp.hunks.get(0).oldCount == 0 && !Files.exists(target);
            boolean deleting = fp.hunks.size() == 1 && fp.hunks.get(0).newCount == 0 && fp.hunks.get(0).oldStart <= 1;
            List<String> lines = new ArrayList<>();
            boolean endsWithNewline = true;
            if (!creating) {
                if (!Files.exists(target)) throw new IllegalStateException(patchFile.getFileName() + ": missing file " + rel);
                String content = new String(Files.readAllBytes(target), StandardCharsets.UTF_8);
                endsWithNewline = content.endsWith("\n");
                String[] split = content.split("\n", -1);
                lines.addAll(Arrays.asList(split).subList(0, endsWithNewline ? split.length - 1 : split.length));
            }
            int offset = 0;
            for (Hunk h : fp.hunks) {
                int pos = (h.oldCount == 0 ? h.oldStart : h.oldStart - 1) + offset;
                List<String> expected = new ArrayList<>();
                List<String> replacement = new ArrayList<>();
                for (String hl : h.lines) {
                    char c = hl.charAt(0);
                    String body = hl.substring(1);
                    if (c != '+') expected.add(body);
                    if (c != '-') replacement.add(body);
                }
                if (pos < 0 || pos + expected.size() > lines.size()
                        || !lines.subList(pos, pos + expected.size()).equals(expected)) {
                    throw new IllegalStateException(patchFile.getFileName() + ": hunk @@ -" + h.oldStart + "," + h.oldCount
                            + " does not apply cleanly to " + rel + " (strict mode, no fuzz)");
                }
                for (int k = 0; k < expected.size(); k++) lines.remove(pos);
                lines.addAll(pos, replacement);
                offset += replacement.size() - expected.size();
                if (h.newNoNewlineAtEnd) endsWithNewline = false;
                else if (h.oldNoNewlineAtEnd) endsWithNewline = true;
            }
            if (deleting && lines.isEmpty()) {
                Files.deleteIfExists(target);
            } else {
                Files.createDirectories(target.getParent());
                String out = String.join("\n", lines) + (endsWithNewline && !lines.isEmpty() ? "\n" : "");
                Files.write(target, out.getBytes(StandardCharsets.UTF_8));
            }
            touched.add(rel);
        }
        return touched;
    }
}
