package com.fitmatch.service;

import com.fitmatch.dto.pt.AvailabilitySlotDto;
import com.fitmatch.dto.pt.UpdateAvailabilityRequest;
import com.fitmatch.entity.Booking;
import com.fitmatch.entity.PtProfile;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.repository.AvailabilitySlotRepository;
import com.fitmatch.repository.BookingRepository;
import com.fitmatch.repository.PtProfileRepository;
import com.fitmatch.service.impl.PtAvailabilityServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** P1-15 (C-2): thu hẹp lịch rảnh PT không được bỏ rơi booking đang giữ chỗ. */
@ExtendWith(MockitoExtension.class)
class PtScheduleNarrowingGuardTest {

    @Mock private AvailabilitySlotRepository availabilitySlotRepository;
    @Mock private PtProfileRepository ptProfileRepository;
    @Mock private BookingRepository bookingRepository;
    @InjectMocks private PtAvailabilityServiceImpl service;

    private UpdateAvailabilityRequest mondayMorning() {
        return UpdateAvailabilityRequest.builder()
                .slots(List.of(AvailabilitySlotDto.builder()
                        .dayOfWeek(1).startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(10, 0)).build()))
                .build();
    }

    @Test
    void replaceSlots_upcomingBookingOutsideNewSlots_blocks() {
        PtProfile pt = PtProfile.builder().id(2L).build();
        when(ptProfileRepository.findByIdAndGymProfile_User_Username(2L, "gym"))
                .thenReturn(Optional.of(pt));
        // Booking chiều thứ Hai 18:00-19:00 — ngoài slot mới 08:00-10:00.
        Booking b = Booking.builder().id(9L)
                .startAt(LocalDateTime.of(2026, 7, 20, 18, 0))
                .endAt(LocalDateTime.of(2026, 7, 20, 19, 0))
                .build();
        when(bookingRepository.findByPtProfile_IdAndStatusInAndStartAtGreaterThan(eq(2L), any(), any()))
                .thenReturn(List.of(b));

        assertThatThrownBy(() -> service.updateForGym("gym", 2L, mondayMorning()))
                .isInstanceOf(BusinessException.class);

        verify(availabilitySlotRepository, never()).deleteByPtProfile_Id(any());
    }

    @Test
    void replaceSlots_noUpcomingBookings_proceeds() {
        PtProfile pt = PtProfile.builder().id(2L).build();
        when(ptProfileRepository.findByIdAndGymProfile_User_Username(2L, "gym"))
                .thenReturn(Optional.of(pt));
        when(bookingRepository.findByPtProfile_IdAndStatusInAndStartAtGreaterThan(eq(2L), any(), any()))
                .thenReturn(List.of());
        when(availabilitySlotRepository.saveAll(any())).thenReturn(List.of());

        var result = service.updateForGym("gym", 2L, mondayMorning());

        assertThat(result).isNotNull();
        verify(availabilitySlotRepository).deleteByPtProfile_Id(2L);
    }
}
