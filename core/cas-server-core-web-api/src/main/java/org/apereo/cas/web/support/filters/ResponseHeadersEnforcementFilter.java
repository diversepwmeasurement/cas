package org.apereo.cas.web.support.filters;

import org.apereo.cas.services.RegisteredService;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Allows users to easily inject the default security headers to assist in protecting the application.
 * The default for is to include the following headers:
 * &lt;pre&gt;
 * Cache-Control: no-cache, no-store, max-age=0, must-revalidate
 * Pragma: no-cache
 * Expires: 0
 * X-Content-Type-Options: nosniff
 * Strict-Transport-Security: max-age=15768000 ; includeSubDomains
 * X-Frame-Options: DENY
 * X-XSS-Protection: 1; mode=block
 * &lt;/pre&gt;
 *
 * @author Misagh Moayyed
 * @since 6.1.0
 */
@Slf4j
@Setter
@Getter
@SuppressWarnings("JdkObsolete")
public class ResponseHeadersEnforcementFilter extends AbstractSecurityFilter implements Filter {
    /**
     * Enable CACHE_CONTROL.
     */
    public static final String INIT_PARAM_ENABLE_CACHE_CONTROL = "enableCacheControl";

    /**
     * Enable XCONTENT_OPTIONS.
     */
    public static final String INIT_PARAM_ENABLE_XCONTENT_OPTIONS = "enableXContentTypeOptions";

    /**
     * Enable STRICT_TRANSPORT_SECURITY.
     */
    public static final String INIT_PARAM_ENABLE_STRICT_TRANSPORT_SECURITY = "enableStrictTransportSecurity";

    /**
     * Enable STRICT_XFRAME_OPTIONS.
     */
    public static final String INIT_PARAM_ENABLE_STRICT_XFRAME_OPTIONS = "enableXFrameOptions";

    /**
     * The constant INIT_PARAM_STRICT_XFRAME_OPTIONS.
     */
    public static final String INIT_PARAM_STRICT_XFRAME_OPTIONS = "XFrameOptions";

    /**
     * Enable XSS_PROTECTION.
     */
    public static final String INIT_PARAM_ENABLE_XSS_PROTECTION = "enableXSSProtection";

    /**
     * XSS protection value.
     */
    public static final String INIT_PARAM_XSS_PROTECTION = "XSSProtection";

    /**
     * Consent security policy.
     */
    public static final String INIT_PARAM_CONTENT_SECURITY_POLICY = "contentSecurityPolicy";

    /**
     * Static resources file extension values.
     */
    public static final String INIT_PARAM_CACHE_CONTROL_STATIC_RESOURCES = "cacheControlStaticResources";

    private Pattern cacheControlStaticResourcesPattern;

    private boolean enableCacheControl;

    private String cacheControlHeader = "no-cache, no-store, max-age=0, must-revalidate";

    private boolean enableXContentTypeOptions;

    private String xContentTypeOptionsHeader = "nosniff";

    private boolean enableStrictTransportSecurity;

    /**
     * Allow for 6 months; value is in seconds.
     */
    private String strictTransportSecurityHeader = "max-age=15768000 ; includeSubDomains";

    private boolean enableXFrameOptions;

    private String xframeOptions = "DENY";

    private boolean enableXSSProtection;

    private String xssProtection = "1; mode=block";

    private String contentSecurityPolicy;

    private static void throwIfUnrecognizedParamName(final Enumeration initParamNames) {
        val recognizedParameterNames = new HashSet<String>();
        recognizedParameterNames.add(INIT_PARAM_ENABLE_CACHE_CONTROL);
        recognizedParameterNames.add(INIT_PARAM_ENABLE_XCONTENT_OPTIONS);
        recognizedParameterNames.add(INIT_PARAM_ENABLE_STRICT_TRANSPORT_SECURITY);
        recognizedParameterNames.add(INIT_PARAM_ENABLE_STRICT_XFRAME_OPTIONS);
        recognizedParameterNames.add(INIT_PARAM_STRICT_XFRAME_OPTIONS);
        recognizedParameterNames.add(INIT_PARAM_CONTENT_SECURITY_POLICY);
        recognizedParameterNames.add(INIT_PARAM_ENABLE_XSS_PROTECTION);
        recognizedParameterNames.add(INIT_PARAM_XSS_PROTECTION);
        recognizedParameterNames.add(INIT_PARAM_CACHE_CONTROL_STATIC_RESOURCES);

        while (initParamNames.hasMoreElements()) {
            val initParamName = (String) initParamNames.nextElement();
            if (!recognizedParameterNames.contains(initParamName)) {
                throwException(new ServletException("Unrecognized init parameter [" + initParamName + ']'));
            }
        }
    }

    @Override
    public void init(final FilterConfig filterConfig) {
        val initParamNames = filterConfig.getInitParameterNames();
        throwIfUnrecognizedParamName(initParamNames);

        val cacheControl = filterConfig.getInitParameter(INIT_PARAM_ENABLE_CACHE_CONTROL);
        val contentTypeOpts = filterConfig.getInitParameter(INIT_PARAM_ENABLE_XCONTENT_OPTIONS);
        val stsEnabled = filterConfig.getInitParameter(INIT_PARAM_ENABLE_STRICT_TRANSPORT_SECURITY);
        val xframeOpts = filterConfig.getInitParameter(INIT_PARAM_ENABLE_STRICT_XFRAME_OPTIONS);
        val xssOpts = filterConfig.getInitParameter(INIT_PARAM_ENABLE_XSS_PROTECTION);
        val cacheControlStaticResources = filterConfig.getInitParameter(INIT_PARAM_CACHE_CONTROL_STATIC_RESOURCES);

        this.cacheControlStaticResourcesPattern = Pattern.compile("^.+\\.(" + cacheControlStaticResources + ")$", Pattern.CASE_INSENSITIVE);
        this.enableCacheControl = Boolean.parseBoolean(cacheControl);
        this.enableXContentTypeOptions = Boolean.parseBoolean(contentTypeOpts);
        this.enableStrictTransportSecurity = Boolean.parseBoolean(stsEnabled);
        this.enableXFrameOptions = Boolean.parseBoolean(xframeOpts);
        this.xframeOptions = filterConfig.getInitParameter(INIT_PARAM_STRICT_XFRAME_OPTIONS);
        if (this.xframeOptions == null || this.xframeOptions.isEmpty()) {
            this.xframeOptions = "DENY";
        }
        this.enableXSSProtection = Boolean.parseBoolean(xssOpts);
        this.xssProtection = filterConfig.getInitParameter(INIT_PARAM_XSS_PROTECTION);
        if (this.xssProtection == null || this.xssProtection.isEmpty()) {
            this.xssProtection = "1; mode=block";
        }
        this.contentSecurityPolicy = filterConfig.getInitParameter(INIT_PARAM_CONTENT_SECURITY_POLICY);
    }

    @Override
    public void doFilter(final ServletRequest servletRequest, final ServletResponse servletResponse,
                         final FilterChain filterChain) throws IOException, ServletException {
        if (servletResponse instanceof final HttpServletResponse response
            && servletRequest instanceof final HttpServletRequest request) {
            try {
                val result = prepareFilterBeforeExecution(response, request);
                decideInsertCacheControlHeader(response, request, result);
                decideInsertStrictTransportSecurityHeader(response, request, result);
                decideInsertXContentTypeOptionsHeader(response, request, result);
                decideInsertXFrameOptionsHeader(response, request, result);
                decideInsertXSSProtectionHeader(response, request, result);
                decideInsertContentSecurityPolicyHeader(response, request, result);
                filterChain.doFilter(servletRequest, servletResponse);
            } catch (final Throwable e) {
                throwException(e);
            }
        }
    }

    protected Optional<RegisteredService> prepareFilterBeforeExecution(final HttpServletResponse httpServletResponse,
                                                                       final HttpServletRequest httpServletRequest) throws Throwable {
        return Optional.empty();
    }

    protected void decideInsertContentSecurityPolicyHeader(final HttpServletResponse httpServletResponse,
                                                           final HttpServletRequest httpServletRequest, final Optional<RegisteredService> result) {
        if (this.contentSecurityPolicy == null) {
            return;
        }
        insertContentSecurityPolicyHeader(httpServletResponse, httpServletRequest);
    }

    /**
     * Insert content security policy header.
     *
     * @param httpServletResponse the http servlet response
     * @param httpServletRequest  the http servlet request
     */
    protected void insertContentSecurityPolicyHeader(final HttpServletResponse httpServletResponse,
                                                     final HttpServletRequest httpServletRequest) {
        this.insertContentSecurityPolicyHeader(httpServletResponse, httpServletRequest, this.contentSecurityPolicy);
    }

    protected void insertContentSecurityPolicyHeader(final HttpServletResponse httpServletResponse,
                                                     final HttpServletRequest httpServletRequest,
                                                     final String contentSecurityPolicy) {
        val uri = httpServletRequest.getRequestURI();
        httpServletResponse.addHeader("Content-Security-Policy", contentSecurityPolicy);
        LOGGER.trace("Adding Content-Security-Policy response header [{}] for [{}]", contentSecurityPolicy, uri);
    }

    protected void decideInsertXSSProtectionHeader(final HttpServletResponse httpServletResponse,
                                                   final HttpServletRequest httpServletRequest,
                                                   final Optional<RegisteredService> result) {
        if (!this.enableXSSProtection) {
            return;
        }
        insertXSSProtectionHeader(httpServletResponse, httpServletRequest);
    }

    protected void insertXSSProtectionHeader(final HttpServletResponse httpServletResponse, final HttpServletRequest httpServletRequest) {
        insertXSSProtectionHeader(httpServletResponse, httpServletRequest, this.xssProtection);
    }

    protected void insertXSSProtectionHeader(final HttpServletResponse httpServletResponse, final HttpServletRequest httpServletRequest,
                                             final String value) {
        val uri = httpServletRequest.getRequestURI();
        httpServletResponse.addHeader("X-XSS-Protection", value);
        LOGGER.trace("Adding X-XSS Protection [{}] response headers for [{}]", value, uri);
    }

    protected void decideInsertXFrameOptionsHeader(final HttpServletResponse httpServletResponse,
                                                   final HttpServletRequest httpServletRequest,
                                                   final Optional<RegisteredService> result) {
        if (!this.enableXFrameOptions) {
            return;
        }
        insertXFrameOptionsHeader(httpServletResponse, httpServletRequest);
    }

    protected void insertXFrameOptionsHeader(final HttpServletResponse httpServletResponse,
                                             final HttpServletRequest httpServletRequest) {
        insertXFrameOptionsHeader(httpServletResponse, httpServletRequest, this.xframeOptions);
    }

    protected void insertXFrameOptionsHeader(final HttpServletResponse httpServletResponse,
                                             final HttpServletRequest httpServletRequest,
                                             final String value) {
        val uri = httpServletRequest.getRequestURI();
        httpServletResponse.addHeader("X-Frame-Options", value);
        LOGGER.trace("Adding X-Frame Options [{}] response headers for [{}]", value, uri);
    }

    protected void decideInsertXContentTypeOptionsHeader(final HttpServletResponse httpServletResponse,
                                                         final HttpServletRequest httpServletRequest,
                                                         final Optional<RegisteredService> result) {
        if (!this.enableXContentTypeOptions) {
            return;
        }
        insertXContentTypeOptionsHeader(httpServletResponse, httpServletRequest);
    }

    protected void insertXContentTypeOptionsHeader(final HttpServletResponse httpServletResponse,
                                                   final HttpServletRequest httpServletRequest) {
        insertXContentTypeOptionsHeader(httpServletResponse, httpServletRequest, this.xContentTypeOptionsHeader);
    }

    protected void insertXContentTypeOptionsHeader(final HttpServletResponse httpServletResponse,
                                                   final HttpServletRequest httpServletRequest,
                                                   final String value) {
        val uri = httpServletRequest.getRequestURI();
        httpServletResponse.addHeader("X-Content-Type-Options", value);
        LOGGER.trace("Adding X-Content Type response headers [{}] for [{}]", value, uri);
    }

    protected void decideInsertCacheControlHeader(final HttpServletResponse httpServletResponse,
                                                  final HttpServletRequest httpServletRequest,
                                                  final Optional<RegisteredService> result) {
        if (!this.enableCacheControl) {
            return;
        }
        insertCacheControlHeader(httpServletResponse, httpServletRequest);
    }

    protected void insertCacheControlHeader(final HttpServletResponse httpServletResponse, final HttpServletRequest httpServletRequest) {
        insertCacheControlHeader(httpServletResponse, httpServletRequest, this.cacheControlHeader);
    }

    protected void insertCacheControlHeader(final HttpServletResponse httpServletResponse,
                                            final HttpServletRequest httpServletRequest,
                                            final String value) {

        val uri = httpServletRequest.getRequestURI();
        if (!cacheControlStaticResourcesPattern.matcher(uri).matches()) {
            httpServletResponse.addHeader("Cache-Control", value);
            httpServletResponse.addHeader("Pragma", "no-cache");
            httpServletResponse.addIntHeader("Expires", 0);
            LOGGER.trace("Adding Cache Control response headers for [{}]", uri);
        }
    }

    protected void decideInsertStrictTransportSecurityHeader(final HttpServletResponse httpServletResponse,
                                                             final HttpServletRequest httpServletRequest,
                                                             final Optional<RegisteredService> result) {
        if (!this.enableStrictTransportSecurity) {
            return;
        }
        insertStrictTransportSecurityHeader(httpServletResponse, httpServletRequest);
    }

    protected void insertStrictTransportSecurityHeader(final HttpServletResponse httpServletResponse,
                                                       final HttpServletRequest httpServletRequest) {
        insertStrictTransportSecurityHeader(httpServletResponse, httpServletRequest, this.strictTransportSecurityHeader);
    }

    protected void insertStrictTransportSecurityHeader(final HttpServletResponse httpServletResponse,
                                                       final HttpServletRequest httpServletRequest,
                                                       final String strictTransportSecurityHeader) {
        if (httpServletRequest.isSecure()) {
            val uri = httpServletRequest.getRequestURI();

            httpServletResponse.addHeader("Strict-Transport-Security", strictTransportSecurityHeader);
            LOGGER.trace("Adding HSTS response headers for [{}]", uri);
        }
    }
}
