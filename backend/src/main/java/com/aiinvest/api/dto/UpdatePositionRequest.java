package com.aiinvest.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class UpdatePositionRequest {
    @NotBlank
    private String symbol;
    @NotNull
    @Min(0)
    private BigDecimal percent;
    @NotNull
    @Min(0)
    private BigDecimal amountUsd;

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public BigDecimal getPercent() { return percent; }
    public void setPercent(BigDecimal percent) { this.percent = percent; }
    public BigDecimal getAmountUsd() { return amountUsd; }
    public void setAmountUsd(BigDecimal amountUsd) { this.amountUsd = amountUsd; }
}

