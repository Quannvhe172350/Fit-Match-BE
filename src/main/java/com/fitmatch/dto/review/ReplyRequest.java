package com.fitmatch.dto.review;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Gym phản hồi một review (UC-069). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReplyRequest {

    @NotBlank(message = "reply is required")
    @Size(max = 2000)
    private String reply;
}
