//package com.algorand.algosdk.account;
package com.example.locky;
import com.algorand.algosdk.account.Account;

import java.security.GeneralSecurityException;
import java.util.Scanner;
import com.algorand.algosdk.crypto.Address;
import com.algorand.algosdk.transaction.SignedTransaction;
import com.algorand.algosdk.transaction.Transaction;
import com.algorand.algosdk.util.Encoder;
import com.algorand.algosdk.v2.client.common.AlgodClient;
import com.algorand.algosdk.v2.client.common.Response;
import com.algorand.algosdk.v2.client.model.NodeStatusResponse;
import com.algorand.algosdk.v2.client.model.PendingTransactionResponse;
import com.algorand.algosdk.v2.client.model.PostTransactionsResponse;
import com.algorand.algosdk.v2.client.model.TransactionParametersResponse;
import org.json.JSONObject;
import com.algorand.algosdk.v2.client.Utils;

class GettingStarted {

    public static void main() {
    }

    public Account Act1() throws GeneralSecurityException {
        Account myAcct = new Account("defy state team pull pizza legend exit list mimic find torch fun finish throw concert ostrich bulb custom small monitor only guilt glow able ketchup");

        try {
            System.out.println("Algorand Address: " + myAcct.getAddress());
            System.out.println("Algorand Passphrase: "+ myAcct.toMnemonic());

        } catch (Exception e)
        {
            e.printStackTrace();
        }
        return myAcct;
    }

    private AlgodClient client = null;

    private AlgodClient connectToNetwork() {
        final String ALGOD_API_ADDR = "localhost";
        final String ALGOD_API_TOKEN = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        final int ALGOD_PORT = 4001;
        return new AlgodClient(ALGOD_API_ADDR, ALGOD_PORT, ALGOD_API_TOKEN);
    }

    private String getBalance(Account myAccount) throws Exception {
        String myAddress = myAccount.getAddress().toString();
        Response<com.algorand.algosdk.v2.client.model.Account> respAcct = client.
                AccountInformation(myAccount.getAddress()).execute();

        if (!respAcct.isSuccessful()) {
            throw new Exception(respAcct.message());
        }
        com.algorand.algosdk.v2.client.model.Account accountInfo = respAcct.body();
        System.out.println(String.format("Account Balance: %d microAlgos", accountInfo.amount));
        return myAddress;
    }

    public void Transaction(Account myAccount) throws Exception {
        if (client == null) {
            this.client = connectToNetwork();
        }
        getBalance(myAccount);

        try {
            final String RECEIVER = "MB2C3UU2WBDSJUNOISPADXXU7POEZRLYMRZVXDO63FB5RKJHYC6P4L6VYU";
            String note = "TEST NOTE";
            Response<TransactionParametersResponse> resp = client.TransactionParams().execute();
            if (!resp.isSuccessful()) {
                throw new Exception(resp.message());
            }
            TransactionParametersResponse params = resp.body();
            if (params == null) {
                throw new Exception("Params Retrieval error");
            }
            JSONObject jsonObj  = new JSONObject(params.toString());
            System.out.println("Algorand suggested params: " +jsonObj.toString(2));

            Transaction txn = Transaction.PaymentTransactionBuilder()
                    .sender(myAccount.getAddress().toString())
                    .note(note.getBytes())
                    .amount(500000)
                    .suggestedParams(params)
                    .receiver(new Address(RECEIVER))
                    .build();

            SignedTransaction signedTxn = myAccount.signTransaction(txn);
            System.out.println("Signed transaction with txid: " + signedTxn.transactionID);

            String[] headers = {"Context-Type"};
            String[] values = {"application/x-binary"};
            byte[] encodedTxnByte = Encoder.encodeToMsgPack(signedTxn);
            Response<PostTransactionsResponse> rawtxxnresp = client.RawTransaction().rawtxn(encodedTxnByte)
                    .execute(headers, values);

            if (!rawtxxnresp.isSuccessful()) {
                throw new Exception(rawtxxnresp.message());
            }

            String id = rawtxxnresp.body().txId;

            PendingTransactionResponse pTrx = Utils.waitForConfirmation(client, id, 4);
            System.out.println("Transaction " + id + "confirmed in round " + pTrx.confirmedRound);
            JSONObject jsonObj2 = new JSONObject(pTrx.toString());

            System.out.println("Decoded note: " + new String(pTrx.txn.tx.note));
            System.out.println("Amount: "+ new String(pTrx.txn.tx.amount.toString()));
            System.out.println("Fee" + new String(pTrx.txn.tx.fee.toString()));

            if (pTrx.closingAmount != null) {
                System.out.println("Closing amount: " + new String(pTrx.closingAmount.toString()));
            }
            getBalance(myAccount);
        }
        catch (Exception e) {
            System.err.println("Exception when calling algod#transactionInfo: " + e.getMessage());
        }
    }

    public static void main(String[] args) throws Exception {
        GettingStarted txn = new GettingStarted();
        Account act1 = txn.Act1();
        txn.Transaction(act1);
    }
}