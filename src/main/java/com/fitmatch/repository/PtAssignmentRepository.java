package com.fitmatch.repository;

import com.fitmatch.entity.PtAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PtAssignmentRepository extends JpaRepository<PtAssignment, Long> {

    List<PtAssignment> findByPtProfile_Id(Long ptProfileId);

    Optional<PtAssignment> findByIdAndPtProfile_GymProfile_User_Username(Long id, String username);

    boolean existsByPtProfile_IdAndGymBranch_Id(Long ptId, Long branchId);

    /**
     * Số phân công của một PT — dùng để giữ bất biến "PT luôn thuộc ít nhất một
     * chi nhánh" khi gỡ phân công (UC-022). Từ V78 chi nhánh là đích phân công
     * duy nhất (câu 24) nên mọi bản ghi đều là phân công chi nhánh.
     */
    long countByPtProfile_Id(Long ptId);

    /**
     * Bug S2-04: id các PT được phân công cho một chi nhánh. Khách chọn chi nhánh
     * rồi mới chọn PT — danh sách phải là PT thật sự phụ trách chi nhánh đó, không
     * phải toàn bộ PT của gym (validator sẽ từ chối ở checkout).
     */
    @org.springframework.data.jpa.repository.Query(
            "select a.ptProfile.id from PtAssignment a where a.gymBranch.id = :branchId")
    List<Long> findPtIdsByBranchId(@org.springframework.data.repository.query.Param("branchId") Long branchId);

    /**
     * Phân công của NHIỀU PT trong một truy vấn — bảng PT của gym hiện chi nhánh
     * cho từng dòng, hỏi lẻ từng PT là N+1 ngay trên màn hình mở nhiều nhất của gym.
     * {@code join fetch} vì chỉ dùng đúng id + tên chi nhánh.
     */
    @org.springframework.data.jpa.repository.Query(
            "select a from PtAssignment a join fetch a.gymBranch b "
                    + "where a.ptProfile.id in :ptIds order by b.name asc")
    List<PtAssignment> findByPtProfileIds(
            @org.springframework.data.repository.query.Param("ptIds") java.util.Collection<Long> ptIds);
}
