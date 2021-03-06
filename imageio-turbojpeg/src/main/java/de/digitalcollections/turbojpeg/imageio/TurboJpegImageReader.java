package de.digitalcollections.turbojpeg.imageio;

import de.digitalcollections.turbojpeg.Info;
import de.digitalcollections.turbojpeg.TurboJpeg;
import de.digitalcollections.turbojpeg.TurboJpegException;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.stream.Stream;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;

import static java.awt.image.BufferedImage.TYPE_3BYTE_BGR;
import static java.awt.image.BufferedImage.TYPE_4BYTE_ABGR;
import static java.awt.image.BufferedImage.TYPE_4BYTE_ABGR_PRE;
import static java.awt.image.BufferedImage.TYPE_BYTE_GRAY;

public class TurboJpegImageReader extends ImageReader {

  private final TurboJpeg lib;
  private ByteBuffer jpegData;
  private Info info;

  protected TurboJpegImageReader(ImageReaderSpi originatingProvider, TurboJpeg lib) {
    super(originatingProvider);
    this.lib = lib;
  }

  @Override
  public void setInput(Object input, boolean seekForwardOnly, boolean ignoreMetadata) {
    super.setInput(input, seekForwardOnly, ignoreMetadata);
    if (input == null) {
      return;
    }
    if (input instanceof ImageInputStream) {
      try {
        jpegData = bufferFromStream((ImageInputStream) input);
        info = lib.getInfo(jpegData.array());
      } catch (IOException e) {
        throw new IllegalArgumentException("Failed to read input.");
      } catch (TurboJpegException e) {
        throw new IllegalArgumentException("Failed to read JPEG info.");
      }
    } else {
      throw new IllegalArgumentException("Bad input.");
    }
  }

  private void checkIndex(int imageIndex) {
    if (imageIndex >= info.getAvailableSizes().size()) {
      throw new IndexOutOfBoundsException("bad index");
    }
  }

  private ByteBuffer bufferFromStream(ImageInputStream stream) throws IOException {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    final byte[] buf = new byte[8192];
    int n;
    while (0 < (n = stream.read(buf))) {
      bos.write(buf, 0, n);
    }
    return ByteBuffer.wrap(bos.toByteArray());
  }

  private void readData() throws IOException {
    jpegData = bufferFromStream((ImageInputStream) input);
  }

  private void parseInfo() throws IOException {
    if (jpegData == null) {
      readData();
    }
    try {
      info = lib.getInfo(jpegData.array());
    } catch (TurboJpegException e) {
      throw new IOException(e);
    }
  }

  @Override
  public ImageReadParam getDefaultReadParam() {
    return new TurboJpegImageReadParam();
  }

  @Override
  public int getNumImages(boolean allowSearch) throws IOException {
    if (info == null) {
      parseInfo();
    }
    return info.getAvailableSizes().size();
  }

  @Override
  public int getWidth(int imageIndex) throws IOException {
    checkIndex(imageIndex);
    if (info == null) {
      parseInfo();
    }
    return info.getAvailableSizes().get(imageIndex).width;
  }

  @Override
  public int getHeight(int imageIndex) throws IOException {
    if (info == null) {
      parseInfo();
    }
    return info.getAvailableSizes().get(imageIndex).height;
  }

  @Override
  public Iterator<ImageTypeSpecifier> getImageTypes(int imageIndex) throws IOException {
    return Stream.of(TYPE_3BYTE_BGR, TYPE_4BYTE_ABGR, TYPE_4BYTE_ABGR_PRE, TYPE_BYTE_GRAY)
        .map(ImageTypeSpecifier::createFromBufferedImageType)
        .iterator();
  }

  /** Since TurboJPEG can only crop to values divisible by 8, we may need to
   *  expand the cropping area to get a suitable rectangle.
   *
   * @param region The source region to be cropped
   * @return The region that needs to be cropped from the image cropped to the expanded rectangle
   */
  private Rectangle adjustRegion(Rectangle region) {
    if (region == null) {
      return null;
    }
    boolean modified = false;
    Rectangle extraCrop = new Rectangle(0, 0, region.width, region.height);
    if (region.x % 8 != 0) {
      extraCrop.x = region.x % 8;
      region.x -= extraCrop.x;
      region.width += extraCrop.x;
      modified = true;
    }
    if (region.y % 8 != 0) {
      extraCrop.y = region.y % 8;
      region.y -= extraCrop.y;
      region.height += extraCrop.y;
      modified = true;
    }
    if (region.width % 8 != 0) {
      region.width = (int) (8*(Math.ceil(region.getWidth() / 8)));
      modified = true;
    }
    if (region.height % 8 != 0) {
      region.height = (int) (8*(Math.ceil(region.getHeight() / 8)));
      modified = true;
    }
    if (modified) {
      return extraCrop;
    } else {
      return null;
    }
  }

  public void adjustExtraCrop(int imageIndex, Info croppedInfo, int rotation, Rectangle rectangle) {
    if (rectangle == null) {
      return;
    }

    double factor = croppedInfo.getAvailableSizes().get(imageIndex).getWidth() / croppedInfo.getAvailableSizes().get(0).getWidth();
    if (factor < 1) {
      rectangle.x = (int) Math.ceil(factor * rectangle.x);
      rectangle.y = (int) Math.ceil(factor * rectangle.y);
    }

    if (rotation == 90 || rotation == 270) {
      int x = rectangle.x;
      int y = rectangle.y;
      rectangle.x = y;
      rectangle.y = x;
    }
  }

  private void scaleRegion(int targetIndex, Rectangle sourceRegion) throws IOException {
    double scaleFactor = (double) getWidth(0) / (double) getWidth(targetIndex);
    sourceRegion.x = (int) Math.ceil(scaleFactor * sourceRegion.x);
    sourceRegion.y = (int) Math.ceil(scaleFactor * sourceRegion.y);
    sourceRegion.width = (int) Math.ceil(scaleFactor * sourceRegion.width);
    sourceRegion.height = (int) Math.ceil(scaleFactor * sourceRegion.height);
  }

  @Override
  public BufferedImage read(int imageIndex, ImageReadParam param) throws IOException {
    checkIndex(imageIndex);
    if (jpegData == null) {
      readData();
    }
    ByteBuffer data = jpegData;
    if (info == null) {
      parseInfo();
    }
    try {
      int rotation = 0;
      Rectangle region = null;
      Rectangle extraCrop = null;
      if (param instanceof TurboJpegImageReadParam) {
        rotation = ((TurboJpegImageReadParam) param).getRotationDegree();
      }
      if (param != null && param.getSourceRegion() != null) {
        region = param.getSourceRegion();
        scaleRegion(imageIndex, region);
        extraCrop = adjustRegion(region);
      }
      if (region != null || rotation != 0) {
        data = lib.transform(data.array(), info, region, rotation);
      }
      Info transformedInfo = lib.getInfo(data.array());
      BufferedImage img = lib.decode(
          data.array(), transformedInfo, transformedInfo.getAvailableSizes().get(imageIndex));
      if (extraCrop != null) {
        adjustExtraCrop(imageIndex, transformedInfo, rotation, extraCrop);
        extraCrop.width = param.getSourceRegion().width;
        extraCrop.height = param.getSourceRegion().height;
        img = img.getSubimage(extraCrop.x, extraCrop.y, extraCrop.width, extraCrop.height);
      }
      return img;
    } catch (TurboJpegException e) {
      throw new IOException(e);
    }
  }

  @Override
  public IIOMetadata getStreamMetadata() throws IOException {
    return null;
  }

  @Override
  public IIOMetadata getImageMetadata(int imageIndex) throws IOException {
    return null;
  }
}
