/*
 * Copyright (c) 2024-2026, WSO2 LLC. (http://www.wso2.com).
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
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.carbon.identity.central.log.mgt.utils.LoggerUtils;
import org.wso2.carbon.identity.core.util.IdentityTenantUtil;
import org.wso2.carbon.identity.event.publisher.api.exception.EventPublisherException;
import org.wso2.carbon.identity.event.publisher.api.exception.EventPublisherServerException;
import org.wso2.carbon.identity.event.publisher.api.model.EventContext;
import org.wso2.carbon.identity.event.publisher.api.model.EventPayload;
import org.wso2.carbon.identity.event.publisher.api.model.SecurityEventTokenPayload;
import org.wso2.carbon.identity.topic.management.api.exception.TopicManagementException;
import org.wso2.carbon.identity.topic.management.api.service.TopicManagementService;
import org.wso2.carbon.identity.webhook.management.api.exception.WebhookMgtException;
import org.wso2.carbon.identity.webhook.management.api.model.Webhook;
import org.wso2.carbon.identity.webhook.management.api.service.WebhookManagementService;
import org.wso2.identity.event.websubhub.publisher.config.WebSubAdapterConfiguration;
import org.wso2.identity.event.websubhub.publisher.exception.WebSubAdapterException;
import org.wso2.identity.event.websubhub.publisher.internal.ClientManager;
import org.wso2.identity.event.websubhub.publisher.internal.WebSubHubAdapterDataHolder;
import org.wso2.identity.event.websubhub.publisher.util.WebSubHubAdapterUtil;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
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

    private WebSubHubAdapterDataHolder mockDataHolder;
    private WebhookManagementService mockWebhookManagementService;
    private TopicManagementService mockTopicManagementService;

    private MockedStatic<WebSubHubAdapterDataHolder> mockedStaticDataHolder;
    private static MockedStatic<IdentityTenantUtil> mockedStaticIdentityTenantUtil;

    @BeforeClass
    public void setUp() throws Exception {

        mocks = MockitoAnnotations.openMocks(this);
        adapterService = spy(new WebSubEventPublisherImpl());
        mockIdentityTenantUtil();

        mockedStaticDataHolder = mockStatic(WebSubHubAdapterDataHolder.class);
        mockDataHolder = mock(WebSubHubAdapterDataHolder.class);
        mockedStaticDataHolder.when(WebSubHubAdapterDataHolder::getInstance).thenReturn(mockDataHolder);

        when(mockDataHolder.getClientManager()).thenReturn(mockClientManager);
        when(mockDataHolder.getAdapterConfiguration()).thenReturn(mockAdapterConfiguration);
        when(mockAdapterConfiguration.getWebSubHubBaseUrl()).thenReturn("http://mock-websub-hub.com");

        // Mock OrganizationManager
        org.wso2.carbon.identity.organization.management.service.OrganizationManager mockOrgManager =
                mock(org.wso2.carbon.identity.organization.management.service.OrganizationManager.class);
        when(mockDataHolder.getOrganizationManager()).thenReturn(mockOrgManager);
        when(mockOrgManager.resolveOrganizationId(any())).thenReturn("mock-org-id");

        mockWebhookManagementService = mock(WebhookManagementService.class);
        mockTopicManagementService = mock(TopicManagementService.class);
        when(mockDataHolder.getWebhookManagementService()).thenReturn(mockWebhookManagementService);
        when(mockDataHolder.getTopicManagementService()).thenReturn(mockTopicManagementService);
    }

    @BeforeMethod
    public void resetCanHandleEventMocks() {

        // Mocks are class-scoped, so clear invocation history so each test can assert
        // call counts independently of preceding tests.
        if (mockWebhookManagementService != null && mockTopicManagementService != null) {
            clearInvocations(mockWebhookManagementService, mockTopicManagementService);
        }
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
    public void testCanHandleEventReturnsFalseWhenActiveWebhooksNull() throws Exception {

        when(mockWebhookManagementService.getActiveWebhooks(any(), any(), any(), any())).thenReturn(null);

        assertFalse(adapterService.canHandleEvent(buildEventContext()));
        // Topic existence must not be queried when no active webhooks are present.
        verify(mockTopicManagementService, never()).isTopicExists(any(), any(), any(), any());
    }

    @Test
    public void testCanHandleEventReturnsFalseWhenActiveWebhooksEmpty() throws Exception {

        when(mockWebhookManagementService.getActiveWebhooks(any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        assertFalse(adapterService.canHandleEvent(buildEventContext()));
        verify(mockTopicManagementService, never()).isTopicExists(any(), any(), any(), any());
    }

    @Test
    public void testCanHandleEventReturnsTrueWhenWebhooksExistAndTopicExists() throws Exception {

        when(mockWebhookManagementService.getActiveWebhooks(any(), any(), any(), any()))
                .thenReturn(Collections.singletonList(mock(Webhook.class)));
        when(mockTopicManagementService.isTopicExists(any(), any(), any(), any())).thenReturn(true);

        assertTrue(adapterService.canHandleEvent(buildEventContext()));
        verify(mockTopicManagementService, times(1))
                .isTopicExists("test-uri", "WSO2", "v1", "test-tenant");
    }

    @Test
    public void testCanHandleEventReturnsFalseWhenWebhooksExistButTopicMissing() throws Exception {

        when(mockWebhookManagementService.getActiveWebhooks(any(), any(), any(), any()))
                .thenReturn(Collections.singletonList(mock(Webhook.class)));
        when(mockTopicManagementService.isTopicExists(any(), any(), any(), any())).thenReturn(false);

        assertFalse(adapterService.canHandleEvent(buildEventContext()));
    }

    @Test
    public void testCanHandleEventWrapsWebhookMgtExceptionAsServerException() throws Exception {

        when(mockWebhookManagementService.getActiveWebhooks(any(), any(), any(), any()))
                .thenThrow(new WebhookMgtException("boom"));

        try {
            adapterService.canHandleEvent(buildEventContext());
            org.testng.Assert.fail("Expected EventPublisherServerException");
        } catch (EventPublisherServerException e) {
            assertEquals(e.getErrorCode(), "WEBSUB-65016");
            // Topic existence must not be checked once the webhook lookup itself failed.
            verify(mockTopicManagementService, never()).isTopicExists(any(), any(), any(), any());
        }
    }

    @Test(expectedExceptions = EventPublisherException.class)
    public void testCanHandleEventWrapsTopicManagementException() throws Exception {

        when(mockWebhookManagementService.getActiveWebhooks(any(), any(), any(), any()))
                .thenReturn(Collections.singletonList(mock(Webhook.class)));
        when(mockTopicManagementService.isTopicExists(any(), any(), any(), any()))
                .thenThrow(new TopicManagementException("code", "msg", "desc"));

        adapterService.canHandleEvent(buildEventContext());
    }

    private EventContext buildEventContext() {

        return EventContext.builder()
                .tenantDomain("test-tenant")
                .eventProfileName("WSO2")
                .eventProfileVersion("v1")
                .eventUri("test-uri")
                .build();
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
