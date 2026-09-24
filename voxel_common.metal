ld not load the following classes:
 *  net.fabricmc.api.EnvType
 *  net.fabricmc.api.Environment
 *  org.joml.Matrix4fc
 *  org.joml.Vector4fc
 *  org.jspecify.annotations.Nullable
 *  org.lwjgl.system.MemoryStack
 */
package com.prisma.mtl;

import com.prisma.config.PrismaConfig;
import com.prisma.mtl.CAMetalDrawable;
import com.prisma.mtl.CAMetalLayer;
import com.prisma.mtl.MTLBlendFactor;
import com.prisma.mtl.MTLBlendOperation;
import com.prisma.mtl.MTLColorWriteMask;
import com.prisma.mtl.MTLCommandBuffer;
import com.prisma.mtl.MTLCompareFunction;
import com.prisma.mtl.MTLDepthStencilDescriptor;
import com.prisma.mtl.MTLDevice;
import com.prisma.mtl.MTLFence;
import com.prisma.mtl.MTLPixelFormat;
import com.prisma.mtl.MTLPrimitiveType;
import com.prisma.mtl.MTLRenderCommandEncoder;
import com.prisma.mtl.MTLRenderPassDescriptor;
import com.prisma.mtl.MTLRenderPipelineDescriptor;
import com.prisma.mtl.MTLRenderStages;
import com.prisma.mtl.MTLSamplerAddressMode;
import com.prisma.mtl.MTLSamplerDescriptor;
import com.prisma.mtl.MTLSamplerMinMagFilter;
import com.prisma.mtl.MTLSamplerMipFilter;
import com.prisma.mtl.MTLTexture;
import com.prisma.objc.AutoreleasePool;
import com.prisma.objc.ObjC;
import com.prisma.voxel.PointLight;
import com.prisma.voxel.VoxelGridManager;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.joml.Matrix4fc;
import org.joml.Vector4fc;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;

@Environment(value=EnvType.CLIENT)
public final class MTLBuiltinPipelines {
    private static final String PRESENT_MSL = "#include <metal_stdlib>\nusing namespace metal;\n\nstruct PresentVertexOut {\n  float4 position [[position]];\n  float2 uv;\n};\n\nvertex PresentVertexOut prisma_present_vs(uint vertexId [[vertex_id]]) {\n  const float2 positions[3] = {\n    float2(-1.0,  1.0),\n    float2( 3.0,  1.0),\n    float2(-1.0, -3.0)\n  };\n\n  const float2 uvs[3] = {\n    float2(0.0,  1.0),\n    float2(2.0,  1.0),\n    float2(0.0, -1.0)\n  };\n\n  PresentVertexOut out;\n  out.position = float4(positions[vertexId], 0.0, 1.0);\n  out.uv = uvs[vertexId];\n  return out;\n}\n\nfragment float4 prisma_present_fs(\n  PresentVertexOut in [[stage_in]],\n  texture2d<float> colorTex [[texture(0)]],\n  sampler smp [[sampler(0)]]\n) {\n  return float4(colorTex.sample(smp, in.uv).rgb, 1.0f);\n}\n