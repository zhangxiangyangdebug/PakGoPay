package com.pakgopay.common.config;

import com.pakgopay.filter.WebSocketFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${websocket.relay.host:${spring.rabbitmq.host}}")
    private String relayHost;

    @Value("${websocket.relay.port:61613}")
    private Integer relayPort;

    @Value("${websocket.relay.username:${spring.rabbitmq.username}}")
    private String relayUsername;

    @Value("${websocket.relay.password:${spring.rabbitmq.password}}")
    private String relayPassword;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/pakGoPay/server/notify")
                .setAllowedOriginPatterns("*")
                .withSockJS();
        WebSocketMessageBrokerConfigurer.super.registerStompEndpoints(registry);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableStompBrokerRelay("/topic")
                .setRelayHost(relayHost)
                .setRelayPort(relayPort)
                .setClientLogin(relayUsername)
                .setClientPasscode(relayPassword)
                .setSystemLogin(relayUsername)
                .setSystemPasscode(relayPassword)
                .setSystemHeartbeatSendInterval(10000)
                .setSystemHeartbeatReceiveInterval(10000);
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new WebSocketFilter());
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.interceptors(new WebSocketFilter());
    }
}
