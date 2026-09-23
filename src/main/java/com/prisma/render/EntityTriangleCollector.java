package com.prisma.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import org.joml.Vector3f;
import org.joml.Vector2f;

public class EntityTriangleCollector implements VertexConsumer {
    
    public static class Vertex {
        public final Vector3f pos = new Vector3f();
        public final Vector2f uv = new Vector2f();
        public final Vector3f normal = new Vector3f();
        
        public Vertex copy() {
            Vertex v = new Vertex();
            v.pos.set(this.pos);
            v.uv.set(this.uv);
            v.normal.set(this.normal);
            return v;
        }
    }
    
    private final List<Vertex> vertices = new ArrayList<>();
    private final Vertex currentVertex = new Vertex();
    
    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        currentVertex.pos.set(x, y, z);
        vertices.add(currentVertex.copy());
        return this;
    }

    @Override
    public VertexConsumer setColor(int r, int g, int b, int a) {
        return this;
    }

    @Override
    public VertexConsumer setColor(int argb) {
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        currentVertex.uv.set(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        currentVertex.normal.set(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer setLineWidth(float f) {
        return this;
    }
    
    public List<Vertex> getVertices() {
        return vertices;
    }
    
    public void clear() {
        vertices.clear();
    }
}
