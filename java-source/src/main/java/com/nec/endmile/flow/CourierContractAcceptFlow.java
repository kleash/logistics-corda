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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CourierContractAcceptFlow {


    @InitiatingFlow
    @StartableByRPC
    public static class Acceptor extends FlowLogic<SignedTransaction> {
        private final String courierId;
        private final String responderID;
        private final String finalDeliveryType;


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

        public Acceptor(String courierId,String responderID, String finalDeliveryType) {
            this.courierId = courierId;
            this.responderID=responderID;
            this.finalDeliveryType=finalDeliveryType;
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
                throw new FlowException("No field name courierId present in courier schema");
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

            //ENDS HERE


            String price = CourierState.getPrice(courierState.getResponses(),this.finalDeliveryType,this.responderID);

            if(price==null){
                throw new FlowException("Courier responder id is not present or courier delivery type is wrong");
            }

            CourierState courierOutputState = new CourierState(courierState.getCourierLength(), courierState.getCourierWidth(), courierState.getCourierHeight(), courierState.getCourierWeight(),
            null, courierState.getSource(), courierState.getDestination(), courierState.getRequestor(), this.responderID,
                    price , this.finalDeliveryType, CourierStatus.COURIER_ACCEPTED, new UniqueIdentifier(), courierState.getResponses(),
                    courierState.getCourierId(), courierState.getAutoNodes());



            final Command<CourierContract.Commands.CourierContractAccept> txCommand = new Command<>(
                    new CourierContract.Commands.CourierContractAccept(),
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
