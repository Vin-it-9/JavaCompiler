package org.compiler;

import jakarta.enterprise.context.ApplicationScoped;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Comparator;

@ApplicationScoped
public class FileManager {

    private static long tempDirCounter = 0;

    public Path createTempDirectory() {
        try {
            String baseTempDir = System.getProperty("java.io.tmpdir");
            Path tempDir = Paths.get(baseTempDir, "jc" + System.nanoTime() + "_" + (tempDirCounter++));
            return Files.createDirectory(tempDir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void writeFile(Path file, String content) {
        try {
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String readFile(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public byte[] readBytes(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void writeBytes(Path file, byte[] bytes) {
        try {
            Files.write(file, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void deleteDirectory(Path directory) {
        try {
            if (Files.exists(directory)) {
                Files.walk(directory)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            // Ignore cleanup errors
                        }
                    });
            }
        } catch (IOException e) {
            // Ignore cleanup errors
        }
    }

    public boolean exists(Path file) {
        return Files.exists(file);
    }
}
