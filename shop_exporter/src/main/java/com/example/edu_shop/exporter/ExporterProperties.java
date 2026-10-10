package com.example.edu_shop.exporter;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param apiUrl адрес API, у которого exporter запрашивает GET /internal/stats (переменная окружения SHOP_API_URL)
 */
@ConfigurationProperties("shop.exporter")
public record ExporterProperties(URI apiUrl) {
}
