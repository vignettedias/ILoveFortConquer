package fortconquer.build;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/** Dependency-free ZIP/APK inspection helpers used by the release tasks. */
public final class ApkZip {
    private ApkZip() {}

    public static String sha256(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A central-directory entry with its resolved data offset. */
    public static final class Entry {
        public final String name;
        public final int method;
        public final long dataOffset;
        public final long compressedSize;

        Entry(String name, int method, long dataOffset, long compressedSize) {
            this.name = name;
            this.method = method;
            this.dataOffset = dataOffset;
            this.compressedSize = compressedSize;
        }
    }

    public static List<Entry> entries(Path apk) throws IOException {
        try (RandomAccessFile f = new RandomAccessFile(apk.toFile(), "r")) {
            long len = f.length();
            int scan = (int) Math.min(len, 65535 + 22);
            byte[] tail = new byte[scan];
            f.seek(len - scan);
            f.readFully(tail);
            int eocd = -1;
            for (int i = scan - 22; i >= 0; i--) {
                if (tail[i] == 0x50 && tail[i + 1] == 0x4b && tail[i + 2] == 0x05 && tail[i + 3] == 0x06) { eocd = i; break; }
            }
            if (eocd < 0) throw new IOException("Not a ZIP file (no EOCD): " + apk);
            ByteBuffer e = ByteBuffer.wrap(tail, eocd, 22).order(ByteOrder.LITTLE_ENDIAN);
            int count = e.getShort(eocd + 10) & 0xffff;
            long cdSize = e.getInt(eocd + 12) & 0xffffffffL;
            long cdOffset = e.getInt(eocd + 16) & 0xffffffffL;
            byte[] cd = new byte[(int) cdSize];
            f.seek(cdOffset);
            f.readFully(cd);
            ByteBuffer c = ByteBuffer.wrap(cd).order(ByteOrder.LITTLE_ENDIAN);
            List<Entry> out = new ArrayList<>();
            int p = 0;
            for (int i = 0; i < count; i++) {
                if (c.getInt(p) != 0x02014b50) throw new IOException("Bad central directory entry #" + i);
                int method = c.getShort(p + 10) & 0xffff;
                long csize = c.getInt(p + 20) & 0xffffffffL;
                int nameLen = c.getShort(p + 28) & 0xffff;
                int extraLen = c.getShort(p + 30) & 0xffff;
                int commentLen = c.getShort(p + 32) & 0xffff;
                long lho = c.getInt(p + 42) & 0xffffffffL;
                String name = new String(cd, p + 46, nameLen, StandardCharsets.UTF_8);
                byte[] lh = new byte[30];
                f.seek(lho);
                f.readFully(lh);
                ByteBuffer l = ByteBuffer.wrap(lh).order(ByteOrder.LITTLE_ENDIAN);
                if (l.getInt(0) != 0x04034b50) throw new IOException("Bad local header for " + name);
                long dataOffset = lho + 30 + (l.getShort(26) & 0xffff) + (l.getShort(28) & 0xffff);
                out.add(new Entry(name, method, dataOffset, csize));
                p += 46 + nameLen + extraLen + commentLen;
            }
            return out;
        }
    }

    /**
     * Returns human-readable alignment problems: uncompressed entries must start on a 4-byte
     * boundary (Android 11+ refuses to install targetSdk >= 30 APKs whose resources.arsc is not
     * stored and aligned), native libraries on a 16 KiB boundary.
     */
    public static List<String> alignmentProblems(Path apk) throws IOException {
        List<String> problems = new ArrayList<>();
        for (Entry en : entries(apk)) {
            if (en.name.equals("resources.arsc") && en.method != 0) problems.add("resources.arsc is compressed");
            if (en.method != 0) continue;
            long req = en.name.endsWith(".so") ? 16384 : 4;
            if (en.dataOffset % req != 0) problems.add(en.name + " data offset " + en.dataOffset + " not aligned to " + req);
        }
        return problems;
    }

    public static void main(String[] args) throws IOException {
        Path apk = Path.of(args[0]);
        List<String> p = alignmentProblems(apk);
        System.out.println(p.isEmpty() ? "alignment OK" : String.join("\n", p));
        if (!p.isEmpty()) System.exit(1);
    }
}
