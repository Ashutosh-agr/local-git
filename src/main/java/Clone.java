import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.zip.InflaterInputStream;
import java.util.*;
import java.util.zip.*;
import java.nio.file.Paths;
import java.net.http.HttpResponse.BodyHandlers;
import java.io.*;

public class Clone{

    class DeltaObject {
        int type;
        byte[] baseRef;
        byte[] deltaData;
        int objectPosition; // Position in packfile where this object starts

        DeltaObject(int type, byte[] baseRef, byte[] deltaData, int objectPosition) {
            this.type = type;
            this.baseRef = baseRef;
            this.deltaData = deltaData;
            this.objectPosition = objectPosition;
        }
    }

    class TreeEntry {
        String mode;
        String name;
        String sha;
    }

    private String dir;

    public HashMap<String,byte[]> unpackedObjects = new HashMap<>();
    private HashMap<Integer, byte[]> objectsByPosition = new HashMap<>(); // For OFS_DELTA

    private List<String> pktLineParser(String data){
        List<String> lines = new ArrayList<>();
        int i = 0;

        while(i<data.length()){
            String lenHex =  data.substring(i,i + 4);
            int len = Integer.parseInt(lenHex, 16);

            if(len == 0){
                i += 4;
                continue;
            }

            String payLoad = data.substring(i + 4,i + len);
            lines.add(payLoad);

            i += len;
        }

        return lines;
    }

    private long readOffset(ByteBuffer buf) {
        if (!buf.hasRemaining()) {
            throw new RuntimeException("Buffer underflow in readOffset");
        }
        int c = buf.get() & 0xff;
        long offset = c & 0x7f;

        while ((c & 0x80) != 0) {
            if (!buf.hasRemaining()) {
                throw new RuntimeException("Buffer underflow in readOffset continuation");
            }
            c = buf.get() & 0xff;
            offset = ((offset + 1) << 7) | (c & 0x7f);
        }

        return offset;
    }

    private byte[] tryLoadBaseObject(DeltaObject d) throws IOException {
        if (d.type == 7) { // REF_DELTA
            String sha = bytesToHex(d.baseRef);
            byte[] obj = unpackedObjects.get(sha);
            if (obj != null) return obj;

            // Load from disk
            Path p = Paths.get(dir, ".git", "objects", sha.substring(0,2), sha.substring(2));
            if (!Files.exists(p)) return null;
            byte[] compressed = Files.readAllBytes(p);
            return inflateLooseObject(compressed);
        }

        if (d.type == 6) { // OFS_DELTA
            // baseRef contains the absolute position of the base object (as long bytes)
            ByteBuffer bb = ByteBuffer.wrap(d.baseRef);
            int basePos = (int) bb.getLong();
            return objectsByPosition.get(basePos);
        }

        return null;
    }

    private byte[] inflateLooseObject(byte[] compressed) throws IOException {
        InflaterInputStream in =
                new InflaterInputStream(new ByteArrayInputStream(compressed));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        in.transferTo(out);
        return out.toByteArray();
    }

    private byte[] applyDelta(byte[] base, byte[] delta) throws IOException {
        ByteBuffer buf = ByteBuffer.wrap(delta);

        long baseSize = readVarInt(buf);
        long resultSize = readVarInt(buf);

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        while (buf.hasRemaining()) {
            int cmd = buf.get() & 0xff;

            if ((cmd & 0x80) != 0) {
                int offset = 0;
                int size = 0;

                if ((cmd & 0x01) != 0) offset |= buf.get() & 0xff;
                if ((cmd & 0x02) != 0) offset |= (buf.get() & 0xff) << 8;
                if ((cmd & 0x04) != 0) offset |= (buf.get() & 0xff) << 16;
                if ((cmd & 0x08) != 0) offset |= (buf.get() & 0xff) << 24;

                if ((cmd & 0x10) != 0) size |= buf.get() & 0xff;
                if ((cmd & 0x20) != 0) size |= (buf.get() & 0xff) << 8;
                if ((cmd & 0x40) != 0) size |= (buf.get() & 0xff) << 16;

                if (size == 0) size = 0x10000;

                out.write(base, offset, size);
            } else {
                byte[] literal = new byte[cmd];
                buf.get(literal);
                out.write(literal);
            }
        }

        return out.toByteArray();
    }

    private long readVarInt(ByteBuffer buf) {
        long result = 0;
        int shift = 0;
        int b;

        do {
            b = buf.get() & 0xff;
            result |= (long)(b & 0x7f) << shift;
            shift += 7;
        } while ((b & 0x80) != 0);

        return result;
    }

    private void storeAsLooseObject(byte[] fullObject) throws Exception {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] sha = sha1.digest(fullObject);
        String hex = bytesToHex(sha);

        Path dirPath = Paths.get(dir, ".git", "objects", hex.substring(0,2));
        Files.createDirectories(dirPath);

        Path file = dirPath.resolve(hex.substring(2));
        if (Files.exists(file)) return;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (DeflaterOutputStream def = new DeflaterOutputStream(out)) {
            def.write(fullObject);
        }

        Files.write(file, out.toByteArray());
        unpackedObjects.put(hex, fullObject);
    }

    private byte[] pktLine(String payload){
        int len = payload.getBytes(StandardCharsets.UTF_8).length + 4;
        String header = String.format("%04x",len);
        return (header + payload).getBytes(StandardCharsets.UTF_8);
    }

    private byte[] longToBytes(long value){
        ByteBuffer buf = ByteBuffer.allocate(Long.BYTES);
        buf.putLong(value);
        return buf.array();
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private byte[] readLooseObject(String sha) throws IOException {
        Path path = Paths.get(
                dir, ".git", "objects",
                sha.substring(0, 2),
                sha.substring(2)
        );

        byte[] compressed = Files.readAllBytes(path);

        InflaterInputStream inflater =
                new InflaterInputStream(new ByteArrayInputStream(compressed));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        inflater.transferTo(out);

        return out.toByteArray(); // header + content
    }

    private String extractTreeShaFromCommit(String commitSha) throws IOException {
        byte[] obj = readLooseObject(commitSha);

        int zero = 0;
        while (obj[zero] != 0) zero++;

        String body = new String(obj, zero + 1, obj.length - zero - 1, StandardCharsets.UTF_8);

        for (String line : body.split("\n")) {
            if (line.startsWith("tree ")) {
                return line.substring(5).trim();
            }
        }
        throw new RuntimeException("No tree found in commit");
    }

    private List<TreeEntry> parseTree(byte[] treeObj) {
        List<TreeEntry> entries = new ArrayList<>();

        int i = 0;
        while (treeObj[i] != 0) i++; // skip header
        i++;

        while (i < treeObj.length) {
            int modeStart = i;
            while (treeObj[i] != ' ') i++;
            String mode = new String(treeObj, modeStart, i - modeStart);

            i++; // space

            int nameStart = i;
            while (treeObj[i] != 0) i++;
            String name = new String(treeObj, nameStart, i - nameStart);

            i++; // null

            byte[] shaBytes = Arrays.copyOfRange(treeObj, i, i + 20);
            i += 20;

            TreeEntry e = new TreeEntry();
            e.mode = mode;
            e.name = name;
            e.sha = bytesToHex(shaBytes);

            entries.add(e);
        }

        return entries;
    }

    private void checkoutTree(String treeSha, Path targetDir) throws Exception {
        byte[] treeObj = readLooseObject(treeSha);
        List<TreeEntry> entries = parseTree(treeObj);

        for (TreeEntry e : entries) {
            Path outPath = targetDir.resolve(e.name);

            if (e.mode.equals("40000")) {
                Files.createDirectories(outPath);
                checkoutTree(e.sha, outPath);
            } else {
                writeBlob(e.sha, outPath);
            }
        }
    }

    private void writeBlob(String blobSha, Path path) throws Exception {
        byte[] blob = readLooseObject(blobSha);

        int zero = 0;
        while (blob[zero] != 0) zero++;

        byte[] content = Arrays.copyOfRange(blob, zero + 1, blob.length);

        Files.createDirectories(path.getParent());
        Files.write(path, content);
    }

    public void clone(String repo, String dirc) throws Exception {
        dir = dirc;
        new File(dir).mkdirs();
        final File root = new File(dir, ".git");
        root.mkdirs();
        new File(root, "objects").mkdirs();
        new File(root, "refs/heads").mkdirs();
        final File head = new File(root, "HEAD");

        try {
            head.createNewFile();
            Files.write(head.toPath(), "ref: refs/heads/main\n".getBytes());

            Path config = Path.of(dir, ".git", "config");
            Files.write(
                    config,
                    (
                            "[core]\n" +
                                    "    repositoryformatversion = 0\n" +
                                    "    filemode = false\n" +
                                    "    bare = false\n"
                    ).getBytes(StandardCharsets.UTF_8)
            );

            System.out.println("Initialized git directory");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

//        Discovery phase
        HttpClient client = HttpClient.newHttpClient();
        String url = repo + ".git/info/refs?service=git-upload-pack";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, BodyHandlers.ofString());

        String pktLineRefs = response.body();

        List<String> parseRef = pktLineParser(pktLineRefs);

        Map<String, String> refs = new HashMap<>();

        for (String ref : parseRef) {
            // Remove capabilities (everything after \0)
            String clean = ref.split("\0")[0];

            String[] parts = clean.split(" ");
            if (parts.length == 2) {
                refs.put(parts[1], parts[0]);
            }
        }

        String headSha = null;
        String headRef = null;

        if (refs.containsKey("HEAD")) {
            String hs = refs.get("HEAD");
            headSha = hs;
            headRef = refs.entrySet().stream()
                    .filter(e -> e.getValue().equals(hs))
                    .map(Map.Entry::getKey)
                    .filter(r -> r.startsWith("refs/heads/"))
                    .findFirst()
                    .orElse("refs/heads/main"); // fallback if HEAD doesn't point to a branch

        } else if (refs.containsKey("refs/heads/main")) {
            headRef = "refs/heads/main";
            headSha = refs.get(headRef);

        } else if (refs.containsKey("refs/heads/master")) {
            headRef = "refs/heads/master";
            headSha = refs.get(headRef);

        } else {
            throw new RuntimeException("No default branch found");
        }

        if (headSha == null) {
            throw new RuntimeException("refs/heads/main not found in repository");
        }

//        upload-pack
        ByteArrayOutputStream reqBody = new ByteArrayOutputStream();

        reqBody.write(pktLine(
                "want " + headSha +
                        " multi_ack_detailed no-done side-band-64k ofs-delta agent=git/2.0\n"
        ));

        // Flush packet to end wants
        reqBody.write("0000".getBytes(StandardCharsets.UTF_8));

        reqBody.write(pktLine("done\n"));
        byte[] body = reqBody.toByteArray();

//        post request
        HttpRequest request1 = HttpRequest.newBuilder()
                .uri(URI.create(repo + ".git/git-upload-pack"))
                .header("Content-Type", "application/x-git-upload-pack-request")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        HttpResponse<byte[]> res = client.send(request1,HttpResponse.BodyHandlers.ofByteArray());

        byte[] packets = res.body();
        int ind = 0;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        while(ind < packets.length){
            if (ind + 4 > packets.length) break;

            String hexLen = new String(packets,ind,4,StandardCharsets.US_ASCII);

            // Handle flush packet (0000)
            if (hexLen.equals("0000")) {
                ind += 4;
                continue;
            }

            // Handle NAK packet (0008NAK\n)
            if (hexLen.equals("0008")) {
                String content = new String(packets, ind + 4, 4, StandardCharsets.US_ASCII);
                if (content.startsWith("NAK")) {
                    ind += 8;
                    continue;
                }
            }

            int len;
            try {
                len = Integer.parseInt(hexLen, 16);
            } catch (NumberFormatException e) {
                // Not a valid pkt-line, might be raw pack data
                baos.write(packets, ind, packets.length - ind);
                break;
            }

            ind += 4;

            if(len == 0){
                continue;
            }

            if (len < 4) {
                continue;
            }

            int payLoadLen = len - 4;

            if (ind + payLoadLen > packets.length) break;

            byte channel = packets[ind];

            // Check if this is side-band data (channel 1, 2, or 3)
            if (channel == 1 || channel == 2 || channel == 3) {
                byte[] payLoad = new byte[payLoadLen - 1];
                System.arraycopy(packets, ind + 1, payLoad, 0, payLoad.length);

                if(channel == 1) baos.write(payLoad);
                else if(channel == 2) System.err.print(new String(payLoad));
                else if (channel == 3) {
                    throw new RuntimeException("Git error: " +
                            new String(payLoad, StandardCharsets.UTF_8));
                }
            } else {
                // No side-band, raw data
                byte[] payLoad = new byte[payLoadLen];
                System.arraycopy(packets, ind, payLoad, 0, payLoad.length);

                String str = new String(payLoad, StandardCharsets.UTF_8).trim();
                if (!str.equals("NAK")) {
                    baos.write(payLoad);
                }
            }

            ind += payLoadLen;
        }

        byte[] packFileBytes = baos.toByteArray();

        if (packFileBytes.length < 12) {
            throw new RuntimeException("Pack file too small: " + packFileBytes.length + " bytes");
        }

        ByteBuffer buf = ByteBuffer.wrap(packFileBytes);
        buf.order(ByteOrder.BIG_ENDIAN);

        byte[] magic = new byte[4];
        buf.get(magic);

        if (!new String(magic).equals("PACK")) {
            throw new RuntimeException("Invalid packfile");
        }

        int version = buf.getInt();
        if (version != 2 && version != 3) {
            throw new RuntimeException("Unsupported pack version: " + version);
        }

        int objectCount = buf.getInt();
        System.out.println("Objects in pack: " + objectCount);

        List<DeltaObject> deltas = new ArrayList<>();

        for(int objInd = 0;objInd < objectCount;objInd++) {
            if (!buf.hasRemaining()) {
                throw new RuntimeException("Buffer exhausted at object " + objInd + " of " + objectCount);
            }

            int objectStartPos = buf.position(); // Track position for OFS_DELTA

            int c = buf.get() & 0xff;

            int type = (c >> 4) & 7;
            long size = c & 0x0f;
            int shift = 4;

            while ((c & 0x80) != 0) {
                if (!buf.hasRemaining()) {
                    throw new RuntimeException("Buffer exhausted reading size at object " + objInd);
                }
                c = buf.get() & 0xff;
                size |= (long) (c & 0x7f) << shift;
                shift += 7;
            }

            byte[] baseRef = null;
            long baseOffset = 0;
            if (type == 6) {
                // OFS_DELTA: read offset before inflating
                baseOffset = readOffset(buf);
                baseRef = longToBytes(objectStartPos - baseOffset); // Store absolute position of base
            }
            if (type == 7) {
                // REF_DELTA: read SHA before inflating
                if (buf.remaining() < 20) {
                    throw new RuntimeException("Buffer exhausted reading REF_DELTA SHA at object " + objInd);
                }
                baseRef = new byte[20];
                buf.get(baseRef);
            }

            Inflater inflater = new Inflater();
            ByteArrayOutputStream baos2 = new ByteArrayOutputStream();

            byte[] buffer = new byte[8192];

            // Track position before inflating
            int inflateStart = buf.position();

            // Feed all remaining data to inflater
            byte[] compressedInput = new byte[buf.remaining()];
            buf.get(compressedInput);
            inflater.setInput(compressedInput);

            while (!inflater.finished()) {
                int n = inflater.inflate(buffer);
                if (n == 0 && !inflater.finished()) {
                    throw new RuntimeException("Inflate stalled at object " + objInd);
                }
                baos2.write(buffer, 0, n);
            }

            // Set position to just after the consumed compressed data
            int bytesConsumed = (int) inflater.getBytesRead();
            buf.position(inflateStart + bytesConsumed);

            inflater.end();
            byte[] content = baos2.toByteArray();

            if (type == 6 || type == 7) {
                deltas.add(new DeltaObject(type, baseRef, content, objectStartPos));
                continue;
            }

            String typeStr;
            switch (type) {
                case 1 -> typeStr = "commit";
                case 2 -> typeStr = "tree";
                case 3 -> typeStr = "blob";
                default -> throw new RuntimeException("Unsupported object type: " + type);
            }

            ByteArrayOutputStream fullObj = new ByteArrayOutputStream();
            fullObj.write((typeStr + " " + content.length + "\0")
                    .getBytes(StandardCharsets.UTF_8));
            fullObj.write(content);

            byte[] fullObject = fullObj.toByteArray();

            MessageDigest sha2 = MessageDigest.getInstance("SHA-1");
            byte[] shaBytes = sha2.digest(fullObject);
            String shaHex = bytesToHex(shaBytes);

            // Store by position for OFS_DELTA resolution
            objectsByPosition.put(objectStartPos, fullObject);

            Path objDir = Paths.get(
                    dir, ".git", "objects", shaHex.substring(0, 2)
            );
            Files.createDirectories(objDir);

            Path objFile = objDir.resolve(shaHex.substring(2));

            if (!Files.exists(objFile)) {
                ByteArrayOutputStream compressed = new ByteArrayOutputStream();
                try (DeflaterOutputStream deflater =
                             new DeflaterOutputStream(compressed)) {
                    deflater.write(fullObject);
                }
                Files.write(objFile, compressed.toByteArray());
                unpackedObjects.put(shaHex, fullObject);
            }
        }

        int packEnd = buf.position();
        byte[] expected = Arrays.copyOfRange(packFileBytes,
                packFileBytes.length - 20,
                packFileBytes.length);

        MessageDigest sha3 = MessageDigest.getInstance("SHA-1");
        byte[] actual = sha3.digest(
                Arrays.copyOf(packFileBytes, packEnd)
        );

        if (!Arrays.equals(expected, actual)) {
            throw new RuntimeException("Pack checksum mismatch");
        }

        boolean progress;
        do {
            progress = false;
            Iterator<DeltaObject> it = deltas.iterator();

            while (it.hasNext()) {
                DeltaObject d = it.next();

                byte[] base = tryLoadBaseObject(d);
                if (base == null) continue;

                byte[] reconstructed = applyDelta(base, d.deltaData);
                storeAsLooseObject(reconstructed);

                // Store by position for chained OFS_DELTA resolution
                objectsByPosition.put(d.objectPosition, reconstructed);

                it.remove();
                progress = true;
            }
        } while (progress);

        if (!deltas.isEmpty()) {
            throw new RuntimeException("Unresolved deltas remain");
        }

        String rootTree = extractTreeShaFromCommit(headSha);
        checkoutTree(rootTree, Paths.get(dir));
    }
}