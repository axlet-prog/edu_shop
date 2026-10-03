package com.example.edu_shop.api.order;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.example.edu_shop.order.Order;

@RestController
@RequestMapping("/orders")
public class OrderController {

	private final OrderService orderService;

	public OrderController(OrderService orderService) {
		this.orderService = orderService;
	}

	@PostMapping
	public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
		Order order = orderService.create(request.amount(), request.itemsCount());
		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
				.path("/{id}")
				.buildAndExpand(order.id())
				.toUri();
		return ResponseEntity.created(location).body(OrderResponse.from(order));
	}

	@GetMapping("/{id}")
	public OrderResponse get(@PathVariable long id) {
		return OrderResponse.from(orderService.get(id));
	}

	@GetMapping
	public List<OrderResponse> list() {
		return orderService.findAll().stream().map(OrderResponse::from).toList();
	}

}
