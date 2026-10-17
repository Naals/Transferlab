package dev.transferlab.transferlab.shared.domain;

import java.util.Objects;

public final class BusinessRejection extends RuntimeException {

    public enum Code {
        INSUFFICIENT_FUNDS,
        ACCOUNT_INACTIVE
    }

    private final Code code;

    public BusinessRejection(Code code) {
        super(Objects.requireNonNull(code, "code").name());
        this.code = code;
    }

    public Code code() {
        return code;
    }
}