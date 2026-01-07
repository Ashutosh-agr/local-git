import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.zip.InflaterInputStream;

public class Cat{
    public void p(String hash) throws IOException{
        String dir = hash.substring(0,2);
        String file = hash.substring(2);

        String objectPath = ".git/objects/" + dir + "/" + file;

        byte[] compressedContent =  new byte[1024];
        compressedContent = Files.readAllBytes(Paths.get(objectPath));


        InflaterInputStream inflater = new InflaterInputStream(new ByteArrayInputStream(compressedContent));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        inflater.transferTo(out);


        byte[] decompressed = new byte[1024];
        try{
            decompressed = out.toByteArray();
        }catch (Exception e){
            System.out.println("Error: " + e);
        }

        int i = 0;
        while(decompressed[i] != 0) i++;

        String header = new String(decompressed,0,i);
        //System.out.println(header);

        byte[] content = Arrays.copyOfRange(decompressed,i + 1,decompressed.length);

        System.out.write(content);
    }
}