package dev.smpb.containersearch.store;

import java.io.IOException;
import java.nio.file.Path;

@FunctionalInterface
public interface FileMover {
    void move(Path source, Path target) throws IOException;
}

