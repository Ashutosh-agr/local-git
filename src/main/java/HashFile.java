import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.DeflaterOutputStream;

public class HashFile{
    public byte[] w(String fileName) throws IOException{
        Path filePath = Path.of(fileName);
        byte[] content = Files.readAllBytes(filePath);
        String header = "blob " + content.length + "\0";
        byte[] headerBytes = header.getBytes(StandardCharsets.UTF_8);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(headerBytes);
        out.write(content);

        byte[] blobData = out.toByteArray();

//        Compute SHA-1 Hash
        MessageDigest sh1 = null;
        try {
            sh1 = MessageDigest.getInstance("SHA-1");
        }catch (NoSuchAlgorithmException e){
            e.printStackTrace();
        }
        byte[] hashBytes = sh1.digest(blobData);

        StringBuilder hash = new StringBuilder();
        for (byte b : hashBytes) {
            hash.append(String.format("%02x", b));
        }

        String objectHash = hash.toString();

//        Compress using ZLIB
        ByteArrayOutputStream compressedOut = new ByteArrayOutputStream();
        DeflaterOutputStream deflater = new DeflaterOutputStream(compressedOut);

        deflater.write(blobData);
        deflater.close();

        byte[] compressedData = compressedOut.toByteArray();

//        write to .git/objects
        String dirName = objectHash.substring(0,2);
        String filePart = objectHash.substring(2);

        Path objectDir = Path.of(".git","objects",dirName);
        Files.createDirectories(objectDir);

        Path objectFile = objectDir.resolve(filePart);
        if (!Files.exists(objectFile)) {
            Files.write(objectFile,compressedData);
        }


//        print the Hash
        System.out.print(objectHash);
        return hashBytes;
    }
}