package com.pakgopay.common.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(RedisProperties redisProperties) {
        Config config = new Config();
        SingleServerConfig singleServer = config.useSingleServer()
                .setAddress("redis://" + redisProperties.getHost() + ":" + redisProperties.getPort())
                .setDatabase(redisProperties.getDatabase());
        if (redisProperties.getPassword() != null && !redisProperties.getPassword().isBlank()) {
            singleServer.setPassword(redisProperties.getPassword());
        }
        if (redisProperties.getTimeout() != null) {
            singleServer.setTimeout((int) redisProperties.getTimeout().toMillis());
        }
        if (redisProperties.getConnectTimeout() != null) {
            singleServer.setConnectTimeout((int) redisProperties.getConnectTimeout().toMillis());
        }
        return Redisson.create(config);
    }
}
