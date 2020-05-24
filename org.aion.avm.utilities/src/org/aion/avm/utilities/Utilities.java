package org.aion.avm.utilities;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;


/**
 * Generic utilities.
 */
public class Utilities {
    private static final int MAX_CLASS_BYTES = 1024 * 1024;

    /**
     * Converts a fully qualified class name into it's JVM internal form.
     *
     * @param fullyQualifiedName
     * @return
     */
    public static String fulllyQualifiedNameToInternalName(String fullyQualifiedName) {
        return fullyQualifiedName.replaceAll("\\.", "/");
    }

    /**
     * Converts a JVM internal class name into a fully qualified name.
     *
     * @param internalName
     * @return
     */
    public static String internalNameToFulllyQualifiedName(String internalName) {
        return internalName.replaceAll("/", ".");
    }

    /**
     * A helper which will attempt to load the given resource path as bytes.
     * Returns null if the resource could not be found.
     *
     * @param resourcePath The path to this resource, within the parent class loader.
     * @return The resource as bytes, or null if not found.
     */
    public static byte[] loadRequiredResourceAsBytes(String resourcePath) {
        InputStream stream = Utilities.class.getClassLoader().getResourceAsStream(resourcePath);
        byte[] raw = null;
        if (null != stream) {
            try {
                raw = _stream_readAllBytes(stream);
            } catch (IOException e) {
                // We treat this as a fatal error, within this simple utility class.
                throw new AssertionError(e);
            }
        }
        return raw;
    }

    public static Map<String, byte[]> extractClasses(JarInputStream jarReader, NameStyle nameStyle) throws IOException {

        Map<String, byte[]> classMap = new HashMap<>();
        byte[] tempReadingBuffer = new byte[MAX_CLASS_BYTES];

        JarEntry entry;
        while (null != (entry = jarReader.getNextJarEntry())) {
            String name = entry.getName();

            if (name.endsWith(".class")
                    && !name.equals("package-info.class")
                    && !name.equals("module-info.class")) {

                String internalClassName = name.replaceAll(".class$", "");
                if (nameStyle.equals(NameStyle.DOT_NAME)) {
                    internalClassName = Utilities.internalNameToFulllyQualifiedName(internalClassName);
                }
                int readSize = _stream_readNBytes(jarReader, tempReadingBuffer);

                if (0 != jarReader.available()) {
                    throw new RuntimeException("Class file too big: " + name);
                }

                byte[] classBytes = new byte[readSize];
                System.arraycopy(tempReadingBuffer, 0, classBytes, 0, readSize);
                classMap.put(internalClassName, classBytes);
            }
        }
        return classMap;
    }

    public static String extractMainClassName(JarInputStream jarReader, NameStyle nameStyle) {
        Manifest manifest = jarReader.getManifest();
        if (null != manifest && manifest.getMainAttributes() != null) {
            String mainClassName = manifest.getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);
            if (nameStyle.equals(NameStyle.SLASH_NAME)) {
                mainClassName = Utilities.fulllyQualifiedNameToInternalName(mainClassName);
            }
            return mainClassName;
        } else {
            throw new RuntimeException("Manifest file required");
        }
    }

    public static byte[] stream_readAllBytes(InputStream stream) throws IOException {
        return _stream_readAllBytes(stream);
        
    }

    public static int stream_readNBytes(InputStream stream, byte[] buffer) throws IOException {
        return _stream_readNBytes(stream, buffer);
    }

    public enum NameStyle {
        DOT_NAME,
        SLASH_NAME,
        ;
    }


    private static byte[] _stream_readAllBytes(InputStream stream) throws IOException {
        byte[] temp = new byte[MAX_CLASS_BYTES+1];
        boolean done = false;
        int readSize = 0;
        while (!done) {
            int didRead = stream.read(temp, readSize, temp.length - readSize);
            if (-1 == didRead) {
                done = true;
            } else {
                readSize += didRead;
            }
        }
        if (temp.length == readSize) {
            // Overflow.
            throw new AssertionError("Overflow reading buffer");
        }
        byte[] raw = new byte[readSize];
        System.arraycopy(temp, 0, raw, 0, readSize);
        return raw;
    }

    private static int _stream_readNBytes(InputStream stream, byte[] buffer) throws IOException {
        boolean done = false;
        int readSize = 0;
        while (!done) {
            int didRead = stream.read(buffer, readSize, buffer.length - readSize);
            if (-1 == didRead) {
                done = true;
            } else {
                readSize += didRead;
                if (buffer.length == readSize) {
                    done = true;
                }
            }
        }
        return readSize;
    }
}
