package com.eda.product.adapter.out.persistence;

import com.eda.product.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;

interface ProductJpaRepository extends JpaRepository<Product, Long> {
}
