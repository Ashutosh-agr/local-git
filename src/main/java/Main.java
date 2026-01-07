import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

public class Main {
  public static void main(String[] args){
    // You can use print statements as follows for debugging, they'll be visible when running tests.
    System.err.println("Logs from your program will appear here!");
    Init init = new Init();
    Cat cat = new Cat();
    HashFile hf = new HashFile();
    Tree tree = new Tree();
    Commit commit = new Commit();
    Clone clone = new Clone();

    // TODO: Uncomment the code below to pass the first stage
    //
     final String command = args[0];

     switch (command) {
       case "init" :
          init.init();
         break;

       case "cat-file":
         try {
           cat.p(args[2]);
         }catch (IOException e){
           throw new RuntimeException(e);
         }
         break;

       case "hash-object":
         String com = args[1];
         String fileName = args[2];

         try {
           hf.w(fileName);
         }catch (IOException e){
           throw new RuntimeException(e);
         }
         break;

       case "ls-tree":
         String treeSha1 = args[1];

         try{
           tree.ls(treeSha1);
         }catch (IOException e){
           throw new RuntimeException(e);
         }
         break;

       case "write-tree":
         try{
           File dir = new File(".");
           tree.writeTree(dir);
         }catch(Exception e){
           throw new RuntimeException(e);
         }
         break;

       case "commit-tree":
         try{
           String treeSha = args[1];
           String parentSha = null;
           String message = null;

           if(args[2].equals("-p")){
             parentSha = args[3];
           }else{
             message = args[3];
           }

           if(args.length == 6){
             if(args[4].equals("-m"))
                message = args[5];
           }

           commit.commitTree(treeSha,parentSha,message);
         }catch (Exception e){
           throw new RuntimeException(e);
         }
         break;

       case "clone":
         String repo = args[1];
         String dir = args[2];

         try{
           clone.clone(repo,dir);
         }catch (Exception e){
           throw new RuntimeException(e);
         }
         break;

       default:
         System.out.println("Unknown command: " + command);
     }
  }
}
