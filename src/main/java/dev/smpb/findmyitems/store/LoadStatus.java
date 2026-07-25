package dev.smpb.findmyitems.store;

public enum LoadStatus {
    EMPTY,
    LOADED,
    RECOVERED_BACKUP,
    NEWER_SCHEMA,
    CORRUPT
}

