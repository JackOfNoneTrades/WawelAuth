package org.fentanylsolutions.wawelauth.client.gui;

import java.nio.charset.StandardCharsets;

import org.junit.Assert;
import org.junit.Test;

public class TextureUploadPreviewTest {

    @Test
    public void recognizesBothGifSignatures() {
        Assert.assertTrue(TextureUploadPreview.isGif("GIF87a".getBytes(StandardCharsets.US_ASCII)));
        Assert.assertTrue(TextureUploadPreview.isGif("GIF89a".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    public void rejectsNonGifAndTruncatedHeaders() {
        Assert.assertFalse(TextureUploadPreview.isGif(null));
        Assert.assertFalse(TextureUploadPreview.isGif(new byte[0]));
        Assert.assertFalse(TextureUploadPreview.isGif("GIF89".getBytes(StandardCharsets.US_ASCII)));
        Assert.assertFalse(TextureUploadPreview.isGif("GIF88a".getBytes(StandardCharsets.US_ASCII)));
        Assert.assertFalse(TextureUploadPreview.isGif("\u0089PNG\r\n".getBytes(StandardCharsets.ISO_8859_1)));
    }
}
