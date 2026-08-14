package com.fitmatch.repository;

import com.fitmatch.entity.TicketServiceItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TicketServiceItemRepository extends JpaRepository<TicketServiceItem, Long> {

    List<TicketServiceItem> findByTicket_IdOrderByIdAsc(Long ticketId);
}
