package com.codegym.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AppointmentLookupController {

    @GetMapping("/appointments/search")
    public String showSearchPage() {
        return "appointment/search";
    }
}