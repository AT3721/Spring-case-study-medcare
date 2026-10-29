package com.codegym.controller.auth;

import com.codegym.dto.auth.ForgotPasswordForm;
import com.codegym.dto.auth.ResetPasswordForm;
import com.codegym.dto.auth.PasswordResetLinkForm;
import com.codegym.dto.auth.PatientRegistrationForm;
import com.codegym.dto.auth.ForgotPasswordForm;
import com.codegym.dto.auth.ResetPasswordForm;
import com.codegym.dto.auth.PasswordResetLinkForm;
import com.codegym.dto.auth.PatientRegistrationForm;
import com.codegym.dto.auth.ForgotPasswordForm;
import com.codegym.dto.auth.ResetPasswordForm;
import com.codegym.dto.auth.PasswordResetLinkForm;
import com.codegym.dto.auth.PatientRegistrationForm;
import com.codegym.service.auth.IPasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;

@Controller
public class PasswordResetController {

    private final IPasswordResetService passwordResetService;

    public PasswordResetController(IPasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @GetMapping("/forgot-password")
    public String showForgotPasswordForm(Model model) {
        if (!model.containsAttribute("forgotPasswordForm")) {
            model.addAttribute("forgotPasswordForm", new ForgotPasswordForm());
        }
        if (!model.containsAttribute("emailSent")) {
            model.addAttribute("emailSent", false);
        }
        return "auth/forgot-password";
    }

    @PostMapping("/forgot-password")
    public String processForgotPassword(@Valid @ModelAttribute("forgotPasswordForm") ForgotPasswordForm form,
                                        BindingResult bindingResult,
                                        HttpServletRequest request,
                                        Model model) {
        if (bindingResult.hasErrors()) {
            return "auth/forgot-password";
        }

        String baseUrl = request.getScheme() + "://" + request.getServerName()
                + (request.getServerPort() == 80 || request.getServerPort() == 443 ? "" : ":" + request.getServerPort())
                + request.getContextPath();

        String token = passwordResetService.processForgotPassword(form.getEmail(), baseUrl);

        model.addAttribute("emailSent", true);
        model.addAttribute("submittedEmail", form.getEmail());
        if (token != null) {
            String directLink = baseUrl + "/reset-password?token=" + token;
            model.addAttribute("demoResetLink", directLink);
        }

        return "auth/forgot-password";
    }

    @GetMapping("/reset-password")
    public String showResetPasswordForm(@ModelAttribute PasswordResetLinkForm form,
                                        Model model) {
        String token = form.getToken();
        if (token == null || !passwordResetService.validateToken(token)) {
            model.addAttribute("invalidToken", true);
            return "auth/reset-password";
        }

        model.addAttribute("resetPasswordForm", new ResetPasswordForm(token));
        return "auth/reset-password";
    }

    @PostMapping("/reset-password")
    public String processResetPassword(@Valid @ModelAttribute("resetPasswordForm") ResetPasswordForm form,
                                       BindingResult bindingResult,
                                       Model model,
                                       RedirectAttributes redirectAttributes) {
        if (!passwordResetService.validateToken(form.getToken())) {
            model.addAttribute("invalidToken", true);
            return "auth/reset-password";
        }

        if (form.getPassword() != null && !form.getPassword().equals(form.getConfirmPassword())) {
            bindingResult.rejectValue("confirmPassword", "password.mismatch", "Mật khẩu xác nhận không khớp.");
        }

        if (form.getPassword() != null
                && form.getPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            bindingResult.rejectValue("password", "password.tooLong", "Mật khẩu vượt quá giới hạn độ dài cho phép.");
        }

        if (bindingResult.hasErrors()) {
            return "auth/reset-password";
        }

        boolean resetSuccess = passwordResetService.resetPassword(form.getToken(), form.getPassword());
        if (!resetSuccess) {
            model.addAttribute("invalidToken", true);
            return "auth/reset-password";
        }

        redirectAttributes.addFlashAttribute("resetSuccess", true);
        return "redirect:/login";
    }
}
