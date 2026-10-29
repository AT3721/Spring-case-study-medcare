package com.codegym.dto.auth;

import jakarta.validation.constraints.NotBlank;

public class ForgotPasswordForm {

    @NotBlank(message = "Vui lòng nhập email hoặc tên tài khoản đã đăng ký")
    private String email;

    public ForgotPasswordForm() {}

    public ForgotPasswordForm(String email) {
        this.email = email;
    }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
