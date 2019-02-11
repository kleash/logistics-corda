package com.nec.endmile.api;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.nec.endmile.flow.*;
import com.nec.endmile.state.CourierState;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.crypto.SecureHash;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.identity.Party;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.core.node.NodeInfo;
import net.corda.core.transactions.SignedTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static java.util.stream.Collectors.toList;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.CREATED;
import static javax.ws.rs.core.Response.Status.OK;

// This API is accessible from /api/example. All paths specified below are relative to it.
@Path("courier")
public class CourierApi2 {
    private final CordaRPCOps rpcOps;
    private final CordaX500Name myLegalName;

    private final List<String> serviceNames = ImmutableList.of("Notary");

    static private final Logger logger = LoggerFactory.getLogger(com.nec.endmile.api.CourierApi.class);

    public CourierApi2(CordaRPCOps rpcOps) {
        this.rpcOps = rpcOps;
        this.myLegalName = rpcOps.nodeInfo().getLegalIdentities().get(0).getName();
    }

    /**
     * Requestor uploading relevant docs for the courier
     * <p>
     * <p>
     * The flow is invoked asynchronously. It returns a future when the flow's call() method returns.
     * <p>
     * curl -X POST 'http://localhost:10009/api/courier/uploadCourierReceipt?courierId=&courierReceiptPhoto='
     */
    @PUT
    @Path("uploadCourierReceipt")
    public Response uploadCourierReceipt(@QueryParam("courierId") int courierId,
                                         @QueryParam("courierReceiptPhoto") InputStream courierReceiptPhoto) throws InterruptedException, ExecutionException {

        SecureHash courierReceiptHash = rpcOps.uploadAttachment(courierReceiptPhoto);

        try {
            final SignedTransaction signedTx = rpcOps
                    .startTrackedFlowDynamic(CourierDocUploadFlow.Initiator.class, courierId, courierReceiptHash.toString())
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
     * Responder updating courier status to "picked" or "delivered"
     * <p>
     * <p>
     * The flow is invoked asynchronously. It returns a future when the flow's call() method returns.
     * <p>
     * curl -X POST 'http://localhost:10009/api/courier/updateCourierState?courierId=&status='
     */
    @PUT
    @Path("updateCourierStatus")
    public Response updateCourierStatus(@QueryParam("courierId") int courierId,
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
     * curl -X POST 'http://localhost:10009/api/courier/cancelByRequestor?courierId='
     */
    @PUT
    @Path("cancelByRequestor")
    public Response cancelByRequestor(@QueryParam("courierId") int courierId) throws InterruptedException, ExecutionException {

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
     * curl -X POST 'http://localhost:10009/api/courier/cancelByResponder?courierId='
     */
    @PUT
    @Path("cancelByResponder")
    public Response cancelByResponder(@QueryParam("courierId") int courierId) throws InterruptedException, ExecutionException {

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