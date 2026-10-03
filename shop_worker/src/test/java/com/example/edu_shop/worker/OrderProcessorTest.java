package com.example.edu_shop.worker;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.example.edu_shop.messaging.OrderCreatedMessage;
import com.example.edu_shop.order.OrderRepository;

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

	private final OrderProcessor processor = new OrderProcessor(orders, new WorkerProperties(Duration.ZERO));

	@Test
	void marksOrderProcessingThenDone() throws InterruptedException {
		given(orders.markProcessing(42L)).willReturn(true);

		processor.process(new OrderCreatedMessage(42L));

		InOrder inOrder = inOrder(orders);
		inOrder.verify(orders).markProcessing(42L);
		inOrder.verify(orders).markDone(eq(42L), any(Instant.class));
	}

	@Test
	void skipsOrderThatIsMissingOrAlreadyDone() throws InterruptedException {
		given(orders.markProcessing(42L)).willReturn(false);

		processor.process(new OrderCreatedMessage(42L));

		verify(orders, never()).markDone(anyLong(), any());
	}

}
