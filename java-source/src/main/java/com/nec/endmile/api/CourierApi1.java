package com.nec.endmile.api;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.nec.endmile.flow.CourierContractAcceptFlow;
import com.nec.endmile.flow.CourierRequestFlow;
import com.nec.endmile.flow.CourierRespondFlow;
import com.nec.endmile.schema.CourierSchemaV1;
import com.nec.endmile.state.CourierState;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.identity.Party;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.core.node.NodeInfo;
import net.corda.core.node.services.Vault;
import net.corda.core.node.services.vault.Builder;
import net.corda.core.node.services.vault.CriteriaExpression;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.transactions.SignedTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static java.util.stream.Collectors.toList;
import static javax.ws.rs.core.Response.Status.*;

// This API is accessible from /api. All paths specified below are relative to it.
@Path("couriers")
public class CourierApi1 {
    private final CordaRPCOps rpcOps;
    private final CordaX500Name myLegalName;

    private final List<String> serviceNames = ImmutableList.of("Notary");

    static private final Logger logger = LoggerFactory.getLogger(CourierApi1.class);

    public CourierApi1(CordaRPCOps rpcOps) {
        this.rpcOps = rpcOps;
        this.myLegalName = rpcOps.nodeInfo().getLegalIdentities().get(0).getName();
    }

    /**
     * Returns the node's name.
     */
    @GET
    @Path("any/me")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, CordaX500Name> whoami() {
        return ImmutableMap.of("me", myLegalName);
    }

    /**
     * Returns all parties registered with the [NetworkMapService]. These names can be used to look up identities
     * using the [IdentityService].
     */
    @GET
    @Path("any/peers")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, List<CordaX500Name>> getPeers() {
        List<NodeInfo> nodeInfoSnapshot = rpcOps.networkMapSnapshot();
        return ImmutableMap.of("peers", nodeInfoSnapshot
                .stream()
                .map(node -> node.getLegalIdentities().get(0).getName())
                .filter(name -> !name.equals(myLegalName) && !serviceNames.contains(name.getOrganisation()))
                .collect(toList()));
    }

    /**
     * Displays all Courier states that exist in the node's vault.
     */
    @GET
    @Path("any/getAll")
    @Produces(MediaType.APPLICATION_JSON)
    public List<StateAndRef<CourierState>> getAll() {
        return rpcOps.vaultQuery(CourierState.class).getStates();
    }

    /**
     * Displays all courier states that are created by Party.
     */
    @GET
    @Path("any/getByStatus")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getByStatus(@QueryParam("status") String status) throws NoSuchFieldException {
        QueryCriteria generalCriteria = new QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED);
        Field statusInVault = CourierSchemaV1.PersistentCourier.class.getDeclaredField("status");
        CriteriaExpression statusIndex = Builder.equal(statusInVault, status);
        QueryCriteria statusCriteria = new QueryCriteria.VaultCustomQueryCriteria(statusIndex);
        QueryCriteria criteria = generalCriteria.and(statusCriteria);
        List<StateAndRef<CourierState>> results = rpcOps.vaultQueryByCriteria(criteria, CourierState.class).getStates();
        return Response.status(OK).entity(results).build();
    }

    /**
     * Displays courier by courierId.
     */
    @GET
    @Path("any/getById")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getById(@QueryParam("courierId") String courierId) throws NoSuchFieldException {
        QueryCriteria generalCriteria = new QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED);
        Field courierIdInVault = CourierSchemaV1.PersistentCourier.class.getDeclaredField("courierId");
        CriteriaExpression statusIndex = Builder.equal(courierIdInVault, courierId);
        QueryCriteria statusCriteria = new QueryCriteria.VaultCustomQueryCriteria(statusIndex);
        QueryCriteria criteria = generalCriteria.and(statusCriteria);
        List<StateAndRef<CourierState>> results = rpcOps.vaultQueryByCriteria(criteria, CourierState.class).getStates();
        return Response.status(OK).entity(results.get(0)).build();
    }

    /**
     * Initiates a flow to create a Courier request.
     * <p>
     * <p>
     * The flow is invoked asynchronously. It returns a future when the flow's call() method returns.
     * <p>
     * curl -X GET 'http://localhost:10009/api/couriers/requestor/create?courierLength=10&courierWidth=10&courierHeight=10&courierWeight=10&source=krpuram&destination=marathahalli&partyName=O=NECAuto,L=New%20York,C=US'
     */
    @GET
    @Path("requestor/create")
    public Response create(@QueryParam("courierLength") int courierLength, @QueryParam("courierWidth") int courierWidth,
                           @QueryParam("courierHeight") int courierHeight, @QueryParam("courierWeight") int courierWeight,
                           @QueryParam("source") String source,
                           @QueryParam("destination") String destination,
                           @QueryParam("partyName") CordaX500Name partyName) throws InterruptedException, ExecutionException {

        if (partyName == null) {
            return Response.status(BAD_REQUEST).entity("Query parameter 'partyName' missing or has wrong format.\n").build();
        }

        final Party otherParty = rpcOps.wellKnownPartyFromX500Name(partyName);
        if (otherParty == null) {
            return Response.status(BAD_REQUEST).entity("Party named " + partyName + "cannot be found.\n").build();
        }

        try {
            final SignedTransaction signedTx = rpcOps
                    .startTrackedFlowDynamic(CourierRequestFlow.Initiator.class, courierLength, courierWeight, courierHeight, courierWeight, source, destination, otherParty)
                    .getReturnValue()
                    .get();

            final String msg = String.format("Transaction id %s committed to ledger.\n", signedTx.getId());
            return Response.status(CREATED).entity(msg).build();

        } catch (Throwable ex) {
            final String msg = ex.getMessage();
            logger.error(ex.getMessage(), ex);
            return Response.status(BAD_REQUEST).entity(msg).build();
        }
    }

    /**
     * Initiates a flow to create a Courier request.
     * <p>
     * <p>
     * The flow is invoked asynchronously. It returns a future when the flow's call() method returns.
     * <p>
     * curl -X GET 'http://localhost:10012/api/couriers/responder/setFares?courierId=4d38ccbc-5a7a-49ce-bcc9-bfb21d40e5d4&sharedPrice=10&dedicatedPrice=10&responder=auto-1'
     */
    @GET
    @Path("responder/setFares")
    public Response setFares(@QueryParam("courierId") String courierId, @QueryParam("sharedPrice") String sharedPrice,
                           @QueryParam("dedicatedPrice") String dedicatedPrice, @QueryParam("responder") String responder) throws InterruptedException, ExecutionException {


        try {
            final SignedTransaction signedTx = rpcOps
                    .startTrackedFlowDynamic(CourierRespondFlow.Responder.class, courierId, sharedPrice, dedicatedPrice, responder)
                    .getReturnValue()
                    .get();

            final String msg = String.format("Transaction id %s committed to ledger.\n", signedTx.getId());
            return Response.status(CREATED).entity(msg).build();

        } catch (Throwable ex) {
            final String msg = ex.getMessage();
            logger.error(ex.getMessage(), ex);
            return Response.status(BAD_REQUEST).entity(msg).build();
        }
    }

    /**
     * Initiates a flow to accept a courier response.
     * <p>
     * <p>
     * The flow is invoked asynchronously. It returns a future when the flow's call() method returns.
     * <p>
     * curl -X GET 'http://localhost:10009/api/couriers/requestor/acceptCourier?courierId=4d38ccbc-5a7a-49ce-bcc9-bfb21d40e5d4&responder=auto-1&finalDeliveryType=shared'
     */
    @GET
    @Path("requestor/acceptCourier")
    public Response acceptCourier(@QueryParam("courierId") String courierId, @QueryParam("responder") String responder,
                           @QueryParam("finalDeliveryType")  String finalDeliveryType) throws InterruptedException, ExecutionException {


        try {
            final SignedTransaction signedTx = rpcOps
                    .startTrackedFlowDynamic(CourierContractAcceptFlow.Acceptor.class, courierId, responder, finalDeliveryType)
                    .getReturnValue()
                    .get();

            final String msg = String.format("Transaction id %s committed to ledger.\n", signedTx.getId());
            return Response.status(CREATED).entity(msg).build();

        } catch (Throwable ex) {
            final String msg = ex.getMessage();
            logger.error(ex.getMessage(), ex);
            return Response.status(BAD_REQUEST).entity(msg).build();
        }
    }
}