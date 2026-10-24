package com.example.edu_shop.api.visits;

/**
 * @param source откуда взят счётчик: {@code db} или {@code cache}
 * @param lastId id записи, созданной этим запросом; при ответе из кэша запись не создаётся и поле пустое
 */
public record VisitsResponse(long count, String source, Long lastId) {

	static VisitsResponse fromDatabase(long count, long lastId) {
		return new VisitsResponse(count, "db", lastId);
	}

	static VisitsResponse fromCache(long count) {
		return new VisitsResponse(count, "cache", null);
	}

}
