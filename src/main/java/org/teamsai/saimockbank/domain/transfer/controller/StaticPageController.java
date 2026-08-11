package org.teamsai.saimockbank.domain.transfer.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class StaticPageController {
    @GetMapping("/transfer")
    public String transferPage() {
        return "transfer/transfer";
    }

    @GetMapping("/home")
    public String homePage() {
        return "/home/home";
    }
    @GetMapping("/transfer-history")
    public String transferHistoryPage() {
        return "/transfer/transfer-history";
    }
}
