package com.fitmatch.repository;

import com.fitmatch.entity.Bank;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BankRepository extends JpaRepository<Bank, Long> {

    List<Bank> findByActiveTrueOrderByShortNameAsc();

    Optional<Bank> findByBin(String bin);

    Optional<Bank> findByCode(String code);
}
