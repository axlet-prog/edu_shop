package com.example.edu_shop.api.visits;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ручка для интеграционных тестов ЛР3: одним запросом задействует и PostgreSQL, и Redis.
 */
@RestController
public class VisitController {

	private final VisitService visitService;

	public VisitController(VisitService visitService) {
		this.visitService = visitService;
	}

	@GetMapping("/visits")
	public VisitsResponse visits() {
		return visitService.registerVisit();
	}

}
