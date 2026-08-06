package org.teamsai.saimockbank.domain.transfer.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.teamsai.saimockbank.domain.account.dto.AccountStatus;
import org.teamsai.saimockbank.domain.account.dto.BankAccountDTO;
import org.teamsai.saimockbank.domain.account.mapper.BankAccountMapper;
import org.teamsai.saimockbank.domain.account.util.AccountOwnershipValidator;
import org.teamsai.saimockbank.domain.transaction.dto.BankTransactionDTO;
import org.teamsai.saimockbank.domain.transaction.dto.TransactionType;
import org.teamsai.saimockbank.domain.transaction.mapper.BankTransactionMapper;
import org.teamsai.saimockbank.domain.transfer.dto.TransferRequest;
import org.teamsai.saimockbank.domain.transfer.dto.BankTransferDTO;
import org.teamsai.saimockbank.domain.transfer.dto.TransferStatus;
import org.teamsai.saimockbank.domain.transfer.exception.TransferErrorCode;
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
    private final AccountOwnershipValidator accountOwnershipValidator;

    public BankTransferDTO process(TransferRequest request){
        BankAccountDTO fromAccount = findAccountForUpdate(request.fromAccountId());

        BankAccountDTO toAccount = findAccountForUpdate(request.toAccountId());

        validateAccounts(fromAccount,toAccount,request);

        BigDecimal fromBalanceAfter = fromAccount.getBalance().subtract(request.amount());
        BigDecimal toBalanceAfter = toAccount.getBalance().add(request.amount());

        updateBalances(request);

        LocalDateTime completedAt = LocalDateTime.now();
        BankTransferDTO transfer = saveTransfer(request,completedAt);

        saveTransactions(
                request,
                fromAccount,
                toAccount,
                transfer.getTransferId(),
                fromBalanceAfter,
                toBalanceAfter,
                completedAt
        );

        return transfer;



    }

    private BankAccountDTO findAccountForUpdate(Long accountId) {
        return bankAccountMapper
                .findByIdForUpdate(accountId)
                .orElseThrow(
                        TransferErrorCode.ACCOUNT_NOT_FOUND
                                ::toException
                );
    }

    private void validateAccounts(
            BankAccountDTO fromAccount,
            BankAccountDTO toAccount,
            TransferRequest request
    ) {
        String ownerHash = bankAccountMapper
                .findOwnerUserKeyHashByAccountId(fromAccount.getAccountId())
                .orElse(null);

        accountOwnershipValidator.verify(
                ownerHash,
                request.fromUserKey(),
                TransferErrorCode.ACCOUNT_ACCESS_DENIED::toException
        );

        if (fromAccount.getStatus() != AccountStatus.ACTIVE
                || toAccount.getStatus() != AccountStatus.ACTIVE) {

            throw TransferErrorCode.ACCOUNT_UNAVAILABLE
                    .toException();
        }

        if (fromAccount.getBalance()
                .compareTo(request.amount()) < 0) {

            throw TransferErrorCode.INSUFFICIENT_BALANCE
                    .toException();
        }
    }

    private void updateBalances(TransferRequest request) {
        int decreased = bankAccountMapper.decreaseBalance(
                request.fromAccountId(),
                request.amount()
        );

        if (decreased != 1) {
            throw TransferErrorCode.BALANCE_UPDATE_FAILED
                    .toException();
        }

        int increased = bankAccountMapper.increaseBalance(
                request.toAccountId(),
                request.amount()
        );

        if (increased != 1) {
            throw TransferErrorCode.BALANCE_UPDATE_FAILED
                    .toException();
        }
    }
    private BankTransferDTO saveTransfer(
            TransferRequest request,
            LocalDateTime completedAt
    ) {
        BankTransferDTO transfer = BankTransferDTO.builder()
                .requestKey(request.requestKey())
                .fromAccountId(request.fromAccountId())
                .toAccountId(request.toAccountId())
                .amount(request.amount())
                .senderMemo(request.senderMemo())
                .receiverMemo(request.receiverMemo())
                .status(TransferStatus.SUCCESS)
                .failureReason(null)
                .completedAt(completedAt)
                .build();

        int inserted =
                bankTransferMapper.insert(transfer);

        if (inserted != 1) {
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

        if (withdrawalInserted != 1
                || depositInserted != 1) {

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
                .counterpartyAccountNumber(
                        toAccount.getAccountNumber()
                )
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
                .counterpartyAccountNumber(
                        fromAccount.getAccountNumber()
                )
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
