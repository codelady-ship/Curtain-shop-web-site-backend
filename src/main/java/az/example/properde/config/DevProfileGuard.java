package az.example.properde.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * The "dev" profile ships a public, insecure JWT secret (application-dev.yml). Hosting platforms
 * such as Render set PORT, so dev + PORT almost certainly means a public deployment: refuse to start.
 * Locally, use SERVER_PORT instead of PORT together with the dev profile.
 */
@Slf4j
@Component
@Profile("dev")
public class DevProfileGuard {

    public DevProfileGuard(Environment environment) {
        if (StringUtils.hasText(environment.getProperty("PORT"))) {
            String message = "The 'dev' profile must not run on a hosted deployment (PORT is set). "
                    + "Remove 'dev' from SPRING_PROFILES_ACTIVE; locally use SERVER_PORT instead of PORT.";
            log.error(message);
            throw new IllegalStateException(message);
        }
        log.warn("Running with the 'dev' profile: insecure development JWT secret in use. Never use this profile in production.");
    }
}
