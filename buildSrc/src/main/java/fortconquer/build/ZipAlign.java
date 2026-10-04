package fortconquer.build;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Portable equivalent of {@code zipalign -p 4}: rewrites a ZIP so that the data of every
 * STORED entry starts on a 4-byte boundary (16 KiB for native libraries). Compressed data is
 * copied byte-for-byte; only the local-header extra field is padded and the central directory
 * offsets are rewritten. Must run before apksigner (v2+ signatures cover the final layout).
 *
 * <p>apktool stamps every entry with the wall-clock build time; the DOS modification time and
 * date are normalised to a fixed value (2008-01-01 00:00, as AOSP's build does) so that two
 * builds of the same inputs produce byte-identical archives.
 */
public final class ZipAlign {
    private ZipAlign() {}

    private static final int LFH_SIG = 0x04034b50, CDH_SIG = 0x02014b50, EOCD_SIG = 0x06054b50, DD_SIG = 0x08074b50;
    /** DOS date 2008-01-01 ((year - 1980) << 9 | month << 5 | day) and time 00:00:00. */
    private static final int FIXED_DOS_DATE = (2008 - 1980) << 9 | 1 << 5 | 1, FIXED_DOS_TIME = 0;

    public static void align(Path in, Path out) throws IOException {
        try (RandomAccessFile f = new RandomAccessFile(in.toFile(), "r");
             OutputStream os = new BufferedOutputStream(Files.newOutputStream(out), 1 << 16)) {
            long len = f.length();
            int scan = (int) Math.min(len, 65535 + 22);
            byte[] tail = new byte[scan];
            f.seek(len - scan);
            f.readFully(tail);
            int eocd = -1;
            for (int i = scan - 22; i >= 0; i--) {
                if (le32(tail, i) == EOCD_SIG) { eocd = i; break; }
            }
            if (eocd < 0) throw new IOException("no EOCD in " + in);
            int count = le16(tail, eocd + 10);
            long cdSize = le32(tail, eocd + 12) & 0xffffffffL;
            long cdOffset = le32(tail, eocd + 16) & 0xffffffffL;
            byte[] cd = new byte[(int) cdSize];
            f.seek(cdOffset);
            f.readFully(cd);

            List<byte[]> newCd = new ArrayList<>();
            long pos = 0;
            int p = 0;
            for (int i = 0; i < count; i++) {
                if (le32(cd, p) != CDH_SIG) throw new IOException("bad central directory entry " + i);
                int flags = le16(cd, p + 8);
                int method = le16(cd, p + 10);
                long csize = le32(cd, p + 20) & 0xffffffffL;
                int nameLen = le16(cd, p + 28), extraLen = le16(cd, p + 30), commentLen = le16(cd, p + 32);
                long lho = le32(cd, p + 42) & 0xffffffffL;
                String name = new String(cd, p + 46, nameLen, java.nio.charset.StandardCharsets.UTF_8);

                byte[] lfh = new byte[30];
                f.seek(lho);
                f.readFully(lfh);
                if (le32(lfh, 0) != LFH_SIG) throw new IOException("bad local header for " + name);
                int lNameLen = le16(lfh, 26), lExtraLen = le16(lfh, 28);
                byte[] lName = new byte[lNameLen];
                f.readFully(lName);
                byte[] lExtra = new byte[lExtraLen];
                f.readFully(lExtra);

                int padding = 0;
                if (method == 0) {
                    int alignment = name.endsWith(".so") ? 16384 : 4;
                    long dataStart = pos + 30 + lNameLen + lExtraLen;
                    padding = (int) ((alignment - (dataStart % alignment)) % alignment);
                }
                byte[] newExtra = new byte[lExtraLen + padding];
                System.arraycopy(lExtra, 0, newExtra, 0, lExtraLen);
                putLe16(lfh, 28, newExtra.length);
                putLe16(lfh, 10, FIXED_DOS_TIME);
                putLe16(lfh, 12, FIXED_DOS_DATE);

                long newOffset = pos;
                os.write(lfh);
                os.write(lName);
                os.write(newExtra);
                pos += 30 + lNameLen + newExtra.length;
                pos += copy(f, os, csize);
                if ((flags & 0x08) != 0) { // data descriptor follows the data
                    byte[] dd = new byte[16];
                    f.readFully(dd, 0, 12);
                    int ddLen = 12;
                    if (le32(dd, 0) == DD_SIG) { f.readFully(dd, 12, 4); ddLen = 16; }
                    os.write(dd, 0, ddLen);
                    pos += ddLen;
                }
                byte[] entry = new byte[46 + nameLen + extraLen + commentLen];
                System.arraycopy(cd, p, entry, 0, entry.length);
                putLe32(entry, 42, (int) newOffset);
                putLe16(entry, 12, FIXED_DOS_TIME);
                putLe16(entry, 14, FIXED_DOS_DATE);
                newCd.add(entry);
                p += entry.length;
            }
            long newCdOffset = pos;
            long newCdSize = 0;
            for (byte[] e : newCd) { os.write(e); newCdSize += e.length; }
            byte[] end = new byte[scan - eocd];
            System.arraycopy(tail, eocd, end, 0, end.length);
            putLe32(end, 12, (int) newCdSize);
            putLe32(end, 16, (int) newCdOffset);
            os.write(end);
        }
    }

    private static long copy(RandomAccessFile f, OutputStream os, long n) throws IOException {
        byte[] buf = new byte[1 << 16];
        long left = n;
        while (left > 0) {
            int r = f.read(buf, 0, (int) Math.min(buf.length, left));
            if (r < 0) throw new IOException("unexpected EOF");
            os.write(buf, 0, r);
            left -= r;
        }
        return n;
    }

    private static int le16(byte[] b, int o) { return (b[o] & 0xff) | (b[o + 1] & 0xff) << 8; }
    private static int le32(byte[] b, int o) { return ByteBuffer.wrap(b, o, 4).order(ByteOrder.LITTLE_ENDIAN).getInt(); }
    private static void putLe16(byte[] b, int o, int v) { b[o] = (byte) v; b[o + 1] = (byte) (v >>> 8); }
    private static void putLe32(byte[] b, int o, int v) { ByteBuffer.wrap(b, o, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(v); }
}
