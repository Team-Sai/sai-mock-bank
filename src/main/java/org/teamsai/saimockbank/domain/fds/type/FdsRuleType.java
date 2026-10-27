package org.teamsai.saimockbank.domain.fds.type;

public enum FdsRuleType {

    R01("자동화 소액 송금 의심", 40),
    R02("입금 직후 자금 분산 의심", 50),
    R03("특정 계좌 집중 송금 의심", 30),
    R04("해외 IP 송금 의심", 90);

    private final String description;
    private final int riskScore;

    FdsRuleType(String description, int riskScore) {
        this.description = description;
        this.riskScore = riskScore;
    }

    public String getDescription() {
        return description;
    }

    public int getRiskScore() {
        return riskScore;
    }
}