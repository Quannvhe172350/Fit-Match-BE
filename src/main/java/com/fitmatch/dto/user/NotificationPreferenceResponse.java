package com.fitmatch.dto.user;

import com.fitmatch.entity.NotificationPreference;
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
public class NotificationPreferenceResponse {

    private boolean emailEnabled;
    private boolean pushEnabled;
    private boolean marketingEnabled;
    private boolean bookingReminders;

    public static NotificationPreferenceResponse of(NotificationPreference p) {
        return NotificationPreferenceResponse.builder()
                .emailEnabled(p.isEmailEnabled())
                .pushEnabled(p.isPushEnabled())
                .marketingEnabled(p.isMarketingEnabled())
                .bookingReminders(p.isBookingReminders())
                .build();
    }
}
