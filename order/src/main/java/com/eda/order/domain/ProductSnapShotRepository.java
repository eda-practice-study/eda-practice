package com.eda.order.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductSnapShotRepository extends JpaRepository<ProductSnapshot, Long> {

    Optional<ProductSnapshot> findByProductId(Long productId);
}
