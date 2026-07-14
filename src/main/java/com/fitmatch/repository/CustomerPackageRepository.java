package com.fitmatch.repository;

import com.fitmatch.entity.CustomerPackage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerPackageRepository extends JpaRepository<CustomerPackage, Long> {

    List<CustomerPackage> findByCustomer_UsernameOrderByIdDesc(String username);

    Optional<CustomerPackage> findByIdAndCustomer_Username(Long id, String username);

    boolean existsByPurchaseBooking_Id(Long bookingId);
}
