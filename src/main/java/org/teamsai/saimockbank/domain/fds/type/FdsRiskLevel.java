package org.teamsai.saimockbank.domain.fds.type;

public enum FdsRiskLevel {

    LOW(0, 29),
    MEDIUM(30, 59),
    HIGH(60, 100);

    private final int minScore;
    private final int maxScore;

    FdsRiskLevel(int minScore, int maxScore) {
        this.minScore = minScore;
        this.maxScore = maxScore;
    }

    public static FdsRiskLevel fromScore(int score) {

        if (score < 0 || score > 100) {
            throw new IllegalArgumentException(
                    "위험 점수는 0~100 사이여야 합니다."
            );
        }

        for (FdsRiskLevel level : values()) {
            if (score >= level.minScore
                    && score <= level.maxScore) {
                return level;
            }
        }

        throw new IllegalStateException(
                "위험 수준을 결정할 수 없습니다."
        );
    }

    public int getMinScore() {
        return minScore;
    }

    public int getMaxScore() {
        return maxScore;
    }
}
