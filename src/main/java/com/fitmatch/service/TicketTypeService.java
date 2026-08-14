package com.fitmatch.service;

import com.fitmatch.common.enums.CatalogStatus;
import com.fitmatch.common.enums.TicketKind;
import com.fitmatch.dto.ticket.MarketplaceTicketTypeResponse;
import com.fitmatch.dto.ticket.TicketTypeRequest;
import com.fitmatch.dto.ticket.TicketTypeResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

/**
 * Catalog vé của Gym — thay {@link GymServiceCatalogService} và
 * {@link TrainingPackageService}. Vé khai một lần ở cấp gym rồi tick chọn chi
 * nhánh áp dụng (câu 20).
 */
public interface TicketTypeService {

    TicketTypeResponse create(String username, TicketTypeRequest request);

    TicketTypeResponse update(String username, Long id, TicketTypeRequest request);

    /** Ẩn khỏi marketplace; vé đã bán không bị ảnh hưởng (giá đã snapshot). */
    void deactivate(String username, Long id);

    List<TicketTypeResponse> list(String username);

    TicketTypeResponse updateCatalogStatus(String username, Long id, CatalogStatus status);

    /** Marketplace: vé đang bán tại một chi nhánh (không cần đăng nhập). */
    List<TicketTypeResponse> listForBranch(Long branchId);

    /**
     * Marketplace: duyệt vé toàn sàn (không cần đăng nhập). Mọi tham số lọc đều
     * cho phép null = không lọc; sắp xếp và phân trang theo {@code pageable}.
     *
     * @param kind null = cả vé ngày lẫn vé gói
     */
    Page<MarketplaceTicketTypeResponse> searchPublic(TicketKind kind, String keyword,
                                                     String city, String district,
                                                     BigDecimal minPrice, BigDecimal maxPrice,
                                                     Pageable pageable);
}
