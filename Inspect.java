import com.mojang.renderpearl.backend.api.GpuDeviceBackend;
import java.lang.reflect.Method;
public class Inspect {
    public static void main(String[] args) throws Exception {
        for (Method m : GpuDeviceBackend.class.getMethods()) {
            if (m.getName().equals("createSurface")) {
                System.out.println(m);
            }
        }
    }
}
