package com.fitmatch.dto.payment;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Ghi chú quyết định (duyệt/từ chối/đã chi) cho refund & withdrawal. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DecisionNoteRequest {

    @Size(max = 500)
    private String note;
}
