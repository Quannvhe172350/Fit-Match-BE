package com.fitmatch.repository;

import com.fitmatch.entity.GymPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GymPolicyRepository extends JpaRepository<GymPolicy, Long> {

    Optional<GymPolicy> findByGymProfile_Id(Long gymProfileId);

    Optional<GymPolicy> findByGymProfile_User_Username(String username);
}
