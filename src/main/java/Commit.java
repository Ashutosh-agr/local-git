import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.zip.DeflaterOutputStream;

public class Commit{

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public void commitTree(String treeSha,String parentSha,String message) throws Exception{

        String author = "Ashutosh Agarwal";
        long timestamp = Instant.now().getEpochSecond();
        String timezone = "+0000";

//        Build commit body
        StringBuilder body = new StringBuilder();
        body.append("tree ").append(treeSha).append("\n");

        if (parentSha != null) {
            body.append("parent ").append(parentSha).append("\n");
        }

        body.append("author ")
                .append(author)
                .append(" ")
                .append(timestamp)
                .append(" ")
                .append(timezone)
                .append("\n");

        body.append("committer ")
                .append(author)
                .append(" ")
                .append(timestamp)
                .append(" ")
                .append(timezone)
                .append("\n");

        body.append("\n"); // blank line
        body.append(message).append("\n");

        byte[] bodyBytes = body.toString().getBytes(StandardCharsets.UTF_8);

        // Header
        String header = "commit " + bodyBytes.length + "\0";

        // Full object = header + body
        ByteArrayOutputStream full = new ByteArrayOutputStream();
        full.write(header.getBytes(StandardCharsets.UTF_8));
        full.write(bodyBytes);
        byte[] fullObject = full.toByteArray();

        // SHA-1 hash
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] shaBytes = sha1.digest(fullObject);
        String shaHex = bytesToHex(shaBytes);

        // Compress (zlib)
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (DeflaterOutputStream deflater = new DeflaterOutputStream(compressed)) {
            deflater.write(fullObject);
        }

        // Write to .git/objects
        Path objectDir = Paths.get(".git", "objects", shaHex.substring(0, 2));
        Files.createDirectories(objectDir);

        Path objectFile = objectDir.resolve(shaHex.substring(2));
        if (!Files.exists(objectFile)) {
            Files.write(objectFile, compressed.toByteArray());
        }

        System.out.println(shaHex);
    }
}