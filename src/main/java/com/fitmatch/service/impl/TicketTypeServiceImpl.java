package com.fitmatch.service.impl;

import com.fitmatch.common.enums.CatalogStatus;
import com.fitmatch.common.enums.ErrorCode;
import com.fitmatch.common.enums.TicketKind;
import com.fitmatch.dto.ticket.MarketplaceTicketTypeResponse;
import com.fitmatch.dto.ticket.TicketTypeRequest;
import com.fitmatch.dto.ticket.TicketTypeResponse;
import com.fitmatch.entity.GymBranch;
import com.fitmatch.entity.GymProfile;
import com.fitmatch.entity.TicketType;
import com.fitmatch.entity.TicketTypeBranch;
import com.fitmatch.exception.BusinessException;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.GymBranchRepository;
import com.fitmatch.repository.TicketTypeRepository;
import com.fitmatch.service.TicketTypeService;
import com.fitmatch.service.support.GymProfileResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketTypeServiceImpl implements TicketTypeService {

    private final TicketTypeRepository ticketTypeRepository;
    private final GymBranchRepository gymBranchRepository;
    private final GymProfileResolver gymProfileResolver;

    @Override
    @Transactional
    public TicketTypeResponse create(String username, TicketTypeRequest request) {
        GymProfile gym = gymProfileResolver.requireApprovedGym(username);
        TicketType type = TicketType.builder()
                .gymProfile(gym)
                .name(request.getName())
                .description(request.getDescription())
                .kind(request.getKind())
                .dayCount(normalizeDayCount(request))
                .minutesPerDay(request.getMinutesPerDay())
                .price(request.getPrice())
                .ptSurchargePerDay(request.getPtSurchargePerDay())
                .status(CatalogStatus.PUBLISHED)
                .active(true)
                .build();
        replaceBranches(type, gym, request.getBranchIds());

        TicketType saved = ticketTypeRepository.save(type);
        log.info("Gym {} created ticket type {} ({} x{} ngày) cho {} chi nhánh",
                username, saved.getId(), saved.getKind(), saved.getDayCount(),
                saved.getBranches().size());
        return TicketTypeResponse.of(saved);
    }

    @Override
    @Transactional
    public TicketTypeResponse update(String username, Long id, TicketTypeRequest request) {
        TicketType type = requireOwned(username, id);
        if (type.getStatus() == CatalogStatus.ARCHIVED) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "An ARCHIVED ticket type cannot be edited");
        }
        type.setName(request.getName());
        type.setDescription(request.getDescription());
        type.setKind(request.getKind());
        type.setDayCount(normalizeDayCount(request));
        // Sửa loại vé chỉ đổi luật cho vé bán TỪ ĐÂY: vé đã bán giữ snapshot riêng.
        type.setMinutesPerDay(request.getMinutesPerDay());
        type.setPrice(request.getPrice());
        type.setPtSurchargePerDay(request.getPtSurchargePerDay());
        replaceBranches(type, type.getGymProfile(), request.getBranchIds());
        // Vé đã bán giữ nguyên giá và số ngày đã snapshot — sửa ở đây chỉ ảnh
        // hưởng lần mua tiếp theo.
        return TicketTypeResponse.of(ticketTypeRepository.save(type));
    }

    @Override
    @Transactional
    public void deactivate(String username, Long id) {
        TicketType type = requireOwned(username, id);
        type.setActive(false);
        type.setStatus(CatalogStatus.HIDDEN);
        ticketTypeRepository.save(type);
        log.info("Gym {} deactivated ticket type {}", username, id);
    }

    @Override
    @Transactional
    public TicketTypeResponse updateCatalogStatus(String username, Long id, CatalogStatus status) {
        TicketType type = requireOwned(username, id);
        if (type.getStatus() == CatalogStatus.ARCHIVED) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "An ARCHIVED ticket type cannot change status");
        }
        type.setStatus(status);
        type.setActive(status == CatalogStatus.PUBLISHED);
        log.info("Gym {} set ticket type {} catalog status to {}", username, id, status);
        return TicketTypeResponse.of(ticketTypeRepository.save(type));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TicketTypeResponse> list(String username) {
        GymProfile gym = gymProfileResolver.requireApprovedGym(username);
        return ticketTypeRepository.findByGymProfile_IdOrderByIdDesc(gym.getId())
                .stream().map(TicketTypeResponse::of).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TicketTypeResponse> listForBranch(Long branchId) {
        if (!gymBranchRepository.existsById(branchId)) {
            throw new ResourceNotFoundException("Gym branch", branchId);
        }
        return ticketTypeRepository
                .findDistinctByBranches_GymBranch_IdAndStatusAndActiveTrue(branchId, CatalogStatus.PUBLISHED)
                .stream().map(TicketTypeResponse::of).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MarketplaceTicketTypeResponse> searchPublic(TicketKind kind, String keyword,
                                                            String city, String district,
                                                            BigDecimal minPrice, BigDecimal maxPrice,
                                                            Pageable pageable) {
        // Chuỗi rỗng từ query param phải thành null, nếu không điều kiện
        // `:city is null` không kích hoạt và bộ lọc so khớp với '' => 0 kết quả.
        return ticketTypeRepository
                .searchPublic(kind, blankToNull(keyword), blankToNull(city), blankToNull(district),
                        minPrice, maxPrice, pageable)
                .map(MarketplaceTicketTypeResponse::of);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Vé DAY luôn một ngày; vé PACKAGE phải có ít nhất hai ngày mới gọi là gói. */
    private Integer normalizeDayCount(TicketTypeRequest request) {
        if (request.getKind() == TicketKind.DAY) {
            return 1;
        }
        Integer dayCount = request.getDayCount();
        if (dayCount == null || dayCount < 2) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "A PACKAGE ticket needs dayCount >= 2");
        }
        return dayCount;
    }

    /**
     * Đưa danh sách chi nhánh của loại vé về đúng {@code branchIds}, bằng cách
     * CHỈ gỡ cái không còn và CHỈ thêm cái chưa có.
     *
     * <p>Sửa TRÊN collection hiện có chứ không thay bằng list mới: collection
     * dùng {@code orphanRemoval} và Hibernate không chấp nhận việc thay thế một
     * collection đang được quản lý.
     *
     * <p>Không xoá sạch rồi thêm lại: {@code ticket_type_branches} có unique key
     * {@code (ticket_type_id, gym_branch_id)}, mà trong một lần flush Hibernate
     * chạy INSERT TRƯỚC DELETE. Sửa gói tập nhưng giữ nguyên chi nhánh — thao tác
     * thường gặp nhất, vì form gửi lại y nguyên danh sách cũ — sẽ chèn hàng trùng
     * đúng hàng chưa kịp xoá và chết ở uk_ttb_type_branch.
     *
     * <p>Giữ lại hàng cũ còn cho một cái lợi nữa: id và mốc tạo của liên kết
     * không đổi mỗi lần gym sửa giá.
     */
    private void replaceBranches(TicketType type, GymProfile gym, List<Long> branchIds) {
        List<GymBranch> branches = resolveBranches(gym, branchIds);
        Set<Long> wanted = branches.stream().map(GymBranch::getId).collect(Collectors.toSet());

        type.getBranches().removeIf(link -> !wanted.contains(link.getGymBranch().getId()));

        Set<Long> existing = type.getBranches().stream()
                .map(link -> link.getGymBranch().getId())
                .collect(Collectors.toCollection(HashSet::new));
        for (GymBranch branch : branches) {
            // add() trả false khi chi nhánh đã có liên kết -> vừa lọc trùng trong
            // chính request, vừa bỏ qua hàng đang tồn tại.
            if (existing.add(branch.getId())) {
                type.getBranches().add(TicketTypeBranch.builder()
                        .ticketType(type)
                        .gymBranch(branch)
                        .build());
            }
        }
    }

    /** Chi nhánh phải tồn tại, còn hoạt động và thuộc đúng gym đang thao tác. */
    private List<GymBranch> resolveBranches(GymProfile gym, List<Long> branchIds) {
        return new LinkedHashSet<>(branchIds).stream()
                .map(branchId -> {
                    GymBranch branch = gymBranchRepository.findById(branchId)
                            .orElseThrow(() -> new ResourceNotFoundException("Gym branch", branchId));
                    if (!branch.getGymProfile().getId().equals(gym.getId())) {
                        throw new BusinessException(ErrorCode.FORBIDDEN,
                                "Branch " + branchId + " does not belong to your gym");
                    }
                    if (!branch.isActive()) {
                        throw new BusinessException(ErrorCode.INVALID_STATE,
                                "Branch '" + branch.getName() + "' is inactive");
                    }
                    return branch;
                })
                .toList();
    }

    private TicketType requireOwned(String username, Long id) {
        return ticketTypeRepository.findByIdAndGymProfile_User_Username(id, username)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket type", id));
    }
}
