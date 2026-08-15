package com.fitmatch.service.impl;

import com.fitmatch.common.enums.SessionStatus;
import com.fitmatch.dto.ticket.TrainingSessionResponse;
import com.fitmatch.repository.TrainingSessionRepository;
import com.fitmatch.service.PtSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PtSessionServiceImpl implements PtSessionService {

    private final TrainingSessionRepository sessionRepository;

    @Override
    @Transactional(readOnly = true)
    public List<TrainingSessionResponse> mySessions(String ptUsername, LocalDate from, LocalDate to) {
        return sessionRepository
                .findByPtProfile_User_UsernameAndSessionDateBetweenAndStatusInOrderBySessionDateAscPtSlotStartAsc(
                        ptUsername, from, to, List.of(SessionStatus.SCHEDULED, SessionStatus.DONE))
                .stream().map(TrainingSessionResponse::forPt).toList();
    }
}
