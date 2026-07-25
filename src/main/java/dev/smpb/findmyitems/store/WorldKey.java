package dev.smpb.findmyitems.store;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

public record WorldKey(String id, String label, UUID playerId) {
    public WorldKey {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(playerId, "playerId");
    }

    public static WorldKey singleplayer(String saveIdentity, String label, UUID playerId) {
        Objects.requireNonNull(saveIdentity, "saveIdentity");
        var value = "singleplayer\0" + saveIdentity + "\0" + playerId;
        try {
            var digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return new WorldKey(HexFormat.of().formatHex(digest), label, playerId);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by Java", exception);
        }
    }
}

