package ma.aui.translator.rest;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.Map;

@Path("/health")
@Produces(MediaType.APPLICATION_JSON)
public class HealthResource {

    private final ServiceStatus status;

    @Inject
    public HealthResource(ServiceStatus status) {
        this.status = status;
    }

    @GET
    public Map<String, Object> health() {
        return Map.of("status", "UP", "llmConfigured", status.llmConfigured());
    }
}
