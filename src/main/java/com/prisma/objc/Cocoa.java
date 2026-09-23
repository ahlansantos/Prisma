package com.prisma.objc;

import java.lang.foreign.MemorySegment;

import static java.lang.foreign.ValueLayout.*;

public final class Cocoa {
    private static final Msg BACKING_SCALE_FACTOR = Msg.of("backingScaleFactor", JAVA_DOUBLE);
    private static final Msg SET_WANTS_LAYER = Msg.ofVoid("setWantsLayer:", JAVA_BOOLEAN);
    private static final Msg SET_LAYER = Msg.ofVoid("setLayer:", ADDRESS);

    private final MemorySegment window;
    private final MemorySegment view;

    public Cocoa(final MemorySegment window, final MemorySegment view) {
        if (ObjC.isNil(window)) {
            throw new IllegalStateException("NSWindow handle is null");
        }
        if (ObjC.isNil(view)) {
            throw new IllegalStateException("NSView handle is null");
        }
        this.window = window;
        this.view = view;
    }

    public double backingScaleFactor() {
        double scale = BACKING_SCALE_FACTOR.sendDouble(this.window);
        return scale > 0.0 ? scale : 1.0;
    }

    public void setViewLayer(final MemorySegment layer) {
        SET_WANTS_LAYER.send(this.view, true);
        SET_LAYER.send(this.view, layer);
    }

    public void clearViewLayer() {
        SET_LAYER.send(this.view, MemorySegment.NULL);
        SET_WANTS_LAYER.send(this.view, false);
    }
}