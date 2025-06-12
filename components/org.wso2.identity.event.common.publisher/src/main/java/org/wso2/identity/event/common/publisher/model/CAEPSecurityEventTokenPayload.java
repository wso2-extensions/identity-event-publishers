package org.wso2.identity.event.common.publisher.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.wso2.identity.event.common.publisher.model.common.Subject;

public class CAEPSecurityEventTokenPayload extends SecurityEventTokenPayload {

    @JsonProperty("sub_id")
    private final Subject subId;

    private CAEPSecurityEventTokenPayload(Builder builder) {


        super(builder);
        this.subId = builder.subId;
    }

    public Subject getSubId() {

        return subId;
    }

    public static Builder builder() {

        return new Builder();
    }

    public static class Builder extends SecurityEventTokenPayload.Builder {

        private Subject subId;

        public Builder subId(Subject subId) {

            this.subId = subId;
            return this;
        }

        @Override
        public CAEPSecurityEventTokenPayload build() {

            return new CAEPSecurityEventTokenPayload(this);
        }
    }

}
