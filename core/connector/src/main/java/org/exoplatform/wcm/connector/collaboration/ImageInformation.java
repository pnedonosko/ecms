package org.exoplatform.wcm.connector.collaboration;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.MetadataException;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.jpeg.JpegDirectory;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class ImageInformation {
    public final int orientation;
    public final int width;
    public final int height;
    private static final Log LOG = ExoLogger.getLogger(ImageInformation.class.getName());

    public ImageInformation(int orientation, int width, int height) {
        this.orientation = orientation;
        this.width = width;
        this.height = height;
    }

    public String toString() {
        return String.format("%dx%d,%d", this.width, this.height, this.orientation);
    }


    public static ImageInformation readImageInformation(File imageFile) throws IOException, MetadataException, ImageProcessingException {
        Metadata metadata = ImageMetadataReader.readMetadata(imageFile);
        Directory directory = metadata.getDirectory(ExifIFD0Directory.class);
        JpegDirectory jpegDirectory = metadata.getDirectory(JpegDirectory.class);

        int orientation = 1;
        try {
            orientation = directory.getInt(ExifIFD0Directory.TAG_ORIENTATION);
        } catch (MetadataException me) {
            LOG.warn("Could not get orientation");
        }
        int width = jpegDirectory.getImageWidth();
        int height = jpegDirectory.getImageHeight();

        return new ImageInformation(orientation, width, height);
    }

    public static AffineTransform getExifTransformation(ImageInformation info) {

        AffineTransform affineTransform = new AffineTransform();

        switch (info.orientation) {
            case 1:
                break;
            case 2: // Flip X
                affineTransform.scale(-1.0, 1.0);
                affineTransform.translate(-info.width, 0);
                break;
            case 3: // PI rotation
                affineTransform.translate(info.width, info.height);
                affineTransform.rotate(Math.PI);
                break;
            case 4: // Flip Y
                affineTransform.scale(1.0, -1.0);
                affineTransform.translate(0, -info.height);
                break;
            case 5: // - PI/2 and Flip X
                affineTransform.rotate(-Math.PI / 2);
                affineTransform.scale(-1.0, 1.0);
                break;
            case 6: // -PI/2 and -width
                affineTransform.translate(info.height, 0);
                affineTransform.rotate(Math.PI / 2);
                break;
            case 7: // PI/2 and Flip
                affineTransform.scale(-1.0, 1.0);
                affineTransform.translate(-info.height, 0);
                affineTransform.translate(0, info.width);
                affineTransform.rotate(  3 * Math.PI / 2);
                break;
            case 8: // PI / 2
                affineTransform.translate(0, info.width);
                affineTransform.rotate(  3 * Math.PI / 2);
                break;
        }

        return affineTransform;
    }

    public static BufferedImage transformImage(BufferedImage image, AffineTransform transform) throws Exception {

        AffineTransformOp op = new AffineTransformOp(transform, AffineTransformOp.TYPE_BICUBIC);
        BufferedImage destinationImage = new BufferedImage(image.getWidth(),image.getHeight(), image.getType());
        destinationImage = op.filter(image, destinationImage);
        return destinationImage;
    }
}
