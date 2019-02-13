package com.nec.endmile.flow;

import co.paralleluniverse.fibers.Suspendable;
import com.google.common.collect.ImmutableList;
import com.nec.endmile.config.CourierStatus;
import com.nec.endmile.contract.CourierContract;
import com.nec.endmile.schema.CourierSchemaV1;
import com.nec.endmile.state.CourierState;
import net.corda.core.contracts.Command;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.crypto.SecureHash;
import net.corda.core.flows.*;
import net.corda.core.identity.Party;
import net.corda.core.node.services.Vault;
import net.corda.core.node.services.vault.Builder;
import net.corda.core.node.services.vault.CriteriaExpression;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;

import java.lang.reflect.Field;
import java.util.List;

public class CourierDocUploadFlow {
    @InitiatingFlow
    @StartableByRPC
    public static class Initiator extends FlowLogic<SignedTransaction> {
        private final String courierId;
        private final String courierReceiptHash;

        private final ProgressTracker.Step GENERATING_TRANSACTION = new ProgressTracker.Step("Generating transaction based on new Courier.");
        private final ProgressTracker.Step VERIFYING_TRANSACTION = new ProgressTracker.Step("Verifying contract constraints.");
        private final ProgressTracker.Step SIGNING_TRANSACTION = new ProgressTracker.Step("Signing transaction with our private key.");
        private final ProgressTracker.Step FINALISING_TRANSACTION = new ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
            @Override
            public ProgressTracker childProgressTracker() {
                return FinalityFlow.Companion.tracker();
            }
        };

        // The progress tracker checkpoints each stage of the flow and outputs the specified messages when each
        // checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call()
        // function.
        private final ProgressTracker progressTracker = new ProgressTracker(
                GENERATING_TRANSACTION,
                VERIFYING_TRANSACTION,
                SIGNING_TRANSACTION,
                FINALISING_TRANSACTION
        );


        public Initiator(String courierId, String courierReceiptHash) {
            this.courierId = courierId;
            this.courierReceiptHash = courierReceiptHash;
        }

        @Override
        public ProgressTracker getProgressTracker() {
            return progressTracker;
        }

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            // Obtain a reference to the notary we want to use.
            final Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);

            // Stage 1.
            progressTracker.setCurrentStep(GENERATING_TRANSACTION);
            // Generate an unsigned transaction.
            Party me = getOurIdentity();

            // Fetch existing courierState
            QueryCriteria.VaultQueryCriteria criteria = new QueryCriteria.VaultQueryCriteria();
            Field courierIdField;
            try {
                courierIdField = CourierSchemaV1.PersistentCourier.class.getDeclaredField("courierId");
            } catch(NoSuchFieldException e) {
                throw new FlowException("courierId not present in schema");
            }

            CriteriaExpression courierIndex = Builder.equal(courierIdField, this.courierId);
            QueryCriteria courierIdCriteria = new QueryCriteria.VaultCustomQueryCriteria<>(courierIndex);
            courierIdCriteria.and(courierIdCriteria);

            Vault.Page<CourierState> queryResult = getServiceHub().getVaultService().queryBy(CourierState.class, criteria);
            List<StateAndRef<CourierState>> couriers = queryResult.getStates();

            CourierState courierState = null;
            StateAndRef<CourierState> courierStateStateAndRef = null;
            for(StateAndRef<CourierState> temp : couriers) {
                if(temp.getState().getData().getCourierId().equals(courierId)) {
                    courierState = temp.getState().getData();
                    courierStateStateAndRef = temp;
                }
            }

            if(courierState == null) {
                throw new FlowException("Courier not present in state");
            }

            // Create output state
            CourierState courierOutputState = new CourierState(courierState.getCourierLength(), courierState.getCourierWidth(), courierState.getCourierHeight(),
                    courierState.getCourierWeight(), courierReceiptHash, courierState.getSource(), courierState.getDestination(), courierState.getRequestor(),
                    courierState.getAcceptedResponder(), courierState.getFinalQuotedPrice(), courierState.getFinalDeliveryType(), CourierStatus.COURIER_UPLOADED, new UniqueIdentifier(),
                    courierState.getResponses(), courierState.getCourierId(), courierState.getAutoNodes());

            // Create command
            final Command<CourierContract.Commands.CourierDocUpload> txCommand = new Command<>(
                    new CourierContract.Commands.CourierDocUpload(),
                    ImmutableList.of(me.getOwningKey()));

            // Build transaction with attachment hash
            final TransactionBuilder txBuilder = new TransactionBuilder(notary)
                    .addInputState(courierStateStateAndRef)
                    .addOutputState(courierOutputState, CourierContract.CONTRACT_ID)
                    .addCommand(txCommand)
                    .addAttachment(SecureHash.parse(courierReceiptHash));

            // Stage 2.
            progressTracker.setCurrentStep(VERIFYING_TRANSACTION);
            // Verify that the transaction is valid.
            txBuilder.verify(getServiceHub());

            // Stage 3.
            progressTracker.setCurrentStep(SIGNING_TRANSACTION);
            // Sign the transaction.
            final SignedTransaction partSignedTx = getServiceHub().signInitialTransaction(txBuilder);

            // Stage 4
            progressTracker.setCurrentStep(FINALISING_TRANSACTION);
            // Notarise and record the transaction in both parties' vaults.
            return subFlow(new FinalityFlow(partSignedTx));
        }
    }
}