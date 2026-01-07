import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

public class Tree{

    class Entry{
        String mode,name;
        byte[] sha;
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private byte[] hexToBytes(String hex) {
        byte[] bytes = new byte[20];
        for (int i = 0; i < 20; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(i*2, i*2+2), 16);
        }
        return bytes;
    }

    public void ls(String treeSha) throws IOException{
        String dir = treeSha.substring(0,2);
        String path = treeSha.substring(2);

        String objectPath = ".git/objects/" + dir + "/" + path;

        byte[] compressedContent = new byte[1024];
        compressedContent = Files.readAllBytes(Paths.get(objectPath));

        InflaterInputStream fis = new InflaterInputStream(new ByteArrayInputStream(compressedContent));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        fis.transferTo(baos);

        byte[] decompressedContent = new byte[1024];
        try {
            decompressedContent = baos.toByteArray();
        }catch (Exception e){
            e.printStackTrace();
        }

        int i = 0;
        while(decompressedContent[i] != 0) i++;
        i++;

//        ASCII bytes  +  NULL (0x00)  +  20 raw SHA bytes

        while(i < decompressedContent.length){

//            mode
            int modeStart = i;
            while(decompressedContent[i] != ' ') i++;
            String mode = new String(decompressedContent,modeStart,i-modeStart);

            i++; //skip space

//            name
            int nameStart = i;
            while(decompressedContent[i] != 0) i++;
            String name = new String(decompressedContent,nameStart,i-nameStart);

            i++; // Skip '\0'

//            sha(20 raw bytes)
            byte[] rawSha = Arrays.copyOfRange(decompressedContent,i,i+20);
            i += 20;

            String shaHex =     bytesToHex(rawSha);

//            type
            String type = mode.equals("40000") ? "tree" : "blob";

//            print
            System.out.printf(
                    "%s %s %s\t%s%n",
                    mode, type, shaHex, name
            );
        }
    }

    public String writeTree(File dir) throws Exception{
        HashFile hashFile = new HashFile();
        List<Entry> entries = new ArrayList<>();

        for(File file: Objects.requireNonNull(dir.listFiles())){
            String name = file.getName();

//          skip .git
            if(name.equals(".git")) continue;

            Entry entry = new Entry();
            entry.name = name;

            if(file.isFile()){
                byte[] blobSha = hashFile.w(file.getPath());
                entry.mode = "100644";
                entry.sha = blobSha;
            }

            if(file.isDirectory()){
                String treeSha = writeTree(file);
                entry.mode = "40000";
                entry.sha = hexToBytes(treeSha);
            }

            entries.add(entry);
        }

//        sort alphabetically by name
        entries.sort(Comparator.comparing(e -> e.name));

//        build tree content
        ByteArrayOutputStream treeContent = new ByteArrayOutputStream();
        for(Entry entry: entries){
            treeContent.write((entry.mode + " " + entry.name).getBytes(StandardCharsets.UTF_8));
            treeContent.write(0);
            treeContent.write(entry.sha);
        }

        byte[] contentByte =  treeContent.toByteArray();

//        header
        String header = "tree " + contentByte.length + "\0";
        ByteArrayOutputStream full = new  ByteArrayOutputStream();
        full.write(header.getBytes(StandardCharsets.UTF_8));
        full.write(contentByte);

        byte[] fullObject = full.toByteArray();

//        SHA-1
        MessageDigest sha = MessageDigest.getInstance("SHA-1");
        byte[] shaBytes = sha.digest(fullObject);
        String shaHex = bytesToHex(shaBytes);

//        compress
        ByteArrayOutputStream compressed = new  ByteArrayOutputStream();
        try(DeflaterOutputStream deflater = new DeflaterOutputStream(compressed)){
            deflater.write(fullObject);
        }

//        write to .git/objects
        Path objDir = Paths.get(".git", "objects",shaHex.substring(0,2));
        Files.createDirectories(objDir);
        Path objectFile = objDir.resolve(shaHex.substring(2));

        if (!Files.exists(objectFile)) {
            Files.write(objectFile, compressed.toByteArray());
        }

        System.out.println(shaHex);

        return shaHex;
    }
}