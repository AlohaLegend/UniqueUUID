package com.instacraft.uuidguard;

public record DatabaseSettings(
        String host,
        int port,
        String database,
        String username,
        String password
) {
    public boolean complete() {
        return notBlank(host)
                && port > 0
                && notBlank(database)
                && notBlank(username)
                && password != null;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
