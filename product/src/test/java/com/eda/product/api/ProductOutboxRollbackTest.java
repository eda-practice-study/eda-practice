package com.eda.product.api;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eda.common.outbox.OutboxWriter;
import com.eda.product.domain.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = "outbox.enabled=false")
class ProductOutboxRollbackTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ProductRepository productRepository;

    @MockitoBean
    private OutboxWriter outboxWriter;

    @Test
    @DisplayName("이벤트 저장이 실패하면 상품 저장도 롤백된다")
    void rollbackWhenOutboxFails() throws Exception {
        given(outboxWriter.write(anyString(), anyString(), any())).willThrow(new IllegalStateException("outbox 실패"));

        MockMvcBuilders.webAppContextSetup(context).build()
                .perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"name\": \"키보드\", \"price\": 30000 }"))
                .andExpect(status().isInternalServerError());

        assertThat(productRepository.count()).isZero();
    }
}
