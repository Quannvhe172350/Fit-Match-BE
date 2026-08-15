package com.fitmatch.service;

import com.fitmatch.common.enums.CatalogStatus;
import com.fitmatch.common.enums.TicketKind;
import com.fitmatch.entity.GymBranch;
import com.fitmatch.entity.GymProfile;
import com.fitmatch.entity.TicketType;
import com.fitmatch.entity.TicketTypeBranch;
import com.fitmatch.entity.User;
import com.fitmatch.dto.ticket.TicketTypeRequest;
import com.fitmatch.repository.GymBranchRepository;
import com.fitmatch.repository.TicketTypeRepository;
import com.fitmatch.service.impl.TicketTypeServiceImpl;
import com.fitmatch.service.support.GymProfileResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Liên kết loại vé ↔ chi nhánh.
 *
 * <p>{@code ticket_type_branches} có unique key {@code (ticket_type_id,
 * gym_branch_id)}, mà trong một lần flush Hibernate chạy INSERT TRƯỚC DELETE.
 * Nên "xoá sạch rồi thêm lại" làm mọi lần sửa gói tập giữ nguyên chi nhánh — tức
 * gần như mọi lần sửa, vì form gửi lại y nguyên danh sách cũ — chết vì trùng
 * khoá. Mock repository không dựng lại được ràng buộc đó, nên test khoá thẳng
 * hành vi sinh ra nó: hàng đã có phải được GIỮ, không phải tạo lại.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TicketTypeBranchLinkTest {

    private static final String GYM_USER = "gym1";
    private static final Long TYPE_ID = 5L;

    @Mock private TicketTypeRepository ticketTypeRepository;
    @Mock private GymBranchRepository gymBranchRepository;
    @Mock private GymProfileResolver gymProfileResolver;
    @InjectMocks private TicketTypeServiceImpl service;

    private GymProfile gym;

    @BeforeEach
    void setUp() {
        gym = GymProfile.builder().id(1L).gymName("Gym A")
                .user(User.builder().id(2L).username(GYM_USER).build()).build();
        when(gymProfileResolver.requireApprovedGym(GYM_USER)).thenReturn(gym);
        when(ticketTypeRepository.save(any(TicketType.class))).thenAnswer(inv -> inv.getArgument(0));
        branch(10L, "Chi nhánh 1");
        branch(11L, "Chi nhánh 2");
    }

    private GymBranch branch(Long id, String name) {
        GymBranch b = GymBranch.builder().id(id).name(name).gymProfile(gym).active(true).build();
        when(gymBranchRepository.findById(id)).thenReturn(Optional.of(b));
        return b;
    }

    /** Loại vé đang bán ở đúng những chi nhánh trong {@code branchIds}. */
    private TicketType existingType(Long... branchIds) {
        TicketType type = TicketType.builder().id(TYPE_ID).gymProfile(gym)
                .name("Gói 10 ngày").kind(TicketKind.PACKAGE).dayCount(10)
                .price(BigDecimal.valueOf(1_000_000))
                .status(CatalogStatus.PUBLISHED).active(true)
                .build();
        for (Long branchId : branchIds) {
            type.getBranches().add(TicketTypeBranch.builder()
                    .id(branchId * 100)
                    .ticketType(type)
                    .gymBranch(gymBranchRepository.findById(branchId).orElseThrow())
                    .build());
        }
        when(ticketTypeRepository.findByIdAndGymProfile_User_Username(TYPE_ID, GYM_USER))
                .thenReturn(Optional.of(type));
        return type;
    }

    private TicketTypeRequest request(Long... branchIds) {
        return TicketTypeRequest.builder()
                .name("Gói 10 ngày").kind(TicketKind.PACKAGE).dayCount(10)
                .price(BigDecimal.valueOf(1_200_000))
                .branchIds(List.of(branchIds))
                .build();
    }

    /** Sửa giá nhưng giữ nguyên chi nhánh: KHÔNG được tạo lại hàng liên kết. */
    @Test
    void updateKeepingSameBranches_reusesExistingLinks() {
        TicketType type = existingType(10L, 11L);
        List<TicketTypeBranch> before = List.copyOf(type.getBranches());

        service.update(GYM_USER, TYPE_ID, request(10L, 11L));

        assertThat(type.getBranches()).containsExactlyInAnyOrderElementsOf(before);
        assertThat(type.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(1_200_000));
    }

    @Test
    void updateAddingBranch_keepsOldLinkAndAddsOne() {
        TicketType type = existingType(10L);
        TicketTypeBranch kept = type.getBranches().get(0);

        service.update(GYM_USER, TYPE_ID, request(10L, 11L));

        assertThat(type.getBranches()).hasSize(2).contains(kept);
        assertThat(type.getBranches().stream().map(l -> l.getGymBranch().getId()))
                .containsExactlyInAnyOrder(10L, 11L);
    }

    @Test
    void updateRemovingBranch_dropsOnlyThatLink() {
        TicketType type = existingType(10L, 11L);

        service.update(GYM_USER, TYPE_ID, request(10L));

        assertThat(type.getBranches()).hasSize(1);
        assertThat(type.getBranches().get(0).getGymBranch().getId()).isEqualTo(10L);
    }

    /** Request gửi trùng id chi nhánh cũng không được sinh ra hai hàng. */
    @Test
    void duplicateBranchIdsInRequest_produceOneLink() {
        TicketType type = existingType();

        service.update(GYM_USER, TYPE_ID, request(10L, 10L, 11L));

        assertThat(type.getBranches()).hasSize(2);
    }

    @Test
    void create_linksAllRequestedBranches() {
        service.create(GYM_USER, request(10L, 11L));
        // Không ném lỗi và không cần tới collection cũ — nhánh tạo mới dùng chung
        // đúng một hàm với nhánh sửa, nên nó phải chạy được với collection rỗng.
    }
}
