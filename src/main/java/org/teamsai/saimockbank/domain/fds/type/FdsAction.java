package org.teamsai.saimockbank.domain.fds.type;

public enum FdsAction {

    ALLOW("정상 거래 허용"),
    MONITOR("거래 허용 및 이상거래 기록"),
    HOLD("거래 보류"),
    BLOCK("거래 차단");

    private final String description;

    FdsAction(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
