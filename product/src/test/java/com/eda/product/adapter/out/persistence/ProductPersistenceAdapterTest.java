package com.eda.product.adapter.out.persistence;

import static org.assertj.core.api.Assertions.*;

import com.eda.product.domain.Product;
import com.eda.product.domain.ProductStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(ProductPersistenceAdapter.class)
class ProductPersistenceAdapterTest {

    @Autowired
    private ProductPersistenceAdapter productPersistenceAdapter;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Test
    @DisplayName("상품을 저장하면 ID와 생성 시각이 채워진다")
    void saveProduct() {
        // given
        Product product = Product.register("티셔츠", BigDecimal.valueOf(10000));

        // when
        Product saved = productPersistenceAdapter.save(product);

        // then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("저장한 상품을 다시 조회하면 값이 그대로 유지된다")
    void findSavedProduct() {
        // given
        Product saved = productPersistenceAdapter.save(Product.register("티셔츠", BigDecimal.valueOf(10000)));

        // when
        Product found = productJpaRepository.findById(saved.getId()).orElseThrow();

        // then
        assertThat(found.getName()).isEqualTo("티셔츠");
        assertThat(found.getPrice()).isEqualByComparingTo("10000");
        assertThat(found.getStatus()).isEqualTo(ProductStatus.ACTIVE);
    }
}