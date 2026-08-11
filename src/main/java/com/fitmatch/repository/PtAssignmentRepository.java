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

    boolean existsByPtProfile_IdAndGymService_Id(Long ptId, Long serviceId);

    boolean existsByPtProfile_IdAndTrainingPackage_Id(Long ptId, Long packageId);

    /**
     * Số phân công CHI NHÁNH của một PT — dùng để giữ bất biến "PT luôn thuộc ít
     * nhất một chi nhánh" khi gỡ phân công (UC-022).
     */
    long countByPtProfile_IdAndGymBranchIsNotNull(Long ptId);

    /**
     * Bug S2-04: id các PT được phân công cho một chi nhánh. Khách chọn chi nhánh
     * rồi mới chọn PT — danh sách phải là PT thật sự phụ trách chi nhánh đó, không
     * phải toàn bộ PT của gym (BookingEligibilityChecker sẽ từ chối ở checkout).
     */
    @org.springframework.data.jpa.repository.Query(
            "select a.ptProfile.id from PtAssignment a where a.gymBranch.id = :branchId")
    List<Long> findPtIdsByBranchId(@org.springframework.data.repository.query.Param("branchId") Long branchId);
}
