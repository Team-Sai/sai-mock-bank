package org.teamsai.saimockbank.domain.transfer.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.teamsai.saimockbank.domain.account.dto.AccountStatus;
import org.teamsai.saimockbank.domain.account.dto.BankAccountDTO;
import org.teamsai.saimockbank.domain.account.mapper.BankAccountMapper;
import org.teamsai.saimockbank.domain.transaction.dto.BankTransactionDTO;
import org.teamsai.saimockbank.domain.transaction.dto.TransactionType;
import org.teamsai.saimockbank.domain.transaction.mapper.BankTransactionMapper;
import org.teamsai.saimockbank.domain.transfer.dto.BankTransferDTO;
import org.teamsai.saimockbank.domain.transfer.dto.TransferRequest;
import org.teamsai.saimockbank.domain.transfer.dto.TransferStatus;
import org.teamsai.saimockbank.domain.transfer.exception.TransferErrorCode;
import org.teamsai.saimockbank.domain.transfer.exception.TransferReviewErrorCode;
import org.teamsai.saimockbank.domain.transfer.mapper.BankTransferMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TransferProcessor {

    private final BankAccountMapper bankAccountMapper;
    private final BankTransferMapper bankTransferMapper;
    private final BankTransactionMapper bankTransactionMapper;

    public BankTransferDTO process(
            TransferRequest request,
            Long loginUserId,
            Long toAccountId
    ) {
        BankAccountDTO[] accounts =
                lockAccounts(request.fromAccountId(), toAccountId);

        validateAccounts(
                accounts[0],
                accounts[1],
                request,
                loginUserId
        );

        BigDecimal fromBalanceAfter =
                accounts[0].getBalance().subtract(request.amount());

        BigDecimal toBalanceAfter =
                accounts[1].getBalance().add(request.amount());

        updateBalances(
                request.fromAccountId(),
                toAccountId,
                request.amount()
        );

        LocalDateTime completedAt = LocalDateTime.now();

        BankTransferDTO transfer = saveTransfer(
                request,
                toAccountId,
                TransferStatus.SUCCESS,
                completedAt
        );

        saveTransactions(
                request,
                accounts[0],
                accounts[1],
                transfer.getTransferId(),
                fromBalanceAfter,
                toBalanceAfter,
                completedAt
        );

        return transfer;
    }

    public BankTransferDTO processHold(
            TransferRequest request,
            Long loginUserId,
            Long toAccountId
    ) {
        BankAccountDTO[] accounts =
                lockAccounts(request.fromAccountId(), toAccountId);

        validateAccounts(
                accounts[0],
                accounts[1],
                request,
                loginUserId
        );

        return saveTransfer(
                request,
                toAccountId,
                TransferStatus.PENDING_REVIEW,
                null
        );
    }

    public BankTransferDTO processApproved(
            BankTransferDTO pending,
            Long adminId,
            String reason
    ) {
        if (pending.getStatus() != TransferStatus.PENDING_REVIEW) {
            throw TransferReviewErrorCode.REVIEW_NOT_PENDING
                    .toException();
        }

        BankAccountDTO[] accounts =
                lockAccounts(
                        pending.getFromAccountId(),
                        pending.getToAccountId()
                );

        TransferRequest request = new TransferRequest(
                pending.getRequestKey(),
                pending.getFromAccountId(),
                accounts[1].getAccountNumber(),
                pending.getAmount(),
                pending.getSenderMemo(),
                pending.getReceiverMemo()
        );

        validateAccounts(
                accounts[0],
                accounts[1],
                request,
                accounts[0].getBankUserId()
        );

        BigDecimal fromBalanceAfter =
                accounts[0].getBalance().subtract(pending.getAmount());

        BigDecimal toBalanceAfter =
                accounts[1].getBalance().add(pending.getAmount());

        updateBalances(
                pending.getFromAccountId(),
                pending.getToAccountId(),
                pending.getAmount()
        );

        LocalDateTime completedAt = LocalDateTime.now();

        int updated = bankTransferMapper.approvePending(
                pending.getTransferId(),
                adminId,
                completedAt,
                completedAt,
                reason
        );

        if (updated != 1) {
            throw TransferReviewErrorCode.REVIEW_NOT_PENDING
                    .toException();
        }

        saveTransactions(
                request,
                accounts[0],
                accounts[1],
                pending.getTransferId(),
                fromBalanceAfter,
                toBalanceAfter,
                completedAt
        );

        return bankTransferMapper.findById(
                pending.getTransferId()
        ).orElseThrow(
                TransferErrorCode.TRANSFER_NOT_FOUND::toException
        );
    }

    private BankAccountDTO[] lockAccounts(
            Long fromAccountId,
            Long toAccountId
    ) {
        BankAccountDTO fromAccount;
        BankAccountDTO toAccount;

        if (fromAccountId < toAccountId) {
            fromAccount = findAccountForUpdate(fromAccountId);
            toAccount = findAccountForUpdate(toAccountId);
        } else {
            toAccount = findAccountForUpdate(toAccountId);
            fromAccount = findAccountForUpdate(fromAccountId);
        }

        return new BankAccountDTO[]{
                fromAccount,
                toAccount
        };
    }

    private BankAccountDTO findAccountForUpdate(
            Long accountId
    ) {
        return bankAccountMapper.findByIdForUpdate(accountId)
                .orElseThrow(
                        TransferErrorCode.ACCOUNT_NOT_FOUND::toException
                );
    }

    private void validateAccounts(
            BankAccountDTO fromAccount,
            BankAccountDTO toAccount,
            TransferRequest request,
            Long loginUserId
    ) {
        if (!fromAccount.getBankUserId().equals(loginUserId)) {
            throw TransferErrorCode.ACCOUNT_ACCESS_DENIED
                    .toException();
        }

        if (fromAccount.getAccountId().equals(toAccount.getAccountId())) {
            throw TransferErrorCode.SAME_ACCOUNT_TRANSFER
                    .toException();
        }

        if (fromAccount.getStatus() != AccountStatus.ACTIVE
                || toAccount.getStatus() != AccountStatus.ACTIVE) {
            throw TransferErrorCode.ACCOUNT_UNAVAILABLE
                    .toException();
        }

        if (fromAccount.getBalance().compareTo(request.amount()) < 0) {
            throw TransferErrorCode.INSUFFICIENT_BALANCE
                    .toException();
        }
    }

    private void updateBalances(
            Long fromAccountId,
            Long toAccountId,
            BigDecimal amount
    ) {
        int decreased =
                bankAccountMapper.decreaseBalance(
                        fromAccountId,
                        amount
                );

        if (decreased != 1) {
            throw TransferErrorCode.BALANCE_UPDATE_FAILED
                    .toException();
        }

        int increased =
                bankAccountMapper.increaseBalance(
                        toAccountId,
                        amount
                );

        if (increased != 1) {
            throw TransferErrorCode.BALANCE_UPDATE_FAILED
                    .toException();
        }
    }

    private BankTransferDTO saveTransfer(
            TransferRequest request,
            Long toAccountId,
            TransferStatus status,
            LocalDateTime completedAt
    ) {
        BankTransferDTO transfer = BankTransferDTO.builder()
                .requestKey(request.requestKey())
                .fromAccountId(request.fromAccountId())
                .toAccountId(toAccountId)
                .amount(request.amount())
                .senderMemo(request.senderMemo())
                .receiverMemo(request.receiverMemo())
                .status(status)
                .failureReason(null)
                .completedAt(completedAt)
                .build();

        if (bankTransferMapper.insert(transfer) != 1) {
            throw TransferErrorCode.TRANSFER_SAVE_FAILED
                    .toException();
        }

        return transfer;
    }

    private void saveTransactions(
            TransferRequest request,
            BankAccountDTO fromAccount,
            BankAccountDTO toAccount,
            Long transferId,
            BigDecimal fromBalanceAfter,
            BigDecimal toBalanceAfter,
            LocalDateTime transactionAt
    ) {
        BankTransactionDTO withdrawal =
                createWithdrawTransaction(
                        request,
                        fromAccount,
                        toAccount,
                        transferId,
                        fromBalanceAfter,
                        transactionAt
                );

        BankTransactionDTO deposit =
                createDepositTransaction(
                        request,
                        fromAccount,
                        toAccount,
                        transferId,
                        toBalanceAfter,
                        transactionAt
                );

        int withdrawalInserted =
                bankTransactionMapper.insert(withdrawal);

        int depositInserted =
                bankTransactionMapper.insert(deposit);

        if (withdrawalInserted != 1 || depositInserted != 1) {
            throw TransferErrorCode.TRANSACTION_SAVE_FAILED
                    .toException();
        }
    }

    private BankTransactionDTO createWithdrawTransaction(
            TransferRequest request,
            BankAccountDTO fromAccount,
            BankAccountDTO toAccount,
            Long transferId,
            BigDecimal balanceAfter,
            LocalDateTime transactionAt
    ) {
        return BankTransactionDTO.builder()
                .transactionKey(createTransactionKey())
                .transactionType(TransactionType.WITHDRAW)
                .amount(request.amount())
                .balanceAfter(balanceAfter)
                .counterpartyName(toAccount.getAccountHolderName())
                .counterpartyAccountNumber(toAccount.getAccountNumber())
                .memo(request.senderMemo())
                .transactionAt(transactionAt)
                .transferId(transferId)
                .accountId(fromAccount.getAccountId())
                .build();
    }

    private BankTransactionDTO createDepositTransaction(
            TransferRequest request,
            BankAccountDTO fromAccount,
            BankAccountDTO toAccount,
            Long transferId,
            BigDecimal balanceAfter,
            LocalDateTime transactionAt
    ) {
        return BankTransactionDTO.builder()
                .transactionKey(createTransactionKey())
                .transactionType(TransactionType.DEPOSIT)
                .amount(request.amount())
                .balanceAfter(balanceAfter)
                .counterpartyName(fromAccount.getAccountHolderName())
                .counterpartyAccountNumber(fromAccount.getAccountNumber())
                .memo(request.receiverMemo())
                .transactionAt(transactionAt)
                .transferId(transferId)
                .accountId(toAccount.getAccountId())
                .build();
    }

    private String createTransactionKey() {
        return "TX-" + UUID.randomUUID();
    }
}
