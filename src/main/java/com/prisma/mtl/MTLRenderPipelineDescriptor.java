package com.prisma.mtl;

import com.prisma.objc.AutoreleasePool;
import com.prisma.objc.Msg;
import com.prisma.objc.ObjC;

import java.lang.foreign.MemorySegment;

import static java.lang.foreign.ValueLayout.*;

public final class MTLRenderPipelineDescriptor implements AutoCloseable {
    private static final MemorySegment CLS = ObjC.clazz("MTLRenderPipelineDescriptor");
    private static final Msg NEW = Msg.of("new", ADDRESS);
    private static final Msg SET_VERTEX_FUNCTION = Msg.ofVoid("setVertexFunction:", ADDRESS);
    private static final Msg SET_FRAGMENT_FUNCTION = Msg.ofVoid("setFragmentFunction:", ADDRESS);
    private static final Msg SET_VERTEX_DESCRIPTOR = Msg.ofVoid("setVertexDescriptor:", ADDRESS);
    private static final Msg COLOR_ATTACHMENTS = Msg.of("colorAttachments", ADDRESS);
    private static final Msg OBJECT_AT_INDEXED_SUBSCRIPT = Msg.of("objectAtIndexedSubscript:", ADDRESS, JAVA_LONG);
    private static final Msg SET_PIXEL_FORMAT = Msg.ofVoid("setPixelFormat:", JAVA_LONG);
    private static final Msg SET_DEPTH_ATTACHMENT_PIXEL_FORMAT = Msg.ofVoid("setDepthAttachmentPixelFormat:", JAVA_LONG);
    private static final Msg SET_STENCIL_ATTACHMENT_PIXEL_FORMAT = Msg.ofVoid("setStencilAttachmentPixelFormat:", JAVA_LONG);
    private static final Msg SET_WRITE_MASK = Msg.ofVoid("setWriteMask:", JAVA_LONG);
    private static final Msg SET_BLENDING_ENABLED = Msg.ofVoid("setBlendingEnabled:", JAVA_BOOLEAN);
    private static final Msg SET_SOURCE_RGB_BLEND_FACTOR = Msg.ofVoid("setSourceRGBBlendFactor:", JAVA_LONG);
    private static final Msg SET_DESTINATION_RGB_BLEND_FACTOR = Msg.ofVoid("setDestinationRGBBlendFactor:", JAVA_LONG);
    private static final Msg SET_RGB_BLEND_OPERATION = Msg.ofVoid("setRgbBlendOperation:", JAVA_LONG);
    private static final Msg SET_SOURCE_ALPHA_BLEND_FACTOR = Msg.ofVoid("setSourceAlphaBlendFactor:", JAVA_LONG);
    private static final Msg SET_DESTINATION_ALPHA_BLEND_FACTOR = Msg.ofVoid("setDestinationAlphaBlendFactor:", JAVA_LONG);
    private static final Msg SET_ALPHA_BLEND_OPERATION = Msg.ofVoid("setAlphaBlendOperation:", JAVA_LONG);

    private final MemorySegment handle;
    private boolean closed;

    public MTLRenderPipelineDescriptor() {
        this.handle = NEW.sendPtr(CLS);
    }

    public MemorySegment handle() {
        return this.handle;
    }

    public void setCompiledFunctions(final MemorySegment vertexFunction, final MemorySegment fragmentFunction) {
        SET_VERTEX_FUNCTION.send(this.handle, ObjC.orNil(vertexFunction));
        SET_FRAGMENT_FUNCTION.send(this.handle, ObjC.orNil(fragmentFunction));
    }

    public void setVertexDescriptor(final MTLVertexDescriptor vertexDescriptor) {
        SET_VERTEX_DESCRIPTOR.send(this.handle, vertexDescriptor.handle());
    }

    public void setColorAttachmentFormat(final long index, final MTLPixelFormat format) {
        setColorAttachmentFormat(index, format.value);
    }

    public void setColorAttachmentFormat(final long index, final long pixelFormat) {
        try (AutoreleasePool _ = AutoreleasePool.push()) {
            SET_PIXEL_FORMAT.send(colorAttachment(index), pixelFormat);
        }
    }

    public void setDepthStencilFormats(final MTLPixelFormat depthFormat, final MTLPixelFormat stencilFormat) {
        setDepthStencilFormats(depthFormat.value, stencilFormat.value);
    }

    public void setDepthStencilFormats(final long depthFormat, final long stencilFormat) {
        SET_DEPTH_ATTACHMENT_PIXEL_FORMAT.send(this.handle, depthFormat);
        SET_STENCIL_ATTACHMENT_PIXEL_FORMAT.send(this.handle, stencilFormat);
    }

    public void setBlendState(
            final long index,
            final MTLBlendFactor sourceColorBlendFactor,
            final MTLBlendFactor destinationColorBlendFactor,
            final MTLBlendOperation colorBlendOperation,
            final MTLBlendFactor sourceAlphaBlendFactor,
            final MTLBlendFactor destinationAlphaBlendFactor,
            final MTLBlendOperation alphaBlendOperation,
            final long writeMask
    ) {
        try (AutoreleasePool _ = AutoreleasePool.push()) {
            MemorySegment attachment = colorAttachment(index);
            SET_WRITE_MASK.send(attachment, writeMask);
            SET_BLENDING_ENABLED.send(attachment, true);
            SET_SOURCE_RGB_BLEND_FACTOR.send(attachment, sourceColorBlendFactor.value);
            SET_DESTINATION_RGB_BLEND_FACTOR.send(attachment, destinationColorBlendFactor.value);
            SET_RGB_BLEND_OPERATION.send(attachment, colorBlendOperation.value);
            SET_SOURCE_ALPHA_BLEND_FACTOR.send(attachment, sourceAlphaBlendFactor.value);
            SET_DESTINATION_ALPHA_BLEND_FACTOR.send(attachment, destinationAlphaBlendFactor.value);
            SET_ALPHA_BLEND_OPERATION.send(attachment, alphaBlendOperation.value);
        }
    }

    public void disableBlending(final long index, final long writeMask) {
        try (AutoreleasePool _ = AutoreleasePool.push()) {
            MemorySegment attachment = colorAttachment(index);
            SET_WRITE_MASK.send(attachment, writeMask);
            SET_BLENDING_ENABLED.send(attachment, false);
        }
    }

    private MemorySegment colorAttachment(final long index) {
        return OBJECT_AT_INDEXED_SUBSCRIPT.sendPtr(COLOR_ATTACHMENTS.sendPtr(this.handle), index);
    }

    @Override
    public void close() {
        if (!this.closed) {
            this.closed = true;
            ObjC.release(this.handle);
        }
    }
}
