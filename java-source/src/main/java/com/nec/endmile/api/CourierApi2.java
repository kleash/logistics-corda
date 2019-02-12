package com.nec.endmile.api;

import com.google.common.collect.ImmutableList;
import com.nec.endmile.flow.*;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.core.transactions.SignedTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.OK;

// This API is accessible from /api. All paths specified below are relative to it.
@Path("couriers")
public class CourierApi2 {
    private final CordaRPCOps rpcOps;
    private final CordaX500Name myLegalName;

    private final List<String> serviceNames = ImmutableList.of("Notary");

    static private final Logger logger = LoggerFactory.getLogger(com.nec.endmile.api.CourierApi2.class);

    public CourierApi2(CordaRPCOps rpcOps) {
        this.rpcOps = rpcOps;
        this.myLegalName = rpcOps.nodeInfo().getLegalIdentities().get(0).getName();
    }

    /**
     * Responder updating courier status to "picked" or "delivered"
     * <p>
     * <p>
     * The flow is invoked asynchronously. It returns a future when the flow's call() method returns.
     * <p>
     * curl -X POST 'http://localhost:10012/api/courier/responder/updateStatus?courierId=&status='
     */
    @POST
    @Path("responder/updateStatus")
    public Response updateStatus(@QueryParam("courierId") String courierId,
                                         @QueryParam("status") String status) throws InterruptedException, ExecutionException {

        try {
            final SignedTransaction signedTx = rpcOps
                    .startTrackedFlowDynamic(CourierFinalFlow.Initiator.class, courierId, status)
                    .getReturnValue()
                    .get();

            final String msg = String.format("Transaction id %s committed to ledger.\n", signedTx.getId());
            return Response.status(OK).entity(msg).build();

        } catch (Throwable ex) {
            final String msg = ex.getMessage();
            logger.error(ex.getMessage(), ex);
            return Response.status(BAD_REQUEST).entity(msg).build();
        }
    }

    /**
     * Courier cancellation by Requestor
     * <p>
     * <p>
     * The flow is invoked asynchronously. It returns a future when the flow's call() method returns.
     * <p>
     * curl -X POST 'http://localhost:10009/api/courier/requestor/cancelByRequestor?courierId='
     */
    @POST
    @Path("requestor/cancelByRequestor")
    public Response cancelByRequestor(@QueryParam("courierId") String courierId) throws InterruptedException, ExecutionException {

        try {
            final SignedTransaction signedTx = rpcOps
                    .startTrackedFlowDynamic(CourierRequestorCancelFlow.Initiator.class, courierId)
                    .getReturnValue()
                    .get();

            final String msg = String.format("Transaction id %s committed to ledger.\n", signedTx.getId());
            return Response.status(OK).entity(msg).build();

        } catch (Throwable ex) {
            final String msg = ex.getMessage();
            logger.error(ex.getMessage(), ex);
            return Response.status(BAD_REQUEST).entity(msg).build();
        }
    }

    /**
     * Courier cancellation by Responder
     * <p>
     * <p>
     * The flow is invoked asynchronously. It returns a future when the flow's call() method returns.
     * <p>
     * curl -X POST 'http://localhost:10012/api/courier/responder/cancelByResponder?courierId='
     */
    @POST
    @Path("responder/cancelByResponder")
    public Response cancelByResponder(@QueryParam("courierId") String courierId) throws InterruptedException, ExecutionException {

        try {
            final SignedTransaction signedTx = rpcOps
                    .startTrackedFlowDynamic(CourierResponderCancelFlow.Initiator.class, courierId)
                    .getReturnValue()
                    .get();

            final String msg = String.format("Transaction id %s committed to ledger.\n", signedTx.getId());
            return Response.status(OK).entity(msg).build();

        } catch (Throwable ex) {
            final String msg = ex.getMessage();
            logger.error(ex.getMessage(), ex);
            return Response.status(BAD_REQUEST).entity(msg).build();
        }
    }
}