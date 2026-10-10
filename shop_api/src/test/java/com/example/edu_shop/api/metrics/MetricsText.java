package com.example.edu_shop.api.metrics;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import io.prometheus.metrics.expositionformats.PrometheusTextFormatWriter;
import io.prometheus.metrics.model.registry.PrometheusRegistry;

public final class MetricsText {

	private MetricsText() {
	}

	/** Метрики реестра в том виде, в каком их увидит Prometheus на /metrics. */
	public static String scrape(PrometheusRegistry registry) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			PrometheusTextFormatWriter.create().write(out, registry.scrape());
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		return out.toString(StandardCharsets.UTF_8);
	}

}
