import net.minecraft.Util;
import java.lang.reflect.Method;
public class InspectUtil {
    public static void main(String[] args) throws Exception {
        for (Method m : Util.OS.class.getMethods()) {
            if (m.getName().startsWith("open")) {
                System.out.println(m);
            }
        }
    }
}
