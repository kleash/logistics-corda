package com.nec.endmile.flow;

import com.google.common.collect.ImmutableList;
import com.nec.endmile.state.CourierState;
import net.corda.core.concurrent.CordaFuture;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.transactions.SignedTransaction;
import net.corda.testing.node.MockNetwork;
import net.corda.testing.node.StartedMockNode;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class CourierRequestFlowTests {
    private MockNetwork network;
    private StartedMockNode amazon;
    private StartedMockNode necAuto;

    @Before
    public void setup() {
        network = new MockNetwork(ImmutableList.of("com.nec.endmile.contract","com.nec.endmile.schema"));
        amazon = network.createPartyNode(new CordaX500Name("Amazon", "London", "GB"));
        necAuto = network.createPartyNode(new CordaX500Name("NECAuto", "New York", "US"));

        network.runNetwork();
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
        CourierRequestFlow.Initiator flow = new CourierRequestFlow.Initiator(10, 10, 10, 10, "krpuram", "marathahalli", necAuto.getInfo().getLegalIdentities().get(0));
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
        CourierRequestFlow.Initiator flow = new CourierRequestFlow.Initiator(10, 10, 10, 10, "krpuram", "marathahalli", necAuto.getInfo().getLegalIdentities().get(0));
        CordaFuture<SignedTransaction> future = amazon.startFlow(flow);
        network.runNetwork();
        future.get();

        // We check the recorded IOU in both vaults.
        for (StartedMockNode node : ImmutableList.of(amazon, necAuto)) {
            node.transaction(() -> {
                List<StateAndRef<CourierState>> courier = node.getServices().getVaultService().queryBy(CourierState.class).getStates();
                System.out.println("courier.size() " + courier.size());
                assertEquals(1, courier.size());
                CourierState recordedState = courier.get(0).getState().getData();
                System.out.println(recordedState);
                assertEquals(recordedState.getCourierHeight(), 10);
                assertEquals(recordedState.getCourierLength(), 10);
                assertEquals(recordedState.getCourierWeight(), 10);
                return null;
            });
        }
    }
}