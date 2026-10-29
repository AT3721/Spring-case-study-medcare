package com.codegym.controller.auth;

import com.codegym.dto.auth.PatientRegistrationForm;
import com.codegym.service.auth.PatientRegistrationService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;


@Controller
public class PatientRegistrationController {
    private final PatientRegistrationService registrationService;

    public PatientRegistrationController(PatientRegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @GetMapping("/register")
    public String registrationForm(Model model) {
        model.addAttribute("registrationForm", new PatientRegistrationForm());
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("registrationForm") PatientRegistrationForm form,
                           BindingResult bindingResult,
                           RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "auth/register";
        }
        registrationService.register(form).forEach(issue ->
                bindingResult.rejectValue(issue.field(), issue.code(), issue.message()));
        if (bindingResult.hasErrors()) {
            return "auth/register";
        }
        redirectAttributes.addFlashAttribute("registrationSuccess", true);
        return "redirect:/login";
    }
}
