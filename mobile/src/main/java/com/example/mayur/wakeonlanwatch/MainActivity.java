package com.example.mayur.wakeonlanwatch;

import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.Toast;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class MainActivity extends AppCompatActivity {

    boolean mWriteMode = false;
    private NfcAdapter mNfcAdapter;
    private PendingIntent mNfcPendingIntent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWakeButton().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PacketTask p = new PacketTask(getApplicationContext());
                p.execute();
            }
        });
        getNfcButton().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //A lot of this was from https://github.com/balloob/Android-NFC-Tag-Writer/blob/master/src/nl/paulus/nfctagwriter/MainActivity.java

                mNfcAdapter = NfcAdapter.getDefaultAdapter(MainActivity.this);
                mNfcPendingIntent = PendingIntent.getActivity(MainActivity.this, 0,
                        new Intent(MainActivity.this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);

                enableTagWriteMode();

                new AlertDialog.Builder(MainActivity.this).setTitle("Touch tag to write")
                        .setOnCancelListener(new DialogInterface.OnCancelListener() {
                            @Override
                            public void onCancel(DialogInterface dialog) {
                                disableTagWriteMode();
                            }

                        }).create().show();
            }
        });
    }

    private Button getWakeButton(){
        return (Button) findViewById(R.id.wakeButton);
    }

    private Button getNfcButton(){
        return (Button) findViewById(R.id.nfcButton);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        // Tag writing mode
        if (mWriteMode && NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction())) {
            Tag detectedTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            NdefRecord record = NdefRecord.createMime( getString(R.string.mime) , getString(R.string.mimeData).getBytes());
            NdefMessage message = new NdefMessage(new NdefRecord[] { record });
            if (writeTag(message, detectedTag)) {
                Toast.makeText(this, "Success: Wrote placeid to nfc tag", Toast.LENGTH_SHORT)
                        .show();
            }
        }
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(getIntent().getAction())) {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            NetworkInfo wifi = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

            if (!wifi.isConnected()) {

                WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
                wifiManager.setWifiEnabled(true);
                int netId = -1;
                for (WifiConfiguration tmp : wifiManager.getConfiguredNetworks())
                    if (tmp.SSID.equals( "\""+"BarberPole5GHz"+"\""))
                    {
                        netId = tmp.networkId;
                        wifiManager.enableNetwork(netId, true);
                        break;
                    }
            }
            Log.d("NFC", "Tag Detected");
            PacketTask p = new PacketTask(getApplicationContext());
            p.execute();
            finish();
        }
    }

    private void disableTagWriteMode() {
        mWriteMode = false;
        mNfcAdapter.disableForegroundDispatch(this);
    }

    private void enableTagWriteMode() {
        mWriteMode = true;
        IntentFilter tagDetected = new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED);
        IntentFilter[] mWriteTagFilters = new IntentFilter[] { tagDetected };
        mNfcAdapter.enableForegroundDispatch(this, mNfcPendingIntent, mWriteTagFilters, null);
    }

    /*
	* Writes an NdefMessage to a NFC tag
	*/
    public boolean writeTag(NdefMessage message, Tag tag) {
        int size = message.toByteArray().length;
        try {
            Ndef ndef = Ndef.get(tag);
            if (ndef != null) {
                ndef.connect();
                if (!ndef.isWritable()) {
                    Toast.makeText(getApplicationContext(),
                            "Error: tag not writable",
                            Toast.LENGTH_SHORT).show();
                    return false;
                }
                if (ndef.getMaxSize() < size) {
                    Toast.makeText(getApplicationContext(),
                            "Error: tag too small",
                            Toast.LENGTH_SHORT).show();
                    return false;
                }
                ndef.writeNdefMessage(message);
                return true;
            } else {
                NdefFormatable format = NdefFormatable.get(tag);
                if (format != null) {
                    try {
                        format.connect();
                        format.format(message);
                        return true;
                    } catch (IOException e) {
                        return false;
                    }
                } else {
                    return false;
                }
            }
        } catch (Exception e) {
            return false;
        }
    }

    public static class PacketTask extends AsyncTask<Void, Void, Void> {
        //This is direct copy and paste from Paul Mutton's Wake on Lan java code.
        //Found at http://www.jibble.org/wake-on-lan/WakeOnLan.java.

        private Context mContext;

        public PacketTask (Context context){
            mContext = context;
        }

        @Override
        protected Void doInBackground(Void... params) {
            String macStr = "50:E5:49:E3:12:F4"; //MAC Address for your computer.
            int PORT = 9;
            try {
                byte[] macBytes = getMacBytes(macStr);
                byte[] bytes = new byte[6 + 16 * macBytes.length];
                for (int i = 0; i < 6; i++) {
                    bytes[i] = (byte) 0xff;
                }
                for (int i = 6; i < bytes.length; i += macBytes.length) {
                    System.arraycopy(macBytes, 0, bytes, i, macBytes.length);
                }

                //This next line is to send it to every device on the network.
                InetAddress broadcast = InetAddress.getByName("192.168.1.255");
                //Packet just for sending to everyone
                DatagramPacket broadPacket = new DatagramPacket(bytes, bytes.length, broadcast, PORT);
                DatagramSocket socket = new DatagramSocket();

                //Wait till connected
                while (!isConnected(mContext)) {
                    //Wait to connect
                    Thread.sleep(1000);
                }
                //Send everyone the packet
                socket.send(broadPacket);

                Toast.makeText(mContext, "Woke Up PC", Toast.LENGTH_SHORT)
                        .show();
                //Debugging Purposes
                //String s = socket.getLocalAddress().getHostAddress();
                socket.close();
            }
            catch (Exception e) {
                Log.d("Error", e.toString());
            }
            return null;
        }

        //Copied from http://stackoverflow.com/questions/8678362/wait-until-wifi-connected-on-android
        public static boolean isConnected(Context context) {
            ConnectivityManager connectivityManager = (ConnectivityManager)
                    context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo networkInfo = null;
            if (connectivityManager != null) {
                networkInfo = connectivityManager.getActiveNetworkInfo();
            }

            return networkInfo != null && networkInfo.getState() == NetworkInfo.State.CONNECTED;
        }

        private byte[] getMacBytes(String macStr) throws IllegalArgumentException {
            byte[] bytes = new byte[6];
            String[] hex = macStr.split("(\\:|\\-)");
            if (hex.length != 6) {
                throw new IllegalArgumentException("Invalid MAC address.");
            }
            try {
                for (int i = 0; i < 6; i++) {
                    bytes[i] = (byte) Integer.parseInt(hex[i], 16);
                }
            }
            catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid hex digit in MAC address.");
            }
            return bytes;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
