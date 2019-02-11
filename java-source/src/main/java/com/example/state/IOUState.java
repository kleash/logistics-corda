package com.example.state;

import com.example.schema.IOUSchemaV1;
import com.google.common.collect.ImmutableList;
import net.corda.core.contracts.LinearState;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import net.corda.core.schemas.MappedSchema;
import net.corda.core.schemas.PersistentState;
import net.corda.core.schemas.QueryableState;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * The state object recording IOU agreements between two parties.
 *
 * A state must implement [ContractState] or one of its descendants.
 */
public class IOUState implements LinearState, QueryableState {
    private final Integer value;
    private final Map<String,String> responses;
    private final Party lender;
    private final Party borrower;
    private final UniqueIdentifier linearId;

    /**
     * @param value the value of the IOU.
     * @param lender the party issuing the IOU.
     * @param borrower the party receiving and approving the IOU.
     */
    public IOUState(Integer value,
                    Party lender,
                    Party borrower,
                    UniqueIdentifier linearId,Map<String,String> responses)
    {
        this.value = value;
        this.lender = lender;
        this.borrower = borrower;
        this.linearId = linearId;
        this.responses=responses;
    }

    public Integer getValue() { return value; }
    public Party getLender() { return lender; }
    public Party getBorrower() { return borrower; }

    public Map<String, String> getResponses() {
        return responses;
    }

    @Override public UniqueIdentifier getLinearId() { return linearId; }
    @Override public List<AbstractParty> getParticipants() {
        return Arrays.asList(lender, borrower);
    }

    @Override public PersistentState generateMappedObject(MappedSchema schema) {
        if (schema instanceof IOUSchemaV1) {
            return new IOUSchemaV1.PersistentIOU(
                    this.lender.getName().toString(),
                    this.borrower.getName().toString(),
                    this.value,
                    this.linearId.getId(),
                    this.responses);
        } else {
            throw new IllegalArgumentException("Unrecognised schema $schema");
        }
    }

    @Override public Iterable<MappedSchema> supportedSchemas() {
        return ImmutableList.of(new IOUSchemaV1());
    }

    @Override
    public String toString() {
        return String.format("IOUState(value=%s, lender=%s, borrower=%s, linearId=%s, responses=%s)", value, lender, borrower, linearId,responses);
    }
}