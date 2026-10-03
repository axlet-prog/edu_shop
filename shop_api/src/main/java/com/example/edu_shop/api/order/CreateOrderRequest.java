package com.example.edu_shop.api.order;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateOrderRequest(
		@NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal amount,
		@NotNull @Positive Integer itemsCount) {
}
