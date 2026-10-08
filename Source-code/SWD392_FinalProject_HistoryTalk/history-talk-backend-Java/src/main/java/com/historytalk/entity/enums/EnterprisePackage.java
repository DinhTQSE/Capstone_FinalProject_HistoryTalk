package com.historytalk.entity.enums;

import lombok.Getter;

@Getter
public enum EnterprisePackage {
    ENTERPRISE_SMALL(5_000_000),
    ENTERPRISE_MEDIUM(20_000_000),
    ENTERPRISE_LARGE(50_000_000);

    private final int tokens;

    EnterprisePackage(int tokens) {
        this.tokens = tokens;
    }
}
