package com.linh.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_ENABLE_BT = 1;
    private static final UUID MY_UUID = UUID.fromString("5311b035-80e7-403d-9a99-1de8ab3b2829");
    private static final String TAG = "DEBUG";
    private static final String NAME= "Tablet";

    private static final int STATE_CONNECTING = 2;
    private static final int STATE_CONNECTED = 3;
    private static final int STATE_DISCONNECTED = 4;
    private static final int STATE_DISCONNECTING = 5;
    private static final int STATE_CONNECTION_FAIL = 6;
    private static final int STATE_LISTENED = 7;


    TextView lbDeviceName;
    TextView lbStatus;
    Button btnDisconnect;
    Button btnScan;
    ListView lvDevice;

    BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    BluetoothDevice bluetoothDevice;
    BluetoothSocket bluetoothSocket;
    Set<BluetoothDevice> bluetoothDevices;
    ArrayAdapter<String> arrayAdapter;
    List<String> ltDevice;

    boolean isBtConnected;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        addControls();
        addEvents();
    }

    final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(BluetoothDevice.ACTION_FOUND.equals(action)){
                bluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                bluetoothDevices.add(bluetoothDevice);
                String info = bluetoothDevice.getName() + "\n" + bluetoothDevice.getAddress();
                if(!ltDevice.contains(info)) {
                    ltDevice.add(bluetoothDevice.getName() + "\n" + bluetoothDevice.getAddress());
                    arrayAdapter.notifyDataSetChanged();
                }
            }
            if(BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)){
                lbStatus.setText("Scanning...");
            }
            if(BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)){
                lbStatus.setText("Scanned.");
                Message msg = Message.obtain();
                msg.what = STATE_LISTENED;
                handler.sendMessage(msg);
            }
            if(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE.equals(action)){
                lbStatus.setText("Requesting...");
            }
        }
    };

    private void requestBluetoothPermission() {
        if(bluetoothAdapter == null){
            lbStatus.setText("Not support");
        }else {
            if(!bluetoothAdapter.enable()){
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }else{
                lbStatus.setText("Bluetooth ON");
                Intent discoverableIntent =
                        new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
                discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
                startActivity(discoverableIntent);
            }
        }
    }

    private void scanDevices() {
        bluetoothDevices.clear();
        ltDevice.clear();
        arrayAdapter.notifyDataSetChanged();

        for (BluetoothDevice bluetoothDevice: bluetoothAdapter.getBondedDevices()
             ) {
            bluetoothDevices.add(bluetoothDevice);
            ltDevice.add(bluetoothDevice.getName() + "\n" + bluetoothDevice.getAddress());
        }
        arrayAdapter.notifyDataSetChanged();
        if(bluetoothAdapter.isDiscovering())
            bluetoothAdapter.cancelDiscovery();
        bluetoothAdapter.startDiscovery();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
       if(requestCode == REQUEST_ENABLE_BT){
           if(resultCode == RESULT_OK){
               Toast.makeText(this, "Bluetooth is on", Toast.LENGTH_SHORT).show();
           }else if(resultCode == RESULT_CANCELED){
               Toast.makeText(this, "Bluetooth is canceled", Toast.LENGTH_SHORT).show();
               lbStatus.setText("BLUETOOTH OFF");
           }
       }
    }

    private class AcceptThread extends Thread {
        private final BluetoothServerSocket mmServerSocket;

        public AcceptThread() {
            // Use a temporary object that is later assigned to mmServerSocket
            // because mmServerSocket is final.
            BluetoothServerSocket tmp = null;
            try {
                // MY_UUID is the app's UUID string, also used by the client code.
                tmp = bluetoothAdapter.listenUsingRfcommWithServiceRecord(NAME, MY_UUID);
            } catch (IOException e) {
                Log.e(TAG, "Socket's listen() method failed", e);
            }
            mmServerSocket = tmp;
        }

        public void run() {
            BluetoothSocket socket = null;
            // Keep listening until exception occurs or a socket is returned.
            while (true) {
                try {
                    socket = mmServerSocket.accept();
                } catch (IOException e) {
                    Log.e(TAG, "Socket's accept() method failed", e);
                    break;
                }

                if (socket != null) {
                    // A connection was accepted. Perform work associated with
                    // the connection in a separate thread.
                    //manageMyConnectedSocket(socket);
                    try {
                        mmServerSocket.close();
                    } catch (IOException e) {
                        Log.e(TAG, "Could not close the connect socket", e);
                    }
                    break;
                }
            }
        }

        // Closes the connect socket and causes the thread to finish.
        public void cancel() {
            try {
                mmServerSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the connect socket", e);
            }
        }
    }

    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            // Use a temporary object that is later assigned to mmSocket
            // because mmSocket is final.
            BluetoothSocket tmp = null;
            mmDevice = device;

            try {
               // progress = ProgressDialog.show(MainActivity.this, "Connecting...", "Please wait!!!");  //show a progress dialog
                // Get a BluetoothSocket to connect with the given BluetoothDevice.
                // MY_UUID is the app's UUID string, also used in the server code.
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
                Log.e(TAG, "Socket's create() method failed", e);
            }
            mmSocket = tmp;
        }

        public void run() {
            // Cancel discovery because it otherwise slows down the connection.
            bluetoothAdapter.cancelDiscovery();
            Message msg = Message.obtain();
            msg.what = STATE_CONNECTING;
            handler.sendMessage(msg);
            try {
                // Connect to the remote device through the socket. This call blocks
                // until it succeeds or throws an exception.
                mmSocket.connect();
                msg = Message.obtain();
                msg.what = STATE_CONNECTED;
                handler.sendMessage(msg);
            } catch (IOException connectException) {
                // Unable to connect; close the socket and return.
                //progress.dismiss();
                try {
                    mmSocket.close();
                    msg = Message.obtain();
                    msg.what = STATE_CONNECTION_FAIL;
                    handler.sendMessage(msg);
                } catch (IOException closeException) {
                    Log.e(TAG, "Could not close the client socket", closeException);

                }
                return;
            }

            //progress.dismiss();
            // The connection attempt succeeded. Perform work associated with
            // the connection in a separate thread.
            //manageMyConnectedSocket(mmSocket);
        }

        // Closes the client socket and causes the thread to finish.
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the client socket", e);
            }
        }
    }

    private class ConnectBT extends AsyncTask<Void, Void, Void> {
        private boolean ConnectSuccess = true; //if it's here, it's almost connected

        @Override
        protected void onPreExecute()
        {
            //progress = ProgressDialog.show(MainActivity.this, "Connecting...", "Please wait!!!");  //show a progress dialog
        }

        @Override
        protected Void doInBackground(Void... devices) //while the progress dialog is shown, the connection is done in background
        {
            try
            {
                if (bluetoothSocket == null || !isBtConnected)
                {
                    bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();//get the mobile bluetooth device
                    BluetoothDevice dispositivo = bluetoothAdapter.getRemoteDevice(bluetoothDevice.getAddress());//connects to the device's address and checks if it's available
                    bluetoothSocket = dispositivo.createInsecureRfcommSocketToServiceRecord(MY_UUID);//create a RFCOMM (SPP) connection
                    BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
                    bluetoothSocket.connect();//start connection
                }
            }
            catch (IOException e)
            {
                ConnectSuccess = false;//if the try failed, you can check the exception here
            }
            return null;
        }
        @Override
        protected void onPostExecute(Void result) //after the doInBackground, it checks if everything went fine
        {
            super.onPostExecute(result);

            if (!ConnectSuccess)
            {
                msg("Connection Failed. Is it a SPP Bluetooth? Try again.");
                finish();
            }
            else
            {
                msg("Connected.");
                isBtConnected = true;
            }
            //progress.dismiss();
        }
    }

    private void msg(String s)
    {
        Toast.makeText(getApplicationContext(),s,Toast.LENGTH_LONG).show();
    }

    Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what){
                case STATE_CONNECTING:
                    lbStatus.setText("CONNECTING");
                    break;
                case STATE_CONNECTED:
                    lbStatus.setText("CONNECTED");
                    break;
                case STATE_DISCONNECTED:
                    lbStatus.setText("DISCONNECTED");
                    break;
                case STATE_DISCONNECTING:
                    break;
                case STATE_LISTENED:
                    break;
                case STATE_CONNECTION_FAIL:
                    lbStatus.setText("CONNECTION FAIL");
                    break;
            }
            return false;
        }
    });

    private void addEvents() {
        btnScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scanDevices();
            }
        });
        btnDisconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    if(bluetoothSocket.isConnected())
                        bluetoothSocket.close();
                } catch (IOException e) {
                    lbStatus.setText("Can not disconnect");
                }
            }
        });
        lvDevice.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                lbDeviceName.setText(ltDevice.get(position));
                bluetoothAdapter.cancelDiscovery();
                Log.i(TAG, position +"");
                //AcceptThread acceptThread = new AcceptThread();
                //acceptThread.start();
                bluetoothDevice = (BluetoothDevice) bluetoothDevices.toArray()[position];
                ConnectThread connectThread = new ConnectThread(bluetoothDevice);
                connectThread.start();
                //ConnectBT connectBT = new ConnectBT();
                //connectBT.execute();
            }
        });
    }

    private void addControls() {
        lbDeviceName= findViewById(R.id.lbDeviceName);
        lbStatus = findViewById(R.id.lbStatus);
        btnScan = findViewById(R.id.btnScan);
        btnDisconnect = findViewById(R.id.btnDisconnect);

        bluetoothDevices = new HashSet<>();

        lvDevice = findViewById(R.id.lvDeviceName);
        ltDevice = new ArrayList<>();
        arrayAdapter = new ArrayAdapter<>(MainActivity.this, android.R.layout.simple_list_item_1, ltDevice);
        lvDevice.setAdapter(arrayAdapter);

        requestBluetoothPermission();


        registerReceiver(broadcastReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
        registerReceiver(broadcastReceiver, new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_STARTED));
        registerReceiver(broadcastReceiver, new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(broadcastReceiver);
    }
}
