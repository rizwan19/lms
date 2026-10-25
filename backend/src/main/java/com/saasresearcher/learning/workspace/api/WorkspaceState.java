package com.saasresearcher.learning.workspace.api;

import java.util.Arrays;

public enum WorkspaceState {
    REQUIRED(0),
    OWNER_VERIFICATION_PENDING(1),
    ACTIVE(2);

    private final short code;

    WorkspaceState(int code) {
        this.code = (short) code;
    }

    public short code() {
        return code;
    }

    public static WorkspaceState fromCode(short code) {
        return Arrays.stream(values())
                .filter(state -> state.code == code)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown workspace state " + code));
    }
}
