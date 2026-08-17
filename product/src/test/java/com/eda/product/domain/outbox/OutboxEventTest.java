package com.eda.product.domain.outbox;

import static org.assertj.core.api.Assertions.*;

import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class OutboxEventTest {

    private static final String PAYLOAD = "{\"productId\":1}";

    @Test
    @DisplayName("이벤트를 기록하면 PENDING 상태로 생성된다")
    void createPendingEvent() {
        // when
        OutboxEvent event = OutboxEvent.pending("PRODUCT", "1", "PRODUCT_CREATED", PAYLOAD);

        // then
        assertThat(event.getAggregateType()).isEqualTo("PRODUCT");
        assertThat(event.getAggregateId()).isEqualTo("1");
        assertThat(event.getEventType()).isEqualTo("PRODUCT_CREATED");
        assertThat(event.getPayload()).isEqualTo(PAYLOAD);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getPublishedAt()).isNull();
    }

    @ParameterizedTest
    @MethodSource("blankArgs")
    @DisplayName("필수 값이 비어 있으면 기록에 실패한다")
    void failToCreateWithBlankValue(String aggregateType, String aggregateId, String eventType, String payload) {
        // when & then
        assertThatThrownBy(() -> OutboxEvent.pending(aggregateType, aggregateId, eventType, payload))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    static Stream<Arguments> blankArgs() {
        return Stream.of(
                Arguments.of(null, "1", "PRODUCT_CREATED", PAYLOAD),        // 애그리거트 종류 null
                Arguments.of("  ", "1", "PRODUCT_CREATED", PAYLOAD),        // 애그리거트 종류 blank
                Arguments.of("PRODUCT", null, "PRODUCT_CREATED", PAYLOAD),  // 애그리거트 ID null
                Arguments.of("PRODUCT", "  ", "PRODUCT_CREATED", PAYLOAD),  // 애그리거트 ID blank
                Arguments.of("PRODUCT", "1", null, PAYLOAD),                // 이벤트 종류 null
                Arguments.of("PRODUCT", "1", "  ", PAYLOAD),                // 이벤트 종류 blank
                Arguments.of("PRODUCT", "1", "PRODUCT_CREATED", null),      // payload null
                Arguments.of("PRODUCT", "1", "PRODUCT_CREATED", "  ")       // payload blank
        );
    }

    @Test
    @DisplayName("발행을 표시하면 PUBLISHED 상태가 되고 발행 시각이 남는다")
    void markPublished() {
        // given
        OutboxEvent event = OutboxEvent.pending("PRODUCT", "1", "PRODUCT_CREATED", PAYLOAD);
        LocalDateTime publishedAt = LocalDateTime.of(2026, 8, 17, 10, 0);

        // when
        event.markPublished(publishedAt);

        // then
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isEqualTo(publishedAt);
    }

    @Test
    @DisplayName("이미 발행된 이벤트를 다시 표시해도 최초 발행 시각이 유지된다")
    void keepFirstPublishedAtOnDuplicatedMark() {
        // given: 중복 발행된 상황
        OutboxEvent event = OutboxEvent.pending("PRODUCT", "1", "PRODUCT_CREATED", PAYLOAD);
        LocalDateTime first = LocalDateTime.of(2026, 8, 17, 10, 0);
        event.markPublished(first);

        // when
        event.markPublished(LocalDateTime.of(2026, 8, 17, 11, 0));

        // then
        assertThat(event.getPublishedAt()).isEqualTo(first);
    }

    @Test
    @DisplayName("발행 시각 없이 발행을 표시할 수 없다")
    void failToMarkPublishedWithoutTime() {
        // given
        OutboxEvent event = OutboxEvent.pending("PRODUCT", "1", "PRODUCT_CREATED", PAYLOAD);

        // when & then
        assertThatThrownBy(() -> event.markPublished(null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
    }
}
