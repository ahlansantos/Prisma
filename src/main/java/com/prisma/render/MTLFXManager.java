package com.prisma.render;

import com.prisma.mtl.MTLDevice;
import com.prisma.objc.ObjC;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.ValueLayout;

/**
 * MetalFX Spatial Scaler — cheaper and simpler than Temporal.
 * No motion vectors, no jitter, no depth needed. Very close to EASU quality
 * at near-zero overhead. Much better fit for a real-time shader pack.
 */
public class MTLFXManager {
    private static final SymbolLookup METALFX;
    private static final MemorySegment CLS_MTLFXSpatialScalerDescriptor;

    private static final MemorySegment SEL_newSpatialScalerWithDevice;
    private static final MemorySegment SEL_setColorTextureFormat;
    private static final MemorySegment SEL_setOutputTextureFormat;
    private static final MemorySegment SEL_setInputWidth;
    private static final MemorySegment SEL_setInputHeight;
    private static final MemorySegment SEL_setOutputWidth;
    private static final MemorySegment SEL_setOutputHeight;
    private static final MemorySegment SEL_setColorTexture;
    private static final MemorySegment SEL_setOutputTexture;
    private static final MemorySegment SEL_encodeToCommandBuffer;
    private static final MemorySegment SEL_setColorProcessingMode;

    private static final MethodHandle MSG_PTR_ARG;
    private static final MethodHandle MSG_VOID_PTR_ARG;
    private static final MethodHandle MSG_INT_ARG;

    static {
        try {
            METALFX = SymbolLookup.libraryLookup("/System/Library/Frameworks/MetalFX.framework/MetalFX", Arena.global());
            CLS_MTLFXSpatialScalerDescriptor = ObjC.clazz("MTLFXSpatialScalerDescriptor");

            SEL_newSpatialScalerWithDevice   = ObjC.selector("newSpatialScalerWithDevice:");
            SEL_setColorTextureFormat        = ObjC.selector("setColorTextureFormat:");
            SEL_setOutputTextureFormat       = ObjC.selector("setOutputTextureFormat:");
            SEL_setInputWidth                = ObjC.selector("setInputWidth:");
            SEL_setInputHeight               = ObjC.selector("setInputHeight:");
            SEL_setOutputWidth               = ObjC.selector("setOutputWidth:");
            SEL_setOutputHeight              = ObjC.selector("setOutputHeight:");
            SEL_setColorTexture              = ObjC.selector("setColorTexture:");
            SEL_setOutputTexture             = ObjC.selector("setOutputTexture:");
            SEL_encodeToCommandBuffer        = ObjC.selector("encodeToCommandBuffer:");
            SEL_setColorProcessingMode       = ObjC.selector("setColorProcessingMode:");

            MSG_PTR_ARG      = ObjC.msgSend(FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            MSG_VOID_PTR_ARG = ObjC.msgSend(FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            MSG_INT_ARG      = ObjC.msgSend(FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
        } catch (Throwable t) {
            throw new RuntimeException("Failed to load MetalFX", t);
        }
    }

    private MemorySegment scaler = MemorySegment.NULL;
    private long cachedInputW  = 0;
    private long cachedInputH  = 0;
    private long cachedOutputW = 0;
    private long cachedOutputH = 0;

    public void ensureScaler(MetalDevice mtl, long inputWidth, long inputHeight, long outputWidth, long outputHeight) {
        if (!ObjC.isNil(scaler)
                && cachedInputW  == inputWidth  && cachedInputH  == inputHeight
                && cachedOutputW == outputWidth && cachedOutputH == outputHeight) {
            return;
        }
        if (!ObjC.isNil(scaler)) {
            ObjC.release(scaler);
            scaler = MemorySegment.NULL;
        }
        cachedInputW  = inputWidth;
        cachedInputH  = inputHeight;
        cachedOutputW = outputWidth;
        cachedOutputH = outputHeight;

        try {
            MemorySegment desc = (MemorySegment) MSG_PTR_ARG.invokeExact(
                    CLS_MTLFXSpatialScalerDescriptor, ObjC.selector("new"), MemorySegment.NULL);

            MSG_INT_ARG.invokeExact(desc, SEL_setColorTextureFormat,  115L); // MTLPixelFormatRGBA16Float
            MSG_INT_ARG.invokeExact(desc, SEL_setOutputTextureFormat, 115L); // MTLPixelFormatRGBA16Float
            // colorProcessingMode = 1 → MTLFXSpatialScalerColorProcessingModeLinear (linear [0,1] space)
            MSG_INT_ARG.invokeExact(desc, SEL_setColorProcessingMode,   1L);
            MSG_INT_ARG.invokeExact(desc, SEL_setInputWidth,   inputWidth);
            MSG_INT_ARG.invokeExact(desc, SEL_setInputHeight,  inputHeight);
            MSG_INT_ARG.invokeExact(desc, SEL_setOutputWidth,  outputWidth);
            MSG_INT_ARG.invokeExact(desc, SEL_setOutputHeight, outputHeight);

            this.scaler = (MemorySegment) MSG_PTR_ARG.invokeExact(
                    desc, SEL_newSpatialScalerWithDevice, mtl.metalDevice().handle());
            ObjC.release(desc);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    /**
     * Encode a spatial upscale pass. Depth, motion and jitter are NOT used.
     */
    public void encode(MemorySegment commandBuffer, MemorySegment inColor, MemorySegment outColor) {
        if (ObjC.isNil(this.scaler)) return;
        try {
            MSG_VOID_PTR_ARG.invokeExact(this.scaler, SEL_setColorTexture,  inColor);
            MSG_VOID_PTR_ARG.invokeExact(this.scaler, SEL_setOutputTexture, outColor);
            MSG_VOID_PTR_ARG.invokeExact(this.scaler, SEL_encodeToCommandBuffer, commandBuffer);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
