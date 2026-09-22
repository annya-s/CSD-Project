package com.csd.farm.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class FrontendController {

    // The homepage opens the login page, which checks for an existing session.
    @GetMapping({"/", "/index.html"})
    public String home() {
        return "redirect:/login.html";
    }
}
