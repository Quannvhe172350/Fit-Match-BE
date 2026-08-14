package com.fitmatch.dto.user;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmergencyContactDto {

    private String name;
    private String relationship;
    /**
     * Người nhà để gọi khi có sự cố trong buổi tập — một số sai ở đây là vô dụng
     * đúng lúc cần nhất. Cấp trên đã có {@code @Valid} nên ràng buộc này chạy thật.
     */
    @Size(max = 30)
    @com.fitmatch.common.validation.VietnamPhone
    private String phone;
}
