package com.example.edu_shop.api.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.edu_shop.order.Order;
import com.example.edu_shop.order.OrderStatus;

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

	private static final Order ORDER = new Order(42L, new BigDecimal("1500.00"), 3, OrderStatus.NEW,
			Instant.parse("2026-01-01T10:00:00Z"), null);

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private OrderService orderService;

	@Test
	void createReturnsCreatedOrderWithLocation() throws Exception {
		given(orderService.create(new BigDecimal("1500.00"), 3)).willReturn(ORDER);

		mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content("""
				{"amount": 1500.00, "items_count": 3}
				"""))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", endsWith("/orders/42")))
				.andExpect(jsonPath("$.id").value(42))
				.andExpect(jsonPath("$.items_count").value(3))
				.andExpect(jsonPath("$.status").value("NEW"));
	}

	@Test
	void createRejectsInvalidRequest() throws Exception {
		mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content("""
				{"amount": -1, "items_count": 0}
				"""))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

		verifyNoInteractions(orderService);
	}

	@Test
	void getReturnsOrder() throws Exception {
		given(orderService.get(42L)).willReturn(ORDER);

		mvc.perform(get("/orders/42"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(42))
				.andExpect(jsonPath("$.created_at").value("2026-01-01T10:00:00Z"));
	}

	@Test
	void getReturnsProblemDetailsForUnknownOrder() throws Exception {
		given(orderService.get(99L)).willThrow(new OrderNotFoundException(99L));

		mvc.perform(get("/orders/99"))
				.andExpect(status().isNotFound())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.detail").value("Order 99 not found"));
	}

	@Test
	void listReturnsAllOrders() throws Exception {
		given(orderService.findAll()).willReturn(List.of(ORDER));

		mvc.perform(get("/orders"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id").value(42));
	}

}
