package org.fentanylsolutions.wawelauth.client.render.skinlayers.volumed.voxels;

public class Mth {

    public static final float PI = 3.1415927F;

    public static final float HALF_PI = 1.5707964F;

    public static final float TWO_PI = 6.2831855F;

    public static final float DEG_TO_RAD = 0.017453292F;

    public static final float RAD_TO_DEG = 57.295776F;

    public static final float EPSILON = 1.0E-5F;

    public static final float SQRT_OF_TWO = sqrt(2.0F);

    public static float sqrt(float f) {
        return (float) Math.sqrt(f);
    }

    public static int floor(float f) {
        int i = (int) f;
        return (f < i) ? (i - 1) : i;
    }

    public static int floor(double d) {
        int i = (int) d;
        return (d < i) ? (i - 1) : i;
    }

    public static long lfloor(double d) {
        long l = (long) d;
        return (d < l) ? (l - 1L) : l;
    }

    public static float abs(float f) {
        return Math.abs(f);
    }

    public static int abs(int i) {
        return Math.abs(i);
    }

    public static int ceil(float f) {
        int i = (int) f;
        return (f > i) ? (i + 1) : i;
    }

    public static int clamp(int i, int j, int k) {
        if (i < j) return j;
        if (i > k) return k;
        return i;
    }

    public static float clamp(float f, float g, float h) {
        if (f < g) return g;
        if (f > h) return h;
        return f;
    }

    public static double clamp(double d, double e, double f) {
        if (d < e) return e;
        if (d > f) return f;
        return d;
    }

    public static float lerp(float f, float g, float h) {
        return g + f * (h - g);
    }

    public static double lerp(double d, double e, double f) {
        return e + d * (f - e);
    }

    public static float cos(float deg) {
        return (float) Math.cos(deg);
    }

    public static float sin(float deg) {
        return (float) Math.sin(deg);
    }

}
