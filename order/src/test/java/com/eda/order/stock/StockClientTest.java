package com.eda.order.stock;

import static org.assertj.core.api.Assertions.*;

import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

// 운영과 같은 요청 팩토리로 진짜 소켓을 열어, 타임아웃과 연결 실패가 실제로 어떤 예외로 오는지 확인한다
class StockClientTest {

    private static final String ORDER_KEY = "7f3c2a10-0000-4000-8000-000000000001";
    private static final Duration READ_TIMEOUT = Duration.ofMillis(300);

    private HttpServer server;
    private final AtomicReference<String> requestBody = new AtomicReference<>();

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("차감에 성공하면 orderKey 와 items 를 보낸다")
    void decrease() throws Exception {
        StockClient client = clientRespondingWith(200, "{\"success\":true,\"message\":\"success\",\"data\":null}");

        client.decrease(ORDER_KEY, List.of(new StockClient.Item(1L, 2), new StockClient.Item(2L, 1)));

        assertThat(requestBody.get()).isEqualTo("{\"orderKey\":\"" + ORDER_KEY + "\",\"items\":["
                + "{\"productId\":1,\"quantity\":2},{\"productId\":2,\"quantity\":1}]}");
    }

    @Test
    @DisplayName("409 면 OUT_OF_STOCK 이다")
    void outOfStock() throws Exception {
        StockClient client = clientRespondingWith(409, "{\"success\":false,\"errorCode\":\"OUT_OF_STOCK\"}");

        assertThatThrownBy(() -> client.decrease(ORDER_KEY, List.of(new StockClient.Item(1L, 1))))
                .isInstanceOf(BusinessException.class)
                .isNotInstanceOf(StockCallException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.OUT_OF_STOCK);
    }

    @Test
    @DisplayName("응답이 read timeout 안에 오지 않으면 차감됐을 수 있는 503 이다")
    void readTimeout() throws Exception {
        start(exchange -> {
            sleep(READ_TIMEOUT.multipliedBy(4));
            respond(exchange, 200, "{}");
        });

        assertThat(callFailure(client(baseUrl()))).satisfies(failure -> {
            assertThat(failure.getErrorCode()).isEqualTo(ErrorCode.STOCK_SERVICE_UNAVAILABLE);
            assertThat(failure.mayHaveDecreased()).isTrue();
        });
    }

    @Test
    @DisplayName("연결이 거부되면 요청이 닿지 않았으므로 차감되지 않은 503 이다")
    void connectionRefused() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }

        assertThat(callFailure(client("http://localhost:" + closedPort))).satisfies(failure -> {
            assertThat(failure.getErrorCode()).isEqualTo(ErrorCode.STOCK_SERVICE_UNAVAILABLE);
            assertThat(failure.mayHaveDecreased()).isFalse();
        });
    }

    @Test
    @DisplayName("연결 타임아웃도 요청이 닿지 않은 것으로 본다")
    void connectTimeout() {
        // 라우팅되지 않는 주소. 환경에 따라 타임아웃 대신 즉시 거부될 수 있지만 둘 다 차감 전 실패다
        StockClient client = new StockClient(StockClientConfig.restClient(
                new StockClientProperties("http://10.255.255.1:8084", Duration.ofMillis(200), READ_TIMEOUT)));

        assertThat(callFailure(client).mayHaveDecreased()).isFalse();
    }

    @Test
    @DisplayName("5xx 면 stock 안에서 차감됐을 수 있는 503 이다")
    void serverError() throws Exception {
        StockClient client = clientRespondingWith(500, "{\"success\":false,\"errorCode\":\"INTERNAL_ERROR\"}");

        assertThat(callFailure(client).mayHaveDecreased()).isTrue();
    }

    @Test
    @DisplayName("409 가 아닌 4xx 는 차감되지 않은 503 이다")
    void clientError() throws Exception {
        StockClient client = clientRespondingWith(400, "{\"success\":false,\"errorCode\":\"VALIDATION_ERROR\"}");

        assertThat(callFailure(client).mayHaveDecreased()).isFalse();
    }

    @Test
    @DisplayName("restore 는 orderKey 를 보낸다")
    void restore() throws Exception {
        StockClient client = clientRespondingWith(200, "{\"success\":true,\"message\":\"success\",\"data\":null}");

        client.restore(ORDER_KEY);

        assertThat(requestBody.get()).isEqualTo("{\"orderKey\":\"" + ORDER_KEY + "\"}");
    }

    @Test
    @DisplayName("restore 가 실패하면 재시도할 수 있도록 원래 예외를 그대로 던진다")
    void restoreFailure() throws Exception {
        StockClient client = clientRespondingWith(503, "{}");

        assertThatThrownBy(() -> client.restore(ORDER_KEY)).isInstanceOf(HttpServerErrorException.class);

        tearDown();
        StockClient slow = clientWith(exchange -> {
            sleep(READ_TIMEOUT.multipliedBy(4));
            respond(exchange, 200, "{}");
        });
        assertThatThrownBy(() -> slow.restore(ORDER_KEY))
                .isInstanceOf(ResourceAccessException.class)
                .cause().isExactlyInstanceOf(HttpTimeoutException.class);
    }

    @Test
    @DisplayName("연결이 거부되면 원인은 ConnectException 이다")
    void refusedCause() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }

        assertThatThrownBy(() -> client("http://localhost:" + closedPort).restore(ORDER_KEY))
                .isInstanceOf(ResourceAccessException.class)
                .cause().isInstanceOf(ConnectException.class);
    }

    private StockCallException callFailure(StockClient client) {
        Throwable thrown = catchThrowable(() -> client.decrease(ORDER_KEY, List.of(new StockClient.Item(1L, 1))));
        assertThat(thrown).isInstanceOf(StockCallException.class);
        return (StockCallException) thrown;
    }

    private StockClient clientRespondingWith(int status, String body) throws IOException {
        return clientWith(exchange -> respond(exchange, status, body));
    }

    private StockClient clientWith(Handler handler) throws IOException {
        start(handler);
        return client(baseUrl());
    }

    private StockClient client(String baseUrl) {
        return new StockClient(StockClientConfig.restClient(
                new StockClientProperties(baseUrl, Duration.ofMillis(300), READ_TIMEOUT)));
    }

    private void start(Handler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/internal/stocks", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            handler.handle(exchange);
        });
        server.start();
    }

    private String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @FunctionalInterface
    private interface Handler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
