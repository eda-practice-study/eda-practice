package com.eda.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import com.eda.common.outbox.OutboxRepository;
import com.eda.common.outbox.OutboxStatus;
import com.eda.product.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Limit;
import org.springframework.http.MediaType;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = {"outbox.enabled=false", "spring.datasource.url=jdbc:h2:mem:product-api"})
class ProductApplicationTests {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ProductRepository products;

    @Autowired
    private OutboxRepository outbox;

    @Test
    void createsProductAndRecordsExactlyOneEventWhenNameIsRetried() throws Exception {
        var mvc = webAppContextSetup(context).build();
        var request = post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"키보드","price":30000}
                        """);

        mvc.perform(request)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.productId").isNumber())
                .andExpect(jsonPath("$.data.price").value(30000))
                .andExpect(jsonPath("$.data.createdAt").isString());

        mvc.perform(request)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PRODUCT_NAME_DUPLICATED"));

        var product = products.findAll().getFirst();
        assertThat(products.count()).isEqualTo(1);
        assertThat(outbox.findByStatusOrderByIdAsc(OutboxStatus.PENDING, Limit.of(2)))
                .singleElement()
                .satisfies(message -> {
                    assertThat(message.getTopic()).isEqualTo("product.events");
                    assertThat(message.getMessageKey()).isEqualTo(product.getId().toString());
                    assertThat(message.getMessageType()).isEqualTo("ProductCreated");
                });
    }

    @Test
    void rejectsGetWithMethodNotAllowed() throws Exception {
        webAppContextSetup(context).build().perform(get("/api/products"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "POST"))
                .andExpect(jsonPath("$.errorCode").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void rejectsFractionalPrice() throws Exception {
        webAppContextSetup(context).build().perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"마우스","price":30000.5}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }
}
