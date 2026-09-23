package com.prisma.objc;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import static java.lang.foreign.ValueLayout.*;

public final class ObjCBlock {
    private static final int BLOCK_IS_GLOBAL = 1 << 28;

    private static final MemorySegment NS_CONCRETE_GLOBAL_BLOCK =
            Linker.nativeLinker().defaultLookup().findOrThrow("_NSConcreteGlobalBlock");
    private static final Arena BLOCK_ARENA = Arena.global();
    private static final MemorySegment BLOCK_DESCRIPTOR;
    private static final MethodHandle INVOKE_RUNNABLE;

    static {
        BLOCK_DESCRIPTOR = BLOCK_ARENA.allocate(16, 8);
        BLOCK_DESCRIPTOR.set(JAVA_LONG, 0, 0L);
        BLOCK_DESCRIPTOR.set(JAVA_LONG, 8, 32L);
        try {
            INVOKE_RUNNABLE = MethodHandles.lookup().findStatic(
                    ObjCBlock.class,
                    "invokeRunnable",
                    MethodType.methodType(void.class, Runnable.class, MemorySegment.class, MemorySegment.class)
            );
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to resolve block invoke handle", e);
        }
    }

    private ObjCBlock() {
    }

    public static MemorySegment withRunnable(final Runnable action) {
        MethodHandle target = MethodHandles.insertArguments(INVOKE_RUNNABLE, 0, action);
        MemorySegment invoke = ObjC.LINKER.upcallStub(target, FunctionDescriptor.ofVoid(ADDRESS, ADDRESS), BLOCK_ARENA);

        MemorySegment block = BLOCK_ARENA.allocate(32, 8);
        block.set(ADDRESS, 0, NS_CONCRETE_GLOBAL_BLOCK);
        block.set(JAVA_INT, 8, BLOCK_IS_GLOBAL);
        block.set(JAVA_INT, 12, 0);
        block.set(ADDRESS, 16, invoke);
        block.set(ADDRESS, 24, BLOCK_DESCRIPTOR);
        return block;
    }

    private static void invokeRunnable(final Runnable action, final MemorySegment block, final MemorySegment argument) {
        action.run();
    }
}
