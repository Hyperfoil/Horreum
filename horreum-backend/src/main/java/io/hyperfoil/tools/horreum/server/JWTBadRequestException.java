package io.hyperfoil.tools.horreum.server;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.Response;

public class JWTBadRequestException extends BadRequestException {
    public JWTBadRequestException() {
        super(Response.status(Response.Status.BAD_REQUEST)
                .entity("It seems that you are trying to pass JWT token as Horreum token. Please use HTTP header "
                        + "'Authorization: Bearer <token>' instead.")
                .build());
    }
}
