import net.minecraft.Util;
import java.io.File;
import java.net.URI;
public class test_os {
    public static void main(String[] args) {
        // try to find it via reflection if this were runtime, but let's just make intentional errors to see the methods
        net.minecraft.Util.OS os = net.minecraft.Util.getPlatform();
        os.openFile(new File(""));
    }
}
