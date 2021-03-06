package de.digitalcollections.turbojpeg.imageio;

import com.google.common.collect.ImmutableSet;
import de.digitalcollections.turbojpeg.TurboJpeg;
import java.io.IOException;
import java.util.Locale;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriter;
import javax.imageio.spi.ImageWriterSpi;
import javax.imageio.stream.ImageOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.awt.image.BufferedImage.TYPE_3BYTE_BGR;
import static java.awt.image.BufferedImage.TYPE_4BYTE_ABGR;
import static java.awt.image.BufferedImage.TYPE_BYTE_GRAY;

public class TurboJpegImageWriterSpi extends ImageWriterSpi {
  private static final Logger LOGGER = LoggerFactory.getLogger(TurboJpegImageWriterSpi.class);
  private static final String vendorName = "Münchener Digitalisierungszentrum/Digitale Bibliothek, Bayerische Staatsbibliothek";
  private static final String version = "0.1.0";
  private static final String writerClassName = "de.digitalcollections.openjpeg.turbojpeg.TurboJpegImageWriter";
  private static final String[] names = { "JPEG", "jpeg", "JPG", "jpg" };
  private static final String[] suffixes = { "jpg", "jpeg" };
  private static final String[] MIMETypes = { "image/jpeg" };
  private static final String[] readerSpiNames = { "de.digitalcollections.turbojpeg.imageio.TurboJpegImageReaderSpi" };
  private static final Class[] outputTypes = { ImageOutputStream.class };

  private TurboJpeg lib;


  public TurboJpegImageWriterSpi() {
    super(vendorName, version, names, suffixes, MIMETypes, writerClassName, outputTypes, readerSpiNames,
        false, null, null,
        null, null, false,
        null, null, null, null);
  }

  private void loadLibrary() throws IOException {
    if (this.lib == null) {
      try {
        this.lib = new TurboJpeg();
      } catch (UnsatisfiedLinkError e) {
        LOGGER.error("Could not load libturbojpeg", e);
        throw new IOException(e);
      }
    }
  }


  @Override
  public boolean canEncodeImage(ImageTypeSpecifier type) {
      // TODO: Pretty restrictive right now, we should support all greyscale and (s)RGB(A) sample models
    return ((type.getNumBands() == 3 || type.getNumBands() ==1) &&
             ImmutableSet.of(TYPE_3BYTE_BGR, TYPE_4BYTE_ABGR, TYPE_BYTE_GRAY).contains(type.getBufferedImageType()));
  }

  @Override
  public ImageWriter createWriterInstance(Object extension) throws IOException {
    this.loadLibrary();
    return new TurboJpegImageWriter(this, lib);
  }

  @Override
  public String getDescription(Locale locale) {
    return "JPEG writer plugin based on libjpeg-turbo/turbojpeg.";
  }
}
