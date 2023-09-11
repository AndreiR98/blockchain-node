package uk.co.roteala.glaciernode.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.co.roteala.common.AccountModel;
import uk.co.roteala.common.Fees;
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


        Coin amount = fund.getAmount().getRawAmount();
        Fees fees = fund.getAmount().getFees();
        Coin totalFees = fees.getFees().add(fees.getNetworkFees());

        //if true move the actual balance
        if(fund.isProcessed()) {
            targetAccount.setInboundAmount(targetAccount.getInboundAmount().add(amount));
            sourceAccount.setOutboundAmount(sourceAccount.getOutboundAmount().subtract(amount.add(totalFees)));

            targetAccount.setBalance(targetAccount.getBalance().add(amount));
            sourceAccount.setBalance(sourceAccount.getBalance().subtract(amount.add(totalFees)));
        } else {
            targetAccount.setNonce(targetAccount.getNonce());
            sourceAccount.setNonce(sourceAccount.getNonce() + 1);

            targetAccount.setInboundAmount(targetAccount.getInboundAmount().add(amount));
            sourceAccount.setOutboundAmount(sourceAccount.getOutboundAmount().subtract(amount.add(totalFees)));
        }

        storage.updateAccount(targetAccount);
        storage.updateAccount(sourceAccount);
    }

    /**
     * Reverse the funding when, receiving the transactions it will re-create the accounts.
     * */
    @Override
    public void reverseFunding(Fund fund) {
        AccountModel initialSourceAccount = fund.getSourceAccount();

        AccountModel initialTargetAccount = storage.getAccountByAddress(fund.getTargetAccountAddress());

        Coin amount = fund.getAmount().getRawAmount();//Amount that is going to receiver
        Fees fees = fund.getAmount().getFees();
        Coin totalFees = fees.getFees().add(fees.getNetworkFees());
        Coin totalAmount = amount.add(totalFees);//Amount that is paid by sender

        if(fund.isProcessed()) {
            initialSourceAccount.setBalance(initialSourceAccount.getBalance().add(totalAmount));

            initialTargetAccount.setBalance(initialTargetAccount.getBalance().subtract(amount));

            initialSourceAccount.setNonce(initialSourceAccount.getNonce() + 1);

            storage.updateAccount(initialTargetAccount);
            storage.updateAccount(initialSourceAccount);
        }
    }

    /**
     * Execute and assign reward fund to the miner
     * */
    @Override
    public void executeRewardFund(Fund fund) {
        AccountModel minerAccount = storage.getAccountByAddress(fund.getTargetAccountAddress());

        Coin reward = fund.getAmount().getRawAmount();

        minerAccount.setBalance(minerAccount.getBalance().add(reward));

        storage.updateAccount(minerAccount);
    }
}
