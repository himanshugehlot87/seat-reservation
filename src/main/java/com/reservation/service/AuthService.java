package com.reservation.service;

import com.reservation.exception.UnauthorizedException;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    public String getUserId(String authorizationHeader) {

        if (authorizationHeader == null ||
                !authorizationHeader.startsWith("Bearer ")) {
            throw new UnauthorizedException("Invalid Authorization header");
        }

        String token = authorizationHeader.substring(7);

        if (token.isBlank()) {
            throw new UnauthorizedException("Invalid token");
        }

        return token;
    }
}