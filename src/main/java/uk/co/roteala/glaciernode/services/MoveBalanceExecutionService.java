package uk.co.roteala.glaciernode.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.co.roteala.common.AccountModel;
import uk.co.roteala.common.monetary.Coin;
import uk.co.roteala.common.monetary.Fund;
import uk.co.roteala.common.monetary.MoveFund;
import uk.co.roteala.glaciernode.storage.StorageServices;

@Slf4j
@RequiredArgsConstructor
public class MoveBalanceExecutionService implements MoveFund {
    private final StorageServices storage;

    @Override
    public void execute(Fund fund) {
        AccountModel sourceAccount = fund.getSourceAccount();

        AccountModel targetAccount = storage.getAccountByAddress(fund.getTargetAccountAddress());


        Coin amount = fund.getAmount();

        targetAccount.setNonce(targetAccount.getNonce() + 1);
        sourceAccount.setNonce(sourceAccount.getNonce() + 1);

        //if true move the actual balance
        if(fund.isProcessed()) {
            targetAccount.setInboundAmount(targetAccount.getInboundAmount().minus(amount));
            sourceAccount.setOutboundAmount(sourceAccount.getOutboundAmount().minus(amount));

            targetAccount.setBalance(targetAccount.getBalance().plus(amount));
            sourceAccount.setBalance(sourceAccount.getBalance().plus(amount));
        } else {
            //TODO: Only for nodes the fund object uses account nonce for source and target and update it here
            targetAccount.setNonce(targetAccount.getNonce() + 1);
            sourceAccount.setNonce(sourceAccount.getNonce() + 1);

            targetAccount.setInboundAmount(targetAccount.getInboundAmount().plus(amount));
            sourceAccount.setOutboundAmount(sourceAccount.getOutboundAmount().plus(amount));
        }

        storage.updateAccount(targetAccount);
        storage.updateAccount(sourceAccount);
    }
}
