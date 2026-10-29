package com.codegym.controller.auth;

import com.codegym.dto.auth.ForgotPasswordForm;
import com.codegym.dto.auth.ResetPasswordForm;
import com.codegym.dto.auth.PasswordResetLinkForm;
import com.codegym.dto.auth.PatientRegistrationForm;
import com.codegym.dto.auth.ForgotPasswordForm;
import com.codegym.dto.auth.ResetPasswordForm;
import com.codegym.dto.auth.PasswordResetLinkForm;
import com.codegym.dto.auth.PatientRegistrationForm;
import com.codegym.service.auth.IPasswordResetService;
import com.codegym.dto.auth.ForgotPasswordForm;
import com.codegym.dto.auth.ResetPasswordForm;
import com.codegym.dto.auth.PasswordResetLinkForm;
import com.codegym.dto.auth.PatientRegistrationForm;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetControllerTest {

    @Mock
    private IPasswordResetService passwordResetService;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private PasswordResetController controller;

    @Test
    void showForgotPasswordFormReturnsView() {
        Model model = new ConcurrentModel();
        String view = controller.showForgotPasswordForm(model);
        assertEquals("auth/forgot-password", view);
        assertTrue(model.containsAttribute("forgotPasswordForm"));
    }

    @Test
    void processForgotPasswordProcessesRequestAndReturnsSuccessStatus() {
        ForgotPasswordForm form = new ForgotPasswordForm("patient@example.com");
        BindingResult bindingResult = new BeanPropertyBindingResult(form, "forgotPasswordForm");
        Model model = new ConcurrentModel();

        when(request.getScheme()).thenReturn("http");
        when(request.getServerName()).thenReturn("localhost");
        when(request.getServerPort()).thenReturn(8080);
        when(request.getContextPath()).thenReturn("");
        when(passwordResetService.processForgotPassword(eq("patient@example.com"), anyString()))
                .thenReturn("sampletoken123");

        String view = controller.processForgotPassword(form, bindingResult, request, model);

        assertEquals("auth/forgot-password", view);
        assertTrue((Boolean) model.getAttribute("emailSent"));
        assertEquals("patient@example.com", model.getAttribute("submittedEmail"));
        assertNotNull(model.getAttribute("demoResetLink"));
    }

    @Test
    void showResetPasswordFormWithValidTokenDisplaysForm() {
        when(passwordResetService.validateToken("valid-token")).thenReturn(true);
        Model model = new ConcurrentModel();

        PasswordResetLinkForm form = new PasswordResetLinkForm();
        form.setToken("valid-token");
        String view = controller.showResetPasswordForm(form, model);

        assertEquals("auth/reset-password", view);
        assertTrue(model.containsAttribute("resetPasswordForm"));
        assertFalse(model.containsAttribute("invalidToken"));
    }

    @Test
    void showResetPasswordFormWithInvalidOrExpiredTokenDisplaysWarning() {
        when(passwordResetService.validateToken("expired-token")).thenReturn(false);
        Model model = new ConcurrentModel();

        PasswordResetLinkForm form = new PasswordResetLinkForm();
        form.setToken("expired-token");
        String view = controller.showResetPasswordForm(form, model);

        assertEquals("auth/reset-password", view);
        assertTrue((Boolean) model.getAttribute("invalidToken"));
    }

    @Test
    void processResetPasswordSuccessRedirectsToLogin() {
        ResetPasswordForm form = new ResetPasswordForm("valid-token");
        form.setPassword("newSecret123");
        form.setConfirmPassword("newSecret123");

        BindingResult bindingResult = new BeanPropertyBindingResult(form, "resetPasswordForm");
        Model model = new ConcurrentModel();
        RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();

        when(passwordResetService.validateToken("valid-token")).thenReturn(true);
        when(passwordResetService.resetPassword("valid-token", "newSecret123")).thenReturn(true);

        String view = controller.processResetPassword(form, bindingResult, model, redirectAttributes);

        assertEquals("redirect:/login", view);
        assertTrue((Boolean) redirectAttributes.getFlashAttributes().get("resetSuccess"));
    }

    @Test
    void processResetPasswordMismatchPasswordsReturnsFormWithError() {
        ResetPasswordForm form = new ResetPasswordForm("valid-token");
        form.setPassword("newSecret123");
        form.setConfirmPassword("mismatchPassword");

        BindingResult bindingResult = new BeanPropertyBindingResult(form, "resetPasswordForm");
        Model model = new ConcurrentModel();
        RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();

        when(passwordResetService.validateToken("valid-token")).thenReturn(true);

        String view = controller.processResetPassword(form, bindingResult, model, redirectAttributes);

        assertEquals("auth/reset-password", view);
        assertTrue(bindingResult.hasErrors());
        assertNotNull(bindingResult.getFieldError("confirmPassword"));
        verify(passwordResetService, never()).resetPassword(anyString(), anyString());
    }
}
