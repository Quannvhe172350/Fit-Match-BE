package com.fitmatch.service;

import com.fitmatch.common.enums.TicketKind;
import com.fitmatch.repository.GymBranchRepository;
import com.fitmatch.repository.TicketTypeRepository;
import com.fitmatch.service.impl.TicketTypeServiceImpl;
import com.fitmatch.service.support.GymProfileResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Duyệt vé toàn sàn — chuẩn hoá tham số lọc.
 *
 * <p>Điểm dễ hỏng: query param vắng mặt cho ra {@code null}, nhưng ô lọc trên FE
 * gửi lên CHUỖI RỖNG khi người dùng bỏ chọn. Truy vấn dùng mẫu
 * {@code (:city is null or g.city = :city)} nên chuỗi rỗng KHÔNG kích hoạt nhánh
 * "bỏ lọc" mà đi so khớp {@code g.city = ''} — kết quả về 0 dòng, và người dùng
 * chỉ thấy "không tìm thấy gói tập nào" chứ không có lỗi nào để lần ra.
 */
@ExtendWith(MockitoExtension.class)
class TicketTypeSearchTest {

    @Mock private TicketTypeRepository ticketTypeRepository;
    @Mock private GymBranchRepository gymBranchRepository;
    @Mock private GymProfileResolver gymProfileResolver;
    @InjectMocks private TicketTypeServiceImpl service;

    private static final Pageable PAGE = PageRequest.of(0, 12);

    private void stubEmptyPage() {
        when(ticketTypeRepository.searchPublic(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
    }

    /** Bắt được đúng các đối số đã truyền xuống repository. */
    private Object[] captureArgs() {
        ArgumentCaptor<String> keyword = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> city = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> district = ArgumentCaptor.forClass(String.class);
        verify(ticketTypeRepository).searchPublic(any(), keyword.capture(), city.capture(),
                district.capture(), any(), any(), any());
        return new Object[]{keyword.getValue(), city.getValue(), district.getValue()};
    }

    @Test
    void blankFilters_areSentAsNull_soTheyDoNotFilterEverythingOut() {
        stubEmptyPage();

        service.searchPublic(TicketKind.PACKAGE, "   ", "", "  ", null, null, PAGE);

        assertThat(captureArgs())
                .as("chuỗi rỗng/khoảng trắng phải thành null = không lọc")
                .containsExactly(null, null, null);
    }

    @Test
    void nullFilters_staySentAsNull() {
        stubEmptyPage();

        service.searchPublic(null, null, null, null, null, null, PAGE);

        assertThat(captureArgs()).containsExactly(null, null, null);
    }

    @Test
    void realFilters_areTrimmedButPreserved() {
        stubEmptyPage();

        service.searchPublic(TicketKind.PACKAGE, "  yoga ", " Ha Noi", "Thanh Xuan  ",
                BigDecimal.ZERO, new BigDecimal("1000000"), PAGE);

        assertThat(captureArgs()).containsExactly("yoga", "Ha Noi", "Thanh Xuan");
    }

    @Test
    void kindAndPriceBounds_arePassedThroughUntouched() {
        stubEmptyPage();
        BigDecimal min = new BigDecimal("100000");
        BigDecimal max = new BigDecimal("500000");

        service.searchPublic(TicketKind.DAY, null, null, null, min, max, PAGE);

        // Giá KHÔNG được chuẩn hoá: 0 là cận dưới hợp lệ ("dưới 1 triệu" gửi min=0),
        // nếu lỡ coi 0 là "bỏ lọc" thì khoảng giá thấp nhất sẽ sai.
        verify(ticketTypeRepository).searchPublic(TicketKind.DAY, null, null, null, min, max, PAGE);
    }

    @Test
    void emptyResult_mapsToEmptyPage_withoutTouchingEntities() {
        stubEmptyPage();

        Page<?> result = service.searchPublic(TicketKind.PACKAGE, null, null, null, null, null, PAGE);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }
}
