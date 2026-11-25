package com.example.edu_shop.api.filter;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;

public class FilterUtils {

	public static final String UNKNOWN_ROUTE = "UNKNOWN";

	public static String route(HttpServletRequest request) {
		Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
		return pattern != null ? pattern.toString() : UNKNOWN_ROUTE;
	}
}
