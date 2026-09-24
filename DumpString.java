import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DumpString {
    public static void main(String[] args) throws Exception {
        String content = new String(Files.readAllBytes(Paths.get("src/main/java/com/prisma/mtl/MTLBuiltinPipelines.java")));
        // Wait, MTLBuiltinPipelines.java doesn't have any dependencies on Minecraft for its constants!
        // We can just compile it!
    }
}
