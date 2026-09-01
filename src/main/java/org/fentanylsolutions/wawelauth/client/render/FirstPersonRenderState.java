package org.fentanylsolutions.wawelauth.client.render;

/** Transient compatibility state for the current first-person arm render call. */
public final class FirstPersonRenderState {

    private static int rightSleeveSuppressionDepth;

    private FirstPersonRenderState() {}

    public static void pushRightSleeveSuppression() {
        rightSleeveSuppressionDepth++;
    }

    public static void popRightSleeveSuppression() {
        if (rightSleeveSuppressionDepth > 0) {
            rightSleeveSuppressionDepth--;
        }
    }

    public static boolean isRightSleeveSuppressed() {
        return rightSleeveSuppressionDepth > 0;
    }
}
