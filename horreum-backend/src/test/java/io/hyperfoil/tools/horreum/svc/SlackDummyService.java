package io.hyperfoil.tools.horreum.svc;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.apache.http.HttpStatus;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.RestResponse.ResponseBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.quarkus.logging.Log;

@ApplicationScoped
@Path("/api/slack")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "SlackService", description = "Mock endpoint for Slack service.")
public class SlackDummyService {

    private static boolean oneTime = true;

    @Inject
    ObjectMapper mapper;

    @POST
    public RestResponse<JsonNode> mockSlackEndpoint(JsonNode payload) {
        Log.infof("Received payload: %s", payload);

        ObjectNode body = mapper.createObjectNode();
        ResponseBuilder<JsonNode> response = ResponseBuilder.ok();
        Log.infof("Switching on channel");
        switch (payload.get("channel").asText()) {
            case "BADCHANNEL": {
                Log.infof("Bad channel: returning JSON failure");
                body.put("ok", false).put("error", "Bad channel");
                response.entity(body);
                break;
            }
            case "ERRORCHANNEL": {
                Log.infof("Edge case: Slack API failure");
                body.put("error", "Forced error");
                response.status(HttpStatus.SC_FORBIDDEN).entity(body);
                break;
            }
            case "BUSYCHANNEL": {
                // NOTE: on retry, this falls through to GOODCHANNEL
                if (oneTime) {
                    oneTime = false;
                    Log.infof("Busy channel: requesting retry");
                    response.status(HttpStatus.SC_TOO_MANY_REQUESTS).header("Retry-After", "1");
                    break;
                } else {
                    Log.infof("Busy channel: redux");
                }
            }
            case "GOODCHANNEL": {
                Log.infof("Good channel: success");
                body.put("ok", true);
                response.entity(body);
                break;
            }
        }
        Log.infof("Returning ...");
        return response.build();
    }
}
