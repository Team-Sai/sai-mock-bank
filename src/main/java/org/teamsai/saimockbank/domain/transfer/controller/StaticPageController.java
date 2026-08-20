package org.teamsai.saimockbank.domain.transfer.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class StaticPageController { //더이상 이체 기능에 수정이 필요없을 때 지울 예정
    @GetMapping("/transfer")
    public String transferPage() {
        return "transfer/transfer";
    }

    @GetMapping("/home")
    public String homePage() {
        return "home/home";
    }
    @GetMapping("/transfer-history")
    public String transferHistoryPage() {
        return "transfer/transfer-history";
    }

    @GetMapping("/transactions")
    public String transactions() {
        return "transaction/transactions";
    }
}
