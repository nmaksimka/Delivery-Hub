package com.deliveryhub.restaurant.storage;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

final class ImageReencoder {

    static final int MAX_SIDE = 4096;

    private ImageReencoder() {
    }

    static byte[] withoutMetadata(byte[] content, ImageFormat format) {
        if (format == ImageFormat.WEBP) {
            return content;
        }
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            BufferedImage image = read(input);
            ByteArrayOutputStream output = new ByteArrayOutputStream(content.length);
            if (!ImageIO.write(image, format == ImageFormat.JPEG ? "jpeg" : "png", output)) {
                throw new UnsupportedImageException();
            }
            return output.toByteArray();
        } catch (IOException | IllegalArgumentException e) {
            throw new UnsupportedImageException();
        }
    }

    private static BufferedImage read(ImageInputStream input) throws IOException {
        Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
        if (!readers.hasNext()) {
            throw new UnsupportedImageException();
        }
        ImageReader reader = readers.next();
        try {
            reader.setInput(input, true, true);
            int width = reader.getWidth(0);
            int height = reader.getHeight(0);
            if (width > MAX_SIDE || height > MAX_SIDE) {
                throw new ImageTooLargeException(width, height, MAX_SIDE);
            }
            return reader.read(0);
        } finally {
            reader.dispose();
        }
    }
}
