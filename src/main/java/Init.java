import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

public class Init{
    public void init(){
        final File root = new File(".git");
        new File(root, "objects").mkdirs();
        new File(root, "refs").mkdirs();
        final File head = new File(root, "HEAD");

        try {
            head.createNewFile();
            Files.write(head.toPath(), "ref: refs/heads/main\n".getBytes());

            Path config = Path.of(".git", "config");
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
    }
}