package com.harborclaims.modern.web;

import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Navigation data shared by every page. The links lead to legacy pages through the gateway. */
@ControllerAdvice
public class NavAdvice {

    @ModelAttribute
    void nav(Model model, Authentication auth) {
        model.addAttribute("username", auth == null ? null : auth.getName());
        model.addAttribute("canReports", Roles.any(auth, "ADMIN", "SUPERVISOR", "AUDITOR"));
        model.addAttribute("canAdmin", Roles.any(auth, "ADMIN"));
    }
}
