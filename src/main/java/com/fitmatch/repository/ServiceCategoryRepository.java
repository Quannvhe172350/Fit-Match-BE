package com.fitmatch.repository;

import com.fitmatch.entity.ServiceCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ServiceCategoryRepository extends JpaRepository<ServiceCategory, Long> {

    boolean existsByNameIgnoreCase(String name);

    List<ServiceCategory> findByActiveTrue();
}
