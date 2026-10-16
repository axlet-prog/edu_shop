package com.example.edu_shop.worker;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import io.prometheus.metrics.expositionformats.PrometheusTextFormatWriter;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.example.edu_shop.messaging.OrderCreatedMessage;
import com.example.edu_shop.order.OrderRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class OrderProcessorTest {

	private final OrderRepository orders = mock(OrderRepository.class);

	private final PrometheusRegistry registry = new PrometheusRegistry();

	private final OrderProcessor processor =
			new OrderProcessor(orders, new WorkerProperties(Duration.ZERO, Duration.ZERO), registry);

	@Test
	void marksOrderProcessingThenDone() throws Exception {
		given(orders.markProcessing(42L)).willReturn(true);

		processor.process(new OrderCreatedMessage(42L));

		InOrder inOrder = inOrder(orders);
		inOrder.verify(orders).markProcessing(42L);
		inOrder.verify(orders).markDone(eq(42L), any(Instant.class));
		assertThat(scrape()).contains(
				"# TYPE shop_order_processing_duration_seconds histogram",
				"shop_order_processing_duration_seconds_count 1");
	}

	@Test
	void skipsOrderThatIsMissingOrAlreadyDone() throws Exception {
		given(orders.markProcessing(42L)).willReturn(false);

		processor.process(new OrderCreatedMessage(42L));

		verify(orders, never()).markDone(anyLong(), any());
		// Время обработки записывается только для заказов, которые дошли до DONE
		assertThat(scrape()).contains("shop_order_processing_duration_seconds_count 0");
	}

	/** Метрики в том виде, в каком их увидит Prometheus на /metrics. */
	private String scrape() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PrometheusTextFormatWriter.create().write(out, registry.scrape());
		return out.toString(StandardCharsets.UTF_8);
	}

}
