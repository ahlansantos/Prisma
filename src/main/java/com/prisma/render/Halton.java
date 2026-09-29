package com.prisma.render;

public class Halton {
    public static double get(int index, int base) {
        double f = 1;
        double r = 0;
        int current = index;
        while (current > 0) {
            f = f / base;
            r = r + f * (current % base);
            current = (int) Math.floor(current / base);
        }
        return r;
    }
}
