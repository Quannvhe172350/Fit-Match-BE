package com.fitmatch.repository;

import com.fitmatch.common.enums.SessionStatus;
import com.fitmatch.entity.TrainingSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface TrainingSessionRepository extends JpaRepository<TrainingSession, Long> {

    List<TrainingSession> findByTicket_IdOrderByDayIndexAsc(Long ticketId);

    Optional<TrainingSession> findByIdAndTicket_Customer_Username(Long id, String username);

    Optional<TrainingSession> findByIdAndTicket_GymProfile_User_Username(Long id, String username);

    /** Số ngày của vé đã được đặt (không tính ngày bị huỷ) — chặn đặt vượt dayCount. */
    long countByTicket_IdAndStatusNot(Long ticketId, SessionStatus status);

    /**
     * Số ngày đã xếp của NHIỀU vé trong một truy vấn — cho danh sách "Vé của tôi".
     *
     * <p>Không dùng {@link #countByTicket_IdAndStatusNot} lặp theo từng vé: một
     * trang 50 vé sẽ thành 50 truy vấn. Trả về các cặp (ticketId, count); vé chưa
     * xếp ngày nào KHÔNG có dòng nào nên bên gọi phải mặc định 0.
     */
    @Query("select s.ticket.id, count(s) from TrainingSession s "
            + "where s.ticket.id in :ticketIds and s.status <> :excluded group by s.ticket.id")
    List<Object[]> countScheduledByTicketIds(@Param("ticketIds") List<Long> ticketIds,
                                             @Param("excluded") SessionStatus excluded);

    /** Lịch quản lý của gym: đọc đúng khoảng ngày đang xem, không tải rồi gom ở client. */
    List<TrainingSession> findByGymBranch_IdAndSessionDateBetweenAndStatusInOrderBySessionDateAscPtSlotStartAsc(
            Long branchId, LocalDate from, LocalDate to, Collection<SessionStatus> statuses);

    /** PtSlotValidator: các buổi đã chiếm chỗ của một PT trong ngày. */
    List<TrainingSession> findByPtProfile_IdAndSessionDateAndStatusIn(
            Long ptProfileId, LocalDate date, Collection<SessionStatus> statuses);

    /**
     * Lưới ngày x giờ: ô nào đã bị đặt, lấy trong MỘT truy vấn cho cả nhóm PT và
     * cả khoảng ngày — hỏi từng PT từng ngày là 300 query cho một lưới 10 PT x 30 ngày.
     */
    List<TrainingSession> findByPtProfile_IdInAndSessionDateBetweenAndStatusIn(
            Collection<Long> ptProfileIds, LocalDate from, LocalDate to,
            Collection<SessionStatus> statuses);

    /** Chống đặt trùng khung giờ của cùng một PT. */
    boolean existsByPtProfile_IdAndSessionDateAndPtSlotStartAndStatusIn(
            Long ptProfileId, LocalDate date, java.time.LocalTime slotStart,
            Collection<SessionStatus> statuses);

    /** Lịch của khách + cảnh báo trùng ngày (câu 29). */
    List<TrainingSession> findByTicket_Customer_UsernameAndSessionDateBetweenAndStatusInOrderBySessionDateAsc(
            String username, LocalDate from, LocalDate to, Collection<SessionStatus> statuses);

    /** Lịch dạy của PT đang đăng nhập. */
    List<TrainingSession> findByPtProfile_User_UsernameAndSessionDateBetweenAndStatusInOrderBySessionDateAscPtSlotStartAsc(
            String username, LocalDate from, LocalDate to, Collection<SessionStatus> statuses);

    /** SessionCompletionJob: buổi còn SCHEDULED nhưng ngày tập đã trôi qua. */
    List<TrainingSession> findByStatusAndSessionDateBefore(SessionStatus status, LocalDate cutoff);

    /** Duyệt hoàn tiền: huỷ mọi buổi tương lai của vé (câu 12). */
    List<TrainingSession> findByTicket_IdAndStatusAndSessionDateGreaterThanEqual(
            Long ticketId, SessionStatus status, LocalDate from);

    /** UC-023: thống kê hiệu suất PT — số buổi đã dạy xong / bị huỷ. */
    long countByPtProfile_IdAndStatus(Long ptProfileId, SessionStatus status);

    /** P1-17: không cho tắt chi nhánh khi còn buổi tập đã đặt trong tương lai. */
    long countByGymBranch_IdAndStatusAndSessionDateGreaterThanEqual(
            Long branchId, SessionStatus status, LocalDate from);

    /** Guard gỡ PT khỏi chi nhánh / thu hẹp lịch rảnh: PT còn buổi tương lai không. */
    long countByPtProfile_IdAndStatusAndSessionDateGreaterThanEqual(
            Long ptProfileId, SessionStatus status, LocalDate from);

    /** Guard thu hẹp lịch rảnh: buổi tương lai của PT, để đối chiếu khung giờ còn khai. */
    List<TrainingSession> findByPtProfile_IdAndStatusAndSessionDateGreaterThanEqual(
            Long ptProfileId, SessionStatus status, LocalDate from);
}
