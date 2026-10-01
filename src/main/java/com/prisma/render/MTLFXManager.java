package com.prisma.render;

import com.prisma.mtl.MTLDevice;
import com.prisma.objc.ObjC;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.ValueLayout;

public class MTLFXManager {
    private static final SymbolLookup METALFX;
    private static final MemorySegment CLS_MTLFXTemporalScalerDescriptor;

    private static final MemorySegment SEL_newTemporalScalerWithDevice;
    private static final MemorySegment SEL_setColorTextureFormat;
    private static final MemorySegment SEL_setDepthTextureFormat;
    private static final MemorySegment SEL_setMotionTextureFormat;
    private static final MemorySegment SEL_setOutputTextureFormat;
    private static final MemorySegment SEL_setInputWidth;
    private static final MemorySegment SEL_setInputHeight;
    private static final MemorySegment SEL_setOutputWidth;
    private static final MemorySegment SEL_setOutputHeight;
    private static final MemorySegment SEL_setColorTexture;
    private static final MemorySegment SEL_setDepthTexture;
    private static final MemorySegment SEL_setMotionTexture;
    private static final MemorySegment SEL_setOutputTexture;
    private static final MemorySegment SEL_encodeToCommandBuffer;
    private static final MemorySegment SEL_setJitterOffsetX;
    private static final MemorySegment SEL_setJitterOffsetY;
    private static final MemorySegment SEL_setReset;
    private static final MemorySegment SEL_setMotionVectorScaleX;
    private static final MemorySegment SEL_setMotionVectorScaleY;

    private static final MethodHandle MSG_PTR_ARG;
    private static final MethodHandle MSG_VOID_PTR_ARG;
    private static final MethodHandle MSG_INT_ARG;
    private static final MethodHandle MSG_FLOAT_ARG;
    private static final MethodHandle MSG_BOOL_ARG;

    static {
        try {
            METALFX = SymbolLookup.libraryLookup("/System/Library/Frameworks/MetalFX.framework/MetalFX", Arena.global());
            CLS_MTLFXTemporalScalerDescriptor = ObjC.clazz("MTLFXTemporalScalerDescriptor");

            SEL_newTemporalScalerWithDevice   = ObjC.selector("newTemporalScalerWithDevice:");
            SEL_setColorTextureFormat        = ObjC.selector("setColorTextureFormat:");
            SEL_setDepthTextureFormat        = ObjC.selector("setDepthTextureFormat:");
            SEL_setMotionTextureFormat       = ObjC.selector("setMotionTextureFormat:");
            SEL_setOutputTextureFormat       = ObjC.selector("setOutputTextureFormat:");
            SEL_setInputWidth                = ObjC.selector("setInputWidth:");
            SEL_setInputHeight               = ObjC.selector("setInputHeight:");
            SEL_setOutputWidth               = ObjC.selector("setOutputWidth:");
            SEL_setOutputHeight              = ObjC.selector("setOutputHeight:");
            
            SEL_setColorTexture              = ObjC.selector("setColorTexture:");
            SEL_setDepthTexture              = ObjC.selector("setDepthTexture:");
            SEL_setMotionTexture             = ObjC.selector("setMotionTexture:");
            SEL_setOutputTexture             = ObjC.selector("setOutputTexture:");
            SEL_encodeToCommandBuffer        = ObjC.selector("encodeToCommandBuffer:");
            
            SEL_setJitterOffsetX             = ObjC.selector("setJitterOffsetX:");
            SEL_setJitterOffsetY             = ObjC.selector("setJitterOffsetY:");
            SEL_setReset                     = ObjC.selector("setReset:");
            SEL_setMotionVectorScaleX        = ObjC.selector("setMotionVectorScaleX:");
            SEL_setMotionVectorScaleY        = ObjC.selector("setMotionVectorScaleY:");

            MSG_PTR_ARG      = ObjC.msgSend(FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            MSG_VOID_PTR_ARG = ObjC.msgSend(FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            MSG_INT_ARG      = ObjC.msgSend(FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
            MSG_FLOAT_ARG    = ObjC.msgSend(FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_FLOAT));
            MSG_BOOL_ARG     = ObjC.msgSend(FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_BOOLEAN));
        } catch (Throwable t) {
            throw new RuntimeException("Failed to load MetalFX", t);
        }
    }

    private MemorySegment scaler = MemorySegment.NULL;
    private long cachedInputW  = 0;
    private long cachedInputH  = 0;
    private long cachedOutputW = 0;
    private long cachedOutputH = 0;
    private boolean isFirstFrame = true;

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
        isFirstFrame = true;

        try {
            MemorySegment desc = (MemorySegment) MSG_PTR_ARG.invokeExact(
                    CLS_MTLFXTemporalScalerDescriptor, ObjC.selector("new"), MemorySegment.NULL);

            MSG_INT_ARG.invokeExact(desc, SEL_setColorTextureFormat,  115L); // RGBA16Float
            MSG_INT_ARG.invokeExact(desc, SEL_setDepthTextureFormat,  255L); // Depth32Float
            MSG_INT_ARG.invokeExact(desc, SEL_setMotionTextureFormat, 115L); // RGBA16Float (we use this for velocity)
            MSG_INT_ARG.invokeExact(desc, SEL_setOutputTextureFormat, 115L); // RGBA16Float
            
            MSG_INT_ARG.invokeExact(desc, SEL_setInputWidth,   inputWidth);
            MSG_INT_ARG.invokeExact(desc, SEL_setInputHeight,  inputHeight);
            MSG_INT_ARG.invokeExact(desc, SEL_setOutputWidth,  outputWidth);
            MSG_INT_ARG.invokeExact(desc, SEL_setOutputHeight, outputHeight);

            this.scaler = (MemorySegment) MSG_PTR_ARG.invokeExact(
                    desc, SEL_newTemporalScalerWithDevice, mtl.metalDevice().handle());
            ObjC.release(desc);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public void encodeTemporal(MemorySegment commandBuffer, MemorySegment inColor, MemorySegment inDepth, MemorySegment inMotion, MemorySegment outColor) {
        if (ObjC.isNil(this.scaler)) return;
        try {
            MSG_VOID_PTR_ARG.invokeExact(this.scaler, SEL_setColorTexture,  inColor);
            MSG_VOID_PTR_ARG.invokeExact(this.scaler, SEL_setDepthTexture,  inDepth);
            MSG_VOID_PTR_ARG.invokeExact(this.scaler, SEL_setMotionTexture, inMotion);
            MSG_VOID_PTR_ARG.invokeExact(this.scaler, SEL_setOutputTexture, outColor);
            
            MSG_FLOAT_ARG.invokeExact(this.scaler, SEL_setJitterOffsetX, 0.0f);
            MSG_FLOAT_ARG.invokeExact(this.scaler, SEL_setJitterOffsetY, 0.0f);
            
            MSG_FLOAT_ARG.invokeExact(this.scaler, SEL_setMotionVectorScaleX, 1.0f);
            MSG_FLOAT_ARG.invokeExact(this.scaler, SEL_setMotionVectorScaleY, 1.0f);
            
            MSG_BOOL_ARG.invokeExact(this.scaler, SEL_setReset, isFirstFrame);
            isFirstFrame = false;
            
            MSG_VOID_PTR_ARG.invokeExact(this.scaler, SEL_encodeToCommandBuffer, commandBuffer);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
