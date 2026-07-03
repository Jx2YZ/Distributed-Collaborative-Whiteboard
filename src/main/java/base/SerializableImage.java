/**
 * Serializable wrapper for BufferedImage to allow image transfer over RMI; handles custom serialization and deserialization of image data.
 * Jx2
 * v.22/05/2026
 **/

package base;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;

public class SerializableImage implements Serializable {
    private transient BufferedImage image; // The actual image, not serialized by default

    public SerializableImage() {
        super();
    }

    public SerializableImage(BufferedImage image) {
        this();
        this.image = image;
    }

    public BufferedImage getImage() {
        return image;
    }

    public void setImage(BufferedImage image) {
        this.image = image;
    }

    // writes the image as PNG to the output stream
    @Serial
    private void writeObject(java.io.ObjectOutputStream out) throws IOException {
        ImageIO.write(getImage(), "png", new MemoryCacheImageOutputStream(out));
    }

    // reads the image from the input stream
    @Serial
    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        setImage(ImageIO.read(new MemoryCacheImageInputStream(in)));
    }

}