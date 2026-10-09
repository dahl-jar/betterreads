package com.betterreads.features.coverimages;

import static com.betterreads.features.coverimages.CoverImageFixtures.patterned;
import static com.betterreads.features.coverimages.CoverImageFixtures.png;
import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.WritableRaster;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.zip.CRC32;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.junit.jupiter.api.Test;

class ImageDecoderTest {

    private static final int SIDE = 100;

    private static final long RGBA_BYTES = SIDE * SIDE * (4L + 4L);

    private static final int AFTER_HEADER = 33;

    private static final int CHUNK_OVERHEAD = 12;

    private static final int ALL_PASSES = Integer.MAX_VALUE;

    private static final int COVER_WIDTH = 300;

    private static final int COVER_HEIGHT = 450;

    @Test
    void shouldDecodeAnImageAtTheByteCap() {
        final byte[] image = rgba();

        final Optional<BufferedImage> decoded = ImageDecoder.decode(image, RGBA_BYTES, ALL_PASSES);

        assertThat(decoded).isPresent();
    }

    @Test
    void shouldRefuseAnImageOverTheByteCap() {
        final byte[] image = rgba();

        final Optional<BufferedImage> decoded = ImageDecoder.decode(image, RGBA_BYTES - 1, ALL_PASSES);

        assertThat(decoded).isEmpty();
    }

    @Test
    void shouldDecodeAnImageWithABrokenTextChunk() {
        final byte[] image = withBrokenTextChunk(rgba());

        final Optional<BufferedImage> decoded = ImageDecoder.decode(image, RGBA_BYTES, ALL_PASSES);

        assertThat(decoded).isPresent();
    }

    @Test
    void shouldRefuseASixteenBitImageOverTheByteCap() {
        final byte[] image = deepRgba();

        final Optional<BufferedImage> decoded = ImageDecoder.decode(image, RGBA_BYTES, ALL_PASSES);

        assertThat(decoded).isEmpty();
    }

    @Test
    void shouldStopAProgressiveImageAtThePassCap() throws IOException {
        final byte[] image = progressive();
        final BufferedImage full = ImageDecoder.decode(image, Long.MAX_VALUE, ALL_PASSES).orElseThrow();

        final BufferedImage firstPass = ImageDecoder.decode(image, Long.MAX_VALUE, 1).orElseThrow();

        assertThat(pixels(firstPass)).isNotEqualTo(pixels(full));
    }

    @Test
    void shouldDecodeAProgressiveCoverInFull() throws IOException {
        final byte[] image = progressive();
        final BufferedImage full = ImageDecoder.decode(image, Long.MAX_VALUE, ALL_PASSES).orElseThrow();

        final BufferedImage decoded = ImageDecoder.decode(image).orElseThrow();

        assertThat(pixels(decoded)).isEqualTo(pixels(full));
    }

    @Test
    void shouldRefuseATiffImage() throws IOException {
        final ByteArrayOutputStream tiff = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(SIDE, SIDE, BufferedImage.TYPE_INT_RGB), "tiff", tiff);

        final Optional<BufferedImage> decoded = ImageDecoder.decode(tiff.toByteArray());

        assertThat(decoded).isEmpty();
    }

    private static int[] pixels(final BufferedImage image) {
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }

    private static byte[] progressive() throws IOException {
        final BufferedImage source = ImageIO.read(new ByteArrayInputStream(patterned(COVER_WIDTH, COVER_HEIGHT)));
        final ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        final ImageWriteParam param = writer.getDefaultWriteParam();
        param.setProgressiveMode(ImageWriteParam.MODE_DEFAULT);
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(stream);
            writer.write(null, new IIOImage(source, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    private static byte[] rgba() {
        return png(new BufferedImage(SIDE, SIDE, BufferedImage.TYPE_4BYTE_ABGR));
    }

    private static byte[] deepRgba() {
        final ComponentColorModel model = new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_sRGB),
            true, false, Transparency.TRANSLUCENT, DataBuffer.TYPE_USHORT);
        final WritableRaster raster = model.createCompatibleWritableRaster(SIDE, SIDE);
        return png(new BufferedImage(model, raster, false, null));
    }

    private static byte[] withBrokenTextChunk(final byte[] png) {
        final byte[] data = "Comment\0\0not deflate data".getBytes(StandardCharsets.ISO_8859_1);
        final byte[] type = "zTXt".getBytes(StandardCharsets.ISO_8859_1);
        final CRC32 crc = new CRC32();
        crc.update(type);
        crc.update(data);
        final ByteBuffer chunk = ByteBuffer.allocate(CHUNK_OVERHEAD + data.length)
            .putInt(data.length).put(type).put(data).putInt((int) crc.getValue());
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(png, 0, AFTER_HEADER);
        out.writeBytes(chunk.array());
        out.write(png, AFTER_HEADER, png.length - AFTER_HEADER);
        return out.toByteArray();
    }
}
