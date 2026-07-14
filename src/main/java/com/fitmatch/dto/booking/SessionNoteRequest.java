package com.fitmatch.dto.booking;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** PT/Gym ghi chú buổi tập kèm bằng chứng (UC-048). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SessionNoteRequest {

    @NotBlank(message = "Note is required")
    @Size(max = 2000)
    private String note;

    @Size(max = 500)
    private String evidenceUrl;
}
