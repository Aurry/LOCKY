package com.example.locky;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.algorand.algosdk.account.Account;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Date;
import java.util.Random;

import com.algorand.algosdk.crypto.Address;
import com.algorand.algosdk.transaction.SignedTransaction;
import com.algorand.algosdk.transaction.Transaction;
import com.algorand.algosdk.util.Encoder;
import com.algorand.algosdk.v2.client.common.AlgodClient;
import com.algorand.algosdk.v2.client.common.Response;
import com.algorand.algosdk.v2.client.model.PendingTransactionResponse;
import com.algorand.algosdk.v2.client.model.TransactionParametersResponse;
import com.google.firebase.firestore.Source;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import java.security.Security;
// Unlock page from collect fragment

public class CollectFragment extends Fragment implements ServiceConnection, SerialListener {

    private enum Connected {False, Pending, True}

    private String deviceAddress;
    private SerialService service;

    private TextView receiveText;
    private TextView sendText;
    private TextView sendText2;
    private String lockerNum;
    private String bookingID;


    //private TextUtil.HexWatcher hexWatcher;

    private Connected connected = Connected.False;
    private boolean initialStart = true;
    //    private boolean hexEnabled = false;
    private boolean pendingNewline = false;
    private String newline = TextUtil.newline_crlf;

    private String BTresponse;
    private String bufBTresponse;
    String bookerEmail;

    static String TAG = "algoDebug";
    AlgodClient client = null;
    public AlgodClient connectToNetwork() {
        final String ALGOD_API_ADDR = "https://testnet-algorand.api.purestake.io/ps2";
        final int ALGOD_PORT = 443;
        final String ALGOD_API_TOKEN_KEY = "X-API-Key";
        final String ALGOD_API_TOKEN = "ju6czNKQbX6VPp0DOCTrB8Sv4PdGKeE06IUWVOoy";
        client = new AlgodClient(ALGOD_API_ADDR, ALGOD_PORT, ALGOD_API_TOKEN, ALGOD_API_TOKEN_KEY);
        return client;
    }

    /*
     * Lifecycle
     */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        setHasOptionsMenu(true);
        setRetainInstance(true);
        Security.removeProvider("BC");
        Security.insertProviderAt(new BouncyCastleProvider(), 0);
        assert getArguments() != null;
        deviceAddress = getArguments().getString("device");
        Log.i("Create running", "check");
        Log.i("address", deviceAddress);
    }

    @Override
    public void onDestroy() {
        if (connected != Connected.False)
            disconnect();
        getActivity().stopService(new Intent(getActivity(), SerialService.class));
        super.onDestroy();
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.i("start", "running");
        connectToNetwork();
        if (service != null) {
            service.attach(this);
            Log.i("service not null", "true");
        } else {
            Log.i("starting serial service", "running");
            getActivity().startService(new Intent(getActivity(), SerialService.class));
            Log.i("serial service", String.valueOf(service));
            Log.i("activity", String.valueOf(getActivity()));
        }// prevents service destroy on unbind from recreated activity caused by orientation change
    }

    @Override
    public void onStop() {
        if (service != null && !getActivity().isChangingConfigurations())
            service.detach();
        super.onStop();
    }

    @SuppressWarnings("deprecation")
    // onAttach(context) was added with API 23. onAttach(activity) works for all API versions
    @Override
    public void onAttach(@NonNull Activity activity) {
        Log.i("Attach", "running");
        super.onAttach(activity);
        requireActivity().bindService(new Intent(getActivity(), SerialService.class), this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onDetach() {
        try {
            getActivity().unbindService(this);
        } catch (Exception ignored) {
        }
        super.onDetach();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (initialStart && service != null) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        service = ((SerialService.SerialBinder) binder).getService();
        Log.i("service", String.valueOf(service));
        service.attach(this);
        if (initialStart && isResumed()) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        service = null;
    }

    /*
     * UI
     */
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.i("creating view", "running");
        View view = inflater.inflate(R.layout.fragment_setup, container, false);

//        sendText2 = view.findViewById(R.id.send_text2); // this part is to confirm pw

        view.findViewById(R.id.confirmPWrow).setVisibility(View.GONE);


        receiveText = view.findViewById(R.id.receive_text);                          // TextView performance decreases with number of spans
        receiveText.setTextColor(getResources().getColor(R.color.colorRecieveText)); // set as default color to reduce number of spans
        receiveText.setMovementMethod(ScrollingMovementMethod.getInstance());

        sendText = view.findViewById(R.id.send_text);
        //hexWatcher = new TextUtil.HexWatcher(sendText);
        //hexWatcher.enable(hexEnabled);
        //sendText.addTextChangedListener(hexWatcher);
        //sendText.setHint(hexEnabled ? "HEX mode" : "");
        //getActivity().onBackPressed();

        View sendBtn = view.findViewById(R.id.send_btn);

        View sendBtn2 = view.findViewById(R.id.send_btn2);

        sendBtn.setOnClickListener(v -> send(sendText.getText().toString()));

        sendBtn2.setOnClickListener(v -> send(""));

        view.findViewById(R.id.confirmPWrow).setVisibility(View.GONE);
        view.findViewById(R.id.unlockPWrow).setVisibility(View.GONE);
        view.findViewById(R.id.buttonrow).setVisibility(View.GONE);
        view.findViewById(R.id.textView2).setVisibility(View.GONE);
        view.findViewById(R.id.buttonrow2).setVisibility(View.GONE);

        receiveText.addTextChangedListener(new TextWatcher() {

            public void afterTextChanged(Editable s) {

            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }


            public void onTextChanged(CharSequence s, int start, int before, int count) {
                try {

                    if ((receiveText.getText().toString().length() == 4) && (s.length() == 4)) {
                        Log.i("received", receiveText.getText().toString());
                        receiveText.removeTextChangedListener(this);
                        String fxBTresponse = receiveText.getText().toString();
                        Log.i("fxBTresponse", fxBTresponse);
                        Toast.makeText(getActivity(), fxBTresponse, Toast.LENGTH_SHORT).show();


                        Log.i("Check3", fxBTresponse);
                        // you can call or do what you want with your EditText here
                        Toast.makeText(getActivity(), "BTresponse...", Toast.LENGTH_SHORT).show();
                        view.findViewById(R.id.textView2).setVisibility(View.VISIBLE);
                        ((TextView) view.findViewById(R.id.textView2)).setText(lockerNum);

                        if (fxBTresponse.charAt(1) == 'A') {
                            ((EditText) view.findViewById(R.id.send_text)).setText("");
                            view.findViewById(R.id.confirmPWrow).setVisibility(View.GONE);
                            view.findViewById(R.id.unlockPWrow).setVisibility(View.VISIBLE);
                            view.findViewById(R.id.buttonrow).setVisibility(View.VISIBLE);
                            view.findViewById(R.id.buttonrow2).setVisibility(View.GONE);

//                            ((EditText) view.findViewById(R.id.send_text)).setText("");
                            ((TextView) view.findViewById(R.id.textView)).setText(R.string.TitleTextSet);
                            ((Button) sendBtn).setText(R.string.buttonSet);
                            Toast.makeText(getActivity(), "AVAILABLE!", Toast.LENGTH_SHORT).show();

                        } else if (fxBTresponse.charAt(1) == 'B') {
                            view.findViewById(R.id.textView).setVisibility(View.GONE);
                            view.findViewById(R.id.confirmPWrow).setVisibility(View.GONE);
                            view.findViewById(R.id.unlockPWrow).setVisibility(View.GONE);
                            view.findViewById(R.id.buttonrow).setVisibility(View.GONE);
                            view.findViewById(R.id.buttonrow2).setVisibility(View.VISIBLE);
//                            ((TextView) view.findViewById(R.id.textView)).setText(R.string.TitleText);
                            ((Button) sendBtn2).setText(R.string.button);
                            Toast.makeText(getActivity(), "BOOKED!", Toast.LENGTH_SHORT).show();

                        } else if (fxBTresponse.charAt(1) == 'O') {
                            Toast.makeText(getActivity(), "UNLOCKED!", Toast.LENGTH_SHORT).show();


                        }
                        receiveText.removeTextChangedListener(this);
                        receiveText.setText("");
                        receiveText.addTextChangedListener(this);
                    }
                } catch (NumberFormatException e) {
                    //do whatever you like when value is incorrect

                }
            }
        });

        return view;
    }

//    @Override
//    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
//        inflater.inflate(R.menu.menu_terminal, menu);
//        menu.findItem(R.id.hex).setChecked(hexEnabled);
//    }

//    @Override
//    public boolean onOptionsItemSelected(MenuItem item) {
//        int id = item.getItemId();
//        if (id == R.id.clear) {
//            receiveText.setText("");
//            return true;
//        } else if (id == R.id.newline) {
//            String[] newlineNames = getResources().getStringArray(R.array.newline_names);
//            String[] newlineValues = getResources().getStringArray(R.array.newline_values);
//            int pos = java.util.Arrays.asList(newlineValues).indexOf(newline);
//            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
//            builder.setTitle("Newline");
//            builder.setSingleChoiceItems(newlineNames, pos, (dialog, item1) -> {
//                newline = newlineValues[item1];
//                dialog.dismiss();
//            });
//            builder.create().show();
//            return true;
//        } else if (id == R.id.hex) {
//            //hexEnabled = !hexEnabled;
//            sendText.setText("");
//            //hexWatcher.enable(hexEnabled);
//            //sendText.setHint(hexEnabled ? "HEX mode" : "");
//            //item.setChecked(hexEnabled);
//            return true;
//        } else {
//            return super.onOptionsItemSelected(item);
//        }
//    }

    /*
     * Serial + UI
     */
    private void connect() {
        try {
            Log.i("device address1", "running");
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
            Log.i("device address", deviceAddress);
            status("connecting...");
            Toast.makeText(getActivity(), "Connecting...", Toast.LENGTH_SHORT).show();
            connected = Connected.Pending;
            SerialSocket socket = new SerialSocket(getActivity().getApplicationContext(), device);
            service.connect(socket);
            lockerNum = device.getName().toUpperCase();


        } catch (Exception e) {
            onSerialConnectError(e);
        }
    }

    private void disconnect() {
        connected = Connected.False;
        service.disconnect();
    }

    private void send(String str) {
        GoogleSignInAccount signInAccount = GoogleSignIn.getLastSignedInAccount(getActivity().getApplicationContext());
        //if the str is master key, reset code $MM#
        //Authenticate here to check if user is supposed to have access. if yes send MM string , if no keep as Red.

        if (str.equals("")) {

            FirebaseFirestore db = FirebaseFirestore.getInstance();
            DocumentReference lockerRef = db.collection("locker").document(lockerNum.toLowerCase());
            Date date = new Date();
            Date booked_date = date;
            Source source = Source.CACHE;
            lockerRef.get(source).addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                @Override
                public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                    if (task.isSuccessful()){
                        DocumentSnapshot document = task.getResult();
                        if (document.exists()){
//                            bookerEmail = (String) document.get("booked_by");
                            String booker_email =  (String) document.get("booked_by");
                            bookingID = (String) document.get("bookingId");
                            boolean collection_status = false;
                            DocumentReference bookingRef = db.collection("booking").document(bookingID);

                            String locker = lockerNum.toLowerCase();
                            String receiver = signInAccount.getEmail();
                            String AlgoNote = "Receiver:" + receiver + "," +
                                    "\nLocker ID:" + locker + "," +
                                    "\nBooker: " + booker_email;

                            try {
                                Thread Transaction = new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        try  {
                                            Address address = new Address("MB2C3UU2WBDSJUNOISPADXXU7POEZRLYMRZVXDO63FB5RKJHYC6P4L6VYU");
                                            getWalletBalance(address);
                                            Transact(AlgoNote);
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                    }
                                });
                                Transaction.start();
                            } catch (Exception e) {
                                Log.d(TAG, e.toString());
                            }
                        }
                    }
                }
            });
//
//            String booker_email = bookerEmail;
//            Log.i(TAG, "2" + bookerEmail);
//            boolean collection_status = false;
//            String locker = lockerNum.toLowerCase();
//            String receiver = signInAccount.getEmail();
//            String AlgoNote = "Receiver:" + receiver + "," +
//                    "\nLocker ID:" + locker + "," +
//                    "\nBooker: " + booker_email;
//
//            try {
//                Thread Transaction = new Thread(new Runnable() {
//                    @Override
//                    public void run() {
//                        try  {
//                            Address address = new Address("MB2C3UU2WBDSJUNOISPADXXU7POEZRLYMRZVXDO63FB5RKJHYC6P4L6VYU");
//                            getWalletBalance(address);
//                            Transact(AlgoNote);
//                        } catch (Exception e) {
//                            e.printStackTrace();
//                        }
//                    }
//                });
//                Transaction.start();
//            } catch (Exception e) {
//                Log.d(TAG, e.toString());
//            }
//            URIBuilder ub = null;
//            try {
//                ub = new URIBuilder("https://lockyv3.netlify.app/collect.html"); //https://locky2.herokuapp.com/
////                ub.addParameter("newReciever2", str);
////                ub.addParameter("newLockerNumber2", lockerNum.toLowerCase());
////                ub.addParameter("newBooker2", signInAccount.getEmail());
//                ub.addParameter("receiver", str);
//                ub.addParameter("lockerID", lockerNum.toLowerCase());
//                ub.addParameter("booker", signInAccount.getEmail());
//
//
//            } catch (URISyntaxException e) {
//                e.printStackTrace();
//            }

            lockerRef.update("booked_status", false, "receiver", "", "booked_by", "").addOnSuccessListener(new OnSuccessListener<Void>() {
                @Override
                public void onSuccess(Void aVoid) {
                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                }
            });
//
//            String url = ub.toString();
//            Uri webpage = Uri.parse(url);
//            Intent webIntent = new Intent(Intent.ACTION_VIEW, webpage);
//            startActivity(webIntent);

            Log.i("before", str);
            str = "$MM#";
            Log.i("after", str);
        } else {
            Random random = new Random();
            int r = random.nextInt(999999);
            str = ('$' + String.valueOf(r) + "#");
            Log.i("random", str);
        }

        if (connected != Connected.True) {
            Toast.makeText(getActivity(), "not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String msg;
            byte[] data;
//            if(hexEnabled) {
//                StringBuilder sb = new StringBuilder();
//                TextUtil.toHexString(sb, TextUtil.fromHexString(str));
//                TextUtil.toHexString(sb, newline.getBytes());
//                msg = sb.toString();
//                data = TextUtil.fromHexString(msg);
//                Log.i("data1", msg);
//            } else {
            msg = str;
            Log.i("data2", msg);
            data = (str + newline).getBytes();
            Log.i("data3", String.valueOf(data));
//            }
            SpannableStringBuilder spn = new SpannableStringBuilder(msg + '\n');
            spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorSendText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            //receiveText.append(spn);//This is sending text...
            service.write(data);
            getActivity().onBackPressed();
        } catch (Exception e) {
            onSerialIoError(e);
        }
    }

    private void receive(byte[] data) {
        String msg = new String(data);
        Log.i("msg3", msg);
//        if(hexEnabled) {
//            //receiveText.append(TextUtil.toHexString(data) + '\n'); //hex text?
//        } else {
//            String msg = new String(data);
        Log.i("e1", msg);
        if (newline.equals(TextUtil.newline_crlf) && msg.length() > 0) {
            // don't show CR as ^M if directly before LF
            msg = msg.replace(TextUtil.newline_crlf, TextUtil.newline_lf);
            Log.i("e2", msg);
            // special handling if CR and LF come in separate fragments
            if (pendingNewline && msg.charAt(0) == '\n') {
                Editable edt = receiveText.getEditableText();
                if (edt != null && edt.length() > 1)
                    edt.replace(edt.length() - 2, edt.length(), "");
            }
            pendingNewline = msg.charAt(msg.length() - 1) == '\r';
            Log.i("e3", msg);
        }

        //Will open when connect "$AA#" "$AE#", pop up to set password
        //If someone booked, will wait for password "$BA#" "$BI#"
        //if password correct, will unlock and reset
        //Master reset need to send $MM#
        //Password set of unlock is $PPPPPP#
        //limit password to 6 digits.
        Log.i("e4", msg);

        receiveText.append(msg);
    }
//    }

    private void status(String str) {
        SpannableStringBuilder spn = new SpannableStringBuilder(str + '\n');
        spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorStatusText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        //receiveText.append(spn); //Status display
    }

    /*
     * SerialListener
     */
    @Override
    public void onSerialConnect() {
        status("connected");
        Toast.makeText(getActivity(), "connected...", Toast.LENGTH_SHORT).show();
        connected = Connected.True;
    }

    @Override
    public void onSerialConnectError(Exception e) {
        status("connection failed: " + e.getMessage());
        disconnect();
        Toast.makeText(getActivity(), "Connection failed!", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onSerialRead(byte[] data) {
        receive(data);
    }

    @Override
    public void onSerialIoError(Exception e) {
        status("connection lost: " + e.getMessage());
        disconnect();
        Toast.makeText(getActivity(), "Connection lost!", Toast.LENGTH_SHORT).show();
    }

    public static Account Act1(String mnemonic) throws GeneralSecurityException {
        Account myAct1 = new Account(mnemonic);
        try {
            Log.d(TAG, " Algo account address: " + myAct1.getAddress());
            Log.d(TAG, " Algo account MNEMONIC: " + myAct1.toMnemonic());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return myAct1;
    }
    public String getWalletBalance(Address myAddress) throws Exception {
        com.algorand.algosdk.v2.client.model.Account accountInfo = client.AccountInformation(myAddress).execute().body();
        try {
            Log.d(TAG, "Account Balance: " + accountInfo.amount + " microAlgos");
        } catch (Exception e)
        {
            Log.d(TAG,"Problem retrieving account balance: " +e.getMessage());
        }
        return accountInfo.amount.toString();
    }
    public void waitForConfirmation(String txID) throws Exception {
        Long lastRound = client.GetStatus().execute().body().lastRound;
        while (true) {
            try {
                Response <PendingTransactionResponse> pendingInfo = client.PendingTransactionInformation(txID).execute();
                if (pendingInfo.body().confirmedRound !=null && pendingInfo.body().confirmedRound > 0) {
                    Log.d(TAG, "Transaction: " + txID + "confirmed in round: " + pendingInfo.body().confirmedRound);
                    break;
                }
                lastRound++;
                client.WaitForBlock(lastRound).execute();
            } catch (Exception e)
            {
                throw(e);
            }
        }
    }
    public void Transact(String AlgoNote) throws Exception {
        final String PASSPHRASE = "uphold scare tribe flight jaguar margin meadow shed senior recycle syrup tenant cradle raw angry say echo cram garbage social adult galaxy dove abstract among";
        Log.d(TAG, "Address: " + Act1(PASSPHRASE).getAddress());
        String myAddress = Act1(PASSPHRASE).getAddress().toString();

        com.algorand.algosdk.v2.client.model.Account accountInfo = client.AccountInformation(Act1(PASSPHRASE).getAddress()).execute().body();

        Log.d(TAG, String.format("Account Balance: %d microAlgos", accountInfo.amount));

        try {
            final String RECEIVER = "MB2C3UU2WBDSJUNOISPADXXU7POEZRLYMRZVXDO63FB5RKJHYC6P4L6VYU";
//            final String RECEIVER = "BABNYNSFRUWXROJWARPRK5QSQGLK4JBN4YZALHSGMVV5RLGZFUR2R4RZ5M";
//            String note = AlgoNote[0] + "\n" +Note[1] + "\n"+ Note[2];
            //note.substring(0, Note[1].length()).getBytes(StandardCharsets.UTF_8))
            TransactionParametersResponse params = client.TransactionParams().execute().body();
            Transaction txn = Transaction.PaymentTransactionBuilder()
                    .sender(myAddress)
                    .note(AlgoNote.getBytes(StandardCharsets.UTF_8))
                    .amount(0)
                    .receiver(new Address(RECEIVER))
                    .suggestedParams(params)
                    .build();

            SignedTransaction signedTxn = Act1(PASSPHRASE).signTransaction(txn);
            Log.d(TAG,"Signed transaction with txid: " +signedTxn.transactionID);

            byte[] encodedTxBytes = Encoder.encodeToMsgPack(signedTxn);
            String id = client.RawTransaction().rawtxn(encodedTxBytes).execute().body().txId;
            Log.d(TAG, "Successfully sent txn with ID: " + id);

            waitForConfirmation(id);

            PendingTransactionResponse pTrx = client.PendingTransactionInformation(id).execute().body();
            Log.d(TAG, "Transaction information: "+ pTrx.toString());
            Log.d(TAG,"Decoded Note: " + new String(pTrx.txn.tx.note));
            if (pTrx.closingAmount != null) {
                Log.d(TAG,"Closing amount: " + new String(pTrx.closingAmount.toString()));
            }

            FirebaseFirestore db = FirebaseFirestore.getInstance();
//            DocumentReference lockerRef = db.collection("locker").document(lockerNum.toLowerCase());
            DocumentReference bookingRef = db.collection("booking").document(bookingID);
            bookingRef.update("collection_status", true, "collectedHash", id).addOnSuccessListener(new OnSuccessListener<Void>() {
                @Override
                public void onSuccess(Void aVoid) {
                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                }
            });

            getWalletBalance(Act1(PASSPHRASE).getAddress());
        } catch (Exception e) {
            Log.e(TAG,"Exception when calling algod#transactionInformation: " + e.getMessage());
        }
    }
}
