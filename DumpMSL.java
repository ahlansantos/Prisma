import java.nio.file.Files;
import java.nio.file.Paths;

public class DumpMSL {
    public static void main(String[] args) throws Exception {
        String code = new String(Files.readAllBytes(Paths.get("src/main/java/com/prisma/mtl/MTLBuiltinPipelines.java")));
        // We will just do a regex or parse the string.
        // Or even simpler: compile it!
    }
}
