package com.example.edu_shop.api.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

import com.example.edu_shop.messaging.OrderMessaging;

public class HeaderRequestFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(HeaderRequestFilter.class);

	private static final String REQUEST_ID_HEADER = "X-Request-ID";

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String requestId = request.getHeader(REQUEST_ID_HEADER);
		if (!StringUtils.hasText(requestId)) {
			requestId = UUID.randomUUID().toString();
		}

		MDC.put(OrderMessaging.REQUEST_ID_LOG_KEY, requestId);


		log.atInfo()
				.addKeyValue("event", "request_started")
				.addKeyValue("method", request.getMethod())
				.addKeyValue("route", request.getRequestURI())
				.log("Started processing request");

		response.setHeader(REQUEST_ID_HEADER, requestId);
		long startTime = System.nanoTime();
		boolean failed = true;
		try {
			filterChain.doFilter(request, response);
			failed = false;
		} finally {
			int status = failed ? HttpServletResponse.SC_INTERNAL_SERVER_ERROR : response.getStatus();
			log.atInfo()
					.addKeyValue("event", "request_finished")
					.addKeyValue("method", request.getMethod())
					.addKeyValue("route", request.getRequestURI())
					.addKeyValue("status", status)
					.addKeyValue("duration_ms", Duration.ofNanos(System.nanoTime() - startTime).toMillis())
					.log("Finished processing request");
			MDC.remove(OrderMessaging.REQUEST_ID_LOG_KEY);
		}
	}
}
