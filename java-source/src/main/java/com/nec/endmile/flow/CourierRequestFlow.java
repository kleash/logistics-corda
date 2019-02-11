package com.nec.endmile.flow;


import co.paralleluniverse.fibers.Suspendable;
import com.google.common.collect.ImmutableList;
import com.nec.endmile.config.CourierStatus;
import com.nec.endmile.contract.CourierContract;
import com.nec.endmile.state.CourierState;
import net.corda.core.contracts.Command;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.flows.*;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;

import java.util.ArrayList;
import java.util.List;

public class CourierRequestFlow {


    @InitiatingFlow
    @StartableByRPC
    public static class Initiator extends FlowLogic<SignedTransaction> {
        private final int courierLength;
        private final int courierWidth;
        private final int courierHeight;
        private final int courierWeight;
        private final String source;
        private final String destination;
        private final Party autoNode;

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

        public Initiator(int courierLength, int courierWidth, int courierHeight, int courierWeight, String source, String destination, Party autoNode) {
            this.courierLength = courierLength;
            this.courierWidth = courierWidth;
            this.courierHeight = courierHeight;
            this.courierWeight = courierWeight;
            this.source = source;
            this.destination = destination;


            this.autoNode = autoNode;
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

            //TODO Change it to create list of initiating party and AUTO nodes.
            //Right now it's sending to initiating parties also
            List<Party> autoNodes = new ArrayList<>();
            autoNodes.add(autoNode);

            System.out.println("List of parties " + autoNodes.size());
            //ENDS HERE

            UniqueIdentifier uniqueIdentifier = new UniqueIdentifier();
            CourierState courierState = new CourierState(this.courierLength, this.courierWidth, this.courierHeight, this.courierWeight,
                    this.source, this.destination, me, CourierStatus.COURIER_INITIATED, uniqueIdentifier, uniqueIdentifier.getId().toString(), autoNodes);

            final Command<CourierContract.Commands.CourierPost> txCommand = new Command<>(
                    new CourierContract.Commands.CourierPost(),
                    ImmutableList.of(me.getOwningKey()));
            final TransactionBuilder txBuilder = new TransactionBuilder(notary)
                    .addOutputState(courierState, CourierContract.CONTRACT_ID)
                    .addCommand(txCommand);

            // Stage 2.
            progressTracker.setCurrentStep(VERIFYING_TRANSACTION);
            // Verify that the transaction is valid.
            txBuilder.verify(getServiceHub());

            // Stage 3.
            progressTracker.setCurrentStep(SIGNING_TRANSACTION);
            // Sign the transaction.
            final SignedTransaction signedTx = getServiceHub().signInitialTransaction(txBuilder);


            // Stage 4
            progressTracker.setCurrentStep(FINALISING_TRANSACTION);
            System.out.println(FINALISING_TRANSACTION.getLabel());
            // Notarise and record the transaction in both parties' vaults.
            return subFlow(new FinalityFlow(signedTx));
        }
    }


}
