package com.fitmatch.service.impl;

import com.fitmatch.common.enums.TicketStatus;
import com.fitmatch.common.response.PageResponse;
import com.fitmatch.dto.ticket.TicketResponse;
import com.fitmatch.entity.Ticket;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.TicketRepository;
import com.fitmatch.repository.TrainingSessionRepository;
import com.fitmatch.service.AdminTicketService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminTicketServiceImpl implements AdminTicketService {

    private final TicketRepository ticketRepository;
    private final TrainingSessionRepository sessionRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TicketResponse> search(TicketStatus status, Long gymProfileId,
                                               String customerUsername, Pageable pageable) {
        Specification<Ticket> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (gymProfileId != null) {
                predicates.add(cb.equal(root.get("gymProfile").get("id"), gymProfileId));
            }
            if (customerUsername != null && !customerUsername.isBlank()) {
                predicates.add(cb.equal(root.get("customer").get("username"), customerUsername));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
        return PageResponse.of(ticketRepository.findAll(spec, pageable), TicketResponse::of);
    }

    @Override
    @Transactional(readOnly = true)
    public TicketResponse detail(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));
        return TicketResponse.withSessions(ticket,
                sessionRepository.findByTicket_IdOrderByDayIndexAsc(ticketId));
    }
}
