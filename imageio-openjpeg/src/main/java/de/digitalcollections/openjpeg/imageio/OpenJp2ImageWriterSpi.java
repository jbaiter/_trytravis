package de.digitalcollections.openjpeg.imageio;

import de.digitalcollections.openjpeg.OpenJpeg;
import java.io.IOException;
import java.util.Locale;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriter;
import javax.imageio.spi.ImageWriterSpi;
import javax.imageio.stream.ImageOutputStream;

import static java.awt.image.BufferedImage.TYPE_3BYTE_BGR;

public class OpenJp2ImageWriterSpi extends ImageWriterSpi {
  private static final String vendorName = "Münchener Digitalisierungszentrum/Digitale Bibliothek, Bayerische Staatsbibliothek";
  private static final String version = "0.1.0";
  private static final String writerClassName = "de.digitalcollections.openjpeg.imageio.OpenJp2ImageWriter";
  private static final String[] names = { "jpeg2000" };
  private static final String[] suffixes = { "jp2" };
  private static final String[] MIMETypes = { "image/jp2" };
  private static final String[] readerSpiNames = { "de.digitalcollections.openjpeg.imageio.OpenJp2ImageWriterSpi" };
  private static final Class[] outputTypes = { ImageOutputStream.class };

  private OpenJpeg lib;

  public OpenJp2ImageWriterSpi() {
    super(vendorName, version, names, suffixes, MIMETypes, writerClassName, outputTypes, readerSpiNames,
        false, null, null,
        null, null, false,
        null, null, null,
        null);
  }

  private void loadLibrary() throws IOException {
    if (this.lib == null) {
      try {
        this.lib = new OpenJpeg();
      } catch (UnsatisfiedLinkError e) {
        throw new IOException(e);
      }
    }
  }

  @Override
  public boolean canEncodeImage(ImageTypeSpecifier type) {
    // TODO: Pretty restrictive right now, we should support all greyscale and (s)RGB(A) sample models
    return (type.getNumBands() == 3 && type.getBufferedImageType() == TYPE_3BYTE_BGR);
  }

  @Override
  public ImageWriter createWriterInstance(Object extension) throws IOException {
    this.loadLibrary();
    return new OpenJp2ImageWriter(this, lib);
  }

  @Override
  public String getDescription(Locale locale) {
    return "JPEG2000 reader plugin based on the OpenJp2 library from the OpenJPEG project.";
  }
}
