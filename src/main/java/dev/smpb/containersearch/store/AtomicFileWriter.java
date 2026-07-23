package dev.smpb.containersearch.store;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

public final class AtomicFileWriter {
    private final FileMover mover;

    public AtomicFileWriter() {
        this(AtomicFileWriter::moveWithFallback);
    }

    public AtomicFileWriter(FileMover mover) {
        this.mover = mover;
    }

    public void write(Path target, String content) throws IOException {
        Files.createDirectories(target.getParent());
        var temporary = target.resolveSibling(target.getFileName() + ".tmp");
        var backup = target.resolveSibling(target.getFileName() + ".bak");
        try {
            writeAndFlush(temporary, content);
            if (Files.isRegularFile(target)) {
                Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING);
            }
            mover.move(temporary, target);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void writeAndFlush(Path path, String content) throws IOException {
        try (var channel = FileChannel.open(
                path,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE)) {
            var bytes = ByteBuffer.wrap(content.getBytes(StandardCharsets.UTF_8));
            while (bytes.hasRemaining()) {
                channel.write(bytes);
            }
            channel.force(true);
        }
    }

    private static void moveWithFallback(Path source, Path target) throws IOException {
        try {
            Files.move(
                    source,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}

