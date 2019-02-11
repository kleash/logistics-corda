package com.nec.endmile.schema;

import com.google.common.collect.ImmutableList;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import net.corda.core.schemas.MappedSchema;
import net.corda.core.schemas.PersistentState;

import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * An CourierState schema.
 */
public class CourierSchemaV1 extends MappedSchema {
    public CourierSchemaV1() {
        super(CourierSchema.class, 1, ImmutableList.of(PersistentCourier.class));
    }

    @Entity
    @Table(name = "courier_states")
    public static class PersistentCourier extends PersistentState {
        @Column(name = "courierLength")
        private final int courierLength;
        @Column(name = "courierWidth")
        private final int courierWidth;
        @Column(name = "courierHeight")
        private final int courierHeight;
        @Column(name = "courierWeight")
        private final int courierWeight;
        @Column(name = "courierReceiptHash")
        private final String courierReceiptHash;
        @Column(name = "source")
        private final String source;
        @Column(name = "destination")
        private final String destination;
        @Column(name = "requestor")
        private final String requestor;
        @Column(name = "acceptedParty")
        private final String acceptedParty;
        @Column(name = "finalQuotedPrice")
        private final String finalQuotedPrice;
        @Column(name = "finalDeliveryType")
        private final String finalDeliveryType;
        @Column(name = "status")
        private final String status;
        @Column(name = "linear_id")
        private final UUID linearId;
        @Column(name = "responses")
        @ElementCollection
        private final Map<String, String> responses;
        @Column(name = "courierId")
        private final String courierId;


        public PersistentCourier(int courierLength, int courierWidth, int courierHeight, int courierWeight, String courierReceiptHash, String source, String destination, String requestor, String acceptedParty,
                                 String finalQuotedPrice, String finalDeliveryType, String status, UUID linearId,
                                 Map<String, String> responses, String courierId) {
            this.courierLength = courierLength;
            this.courierWidth = courierWidth;
            this.courierHeight = courierHeight;
            this.courierWeight = courierWeight;
            this.courierReceiptHash = courierReceiptHash;
            this.source = source;
            this.destination = destination;
            this.requestor = requestor;
            this.acceptedParty = acceptedParty;
            this.finalQuotedPrice = finalQuotedPrice;
            this.finalDeliveryType = finalDeliveryType;
            this.status = status;
            this.linearId = linearId;
            this.responses = responses;
            this.courierId = courierId;
        }

        // Default constructor required by hibernate.
        public PersistentCourier() {
            this.courierLength = 0;
            this.courierWidth = 0;
            this.courierHeight = 0;
            this.courierWeight = 0;
            this.courierReceiptHash = null;
            this.source = null;
            this.destination = null;
            this.requestor = null;
            this.acceptedParty = null;
            this.finalQuotedPrice = null;
            this.finalDeliveryType = null;
            this.status = null;
            this.linearId = null;
            this.responses = null;
            this.courierId = null;
        }

        public int getCourierLength() {
            return courierLength;
        }

        public int getCourierWidth() {
            return courierWidth;
        }

        public int getCourierHeight() {
            return courierHeight;
        }

        public int getCourierWeight() {
            return courierWeight;
        }

        public String getCourierReceiptHash() {
            return courierReceiptHash;
        }

        public String getSource() {
            return source;
        }

        public String getDestination() {
            return destination;
        }

        public String getRequestor() {
            return requestor;
        }

        public String getAcceptedParty() {
            return acceptedParty;
        }

        public String getFinalQuotedPrice() {
            return finalQuotedPrice;
        }

        public String getFinalDeliveryType() {
            return finalDeliveryType;
        }

        public String getStatus() {
            return status;
        }

        public UUID getLinearId() {
            return linearId;
        }

        public Map<String, String> getResponses() {
            return responses;
        }

        public String getCourierId() {
            return courierId;
        }
    }

}