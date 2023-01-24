package io.dockstore.webservice;

import java.io.IOException;
import javax.ws.rs.core.Response.Status;
import okhttp3.Interceptor;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Retry 429 requests automatically. Prevents test failure but ties up threads.
 * <p>
 * Inspired by <a href="https://stackoverflow.com/questions/35364823/okhttp-api-rate-limit">...</a>
 */
public class RateLimitInterceptor implements Interceptor {

    private static final Logger LOG = LoggerFactory.getLogger(RateLimitInterceptor.class);
    private static final long ONE_MINUTE_IN_MS = 60_000L;

    public RateLimitInterceptor() {
    }

    @NotNull
    @Override
    public Response intercept(Chain chain) throws IOException {

        Response response = chain.proceed(chain.request());

        // 429 is how the api indicates a rate limit error
        if (!response.isSuccessful() && response.code() == Status.TOO_MANY_REQUESTS.getStatusCode()) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("rate-limited 429 response on %s".formatted(chain.request().url()));
            }

            // wait & retry
            try {
                LOG.error("We sleep");
                Thread.sleep(ONE_MINUTE_IN_MS); // one minute, should be exponential back-off
            } catch (InterruptedException e) {
                LOG.error("Rate-limit wait interrupted!", e);
                // Restore interrupted state...
                Thread.currentThread().interrupt();
            }

            response = chain.proceed(chain.request());
        }

        return response;
    }
}
