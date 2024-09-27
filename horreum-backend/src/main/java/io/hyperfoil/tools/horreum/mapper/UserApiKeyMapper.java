package io.hyperfoil.tools.horreum.mapper;

import java.time.Instant;

import io.hyperfoil.tools.horreum.api.internal.services.UserService;
import io.hyperfoil.tools.horreum.entity.user.UserApiKey;

public class UserApiKeyMapper {

    public static UserApiKey from(UserService.ApiKeyRequest request, Instant creation, long valid) {
        return new UserApiKey(request.name == null ? "" : request.name, request.type, creation, valid);
    }

    public static UserService.ApiKeyResponse to(UserApiKey key) {
        UserService.ApiKeyResponse response = new UserService.ApiKeyResponse();
        response.id = key.id;
        response.name = key.name;
        response.type = key.type;
        response.creation = key.creation;
        response.access = key.access;
        response.isRevoked = key.revoked;
        response.toExpiration = key.toExpiration(Instant.now());
        return response;
    }
}
