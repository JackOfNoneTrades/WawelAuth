package org.fentanylsolutions.wawelauth.wawelcore.data;

/**
 * Skin model type as defined by the Yggdrasil specification.
 * <p>
 * CLASSIC = standard 4px arm width (called "default" in the Yggdrasil textures property,
 * or omitted entirely: absent metadata implies classic).
 * <p>
 * SLIM = 3px arm width (called "slim" in the Yggdrasil textures property).
 */
public enum SkinModel {

    CLASSIC("default"),
    SLIM("slim");

    private final String yggdrasilName;

    SkinModel(String yggdrasilName) {
        this.yggdrasilName = yggdrasilName;
    }

    /**
     * The value used in the Yggdrasil textures property metadata.model field.
     * "default" for CLASSIC, "slim" for SLIM.
     */
    public String getYggdrasilName() {
        return yggdrasilName;
    }

    /**
     * Parse from the Yggdrasil metadata model string.
     * Returns CLASSIC for null, empty, "default", or any unrecognized value.
     */
    public static SkinModel fromYggdrasil(String model) {
        if ("slim".equalsIgnoreCase(model)) return SLIM;
        return CLASSIC;
    }
}
