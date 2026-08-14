package com.fitmatch.repository;

import com.fitmatch.entity.TicketTypeBranch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TicketTypeBranchRepository extends JpaRepository<TicketTypeBranch, Long> {

    List<TicketTypeBranch> findByTicketType_Id(Long ticketTypeId);

    List<TicketTypeBranch> findByGymBranch_Id(Long branchId);

    boolean existsByGymBranch_Id(Long branchId);
}
