package com.prisma.mtl;

import com.prisma.objc.AutoreleasePool;
import com.prisma.objc.Msg;
import com.prisma.objc.ObjC;

import java.lang.foreign.MemorySegment;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

public final class MTLVertexDescriptor implements AutoCloseable {
    private static final MemorySegment CLS = ObjC.clazz("MTLVertexDescriptor");
    private static final Msg NEW = Msg.of("new", ADDRESS);
    private static final Msg ATTRIBUTES = Msg.of("attributes", ADDRESS);
    private static final Msg LAYOUTS = Msg.of("layouts", ADDRESS);
    private static final Msg OBJECT_AT_INDEXED_SUBSCRIPT = Msg.of("objectAtIndexedSubscript:", ADDRESS, JAVA_LONG);
    private static final Msg SET_FORMAT = Msg.ofVoid("setFormat:", JAVA_LONG);
    private static final Msg SET_OFFSET = Msg.ofVoid("setOffset:", JAVA_LONG);
    private static final Msg SET_BUFFER_INDEX = Msg.ofVoid("setBufferIndex:", JAVA_LONG);
    private static final Msg SET_STRIDE = Msg.ofVoid("setStride:", JAVA_LONG);
    private static final Msg SET_STEP_FUNCTION = Msg.ofVoid("setStepFunction:", JAVA_LONG);
    private static final Msg SET_STEP_RATE = Msg.ofVoid("setStepRate:", JAVA_LONG);

    private final MemorySegment handle;
    private boolean closed;

    public MTLVertexDescriptor() {
        this.handle = NEW.sendPtr(CLS);
    }

    public MemorySegment handle() {
        return this.handle;
    }

    public void setAttribute(long index, long format, long offset, long bufferIndex) {
        try (AutoreleasePool _ = AutoreleasePool.push()) {
            MemorySegment attribute = OBJECT_AT_INDEXED_SUBSCRIPT.sendPtr(ATTRIBUTES.sendPtr(this.handle), index);
            SET_FORMAT.send(attribute, format);
            SET_OFFSET.send(attribute, offset);
            SET_BUFFER_INDEX.send(attribute, bufferIndex);
        }
    }

    public void setLayout(long bufferIndex, long stride, MTLVertexStepFunction stepFunction, long stepRate) {
        try (AutoreleasePool _ = AutoreleasePool.push()) {
            MemorySegment layout = OBJECT_AT_INDEXED_SUBSCRIPT.sendPtr(LAYOUTS.sendPtr(this.handle), bufferIndex);
            SET_STRIDE.send(layout, stride);
            SET_STEP_FUNCTION.send(layout, stepFunction.value);
            SET_STEP_RATE.send(layout, stepRate);
        }
    }

    @Override
    public void close() {
        if (!this.closed) {
            this.closed = true;
            ObjC.release(this.handle);
        }
    }
}
