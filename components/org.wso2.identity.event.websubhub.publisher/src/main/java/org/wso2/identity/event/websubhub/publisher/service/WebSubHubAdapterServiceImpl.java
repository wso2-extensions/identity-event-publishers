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

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.util.EntityUtils;
import org.wso2.carbon.identity.base.IdentityRuntimeException;
import org.wso2.identity.event.common.publisher.EventPublisher;
import org.wso2.identity.event.common.publisher.model.EventContext;
import org.wso2.identity.event.common.publisher.model.SecurityEventTokenPayload;
import org.wso2.identity.event.websubhub.publisher.constant.WebSubHubAdapterConstants;
import org.wso2.identity.event.websubhub.publisher.exception.WebSubAdapterException;
import org.wso2.identity.event.websubhub.publisher.exception.WebSubAdapterServerException;
import org.wso2.identity.event.websubhub.publisher.internal.ClientManager;
import org.wso2.identity.event.websubhub.publisher.internal.WebSubHubAdapterDataHolder;
import org.wso2.identity.event.websubhub.publisher.util.WebSubHubCorrelationLogUtils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.wso2.identity.event.websubhub.publisher.constant.WebSubHubAdapterConstants.ErrorMessages.ERROR_BACKEND_ERROR_FROM_WEBSUB_HUB;
import static org.wso2.identity.event.websubhub.publisher.constant.WebSubHubAdapterConstants.ErrorMessages.ERROR_EMPTY_RESPONSE_FROM_WEBSUB_HUB;
import static org.wso2.identity.event.websubhub.publisher.constant.WebSubHubAdapterConstants.ErrorMessages.ERROR_INVALID_RESPONSE_FROM_WEBSUB_HUB;
import static org.wso2.identity.event.websubhub.publisher.constant.WebSubHubAdapterConstants.ErrorMessages.ERROR_INVALID_WEB_SUB_HUB_BASE_URL;
import static org.wso2.identity.event.websubhub.publisher.constant.WebSubHubAdapterConstants.ErrorMessages.TOPIC_DEREGISTRATION_FAILURE_ACTIVE_SUBS;
import static org.wso2.identity.event.websubhub.publisher.constant.WebSubHubAdapterConstants.Http.DEREGISTER;
import static org.wso2.identity.event.websubhub.publisher.constant.WebSubHubAdapterConstants.Http.ERROR_TOPIC_DEREG_FAILURE_ACTIVE_SUBS;
import static org.wso2.identity.event.websubhub.publisher.constant.WebSubHubAdapterConstants.Http.HUB_ACTIVE_SUBS;
import static org.wso2.identity.event.websubhub.publisher.constant.WebSubHubAdapterConstants.Http.HUB_MODE;
import static org.wso2.identity.event.websubhub.publisher.constant.WebSubHubAdapterConstants.Http.HUB_REASON;
import static org.wso2.identity.event.websubhub.publisher.constant.WebSubHubAdapterConstants.Http.HUB_TOPIC;
import static org.wso2.identity.event.websubhub.publisher.constant.WebSubHubAdapterConstants.Http.PUBLISH;
import static org.wso2.identity.event.websubhub.publisher.constant.WebSubHubAdapterConstants.Http.REGISTER;
import static org.wso2.identity.event.websubhub.publisher.constant.WebSubHubAdapterConstants.Http.RESPONSE_FOR_SUCCESSFUL_OPERATION;
import static org.wso2.identity.event.websubhub.publisher.util.WebSubHubAdapterUtil.handleClientException;
import static org.wso2.identity.event.websubhub.publisher.util.WebSubHubAdapterUtil.handleResponseCorrelationLog;
import static org.wso2.identity.event.websubhub.publisher.util.WebSubHubAdapterUtil.handleServerException;
import static org.wso2.identity.event.websubhub.publisher.util.WebSubHubAdapterUtil.logDiagnosticFailure;
import static org.wso2.identity.event.websubhub.publisher.util.WebSubHubAdapterUtil.logDiagnosticSuccess;
import static org.wso2.identity.event.websubhub.publisher.util.WebSubHubAdapterUtil.logPublishingEvent;
import static org.wso2.identity.event.websubhub.publisher.util.WebSubHubAdapterUtil.parseEventHubResponse;


/**
 * OSGi service for publishing events using web sub hub.
 */
public class WebSubHubAdapterServiceImpl implements EventPublisher {

    private static final Log log = LogFactory.getLog(WebSubHubAdapterServiceImpl.class);
    private String webSubHubBaseUrl = null;

    @Override
    public void publish(SecurityEventTokenPayload eventPayload, EventContext eventContext)
            throws WebSubAdapterException {

        makeAsyncAPICall(eventPayload, eventContext,
                constructHubTopic(eventContext.getEventUri(), eventContext.getTenantDomain()), getWebSubBaseURL());
        log.debug("Event published successfully to the WebSub Hub.");
    }

    /**
     * Register a topic in the WebSub Hub.
     *
     * @param eventUri     Event URI.
     * @param tenantDomain Tenant domain.
     * @throws WebSubAdapterException If an error occurs while registering the topic.
     */
    public void registerTopic(String eventUri, String tenantDomain) throws WebSubAdapterException {

        makeTopicMgtAPICall(constructHubTopic(eventUri, tenantDomain), getWebSubBaseURL(),
                WebSubHubAdapterConstants.Http.REGISTER);
        log.debug("WebSub Hub Topic registered successfully for the event: " + eventUri + " in tenant: " +
                tenantDomain);
    }

    /**
     * Deregister a topic in the WebSub Hub.
     *
     * @param eventUri     Event URI.
     * @param tenantDomain Tenant domain.
     * @throws WebSubAdapterException If an error occurs while deregistering the topic.
     */
    public void deregisterTopic(String eventUri, String tenantDomain) throws WebSubAdapterException {

        makeTopicMgtAPICall(constructHubTopic(eventUri, tenantDomain),
                getWebSubBaseURL(), DEREGISTER);
    }

    private String getWebSubBaseURL() throws WebSubAdapterException {

        if (StringUtils.isEmpty(webSubHubBaseUrl)) {
            webSubHubBaseUrl =
                    WebSubHubAdapterDataHolder.getInstance().getAdapterConfiguration().getWebSubHubBaseUrl();

            // At this point, url shouldn't be null since if adapter is enabled, url is mandatory to configured.
            // But adding this as a second level verification.
            if (StringUtils.isEmpty(webSubHubBaseUrl)) {
                throw handleClientException
                        (WebSubHubAdapterConstants.ErrorMessages.WEB_SUB_BASE_URL_NOT_CONFIGURED);
            }
        }
        return webSubHubBaseUrl;
    }

    private String constructHubTopic(String topicSuffix, String tenantDomain) {

        return tenantDomain + WebSubHubAdapterConstants.Http.TOPIC_SEPARATOR + topicSuffix;
    }

    private void makeAsyncAPICall(SecurityEventTokenPayload eventPayload, EventContext eventContext,
                                        String topic, String webSubHubBaseUrl) throws WebSubAdapterException {

        String url = buildURL(topic, webSubHubBaseUrl, PUBLISH);

        ClientManager clientManager = WebSubHubAdapterDataHolder.getInstance().getClientManager();
        HttpPost request = clientManager.createHttpPost(url, eventPayload);

        logPublishingEvent(url, eventContext, topic);

        final long requestStartTime = System.currentTimeMillis();

        CompletableFuture<HttpResponse> future = clientManager.executeAsync(request);

        future.thenAccept(response -> handleAsyncResponse
                        (response, request, requestStartTime, eventContext, url, topic))
                .exceptionally(ex -> {
                    handleResponseCorrelationLog(request, requestStartTime,
                            WebSubHubCorrelationLogUtils.RequestStatus.FAILED.getStatus(),
                            ex.getMessage());
                    log.error("Publishing event data to WebSubHub failed. ", ex);
                    throw new IdentityRuntimeException("Error occurred while publishing event data to WebSubHub. ", ex);
                });
    }

    private static String buildURL(String topic, String webSubHubBaseUrl, String operation)
            throws WebSubAdapterServerException {

        try {
            URIBuilder uriBuilder = new URIBuilder(webSubHubBaseUrl);
            uriBuilder.addParameter(HUB_MODE, operation);
            uriBuilder.addParameter(HUB_TOPIC, topic);
            return uriBuilder.build().toString();
        } catch (URISyntaxException e) {
            log.error("Error building URL", e);
            throw handleServerException(ERROR_INVALID_WEB_SUB_HUB_BASE_URL, e);
        }
    }

    private void makeTopicMgtAPICall(String topic, String webSubHubBaseUrl, String operation)
            throws WebSubAdapterException {

        String topicMgtUrl = buildURL(topic, webSubHubBaseUrl, operation);

        ClientManager clientManager = WebSubHubAdapterDataHolder.getInstance().getClientManager();
        HttpPost httpPost = clientManager.createHttpPost(topicMgtUrl, null);

        WebSubHubCorrelationLogUtils.triggerCorrelationLogForRequest(httpPost);
        final long requestStartTime = System.currentTimeMillis();

        CompletableFuture<HttpResponse> future = clientManager.executeAsync(httpPost);

        future.thenAccept(response -> {
            try {
                handleTopicMgtResponse((CloseableHttpResponse) response, httpPost, topic, operation, requestStartTime);
            } catch (IOException | WebSubAdapterException e) {
                log.error("Handling topic management response failed. ", e);
            }
        }).exceptionally(ex -> {
            log.error("Topic management API call failed. ", ex);
            return null;
        });
    }

    private void handleTopicMgtResponse(CloseableHttpResponse response, HttpPost httpPost,
                                               String topic, String operation, long requestStartTime)
            throws IOException, WebSubAdapterException {

        StatusLine statusLine = response.getStatusLine();
        int responseCode = statusLine.getStatusCode();
        String responsePhrase = statusLine.getReasonPhrase();

        if (responseCode == HttpStatus.SC_OK) {
            HttpEntity entity = response.getEntity();
            WebSubHubCorrelationLogUtils.triggerCorrelationLogForResponse(httpPost, requestStartTime,
                    WebSubHubCorrelationLogUtils.RequestStatus.COMPLETED.getStatus(),
                    String.valueOf(responseCode), responsePhrase);
            handleSuccessfulTopicMgt(entity, topic, operation);
        } else if ((responseCode == HttpStatus.SC_CONFLICT && operation.equals(REGISTER)) ||
                (responseCode == HttpStatus.SC_NOT_FOUND && operation.equals(DEREGISTER))) {
            HttpEntity entity = response.getEntity();
            WebSubHubCorrelationLogUtils.triggerCorrelationLogForResponse(httpPost, requestStartTime,
                    WebSubHubCorrelationLogUtils.RequestStatus.FAILED.getStatus(),
                    String.valueOf(responseCode), responsePhrase);
            handleConflictOrNotFound(entity, topic, operation);
        } else {
            WebSubHubCorrelationLogUtils.triggerCorrelationLogForResponse(httpPost, requestStartTime,
                    WebSubHubCorrelationLogUtils.RequestStatus.CANCELLED.getStatus(),
                    String.valueOf(responseCode), responsePhrase);
            if (responseCode == HttpStatus.SC_FORBIDDEN) {
                handleForbiddenResponse(response, topic);
            }
            HttpEntity entity = response.getEntity();
            handleFailedTopicMgt(entity, topic, operation, responseCode);
        }
    }

    private static void handleAsyncResponse(HttpResponse response, HttpPost request, long requestStartTime,
                                             EventContext eventContext, String url, String topic) {

        int responseCode = response.getStatusLine().getStatusCode();
        String responsePhrase = response.getStatusLine().getReasonPhrase();
        log.debug("WebSubHub request completed. Response code: " + responseCode);
        handleResponseCorrelationLog(request, requestStartTime,
                WebSubHubCorrelationLogUtils.RequestStatus.COMPLETED.getStatus(),
                String.valueOf(responseCode), responsePhrase);

        if (responseCode == HttpStatus.SC_OK || responseCode == HttpStatus.SC_CREATED ||
                responseCode == HttpStatus.SC_ACCEPTED || responseCode == HttpStatus.SC_NO_CONTENT) {
            logDiagnosticSuccess(eventContext, url, topic);
            try {
                log.debug("Response data: " + EntityUtils.toString(response.getEntity()));
            } catch (IOException e) {
                log.debug("Error while reading WebSubHub event publisher response. ", e);
            }
        } else {
            logDiagnosticFailure(eventContext, url, topic);
            try {
                String errorResponseBody = EntityUtils.toString(response.getEntity());
                log.error("WebHubSub event publisher received " + responseCode + " code. Response data: " +
                        errorResponseBody);
            } catch (IOException e) {
                log.error("Error while reading WebSubHub event publisher response. ", e);
            }
        }
    }

    private static void handleSuccessfulTopicMgt(HttpEntity entity, String topic, String operation)
            throws WebSubAdapterException, IOException {

        if (entity != null) {
            String responseString = EntityUtils.toString(entity, StandardCharsets.UTF_8);
            if (RESPONSE_FOR_SUCCESSFUL_OPERATION.equals(responseString)) {
                log.debug("Success WebSub Hub operation: " + operation + ", topic: " + topic);
            } else {
                throw handleServerException(ERROR_INVALID_RESPONSE_FROM_WEBSUB_HUB, null,
                        topic, operation, responseString);
            }
        } else {
            String message = String.format(ERROR_EMPTY_RESPONSE_FROM_WEBSUB_HUB.getDescription(), topic, operation);
            throw new WebSubAdapterServerException(message, ERROR_EMPTY_RESPONSE_FROM_WEBSUB_HUB.getCode());
        }
    }

    private static void handleConflictOrNotFound(HttpEntity entity, String topic, String operation) throws IOException {

        String responseString = "";
        if (entity != null) {
            responseString = EntityUtils.toString(entity, StandardCharsets.UTF_8);
        }
        log.warn(String.format(ERROR_INVALID_RESPONSE_FROM_WEBSUB_HUB.getDescription(),
                topic, operation, responseString));
    }

    private static void handleFailedTopicMgt(HttpEntity entity, String topic, String operation, int responseCode)
            throws IOException, WebSubAdapterException {

        String responseString = "";
        if (entity != null) {
            responseString = EntityUtils.toString(entity, StandardCharsets.UTF_8);
        }
        String message = String.format(ERROR_BACKEND_ERROR_FROM_WEBSUB_HUB.getDescription(),
                topic, operation, responseString);
        log.error(message + ", Response code:" + responseCode);
        throw new WebSubAdapterServerException(message, ERROR_BACKEND_ERROR_FROM_WEBSUB_HUB.getCode());
    }

    private static void handleForbiddenResponse(CloseableHttpResponse response, String topic) throws IOException,
            WebSubAdapterException {

        Map<String, String> hubResponse = parseEventHubResponse(response);
        if (!hubResponse.isEmpty() && hubResponse.containsKey(HUB_REASON)) {
            String errorMsg = String.format(ERROR_TOPIC_DEREG_FAILURE_ACTIVE_SUBS, topic);
            if (errorMsg.equals(hubResponse.get(HUB_REASON))) {
                log.debug(String.format(TOPIC_DEREGISTRATION_FAILURE_ACTIVE_SUBS.getDescription(),
                        topic, hubResponse.get(HUB_ACTIVE_SUBS)));
                throw handleClientException(TOPIC_DEREGISTRATION_FAILURE_ACTIVE_SUBS, topic,
                        hubResponse.get(HUB_ACTIVE_SUBS));
            }
        }
    }
}
