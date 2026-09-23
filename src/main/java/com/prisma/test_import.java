package com.prisma;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import java.lang.reflect.Method;
public class test_import {
    public static void printMethods() {
        for (Method m : EntityRenderer.class.getDeclaredMethods()) {
            System.out.println(m.getName());
            for (Class<?> p : m.getParameterTypes()) {
                System.out.println("  " + p.getName());
            }
        }
    }
}
