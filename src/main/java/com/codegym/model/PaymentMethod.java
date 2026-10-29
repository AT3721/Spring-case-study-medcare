package com.codegym.model;

public enum PaymentMethod {
    CASH("Tiền mặt tại quầy"),
    VNPAY("VNPay Sandbox");

    private final String displayName;

    PaymentMethod(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
