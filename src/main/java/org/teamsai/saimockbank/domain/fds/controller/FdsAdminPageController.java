package org.teamsai.saimockbank.domain.fds.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class FdsAdminPageController {

    @GetMapping({"/admin", "/admin/"})
    public String adminPage() {
        return "admin/admin";
    }

    @GetMapping("/admin-denied")
    public String adminDeniedPage() {
        return "admin/denied";
    }
}