package com.example.edu_shop.api.order;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class OrderNotFoundException extends ErrorResponseException {

	public OrderNotFoundException(long id) {
		super(HttpStatus.NOT_FOUND,
				ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Order %d not found".formatted(id)), null);
	}

}
