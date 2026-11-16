package ma.aui.translator.rest;

import ma.aui.translator.llm.TranslationService;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ServerProperties;

/**
 * Jakarta REST application: declares the resources, providers and the objects
 * injected into the resources (the LLM-backed {@link TranslationService}).
 */
public class TranslatorApplication extends ResourceConfig {

    public TranslatorApplication(TranslationService translationService, boolean llmConfigured) {
        register(new AbstractBinder() {
            @Override
            protected void configure() {
                bind(translationService).to(TranslationService.class);
                bind(new ServiceStatus(llmConfigured)).to(ServiceStatus.class);
            }
        });
        register(TranslatorResource.class);
        register(HealthResource.class);
        register(JacksonFeature.withoutExceptionMappers());
        register(ErrorMappers.TranslationFailure.class);
        register(ErrorMappers.InvalidJson.class);
        register(ErrorMappers.WebFailure.class);
        register(ErrorMappers.Unexpected.class);
        register(CorsFilter.class);
        property(ServerProperties.WADL_FEATURE_DISABLE, true);
    }
}
