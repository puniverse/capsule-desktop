/*
 * Copyright (c) 2014-2015, Parallel Universe Software Co. and Contributors. All rights reserved.
 *
 * This program and the accompanying materials are licensed under the terms
 * of the Eclipse Public License v1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package co.paralleluniverse.desktop;

//import java.awt.image.BufferedImage;
//import java.io.FileNotFoundException;
//import java.io.IOException;
//import java.io.InputStream;
//import java.io.OutputStream;
//import java.util.List;
//import org.apache.commons.imaging.ImageFormat;
//import org.apache.commons.imaging.ImageFormats;
//import org.apache.commons.imaging.Imaging;
//import org.apache.commons.imaging.ImagingException;
//import org.apache.commons.io.IOUtils;



/**
 *
 * @author pron
 */
public class Icons {
//    public enum Type {
//        ICO, ICNS, PNG
//    };
//
//    public static void getIcon(String resource, Type outputType, OutputStream os) throws IOException {
//        String filename = resource + toSuffix(outputType);
//        try (InputStream is = Icons.class.getClassLoader().getResourceAsStream(filename)) {
//            if (is != null) {
//                IOUtils.copy(is, os);
//                return;
//            }
//        }
//        throw new FileNotFoundException(filename);

        // Unfortunately Commons Imaging doesn't support writing multiple icon images into the same file (but it does support reading)
//        try {
//            for (Type t : Type.values()) {
//                if (t != outputType) {
//                    filename = resource + toSuffix(t);
//                    try (InputStream is = Icons.class.getClassLoader().getResourceAsStream(filename)) {
//                        if (is != null) {
//                            final List<BufferedImage> icons = Imaging.getAllBufferedImages(is, filename);
//                            Imaging.writeImage(icons???, os, toImageFormat(outputType), null);
//                        }
//                    }
//                }
//            }
//        } catch (ImagingException e) {
//            throw new RuntimeException(e);
//        }
//    }
//
//    private static ImageFormat toImageFormat(Type iconType) {
//        switch (iconType) {
//            case ICNS:
//                return ImageFormats.ICNS;
//            case ICO:
//                return ImageFormats.ICO;
//            case PNG:
//                return ImageFormats.PNG;
//            default:
//                throw new IllegalArgumentException("Icon type: " + iconType);
//        }
//    }
//
//    private static String toSuffix(Type iconType) {
//        switch (iconType) {
//            case ICNS:
//                return ".icns";
//            case ICO:
//                return ".ico";
//            case PNG:
//                return ".png";
//            default:
//                throw new IllegalArgumentException("Icon type: " + iconType);
//        }
//    }
}
