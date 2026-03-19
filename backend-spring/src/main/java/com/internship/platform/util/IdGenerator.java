package com.internship.platform.util;

import java.util.UUID;

public final class IdGenerator {

    private IdGenerator() {
    }

    public static String nextId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
