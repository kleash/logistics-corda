package com.nec.endmile.contract;

import com.nec.endmile.config.CourierStatus;
import com.nec.endmile.state.CourierState;
import net.corda.core.contracts.CommandData;
import net.corda.core.contracts.CommandWithParties;
import net.corda.core.contracts.Contract;
import net.corda.core.transactions.LedgerTransaction;

import java.security.PublicKey;
import java.util.HashSet;
import java.util.Set;

import static net.corda.core.contracts.ContractsDSL.requireSingleCommand;
import static net.corda.core.contracts.ContractsDSL.requireThat;

/**
 * A implementation of a smart contract for Logistics - Last mile delivery
 * <p>
 * This contract enforces rules regarding the creation of a valid [CourierState].
 * <p>
 * For a new [CourierState] to be issued onto the ledger, a transaction is required which takes:
 * - Zero input states.
 * - One output state: the new [CourierState].
 * - A CourierPost() command
 * <p>
 * All contracts must sub-class the [Contract] interface.
 */
public class CourierContract implements Contract {
    public static final String CONTRACT_ID = "com.nec.endmile.contract.CourierContract";

    /**
     * The verify() function of all the states' contracts must not throw an exception for a transaction to be
     * considered valid.
     */
    @Override
    public void verify(LedgerTransaction tx) {
        final CommandWithParties<Commands> command = requireSingleCommand(tx.getCommands(), Commands.class);
        final Commands commandData = command.getValue();
        final Set<PublicKey> setOfSigners = new HashSet<>(command.getSigners());

        if (commandData instanceof Commands.CourierPost) {
            verifyCourierPost(tx, setOfSigners);
        } else if (commandData instanceof Commands.CourierRate) {
            verifyCourierRate(tx, setOfSigners);
        } else if (commandData instanceof Commands.CourierContractAccept) {
            verifyCourierAccept(tx, setOfSigners);
        }else if (commandData instanceof Commands.CourierDocUpload) {
            verifyCourierDocUpload(tx, setOfSigners);
        } else {
            throw new IllegalArgumentException("Unrecognised command.");
        }

    }

    private void verifyCourierAccept(LedgerTransaction tx, Set<PublicKey> setOfSigners) {

        requireThat(req -> {
            req.using("Only one courier state when responding to courier request",
                    tx.getInputStates().size() == 1);
            req.using("Only one courier state should be created when responding to courier request.", tx.getOutputStates().size() == 1);


            CourierState courierState = tx.inputsOfType(CourierState.class).get(0);
            req.using("Courier Input state status can be either initiated or response-received", courierState.getStatus().equalsIgnoreCase(CourierStatus.COURIER_RESPONSE_RECEIVED));


            return null;

            //TODO Minimum validation as of now. Implement at API layer
        });
    }

    // This only allows one courier per transaction.
    private void verifyCourierPost(LedgerTransaction tx, Set<PublicKey> signers) {
        requireThat(req -> {
            req.using("No inputs should be consumed when creating courier",
                    tx.getInputStates().isEmpty());
            req.using("Only one courier state should be created when creating courier.", tx.getOutputStates().size() == 1);
            CourierState courier = (CourierState) tx.getOutputStates().get(0);
            req.using("signer should contain owning key.",
                    signers.contains(courier.getRequestor().getOwningKey()));
            return null;
        });
    }

    // This only allows one response per transaction.
    private void verifyCourierRate(LedgerTransaction tx, Set<PublicKey> signers) {
        requireThat(req -> {
            req.using("Only one courier state when responding to courier request",
                    tx.getInputStates().size() == 1);
            req.using("Only one courier state should be created when responding to courier request.", tx.getOutputStates().size() == 1);


            CourierState courierState = tx.inputsOfType(CourierState.class).get(0);
            req.using("Courier Input state status can be either initiated or response-received", courierState.getStatus().equalsIgnoreCase(CourierStatus.COURIER_INITIATED) || courierState.getStatus().equalsIgnoreCase(CourierStatus.COURIER_RESPONSE_RECEIVED));


            return null;

            //TODO Minimum validation as of now. Implement at API layer
        });
    }

    private void verifyCourierDocUpload(LedgerTransaction tx, Set<PublicKey> signers) {
        requireThat(req -> {
            req.using("Only one input state during CourierDocUpload flow",
                    tx.getInputStates().size() == 1);
            req.using("Only one output state during CourierDocUpload flow",
                    tx.getOutputStates().size() == 1);
            req.using("Only one attachment during CourierDocUpload flow",
                    tx.getAttachments().size() == 1);

            CourierState courierState = tx.inputsOfType(CourierState.class).get(0);
            req.using("Input CourierState should have 'accepted' status",
                    courierState.getStatus().equalsIgnoreCase(CourierStatus.COURIER_ACCEPTED));
            return null;
        });
    }

    /**
     * This contract implements all commands related to CourierState
     */
    public interface Commands extends CommandData {
        class CourierPost implements Commands {
        }

        class CourierRate implements Commands {
        }

        class CourierDocUpload implements Commands {
        }

         class CourierContractAccept implements Commands{
        }
    }
}