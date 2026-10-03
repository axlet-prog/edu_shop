package com.example.edu_shop.order;

import java.time.Instant;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.ListPagingAndSortingRepository;

public interface OrderRepository extends ListCrudRepository<Order, Long>, ListPagingAndSortingRepository<Order, Long> {

	/**
	 * Переводит заказ в PROCESSING. Возвращает {@code false}, если заказа нет или он уже DONE.
	 * <p>
	 * Заказ в статусе PROCESSING тоже подходит: если Worker упал посреди обработки,
	 * RabbitMQ доставит сообщение повторно, и обработка начнётся заново.
	 */
	@Modifying
	@Query("UPDATE orders SET status = 'PROCESSING' WHERE id = :id AND status IN ('NEW', 'PROCESSING')")
	boolean markProcessing(long id);

	@Modifying
	@Query("UPDATE orders SET status = 'DONE', processed_at = :processedAt WHERE id = :id AND status = 'PROCESSING'")
	void markDone(long id, Instant processedAt);

}
