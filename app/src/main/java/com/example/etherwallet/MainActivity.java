package com.example.etherwallet;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.json.JSONArray;
import org.json.JSONObject;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.response.EthGetBalance;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Convert;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_REQUEST_CODE = 200;

    private EditText addressInput;
    private TextView balanceText;
    private TextView nonceText;
    private TextView transactionDetailsText;
    private ProgressDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        addressInput = findViewById(R.id.addressInput);
        balanceText = findViewById(R.id.balanceText);
        nonceText = findViewById(R.id.nonceText);
        transactionDetailsText = findViewById(R.id.transactionDetailsText);

        Button scanQRButton = findViewById(R.id.scanQRButton);
        Button fetchBalanceButton = findViewById(R.id.fetchBalanceButton);
        Button fetchTransactionButton = findViewById(R.id.fetchTransactionButton);

        // Setup progress dialog
        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Loading...");
        progressDialog.setCancelable(false);

        scanQRButton.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{Manifest.permission.CAMERA},
                        CAMERA_PERMISSION_REQUEST_CODE);
            } else {
                startQRScanner();
            }
        });

        fetchBalanceButton.setOnClickListener(v -> {
            String address = addressInput.getText().toString().trim();
            if (!address.isEmpty()) {
                new FetchBalanceAndNonceTask().execute(address);
            } else {
                Toast.makeText(MainActivity.this, "Please enter an Ethereum address", Toast.LENGTH_SHORT).show();
            }
        });

        fetchTransactionButton.setOnClickListener(v -> {
            String address = addressInput.getText().toString().trim();
            if (!address.isEmpty()) {
                new FetchTransactionDetailsTask().execute(address);
            } else {
                Toast.makeText(MainActivity.this, "Please enter an Ethereum address", Toast.LENGTH_SHORT).show();
            }
        });

        // Make transactionDetailsText draggable
        makeDraggable(transactionDetailsText);
    }

    private void startQRScanner() {
        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);
        integrator.setPrompt("Scan an Ethereum address QR code");
        integrator.setCameraId(0);  // Use the rear camera
        integrator.setBeepEnabled(false);
        integrator.setBarcodeImageEnabled(false);
        integrator.initiateScan();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null) {
            if (result.getContents() == null) {
                Toast.makeText(this, "Cancelled", Toast.LENGTH_LONG).show();
            } else {
                addressInput.setText(result.getContents());
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startQRScanner();
            } else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private class FetchBalanceAndNonceTask extends AsyncTask<String, Void, String[]> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            progressDialog.show();
        }

        @Override
        protected String[] doInBackground(String... strings) {
            String address = strings[0];
            try {
                Web3j web3j = Web3j.build(new HttpService("https://mainnet.infura.io/v3/0cd8f0cf675e4320a317fc117d0c793c"));

                EthGetBalance ethGetBalance = web3j.ethGetBalance(address, DefaultBlockParameter.valueOf("latest")).send();
                BigInteger weiBalance = ethGetBalance.getBalance();
                BigDecimal etherBalance = Convert.fromWei(weiBalance.toString(), Convert.Unit.ETHER);

                EthGetTransactionCount ethGetTransactionCount = web3j.ethGetTransactionCount(address, DefaultBlockParameter.valueOf("latest")).send();
                BigInteger nonce = ethGetTransactionCount.getTransactionCount();

                return new String[]{etherBalance.toString(), nonce.toString()};
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        protected void onPostExecute(String[] result) {
            progressDialog.dismiss();
            if (result != null) {
                balanceText.setText("Balance: " + result[0] + " ETH");
                nonceText.setText("Nonce: " + result[1]);
            } else {
                Toast.makeText(MainActivity.this, "Error fetching data", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private class FetchTransactionDetailsTask extends AsyncTask<String, Void, List<String>> {

        private final String ETHERSCAN_API_KEY = "47DRUK1X2KQ6MQM1H3XRYAYFUNWJ1MVKVK";
        private final String ETHERSCAN_API_URL = "https://api.etherscan.io/api";
        private final String TAG = FetchTransactionDetailsTask.class.getSimpleName();

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            progressDialog.show();
        }

        @Override
        protected List<String> doInBackground(String... strings) {
            String addressToFetch = strings[0]; // The address to fetch details for

            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder()
                    .url(buildUrl(addressToFetch))
                    .build();

            try {
                Response response = client.newCall(request).execute();
                if (response.isSuccessful()) {
                    String jsonData = response.body().string();
                    return parseTransactionData(jsonData);
                }
            } catch (IOException e) {
                Log.e(TAG, "Error fetching transaction details: " + e.getMessage(), e);
            }

            return null;
        }

        private String buildUrl(String address) {
            return ETHERSCAN_API_URL +
                    "?module=account" +
                    "&action=txlist" +
                    "&address=" + address +
                    "&startblock=0" +
                    "&endblock=99999999" + // Adjust endblock as needed
                    "&sort=asc" +
                    "&apikey=" + ETHERSCAN_API_KEY;
        }

        private List<String> parseTransactionData(String jsonData) {
            List<String> transactionDetails = new ArrayList<>();

            try {
                JSONObject jsonObject = new JSONObject(jsonData);
                JSONArray transactions = jsonObject.getJSONArray("result");

                for (int i = 0; i < transactions.length(); i++) {
                    JSONObject tx = transactions.getJSONObject(i);
                    String from = tx.getString("from");
                    String to = tx.getString("to");
                    String value = tx.getString("value");
                    String gasPrice = tx.getString("gasPrice");

                    String details = "From: " + from + "\n"
                            + "To: " + to + "\n"
                            + "Value: " + value + "\n"
                            + "Gas Price: " + gasPrice + "\n\n";
                    transactionDetails.add(details);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error parsing transaction details: " + e.getMessage(), e);
            }

            return transactionDetails;
        }

        @Override
        protected void onPostExecute(List<String> transactionDetails) {
            progressDialog.dismiss();
            if (transactionDetails != null && !transactionDetails.isEmpty()) {
                StringBuilder resultBuilder = new StringBuilder();
                for (String details : transactionDetails) {
                    resultBuilder.append(details);
                }
                transactionDetailsText.setText(resultBuilder.toString());
            } else {
                transactionDetailsText.setText("No transactions found for this address.");
            }
        }
    }

    private void makeDraggable(TextView textView) {
        textView.setOnTouchListener(new View.OnTouchListener() {
            private int previousX;
            private int previousY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        previousX = (int) event.getRawX();
                        previousY = (int) event.getRawY();
                        break;
                    case MotionEvent.ACTION_MOVE:
                        int deltaX = (int) event.getRawX() - previousX;
                        int deltaY = (int) event.getRawY() - previousY;

                        int newX = v.getLeft() + deltaX;
                        int newY = v.getTop() + deltaY;

                        v.layout(newX, newY, newX + v.getWidth(), newY + v.getHeight());

                        previousX = (int) event.getRawX();
                        previousY = (int) event.getRawY();
                        break;
                }
                return true;
            }
        });
    }
}
