package org.openqa.selenium.remote.internal;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.conn.routing.HttpRoutePlanner;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.conn.DefaultSchemePortResolver;
import org.apache.http.impl.conn.SystemDefaultRoutePlanner;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.nio.reactor.IOReactorException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.openqa.selenium.WebDriverException;

import java.net.ProxySelector;
import java.net.URL;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Override {@link org.openqa.selenium.remote.internal.ApacheHttpClient.Factory#createClient(URL)} to return {@link ApacheHttpAsyncClient}
 */
@Aspect
public class CreateHttpAsyncClient {

    //  {@link org.openqa.selenium.remote.internal.HttpClientFactory}'s defaults
    private final int TIMEOUT_THREE_HOURS = (int) SECONDS.toMillis(60 * 60 * 3);
    private final int TIMEOUT_TWO_MINUTES = (int) SECONDS.toMillis(60 * 2);

    @Around("execution(public org.openqa.selenium.remote.http.HttpClient org.openqa.selenium.remote.internal.ApacheHttpClient.Factory.createClient(java.net.URL))")
    public Object createClient(ProceedingJoinPoint p) throws IOReactorException {
        int connectionTimeout = TIMEOUT_TWO_MINUTES;
        int socketTimeout = TIMEOUT_THREE_HOURS;

        URL url = (URL) p.getArgs()[0];
        checkNotNull(url, "null URL");

        if (connectionTimeout <= 0) {
            throw new IllegalArgumentException("connection timeout must be > 0");
        }
        if (socketTimeout <= 0) {
            throw new IllegalArgumentException("socket timeout must be > 0");
        }

        // same as HttpClientFactory#createRequestConfig(int, int)
        RequestConfig requestConfig = RequestConfig.custom()
                .setStaleConnectionCheckEnabled(true)
                .setConnectTimeout(connectionTimeout)
                .setSocketTimeout(socketTimeout)
                .build();
        // same as HttpClientFactory#createRoutePlanner()
        HttpRoutePlanner routePlanner = new SystemDefaultRoutePlanner(
                new DefaultSchemePortResolver(), ProxySelector.getDefault());

        ConnectingIOReactor ioReactor = new DefaultConnectingIOReactor();
        PoolingNHttpClientConnectionManager cm = new PoolingNHttpClientConnectionManager(ioReactor);
        // these values are obtained from HttpClientFactory#getClientConnectionManager()
        cm.setMaxTotal(2000);
        cm.setDefaultMaxPerRoute(2000);

        HttpAsyncClientBuilder builder = HttpAsyncClientBuilder.create()
                .setConnectionManager(cm)
                .setDefaultRequestConfig(requestConfig)
                .setRoutePlanner(routePlanner);

        if (url.getUserInfo() != null) {
            UsernamePasswordCredentials credentials =
                    new UsernamePasswordCredentials(url.getUserInfo());
            CredentialsProvider provider = new BasicCredentialsProvider();
            provider.setCredentials(AuthScope.ANY, credentials);
            builder.setDefaultCredentialsProvider(provider);
        }

        return new ApacheHttpAsyncClient(builder.build(), url);
    }
}
