package io.hyperfoil.tools.horreum.svc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.apache.http.HttpStatus;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.RestResponse.ResponseBuilder;

@ApplicationScoped
@Path("/api/slack")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "SlackService", description = "Mock endpoint for Slack service.")
public class SlackDummyService {

    private static final Logger log = Logger.getLogger(SlackDummyService.class);
    private static boolean oneTime = true;

    @Inject
    ObjectMapper mapper;

    @POST
    public RestResponse<JsonNode> mockSlackEndpoint(JsonNode payload) {
        log.infof("Received payload: %s", payload);

        ObjectNode body = mapper.createObjectNode();
        ResponseBuilder<JsonNode> response = ResponseBuilder.ok();
        log.infof("Switching on channel");
        switch (payload.get("channel").asText()) {
            case "BADCHANNEL": {
                log.infof("Bad channel: returning JSON failure");
                body.put("ok", false).put("error", "Bad channel");
                response.entity(body);
                break;
            }
            case "ERRORCHANNEL": {
                log.infof("Edge case: Slack API failure");
                body.put("error", "Forced error");
                response.status(HttpStatus.SC_FORBIDDEN).entity(body);
                break;
            }
            case "BUSYCHANNEL": {
                // NOTE: on retry, this falls through to GOODCHANNEL
                if (oneTime) {
                    oneTime = false;
                    log.infof("Busy channel: requesting retry");
                    response.status(HttpStatus.SC_TOO_MANY_REQUESTS).header("Retry-After", "1");
                    break;
                } else {
                    log.infof("Busy channel: redux");
                }
            }
            case "GOODCHANNEL": {
                log.infof("Good channel: success");
                body.put("ok", true);
                response.entity(body);
                break;
            }
        }
        log.infof("Returning ...");
        return response.build();
    }
}
