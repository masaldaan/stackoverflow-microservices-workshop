package com.agilefaqs.stackoverflow.gateway.clients;


import com.agilefaqs.stackoverflow.gateway.model.AuthRequest;
import com.agilefaqs.stackoverflow.hystrix.GenericHystrixCommand;
import com.agilefaqs.stackoverflow.hystrix.HystrixCommandBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;


@Component
public class SessionsClient {

    private SessionsFeignClient sessionsFeignClient;
    private Map<String, UserDetail> sessionsTokens = new ConcurrentHashMap<>();

    @Autowired
    public SessionsClient(SessionsFeignClient sessionsFeignClient) {
        this.sessionsFeignClient = sessionsFeignClient;
    }

    public UserDetail validateToken(AuthRequest authRequest) {
        return new HystrixCommandBuilder<UserDetail>()
            .groupKey("gateway.auth")
            .commandKey("session.validate")
            .threadPoolSize(10)
            .timeout(3000)
            .thresholdValue(20)
            .supplier(fetchUserDetailsFromSessions(authRequest))
            .fallback(getFromLocalCache(authRequest))
            .execute();

    }

    private Supplier<UserDetail> fetchUserDetailsFromSessions(AuthRequest authRequest) {
        return () -> {
            final UserDetail isValid = sessionsFeignClient.validateToken(authRequest);
            updateInLocalCache(authRequest, isValid);
            return isValid;
        };
    }

    private void updateInLocalCache(AuthRequest authRequest, UserDetail isValid) {
        sessionsTokens.put(authRequest.getToken(), isValid);
    }

    private Function<Throwable, UserDetail> getFromLocalCache(AuthRequest authRequest) {
        return e -> sessionsTokens.get(authRequest.getToken());
    }
}
