package org.teamsai.saimockbank.domain.identity.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class IdentityTestPageController {

    @GetMapping("/identity-test")
    public String identityTestPage() {
        return "identity/identity-test";
    }
}