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
import org.wso2.identity.event.common.publisher.EventPublisher;
import org.wso2.identity.event.common.publisher.model.EventContext;
import org.wso2.identity.event.common.publisher.model.SecurityEventTokenPayload;
import org.wso2.identity.event.websubhub.publisher.constant.WebSubHubAdapterConstants;
import org.wso2.identity.event.websubhub.publisher.exception.WebSubAdapterException;
import org.wso2.identity.event.websubhub.publisher.internal.WebSubHubAdapterDataHolder;
import org.wso2.identity.event.websubhub.publisher.util.WebSubHubAdapterUtil;

import java.io.IOException;

/**
 * OSGi service for publishing events using web sub hub.
 */
public class WebSubHubAdapterServiceImpl implements EventPublisher {

    private static final Log log = LogFactory.getLog(WebSubHubAdapterServiceImpl.class);
    private String webSubHubBaseUrl = null;

    @Override
    public void publish(SecurityEventTokenPayload eventPayload, EventContext eventContext)
            throws WebSubAdapterException {

        WebSubHubAdapterUtil.makeAsyncAPICall(eventPayload, eventContext,
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

        try {
            WebSubHubAdapterUtil.makeTopicMgtAPICall(constructHubTopic(eventUri, tenantDomain), getWebSubBaseURL(),
                    WebSubHubAdapterConstants.Http.REGISTER);
            log.debug("WebSub Hub Topic registered successfully for the event: " + eventUri + " in tenant: " +
                    tenantDomain);
        } catch (IOException e) {
            throw WebSubHubAdapterUtil.handleServerException
                    (WebSubHubAdapterConstants.ErrorMessages.ERROR_REGISTERING_HUB_TOPIC, e, eventUri, tenantDomain);
        }
    }

    /**
     * Deregister a topic in the WebSub Hub.
     *
     * @param eventUri     Event URI.
     * @param tenantDomain Tenant domain.
     * @throws WebSubAdapterException If an error occurs while deregistering the topic.
     */
    public void deregisterTopic(String eventUri, String tenantDomain) throws WebSubAdapterException {

        try {
            WebSubHubAdapterUtil.makeTopicMgtAPICall(constructHubTopic(eventUri, tenantDomain),
                    getWebSubBaseURL(), WebSubHubAdapterConstants.Http.DEREGISTER);
        } catch (IOException e) {
            throw WebSubHubAdapterUtil.handleServerException
                    (WebSubHubAdapterConstants.ErrorMessages.ERROR_DEREGISTERING_HUB_TOPIC, e, eventUri, tenantDomain);
        }
    }

    private String getWebSubBaseURL() throws WebSubAdapterException {

        if (StringUtils.isEmpty(webSubHubBaseUrl)) {
            webSubHubBaseUrl =
                    WebSubHubAdapterDataHolder.getInstance().getAdapterConfiguration().getWebSubHubBaseUrl();

            // At this point, url shouldn't be null since if adapter is enabled, url is mandatory to configured.
            // But adding this as a second level verification.
            if (StringUtils.isEmpty(webSubHubBaseUrl)) {
                log.warn("WebSubHub Base URL is empty. WebSubHubEventPublisher will not engage.");
                throw WebSubHubAdapterUtil.handleClientException
                        (WebSubHubAdapterConstants.ErrorMessages.WEB_SUB_BASE_URL_NOT_CONFIGURED);
            }
        }
        return webSubHubBaseUrl;
    }

    private String constructHubTopic(String topicSuffix, String tenantDomain) {

        return tenantDomain + WebSubHubAdapterConstants.Http.TOPIC_SEPARATOR + topicSuffix;
    }
}
