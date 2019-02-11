package com.nec.endmile.flow;

import com.google.common.collect.ImmutableList;
import com.nec.endmile.config.CourierStatus;
import com.nec.endmile.config.CourierType;
import com.nec.endmile.contract.CourierContract;
import com.nec.endmile.state.CourierState;
import net.corda.core.concurrent.CordaFuture;
import net.corda.core.contracts.Command;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.testing.node.MockNetwork;
import net.corda.testing.node.StartedMockNode;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;

public class CourierContractAcceptFlowTest {
    private MockNetwork network;
    private StartedMockNode amazon;
    private StartedMockNode necAuto;

    private String courierId= null;
    @Before
    public void setup() {
        network = new MockNetwork(ImmutableList.of("com.nec.endmile.contract", "com.nec.endmile.schema"));
        amazon = network.createPartyNode(new CordaX500Name("Amazon", "London", "GB"));
        necAuto = network.createPartyNode(new CordaX500Name("NECAuto", "New York", "US"));

        network.runNetwork();

        initTransaction(10,10,10,10,"krpuram","marathahalli");
        try {
            addFirstResponse();
        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void initTransaction(int courierLength, int courierWidth, int courierHeight, int courierWeight, String source, String destination) {
        List<Party> allParties = new ArrayList<>();
        allParties.add(amazon.getInfo().getLegalIdentities().get(0));
        allParties.add(necAuto.getInfo().getLegalIdentities().get(0));
        UniqueIdentifier uniqueIdentifier = new UniqueIdentifier();
        courierId=uniqueIdentifier.getId().toString();
        CourierState courierState = new CourierState(courierLength, courierWidth, courierHeight, courierWeight,source,
                destination, amazon.getInfo().getLegalIdentities().get(0), CourierStatus.COURIER_INITIATED, uniqueIdentifier, uniqueIdentifier.getId().toString(), allParties);


        final TransactionBuilder txBuilder = new TransactionBuilder(network.getDefaultNotaryIdentity()).addOutputState(courierState, CourierContract.CONTRACT_ID)
                .addCommand(new Command<>(
                   new CourierContract.Commands.CourierPost(),
                        ImmutableList.of(amazon.getInfo().getLegalIdentities().get(0).getOwningKey())
                        ));

        final SignedTransaction txn = amazon.getServices().signInitialTransaction(txBuilder);

        final SignedTransaction txnFinal = network.getDefaultNotaryNode().getServices().addSignature(txn);

        amazon.getServices().recordTransactions(txnFinal);
        necAuto.getServices().recordTransactions(txnFinal);
    }

    private void addFirstResponse() throws ExecutionException, InterruptedException {
        CourierRespondFlow.Responder flow = new CourierRespondFlow.Responder(courierId, "100", "200","auto1");
        CordaFuture<SignedTransaction> future = necAuto.startFlow(flow);
        network.runNetwork();
        future.get();
    }


    @After
    public void tearDown() {
        network.stopNodes();
    }

    @Rule
    public final ExpectedException exception = ExpectedException.none();

 /*   @Test
    public void flowRejectsInvalidIOUs() throws Exception {
        // The IOUContract specifies that IOUs cannot have negative values.
        ExampleFlow.Initiator flow = new ExampleFlow.Initiator(-1, necAuto.getInfo().getLegalIdentities().get(0));
        CordaFuture<SignedTransaction> future = amazon.startFlow(flow);
        network.runNetwork();

        // The IOUContract specifies that IOUs cannot have negative values.
        exception.expectCause(instanceOf(TransactionVerificationException.class));
        future.get();
    }

    @Test
    public void signedTransactionReturnedByTheFlowIsSignedByTheInitiator() throws Exception {
        ExampleFlow.Initiator flow = new ExampleFlow.Initiator(1, necAuto.getInfo().getLegalIdentities().get(0));
        CordaFuture<SignedTransaction> future = amazon.startFlow(flow);
        network.runNetwork();

        SignedTransaction signedTx = future.get();
        signedTx.verifySignaturesExcept(necAuto.getInfo().getLegalIdentities().get(0).getOwningKey());
    }

    @Test
    public void signedTransactionReturnedByTheFlowIsSignedByTheAcceptor() throws Exception {
        ExampleFlow.Initiator flow = new ExampleFlow.Initiator(1, necAuto.getInfo().getLegalIdentities().get(0));
        CordaFuture<SignedTransaction> future = amazon.startFlow(flow);
        network.runNetwork();

        SignedTransaction signedTx = future.get();
        signedTx.verifySignaturesExcept(amazon.getInfo().getLegalIdentities().get(0).getOwningKey());
    }*/

    @Test
    public void flowRecordsATransactionInBothPartiesTransactionStorages() throws Exception {

        CourierContractAcceptFlow.Acceptor flow = new CourierContractAcceptFlow.Acceptor(courierId, "auto1", CourierType.SHARED);
        CordaFuture<SignedTransaction> future = amazon.startFlow(flow);
        network.runNetwork();
        SignedTransaction signedTx = future.get();


        // We check the recorded transaction in both vaults.
        for (StartedMockNode node : ImmutableList.of(amazon, necAuto)) {
            node.transaction(() -> {
                final SignedTransaction transaction = node.getServices().getValidatedTransactions().getTransaction(signedTx.getId());

                System.out.println("Transaction from vault " + transaction);
                assertEquals(signedTx, transaction);
                return null;
            });
        }
    }

/*    @Test
    public void recordedTransactionHasNoInputsAndASingleOutputTheInputIOU() throws Exception {
        Integer iouValue = 1;
        ExampleFlow.Initiator flow = new ExampleFlow.Initiator(iouValue, necAuto.getInfo().getLegalIdentities().get(0));
        CordaFuture<SignedTransaction> future = amazon.startFlow(flow);
        network.runNetwork();
        SignedTransaction signedTx = future.get();

        // We check the recorded transaction in both vaults.
        for (StartedMockNode node : ImmutableList.of(amazon, necAuto)) {
            SignedTransaction recordedTx = node.getServices().getValidatedTransactions().getTransaction(signedTx.getId());
            List<TransactionState<ContractState>> txOutputs = recordedTx.getTx().getOutputs();
            assert (txOutputs.size() == 1);

            IOUState recordedState = (IOUState) txOutputs.get(0).getData();
            assertEquals(recordedState.getValue(), iouValue);
            assertEquals(recordedState.getLender(), amazon.getInfo().getLegalIdentities().get(0));
            assertEquals(recordedState.getBorrower(), necAuto.getInfo().getLegalIdentities().get(0));
        }
    }*/

    @Test
    public void flowRecordsTheCorrectCourierInBothPartiesVaults() throws Exception {

        CourierContractAcceptFlow.Acceptor flow = new CourierContractAcceptFlow.Acceptor(courierId, "auto1", CourierType.SHARED);
        CordaFuture<SignedTransaction> future = amazon.startFlow(flow);
        network.runNetwork();
        future.get();

        // We check the recorded IOU in both vaults.
        for (StartedMockNode node : ImmutableList.of(amazon, necAuto)) {
            node.transaction(() -> {
                List<StateAndRef<CourierState>> courier = node.getServices().getVaultService().queryBy(CourierState.class).getStates();
                System.out.println("courier.size() " + courier.size());
                courier.stream().forEach(e->{
                    System.out.println("ALL THE Courier state in Node "+node.getServices().getMyInfo().getLegalIdentities().get(0).getName().toString()+
                            " is "+e.getState().getData());

                });
                assertEquals(1, courier.size());
                CourierState recordedState = courier.get(0).getState().getData();
                assertEquals(recordedState.getCourierHeight(), 10);
                assertEquals(recordedState.getCourierLength(), 10);
                assertEquals(recordedState.getCourierWeight(), 10);

                return null;
            });
        }
    }
}