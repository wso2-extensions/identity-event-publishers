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

package org.wso2.identity.event.websubhub.publisher.internal;

import com.nimbusds.jose.util.DefaultResourceRetriever;
import org.wso2.identity.event.websubhub.publisher.config.WebSubAdapterConfiguration;

import java.security.KeyStore;

/**
 * WebSubHub Outbound Event Adapter service component's value holder.
 */
public class WebSubHubAdapterDataHolder {

    private static final WebSubHubAdapterDataHolder instance = new WebSubHubAdapterDataHolder();
    private ClientManager clientManager;
    private KeyStore trustStore;
    private WebSubAdapterConfiguration adapterConfiguration;
    private DefaultResourceRetriever resourceRetriever;

    private WebSubHubAdapterDataHolder() {

    }

    public static WebSubHubAdapterDataHolder getInstance() {

        return instance;
    }

    public ClientManager getClientManager() {

        return clientManager;
    }

    public void setClientManager(ClientManager clientManager) {

        this.clientManager = clientManager;
    }

    public KeyStore getTrustStore() {

        return trustStore;
    }

    public void setTrustStore(KeyStore trustStore) {

        this.trustStore = trustStore;
    }

    public WebSubAdapterConfiguration getAdapterConfiguration() {

        return adapterConfiguration;
    }

    public void setAdapterConfiguration(WebSubAdapterConfiguration adapterConfiguration) {

        this.adapterConfiguration = adapterConfiguration;
    }

    public DefaultResourceRetriever getResourceRetriever() {

        return resourceRetriever;
    }

    public void setResourceRetriever(DefaultResourceRetriever resourceRetriever) {

        this.resourceRetriever = resourceRetriever;
    }
}
