package dev.smpb.containersearch.store;

public enum LoadStatus {
    EMPTY,
    LOADED,
    RECOVERED_BACKUP,
    NEWER_SCHEMA,
    CORRUPT
}

