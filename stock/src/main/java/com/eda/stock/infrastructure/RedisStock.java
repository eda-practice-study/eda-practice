package com.eda.stock.infrastructure;

public record RedisStock(long available, long reserved) {

    public long total() {
        return available + reserved;
    }
}
