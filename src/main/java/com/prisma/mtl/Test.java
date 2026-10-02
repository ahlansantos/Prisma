
import com.prisma.objc.ObjC;
import com.prisma.objc.Msg;
import java.lang.foreign.MemorySegment;

public class Test {
    public static void main(String[] args) {
        MemorySegment nsColorSpace = ObjC.clazz("NSColorSpace");
        MemorySegment extSRGB = Msg.of("extendedSRGBColorSpace", java.lang.foreign.ValueLayout.ADDRESS).sendPtr(nsColorSpace);
        MemorySegment cgColorSpace = Msg.of("CGColorSpace", java.lang.foreign.ValueLayout.ADDRESS).sendPtr(extSRGB);
        System.out.println(cgColorSpace);
    }
}
