package com.nec.endmile.state;

import com.google.common.collect.ImmutableList;
import com.nec.endmile.schema.CourierSchemaV1;
import net.corda.core.contracts.LinearState;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.crypto.SecureHash;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import net.corda.core.schemas.MappedSchema;
import net.corda.core.schemas.PersistentState;
import net.corda.core.schemas.QueryableState;
import net.corda.core.serialization.ConstructorForDeserialization;

import java.util.*;

/**
 * The state object recording CourierState.
 * <p>
 * A state must implement [ContractState] or one of its descendants.
 */
public class CourierState implements LinearState, QueryableState {
    private final int courierLength;
    private final int courierWidth;
    private final int courierHeight;
    private final int courierWeight;
    private final String courierReceiptHash;
    private final String source;
    private final String destination;
    private final Party requestor;
    private final Party acceptedParty;
    private final String finalQuotedPrice;
    private final String finalDeliveryType;
    private final String status;
    private final UniqueIdentifier linearId;
    private final String courierId;
    private final Map<String, String> responses;
    private final List<Party> autoNodes;

    @ConstructorForDeserialization
    public CourierState(int courierLength, int courierWidth, int courierHeight, int courierWeight,
                        String courierReceiptHash, String source, String destination, Party requestor, Party acceptedParty,
                        String finalQuotedPrice, String finalDeliveryType, String status, UniqueIdentifier linearId, Map<String, String> responses,
                        String courierId, List<Party> autoNodes) {
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
        this.autoNodes = autoNodes;
    }

    /**
     * Constructor for initiating courier request
     *
     * @param courierLength
     * @param courierWidth
     * @param courierHeight
     * @param courierWeight
     * @param source
     * @param destination
     * @param requestor
     * @param status
     * @param linearId
     */
    public CourierState(int courierLength, int courierWidth, int courierHeight, int courierWeight,
                        String source, String destination, Party requestor, String status, UniqueIdentifier linearId, String courierId, List<Party> autoNodes) {
        this.courierLength = courierLength;
        this.courierWidth = courierWidth;
        this.courierHeight = courierHeight;
        this.courierWeight = courierWeight;
        this.courierReceiptHash = null;
        this.source = source;
        this.destination = destination;
        this.requestor = requestor;
        this.acceptedParty = null;
        this.finalQuotedPrice = null;
        this.finalDeliveryType = null;
        this.status = status;
        this.linearId = linearId;
        this.responses = null;
        this.courierId = courierId;
        this.autoNodes = autoNodes;
    }

    /**
     * Constructor for responding to courier request
     *
     * @param courierLength
     * @param courierWidth
     * @param courierHeight
     * @param courierWeight
     * @param source
     * @param destination
     * @param requestor
     * @param status
     * @param linearId
     */
    public CourierState(int courierLength, int courierWidth, int courierHeight, int courierWeight,
                        String source, String destination, Party requestor, String status, UniqueIdentifier linearId, String courierId, Map<String, String> responses, List<Party> autoNodes) {
        this.courierLength = courierLength;
        this.courierWidth = courierWidth;
        this.courierHeight = courierHeight;
        this.courierWeight = courierWeight;
        this.courierReceiptHash = null;
        this.source = source;
        this.destination = destination;
        this.requestor = requestor;
        this.acceptedParty = null;
        this.finalQuotedPrice = null;
        this.finalDeliveryType = null;
        this.status = status;
        this.linearId = linearId;
        this.responses = responses;
        this.courierId = courierId;
        this.autoNodes = autoNodes;
    }


    public static Map<String, String> addResponse(Map<String, String> responses, String responderId, String sharedPrice, String dedicatedPrice) {

        if (responses == null) {
            responses = new LinkedHashMap<>();
        }

        responses.put(responderId, sharedPrice + "-" + dedicatedPrice);

        return responses;
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

    public Party getRequestor() {
        return requestor;
    }

    public Party getAcceptedParty() {
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

    public Map<String, String> getResponses() {
        return responses;
    }

    public String getCourierId() {
        return courierId;
    }

    @Override
    public UniqueIdentifier getLinearId() {
        return linearId;
    }

    public List<Party> getAutoNodes() {
        return autoNodes;
    }


    @Override
    public List<AbstractParty> getParticipants() {

        List<AbstractParty> participants = new ArrayList<>();
        participants.add(requestor);
        participants.addAll(autoNodes);
        //TODO change to requestor
        return participants;
    }

    @Override
    public PersistentState generateMappedObject(MappedSchema schema) {
        if (schema instanceof CourierSchemaV1) {

            String acceptedParty = null;
            if (this.acceptedParty != null)
                acceptedParty = this.acceptedParty.getName().toString();
            return new CourierSchemaV1.PersistentCourier(
                    this.courierLength,
                    this.courierWidth,
                    this.courierHeight,
                    this.courierWeight,
                    this.courierReceiptHash,
                    this.source,
                    this.destination,
                    this.requestor.getName().toString(),
                    acceptedParty,
                    this.finalQuotedPrice,
                    this.finalDeliveryType,
                    this.status,
                    this.linearId.getId(),
                    this.responses,
                    this.courierId);
        } else {
            throw new IllegalArgumentException("Unrecognised schema $schema");
        }
    }

    @Override
    public Iterable<MappedSchema> supportedSchemas() {
        return ImmutableList.of(new CourierSchemaV1());
    }

    @Override
    public String toString() {
        return "CourierState{" +
                "courierLength=" + courierLength + '\'' +
                ", courierWidth=" + courierWidth + '\'' +
                ", courierHeight=" + courierHeight + '\'' +
                ", courierWeight=" + courierWeight + '\'' +
                ", courierReceiptHash='" + courierReceiptHash + '\'' +
                ", source='" + source + '\'' +
                ", destination='" + destination + '\'' +
                ", requestor=" + requestor +
                ", acceptedParty=" + acceptedParty +
                ", finalQuotedPrice='" + finalQuotedPrice + '\'' +
                ", finalDeliveryType='" + finalDeliveryType + '\'' +
                ", status='" + status + '\'' +
                ", linearId=" + linearId +
                ", courierId='" + courierId + '\'' +
                ", responses=" + responses +
                ", autoNodes=" + autoNodes +
                '}';
    }
}