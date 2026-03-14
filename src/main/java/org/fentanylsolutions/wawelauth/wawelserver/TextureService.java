package org.fentanylsolutions.wawelauth.wawelserver;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.UUID;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.fentanylsolutions.wawelauth.Config;
import org.fentanylsolutions.wawelauth.WawelAuth;
import org.fentanylsolutions.wawelauth.api.SkinImageUtil;
import org.fentanylsolutions.wawelauth.wawelcore.config.ServerConfig;
import org.fentanylsolutions.wawelauth.wawelcore.data.SkinModel;
import org.fentanylsolutions.wawelauth.wawelcore.data.TextureType;
import org.fentanylsolutions.wawelauth.wawelcore.data.UuidUtil;
import org.fentanylsolutions.wawelauth.wawelcore.data.WawelProfile;
import org.fentanylsolutions.wawelauth.wawelcore.data.WawelToken;
import org.fentanylsolutions.wawelauth.wawelcore.storage.ProfileDAO;
import org.fentanylsolutions.wawelauth.wawelcore.storage.TokenDAO;
import org.fentanylsolutions.wawelauth.wawelnet.NetException;
import org.fentanylsolutions.wawelauth.wawelnet.RequestContext;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;

/**
 * Handles texture upload (PUT) and delete (DELETE) for
 * /api/user/profile/{uuid}/{textureType}.
 */
public class TextureService {

    private final TokenDAO tokenDAO;
    private final ProfileDAO profileDAO;
    private final TextureFileStore textureFileStore;

    public TextureService(TokenDAO tokenDAO, ProfileDAO profileDAO, TextureFileStore textureFileStore) {
        this.tokenDAO = tokenDAO;
        this.profileDAO = profileDAO;
        this.textureFileStore = textureFileStore;
    }

    /**
     * PUT /api/user/profile/{uuid}/{textureType}
     * <p>
     * Uploads a texture. Request is multipart/form-data with:
     * - "file": the PNG image
     * - "model": (optional, SKIN only) "slim" or "" for classic
     * <p>
     * Authenticated via Bearer token.
     */
    public Object uploadTexture(RequestContext ctx) {
        WawelProfile profile = authenticateAndResolveProfile(ctx);
        TextureType textureType = resolveTextureType(ctx);

        // Validate the profile can upload this type
        if (!profile.canUpload(textureType)) {
            throw NetException.forbidden("Profile is not allowed to upload " + textureType.getApiName() + " textures.");
        }

        // Parse multipart body
        FullHttpRequest request = ctx.getRequest();

        // Netty 4.0 doesn't handle quoted boundary values in Content-Type
        // (e.g. boundary="value"), which some clients send per RFC 2046.
        // Strip the quotes so the multipart decoder can find the boundary.
        String contentType = request.headers()
            .get(HttpHeaders.Names.CONTENT_TYPE);
        if (contentType != null) {
            String fixed = contentType.replaceAll("boundary=\"([^\"]*)\"", "boundary=$1");
            if (!fixed.equals(contentType)) {
                request.headers()
                    .set(HttpHeaders.Names.CONTENT_TYPE, fixed);
            }
        }

        WawelAuth.debug(
            "Upload content-type: " + request.headers()
                .get(HttpHeaders.Names.CONTENT_TYPE)
                + ", body length: "
                + request.content()
                    .readableBytes());

        HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(new DefaultHttpDataFactory(false), request);

        try {
            byte[] fileData = null;
            String model = null;

            for (InterfaceHttpData data : decoder.getBodyHttpDatas()) {
                WawelAuth.debug("Multipart part: type=" + data.getHttpDataType() + " name=" + data.getName());
                if (data.getHttpDataType() == InterfaceHttpData.HttpDataType.FileUpload) {
                    FileUpload upload = (FileUpload) data;
                    fileData = upload.get();
                } else if (data.getHttpDataType() == InterfaceHttpData.HttpDataType.Attribute) {
                    if ("model".equals(data.getName())) {
                        model = ((io.netty.handler.codec.http.multipart.Attribute) data).getValue();
                    }
                }
            }

            if (fileData == null || fileData.length == 0) {
                throw NetException.illegalArgument("No file uploaded.");
            }

            ServerConfig.Textures texConfig = Config.server()
                .getTextures();

            if (isGif(fileData)) {
                // ---- GIF path (animated capes only) ----
                if (textureType != TextureType.CAPE) {
                    throw NetException.illegalArgument("Only capes can be animated GIFs.");
                }
                if (!texConfig.isAllowAnimatedCapes()) {
                    throw NetException.forbidden("Animated capes are not enabled on this server.");
                }
                if (fileData.length > texConfig.getMaxAnimatedCapeFileSizeBytes()) {
                    throw NetException.illegalArgument(
                        "GIF file too large. Maximum size: " + texConfig.getMaxAnimatedCapeFileSizeBytes() + " bytes.");
                }

                int[] gifInfo = validateGifCape(fileData, texConfig);
                int width = gifInfo[0];
                int height = gifInfo[1];

                // Validate cape shape (per frame dimensions)
                validateTextureShape(TextureType.CAPE, width, height);

                // Enforce max cape dimension bounds (per frame)
                if (width > texConfig.getMaxCapeWidth() || height > texConfig.getMaxCapeHeight()) {
                    throw NetException.illegalArgument(
                        "Cape frame dimensions " + width
                            + "x"
                            + height
                            + " exceed maximum "
                            + texConfig.getMaxCapeWidth()
                            + "x"
                            + texConfig.getMaxCapeHeight()
                            + ".");
                }

                // Hash the raw GIF bytes (no re-encoding for GIFs)
                String hash = sha256Hex(fileData);

                try {
                    textureFileStore.writeGif(hash, fileData);
                } catch (IOException e) {
                    WawelAuth.LOG.warn("Failed to write GIF texture file {}: {}", hash, e.getMessage());
                    throw new RuntimeException("Failed to store texture file");
                }

                profile.setTextureHash(textureType, hash);
                profile.setCapeAnimated(true);
                profileDAO.update(profile);

                WawelAuth.debug(
                    "Animated cape uploaded for profile " + profile
                        .getName() + " (hash: " + hash + ", frames: " + gifInfo[2] + ")");
            } else if (isPng(fileData)) {
                // ---- PNG path (existing behavior) ----
                if (fileData.length > texConfig.getMaxFileSizeBytes()) {
                    throw NetException.illegalArgument(
                        "File too large. Maximum size: " + texConfig.getMaxFileSizeBytes() + " bytes.");
                }

                int[] dims = readPngDimensions(fileData);
                if (dims == null) {
                    throw NetException.illegalArgument("Could not read PNG dimensions (corrupt IHDR).");
                }
                int width = dims[0];
                int height = dims[1];
                if (width <= 0 || height <= 0) {
                    throw NetException.illegalArgument("Invalid image dimensions.");
                }

                validateTextureShape(textureType, width, height);

                int maxW, maxH;
                if (textureType == TextureType.CAPE) {
                    maxW = texConfig.getMaxCapeWidth();
                    maxH = texConfig.getMaxCapeHeight();
                } else {
                    maxW = texConfig.getMaxSkinWidth();
                    maxH = texConfig.getMaxSkinHeight();
                }
                if (width > maxW || height > maxH) {
                    throw NetException.illegalArgument(
                        "Image dimensions " + width + "x" + height + " exceed maximum " + maxW + "x" + maxH + ".");
                }

                byte[] cleanData;
                if (textureType == TextureType.CAPE && (width % 22 == 0)
                    && (height % 17 == 0)
                    && !((width % 64 == 0) && (height % 32 == 0))) {
                    cleanData = padAndReEncodeCape(fileData, width, height);
                } else if (textureType == TextureType.SKIN && height * 2 == width) {
                    cleanData = convertLegacySkin(fileData);
                } else {
                    cleanData = reEncodePng(fileData);
                }

                String hash = sha256Hex(cleanData);

                try {
                    textureFileStore.write(hash, cleanData);
                } catch (IOException e) {
                    WawelAuth.LOG.warn("Failed to write texture file {}: {}", hash, e.getMessage());
                    throw new RuntimeException("Failed to store texture file");
                }

                profile.setTextureHash(textureType, hash);
                if (textureType == TextureType.SKIN) {
                    profile.setSkinModel(parseSkinModel(model));
                }
                if (textureType == TextureType.CAPE) {
                    profile.setCapeAnimated(false);
                }
                profileDAO.update(profile);

                WawelAuth.debug(
                    "Texture " + textureType
                        .getApiName() + " uploaded for profile " + profile.getName() + " (hash: " + hash + ")");
            } else {
                throw NetException.illegalArgument("File is not a valid PNG or GIF image.");
            }

        } catch (IOException e) {
            throw new RuntimeException("Failed to read multipart data", e);
        } finally {
            decoder.destroy();
        }

        return null; // 204
    }

    /**
     * DELETE /api/user/profile/{uuid}/{textureType}
     * <p>
     * Deletes a texture from a profile. Authenticated via Bearer token.
     */
    public Object deleteTexture(RequestContext ctx) {
        WawelProfile profile = authenticateAndResolveProfile(ctx);
        TextureType textureType = resolveTextureType(ctx);

        String oldHash = profile.getTextureHash(textureType);

        profile.setTextureHash(textureType, null);
        if (textureType == TextureType.SKIN) {
            profile.setSkinModel(SkinModel.CLASSIC);
        }
        profileDAO.update(profile);

        // Clean up the file if no other profile references this hash.
        if (oldHash != null && !profileDAO.isTextureHashReferenced(oldHash)) {
            textureFileStore.delete(oldHash);
        }

        WawelAuth.debug("Texture " + textureType.getApiName() + " deleted for profile " + profile.getName());

        return null; // 204
    }

    /**
     * Authenticates the request via Bearer token and resolves the profile
     * from the path parameter. Validates ownership.
     */
    private WawelProfile authenticateAndResolveProfile(RequestContext ctx) {
        String bearerToken = ctx.getBearerToken();
        if (bearerToken == null) {
            throw NetException.unauthorized("Missing Authorization header.");
        }

        WawelToken token = tokenDAO.findByAccessToken(bearerToken);
        if (token == null || !token.isUsable()) {
            throw NetException.unauthorized("Invalid token.");
        }

        String uuidStr = ctx.getPathParam("uuid");
        UUID profileUuid;
        try {
            profileUuid = UuidUtil.fromUnsigned(uuidStr);
        } catch (IllegalArgumentException e) {
            throw NetException.illegalArgument("Invalid UUID format.");
        }

        WawelProfile profile = profileDAO.findByUuid(profileUuid);
        if (profile == null) {
            throw NetException.notFound("Profile not found.");
        }

        // Verify the token's user owns this profile
        if (!profile.getOwnerUuid()
            .equals(token.getUserUuid())) {
            throw NetException.forbidden("Profile does not belong to token owner.");
        }

        return profile;
    }

    private TextureType resolveTextureType(RequestContext ctx) {
        String typeStr = ctx.getPathParam("textureType");
        TextureType type = TextureType.fromApiName(typeStr);
        if (type == null) {
            throw NetException.notFound("Unknown texture type: " + typeStr);
        }
        if (type == TextureType.ELYTRA && !Config.server()
            .getTextures()
            .isAllowElytra()) {
            throw NetException.forbidden("Elytra textures are not enabled on this server.");
        }
        return type;
    }

    /**
     * Validates that the image dimensions are a legal skin or cape shape per spec.
     * Skin: multiple of 64x32 or 64x64.
     * Cape: multiple of 64x32 or 22x17.
     */
    private static void validateTextureShape(TextureType type, int w, int h) {
        if (type == TextureType.SKIN || type == TextureType.ELYTRA) {
            // Skin: must be 64x32 multiple OR 64x64 multiple
            boolean valid64x32 = (w % 64 == 0) && (h % 32 == 0) && (w / 64 == h / 32);
            boolean valid64x64 = (w % 64 == 0) && (h % 64 == 0) && (w / 64 == h / 64);
            if (!valid64x32 && !valid64x64) {
                throw NetException.illegalArgument(
                    "Invalid skin dimensions " + w + "x" + h + ". Must be a multiple of 64x32 or 64x64.");
            }
        } else if (type == TextureType.CAPE) {
            // Cape: must be 64x32 multiple OR 22x17 multiple
            boolean valid64x32 = (w % 64 == 0) && (h % 32 == 0) && (w / 64 == h / 32);
            boolean valid22x17 = (w % 22 == 0) && (h % 17 == 0) && (w / 22 == h / 17);
            if (!valid64x32 && !valid22x17) {
                throw NetException.illegalArgument(
                    "Invalid cape dimensions " + w + "x" + h + ". Must be a multiple of 64x32 or 22x17.");
            }
        }
    }

    /**
     * Pads a 22x17-based cape to the next 64x32 multiple with transparent pixels,
     * then re-encodes to strip metadata. Per spec, non-standard 22x17 capes
     * must be padded before storage/serving.
     */
    private static byte[] padAndReEncodeCape(byte[] data, int origW, int origH) {
        try {
            BufferedImage original = ImageIO.read(new ByteArrayInputStream(data));
            if (original == null) {
                throw NetException.illegalArgument("Failed to decode PNG image.");
            }

            // Compute scale factor and target 64x32 multiple
            int scale = origW / 22; // guaranteed integer from shape validation
            int targetW = 64 * scale;
            int targetH = 32 * scale;

            BufferedImage padded = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB);
            // padded is initialized to all-transparent (0x00000000)
            java.awt.Graphics2D g = padded.createGraphics();
            try {
                g.drawImage(original, 0, 0, null);
            } finally {
                g.dispose();
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(padded, "png", out)) {
                throw NetException.illegalArgument("Failed to re-encode padded cape.");
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw NetException.illegalArgument("Failed to process cape image: " + e.getMessage());
        }
    }

    /**
     * Converts a legacy 64x32 skin to 64x64 format using the shared converter,
     * then re-encodes to strip metadata.
     */
    private static byte[] convertLegacySkin(byte[] data) {
        try {
            BufferedImage original = ImageIO.read(new ByteArrayInputStream(data));
            if (original == null) {
                throw NetException.illegalArgument("Failed to decode PNG image.");
            }
            BufferedImage converted = SkinImageUtil.convertLegacySkin(original);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(converted, "png", out)) {
                throw NetException.illegalArgument("Failed to re-encode converted skin.");
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw NetException.illegalArgument("Failed to process skin image: " + e.getMessage());
        }
    }

    /**
     * Decodes and re-encodes a PNG image to strip all non-pixel metadata.
     * This prevents malicious chunks (e.g. tEXt with script payloads) from
     * being served to clients.
     */
    private static byte[] reEncodePng(byte[] data) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
            if (image == null) {
                throw NetException.illegalArgument("Failed to decode PNG image.");
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "png", out)) {
                throw NetException.illegalArgument("Failed to re-encode PNG image.");
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw NetException.illegalArgument("Failed to process PNG image: " + e.getMessage());
        }
    }

    /**
     * Reads width and height from the PNG IHDR chunk without decoding.
     * IHDR is always the first chunk, at bytes 8-33:
     * [8..11] chunk length (always 13)
     * [12..15] "IHDR"
     * [16..19] width (big-endian u32)
     * [20..23] height (big-endian u32)
     * <p>
     * Returns {width, height} or null if the data is too short or IHDR is missing.
     */
    private static int[] readPngDimensions(byte[] data) {
        // Need at least 24 bytes: 8 (sig) + 4 (len) + 4 (type) + 4 (w) + 4 (h)
        if (data.length < 24) return null;
        // Verify IHDR chunk type
        if (data[12] != 'I' || data[13] != 'H' || data[14] != 'D' || data[15] != 'R') return null;

        int width = ((data[16] & 0xFF) << 24) | ((data[17] & 0xFF) << 16)
            | ((data[18] & 0xFF) << 8)
            | (data[19] & 0xFF);
        int height = ((data[20] & 0xFF) << 24) | ((data[21] & 0xFF) << 16)
            | ((data[22] & 0xFF) << 8)
            | (data[23] & 0xFF);

        return new int[] { width, height };
    }

    private static boolean isPng(byte[] data) {
        if (data.length < 8) return false;
        // PNG magic bytes: 89 50 4E 47 0D 0A 1A 0A
        return data[0] == (byte) 0x89 && data[1] == 0x50
            && data[2] == 0x4E
            && data[3] == 0x47
            && data[4] == 0x0D
            && data[5] == 0x0A
            && data[6] == 0x1A
            && data[7] == 0x0A;
    }

    private static boolean isGif(byte[] data) {
        return data.length >= 6 && data[0] == 'G'
            && data[1] == 'I'
            && data[2] == 'F'
            && data[3] == '8'
            && (data[4] == '7' || data[4] == '9')
            && data[5] == 'a';
    }

    /**
     * Validates a GIF file as a valid animated cape.
     * Uses javax.imageio GIF reader to check frame count and dimensions.
     *
     * @return {width, height, frameCount} of the GIF frames
     * @throws NetException if validation fails
     */
    private static int[] validateGifCape(byte[] data, ServerConfig.Textures texConfig) {
        try {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
            if (!readers.hasNext()) {
                throw NetException.illegalArgument("GIF format not supported on this server.");
            }
            ImageReader reader = readers.next();
            try {
                ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(data));
                reader.setInput(iis);

                int frameCount = reader.getNumImages(true);
                if (frameCount < 2) {
                    throw NetException
                        .illegalArgument("Animated cape must have at least 2 frames (got " + frameCount + ").");
                }
                if (frameCount > texConfig.getMaxCapeFrameCount()) {
                    throw NetException.illegalArgument(
                        "Too many frames: " + frameCount + " (max " + texConfig.getMaxCapeFrameCount() + ").");
                }

                int width = reader.getWidth(0);
                int height = reader.getHeight(0);

                return new int[] { width, height, frameCount };
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw NetException.illegalArgument("Failed to read GIF: " + e.getMessage());
        }
    }

    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            char[] hexChars = new char[hash.length * 2];
            for (int i = 0; i < hash.length; i++) {
                int v = hash[i] & 0xFF;
                hexChars[i * 2] = HEX_CHARS[v >>> 4];
                hexChars[i * 2 + 1] = HEX_CHARS[v & 0x0F];
            }
            return new String(hexChars);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private static SkinModel parseSkinModel(String model) {
        if (model == null) {
            return SkinModel.CLASSIC;
        }

        String normalized = model.trim();
        if (normalized.isEmpty() || "default".equalsIgnoreCase(normalized) || "classic".equalsIgnoreCase(normalized)) {
            return SkinModel.CLASSIC;
        }
        if ("slim".equalsIgnoreCase(normalized)) {
            return SkinModel.SLIM;
        }
        throw NetException.illegalArgument("Invalid skin model '" + model + "'. Expected 'default' or 'slim'.");
    }

    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();
}
