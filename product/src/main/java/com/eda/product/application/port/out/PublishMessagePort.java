package com.eda.product.application.port.out;

public interface PublishMessagePort {

    /**
     * 메시지를 발행하고 성공할 때까지 기다린다. 실패하면 예외를 던진다.
     * payload 는 outbox 에 저장된 문자열 그대로다 — 발행 경로에서 내용을 해석하지 않는다.
     */
    void publish(String topic, String key, String payload);
}
