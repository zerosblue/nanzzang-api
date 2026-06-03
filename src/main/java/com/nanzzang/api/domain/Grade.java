package com.nanzzang.api.domain;

public enum Grade {
    BRONZE, SILVER, GOLD, PLATINUM, DIAMOND, LEGEND;

    public static Grade of(int winCount) {
        if (winCount >= 100) return LEGEND;
        if (winCount >= 50)  return DIAMOND;
        if (winCount >= 30)  return PLATINUM;
        if (winCount >= 15)  return GOLD;
        if (winCount >= 5)   return SILVER;
        return BRONZE;
    }
}
