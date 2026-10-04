/*
 * Copyright (c) 2024, WSO2 LLC. (http://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.identity.event.websubhub.publisher.service;

import org.apache.http.HttpResponse;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.carbon.identity.central.log.mgt.utils.LoggerUtils;
import org.wso2.carbon.identity.core.util.IdentityTenantUtil;
import org.wso2.carbon.identity.event.publisher.api.exception.EventPublisherException;
import org.wso2.carbon.identity.event.publisher.api.model.EventContext;
import org.wso2.carbon.identity.event.publisher.api.model.EventPayload;
import org.wso2.carbon.identity.event.publisher.api.model.SecurityEventTokenPayload;
import org.wso2.identity.event.websubhub.publisher.config.WebSubAdapterConfiguration;
import org.wso2.identity.event.websubhub.publisher.exception.WebSubAdapterException;
import org.wso2.identity.event.websubhub.publisher.internal.ClientManager;
import org.wso2.identity.event.websubhub.publisher.internal.WebSubHubAdapterDataHolder;
import org.wso2.identity.event.websubhub.publisher.util.WebSubHubAdapterUtil;
import org.wso2.identity.event.websubhub.publisher.util.WebSubHubCorrelationLogUtils;

import java.nio.file.Paths;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.wso2.identity.event.websubhub.publisher.constant.WebSubHubAdapterConstants.Http.CORRELATION_ID_REQUEST_HEADER;

/**
 * Unit tests for the WebSubHubAdapterServiceImpl class.
 */
public class WebSubEventPublisherImplTest {

    private WebSubEventPublisherImpl adapterService;
    private AutoCloseable mocks;

    @Mock
    private ClientManager mockClientManager;

    @Mock
    private WebSubAdapterConfiguration mockAdapterConfiguration;

    @Mock
    private HttpResponse mockHttpResponse;

    private MockedStatic<WebSubHubAdapterDataHolder> mockedStaticDataHolder;
    private static MockedStatic<IdentityTenantUtil> mockedStaticIdentityTenantUtil;

    @BeforeClass
    public void setUp() throws Exception {

        // The response callback starts a tenant flow, and PrivilegedCarbonContext fails to
        // initialise without a carbon home.
        System.setProperty("carbon.home",
                Paths.get(System.getProperty("user.dir"), "src", "test", "resources").toString());

        mocks = MockitoAnnotations.openMocks(this);
        adapterService = spy(new WebSubEventPublisherImpl());
        mockIdentityTenantUtil();

        mockedStaticDataHolder = mockStatic(WebSubHubAdapterDataHolder.class);
        WebSubHubAdapterDataHolder mockDataHolder = mock(WebSubHubAdapterDataHolder.class);
        mockedStaticDataHolder.when(WebSubHubAdapterDataHolder::getInstance).thenReturn(mockDataHolder);

        when(mockDataHolder.getClientManager()).thenReturn(mockClientManager);
        when(mockDataHolder.getAdapterConfiguration()).thenReturn(mockAdapterConfiguration);
        when(mockAdapterConfiguration.getWebSubHubBaseUrl()).thenReturn("http://mock-websub-hub.com");

        // Mock OrganizationManager
        org.wso2.carbon.identity.organization.management.service.OrganizationManager mockOrgManager =
                mock(org.wso2.carbon.identity.organization.management.service.OrganizationManager.class);
        when(mockDataHolder.getOrganizationManager()).thenReturn(mockOrgManager);
        when(mockOrgManager.resolveOrganizationId(any())).thenReturn("mock-org-id");
    }

    @AfterClass
    public void tearDown() throws Exception {

        if (mocks != null) {
            mocks.close();
        }
        if (mockedStaticDataHolder != null) {
            mockedStaticDataHolder.close();
        }
    }

    @Test
    public void testPublishSuccess() throws EventPublisherException, WebSubAdapterException {

        try (
                MockedStatic<LoggerUtils> mockedLoggerUtils = mockStatic(LoggerUtils.class);
                MockedStatic<WebSubHubAdapterUtil> mockedAdapterUtil = mockStatic(WebSubHubAdapterUtil.class)
        ) {
            mockedLoggerUtils.when(LoggerUtils::isDiagnosticLogsEnabled).thenReturn(false);

            // Mock static utility methods used in publish
            mockedAdapterUtil.when(() -> WebSubHubAdapterUtil.constructHubTopic(any(), any(), any(), any()))
                    .thenReturn("mock-topic");
            mockedAdapterUtil.when(WebSubHubAdapterUtil::getWebSubBaseURL)
                    .thenReturn("http://mock-websub-hub.com");
            mockedAdapterUtil.when(
                            () -> WebSubHubAdapterUtil
                                    .printPublisherDiagnosticLog(any(), any(), any(), any(), any(), any()))
                    .then(invocation -> null);

            // Mock ClientManager.getMaxRetries()
            when(mockClientManager.getMaxRetries()).thenReturn(2);

            // Mock inputs
            EventContext eventContext = EventContext.builder()
                    .tenantDomain("test-tenant")
                    .eventProfileName("WSO2")
                    .eventUri("test-uri")
                    .build();
            SecurityEventTokenPayload payload = SecurityEventTokenPayload.builder()
                    .iss("issuer")
                    .jti("jti-token")
                    .iat(System.currentTimeMillis())
                    .aud("audience")
                    .events(Collections.singletonMap("event1", new EventPayload() {
                    }))
                    .build();

            // Mock HttpPost and its header
            org.apache.http.client.methods.HttpPost mockHttpPost = mock(org.apache.http.client.methods.HttpPost.class);
            org.apache.http.Header mockHeader = mock(org.apache.http.Header.class);
            when(mockHttpPost.getFirstHeader(CORRELATION_ID_REQUEST_HEADER)).thenReturn(mockHeader);
            when(mockHeader.getValue()).thenReturn("mock-correlation-id");

            // Mock ClientManager behavior to simulate success
            CompletableFuture<HttpResponse> future = CompletableFuture.completedFuture(mockHttpResponse);
            when(mockClientManager.executeAsync(any())).thenReturn(future);
            when(mockClientManager.createHttpPost(any(), any(), any())).thenReturn(mockHttpPost);
            when(mockClientManager.getAsyncCallbackExecutor()).thenReturn((Executor) Runnable::run);

            // Execute and verify no exception is thrown
            adapterService.publish(payload, eventContext);

            // Verify interactions
            verify(mockClientManager, times(1)).executeAsync(any());
        }
    }

    @Test
    public void testClientErrorResponseIsReportedAndNotRetried()
            throws EventPublisherException, WebSubAdapterException {

        try (
                MockedStatic<LoggerUtils> mockedLoggerUtils = mockStatic(LoggerUtils.class);
                MockedStatic<WebSubHubAdapterUtil> mockedAdapterUtil = mockStatic(WebSubHubAdapterUtil.class)
        ) {
            mockedLoggerUtils.when(LoggerUtils::isDiagnosticLogsEnabled).thenReturn(false);

            mockedAdapterUtil.when(() -> WebSubHubAdapterUtil.constructHubTopic(any(), any(), any(), any()))
                    .thenReturn("mock-topic");
            mockedAdapterUtil.when(WebSubHubAdapterUtil::getWebSubBaseURL)
                    .thenReturn("http://mock-websub-hub.com");
            mockedAdapterUtil.when(
                            () -> WebSubHubAdapterUtil
                                    .printPublisherDiagnosticLog(any(), any(), any(), any(), any(), any()))
                    .then(invocation -> null);

            when(mockClientManager.getMaxRetries()).thenReturn(2);
            clearInvocations(mockClientManager);

            EventContext eventContext = EventContext.builder()
                    .tenantDomain("test-tenant")
                    .eventProfileName("WSO2")
                    .eventUri("test-uri")
                    .build();
            SecurityEventTokenPayload payload = SecurityEventTokenPayload.builder()
                    .iss("issuer")
                    .jti("jti-token")
                    .iat(System.currentTimeMillis())
                    .aud("audience")
                    .events(Collections.singletonMap("event1", new EventPayload() {
                    }))
                    .build();

            org.apache.http.client.methods.HttpPost mockHttpPost = mock(org.apache.http.client.methods.HttpPost.class);
            org.apache.http.Header mockHeader = mock(org.apache.http.Header.class);
            when(mockHttpPost.getFirstHeader(CORRELATION_ID_REQUEST_HEADER)).thenReturn(mockHeader);
            when(mockHeader.getValue()).thenReturn("mock-correlation-id");

            org.apache.http.StatusLine clientErrorStatusLine = mock(org.apache.http.StatusLine.class);
            when(clientErrorStatusLine.getStatusCode()).thenReturn(400);
            when(clientErrorStatusLine.getReasonPhrase()).thenReturn("Bad Request");
            HttpResponse clientErrorResponse = mock(HttpResponse.class);
            when(clientErrorResponse.getStatusLine()).thenReturn(clientErrorStatusLine);
            when(clientErrorResponse.getEntity()).thenReturn(null);

            CompletableFuture<HttpResponse> future = CompletableFuture.completedFuture(clientErrorResponse);
            when(mockClientManager.executeAsync(any())).thenReturn(future);
            when(mockClientManager.createHttpPost(any(), any(), any())).thenReturn(mockHttpPost);
            when(mockClientManager.getAsyncCallbackExecutor()).thenReturn((Executor) Runnable::run);

            adapterService.publish(payload, eventContext);

            try {
                // The client error reaches the diagnostic log, which is where an operator sees it.
                mockedAdapterUtil.verify(() -> WebSubHubAdapterUtil.printPublisherDiagnosticLog(any(), any(), any(),
                        any(), any(), contains("client error")));
                // And the correlation log, which the client error path previously never reached.
                mockedAdapterUtil.verify(() -> WebSubHubAdapterUtil.handleResponseCorrelationLog(any(), anyLong(),
                        eq(WebSubHubCorrelationLogUtils.RequestStatus.FAILED.getStatus()), eq("400"),
                        eq("Bad Request")));
                // A client error is final. The event is not republished.
                verify(mockClientManager, times(1)).executeAsync(any());
            } finally {
                // This mock is shared across the class, so leave it clean for the other tests.
                clearInvocations(mockClientManager);
            }
        }
    }

    /**
     * Mocks the IdentityTenantUtil.
     */
     private static void mockIdentityTenantUtil() {

        if (mockedStaticIdentityTenantUtil != null && !mockedStaticIdentityTenantUtil.isClosed()) {
            mockedStaticIdentityTenantUtil.close();
        }
        mockedStaticIdentityTenantUtil = mockStatic(IdentityTenantUtil.class);
        when(IdentityTenantUtil.isTenantedSessionsEnabled()).thenReturn(false);
        when(IdentityTenantUtil.getTenantId("test-tenant")).thenReturn(1);
    }
}
