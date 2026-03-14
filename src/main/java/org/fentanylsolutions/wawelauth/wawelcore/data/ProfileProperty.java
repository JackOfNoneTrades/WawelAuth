package org.fentanylsolutions.wawelauth.wawelcore.data;

/**
 * A property attached to a serialized profile or user, as defined by the Yggdrasil spec.
 * <p>
 * Serialized form:
 *
 * <pre>
 * {"name": "textures", "value": "base64...", "signature": "base64..."}
 * </pre>
 * <p>
 * Known profile property keys:
 * - "textures": base64-encoded {@link TextureData} JSON, may include signature
 * - "uploadableTextures": comma-separated list of allowed upload types ("skin", "skin,cape")
 * (authlib-injector extension)
 * <p>
 * Known user property keys:
 * - "preferredLanguage": e.g. "en", "zh_CN"
 * <p>
 * Signature is a base64-encoded SHA1withRSA signature over the property value,
 * using the server's private key. Only included when the endpoint requires it
 * (e.g. hasJoined, profile query with unsigned=false).
 */
public class ProfileProperty {

    private String name;
    private String value;
    private String signature;

    public ProfileProperty() {}

    public ProfileProperty(String name, String value) {
        this.name = name;
        this.value = value;
    }

    public ProfileProperty(String name, String value, String signature) {
        this.name = name;
        this.value = value;
        this.signature = signature;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    /**
     * Base64-encoded SHA1withRSA signature over value. Null if unsigned.
     */
    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public boolean hasSignature() {
        return signature != null && !signature.isEmpty();
    }
}
