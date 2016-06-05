package com.example.mayur.wakeonlanwatch;

import android.app.PendingIntent;
import android.nfc.NfcAdapter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.MenuItem;
import android.widget.Button;

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
                PacketTask p = new PacketTask();
                p.execute();
            }
        });
        getNfcButton().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                
            }
        });
    }

    private Button getWakeButton(){
        return (Button) findViewById(R.id.wakeButton);
    }

    private Button getNfcButton(){
        return (Button) findViewById(R.id.nfcButton);
    }

    public static class PacketTask extends AsyncTask<Void, Void, Void> {
        //This is direct copy and paste from Paul Mutton's Wake on Lan java code.
        //Found at http://www.jibble.org/wake-on-lan/WakeOnLan.java.

        @Override
        protected Void doInBackground(Void... params) {
            String ipStr = "192.168.1.5"; //IP for my home computer
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

                InetAddress address = InetAddress.getByName(ipStr);
                //This next line is to send it to every device on the network.
                InetAddress broadcast = InetAddress.getByName("192.168.1.255");
                DatagramPacket packet = new DatagramPacket(bytes, bytes.length, address, PORT);
                //Packet just for sending to everyone
                DatagramPacket broadPacket = new DatagramPacket(bytes, bytes.length, broadcast, PORT);
                DatagramSocket socket = new DatagramSocket();
                socket.send(packet);
                //Send everyone the packet, just in case.
                socket.send(broadPacket);
                //Debugging Purposes
                //String s = socket.getLocalAddress().getHostAddress();
                socket.close();
            }
            catch (Exception e) {
                Log.d("Error", e.toString());
            }
            return null;
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
