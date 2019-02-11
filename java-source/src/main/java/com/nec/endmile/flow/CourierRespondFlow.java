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
import net.corda.core.flows.*;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import net.corda.core.node.NodeInfo;
import net.corda.core.node.services.Vault;
import net.corda.core.node.services.vault.Builder;
import net.corda.core.node.services.vault.CriteriaExpression;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;
import org.hibernate.Criteria;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

public class CourierRespondFlow {


    @InitiatingFlow
    @StartableByRPC
    public static class Responder extends FlowLogic<SignedTransaction> {
        private final String courierId;
        private final String sharedPrice;
        private final String dedicatedPrice;
        private final String responderID;


        private final ProgressTracker.Step GENERATING_TRANSACTION = new ProgressTracker.Step("Generating transaction for courier response.");
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

        public Responder(String courierId, String sharedPrice, String dedicatedPrice,String responderID) {
            this.courierId = courierId;
            this.sharedPrice = sharedPrice;
            this.dedicatedPrice = dedicatedPrice;
            this.responderID=responderID;
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

            //Vault query
            QueryCriteria.VaultQueryCriteria criteria = new QueryCriteria.VaultQueryCriteria();

            Field courierIDField;
            try {
                courierIDField = CourierSchemaV1.PersistentCourier.class.getDeclaredField("courierId");
            } catch (NoSuchFieldException e) {
                throw new FlowException("No field name linearId present in courier schema");
            }
            CriteriaExpression courierIndex = Builder.equal(courierIDField, this.courierId);
            QueryCriteria courierIDCriteria = new QueryCriteria.VaultCustomQueryCriteria<>(courierIndex);

            criteria.and(courierIDCriteria);

            Vault.Page<CourierState> queryResult = getServiceHub().getVaultService().queryBy(CourierState.class, criteria);
            List<StateAndRef<CourierState>> couriers = queryResult.getStates();


            if (couriers == null) {
                throw new FlowException("No couriers present in ledger");
            }

            CourierState courierState = null;
            StateAndRef<CourierState> courierStateStateAndRef = null;
            for (StateAndRef<CourierState> temp : couriers) {
                if (temp.getState().getData().getCourierId().equals(this.courierId)) {
                    courierStateStateAndRef = temp;
                    courierState = temp.getState().getData();
                }
            }

            if (courierState == null) {
                throw new FlowException("Invalid courier id");
            }else{
                System.out.println("Courier input state"+courierState);
            }

            Map<String, String> responses = new LinkedHashMap<>();;
            if (courierState.getResponses() != null) {

                responses.putAll(courierState.getResponses());
            }


            responses = CourierState.addResponse(responses, responderID, this.sharedPrice ,this.dedicatedPrice);


            //TODO Change it to create list of initiating party and AUTO nodes.
            //Right now it's sending to initiating parties also
            List<Party> autoNodes = new ArrayList<>();
            autoNodes.add(me);

            System.out.println("List of auto nodes " + autoNodes.size());
            //ENDS HERE

            CourierState courierOutputState = new CourierState(courierState.getCourierLength(), courierState.getCourierWidth(), courierState.getCourierHeight(), courierState.getCourierWeight(),
                    courierState.getSource(), courierState.getDestination(), courierState.getRequestor(), CourierStatus.COURIER_RESPONSE_RECEIVED, new UniqueIdentifier(), courierState.getCourierId(), responses,autoNodes);


            final Command<CourierContract.Commands.Respond> txCommand = new Command<>(
                    new CourierContract.Commands.Respond(),
                    ImmutableList.of(me.getOwningKey()));

            final TransactionBuilder txBuilder = new TransactionBuilder(notary)
                    .addInputState(courierStateStateAndRef)
                    .addOutputState(courierOutputState, CourierContract.CONTRACT_ID)
                    .addCommand(txCommand);

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
