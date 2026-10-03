package com.example.edu_shop.api.stats;

import java.time.Instant;

public record OrderStats(
		long ordersNew,
		long ordersProcessing,
		long ordersDone,
		Instant lastProcessedAt) {
}
