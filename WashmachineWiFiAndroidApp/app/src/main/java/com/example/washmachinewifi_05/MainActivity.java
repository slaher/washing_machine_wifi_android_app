package com.example.washmachinewifi_05;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Bundle;
import android.os.StrictMode;
import android.widget.*;
import android.view.View;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;

import java.io.*;
import java.net.*;

// https://www.instructables.com/id/NodeMCU-ESP8266-Details-and-Pinout/

public class MainActivity extends AppCompatActivity
{
    //    IP nad PORT  ;   DDNS: https://www.duckdns.org   slaherpralka.duckdns.org
    private String washmachineIpAddress = "192.168.0.129";
    private int washmashinePortNumber = 5045;

    //    Socket
    private Socket socket = null;
    private SocketAddress socketAddress;

    //     washmachine name
    private String washmashineName = "iwsd51252";

    //    output commands
    private String powerOnStr = washmashineName + "_power_on";
    private String powerOffStr = washmashineName + "_power_off";
    private String startStr = washmashineName + "_start";
    private String pauseStr = washmashineName + "_pause";

    //    buttons initialization
    private Button powerOnBtn;
    private Button powerOffBtn;
    private Button startBtn;
    private Button pauseBtn;
    private ImageView refreshBtn;  // Image as button

    //    input commands
    static final private String washLedStatusInput = "ledwash";
    static final private String rinseLedStatusInput = "ledrinse";
    static final private String runLedStatusInput = "ledrun";
    static final private String pauseLedStatusInput = "ledpause";
    static final private String spinLedStatusInput = "ledspin";
    static final private String drainLedStatusInput = "leddrain";
    static final private String endOfWashLedStatusInput = "ledendofwash";
    static final private String lockLedStatusInput = "ledlock";

    //    address field
    private TextView txtConn;
    private TextView txtAddressField;

    //    images initialization
    private ImageView washImageView;
    private ImageView rinseImageView;
    private ImageView runImageView;
    private ImageView spinImageView;
    private ImageView drainImageView;
    private ImageView endOfWashImageView;
    private ImageView lockImageView;

    //    handler - processing message
    private Handler handler = new Handler();

    //    run and pause state values for showing proper icon
    private Boolean runState = false;
    private Boolean pauseState = false;

    //    Shared preferences - remembers address after closing app
    private SharedPreferences sharedPref;
    private Thread thr;

    public MainActivity() {}

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //    Shared preferences - remembers address after closing app
        sharedPref  = this.getPreferences(Context.MODE_PRIVATE);
        String washmashineIpAddressSharedPref = sharedPref.getString("washmashine_ip_address", "");

        if (washmashineIpAddressSharedPref.equals(""))
        {
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putString("washmashine_ip_address", washmachineIpAddress);
            editor.apply();
        }
        else{
            washmachineIpAddress = washmashineIpAddressSharedPref;
        }

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        //        buttons initialization
        powerOnBtn = (Button) findViewById(R.id.btn_power_on);
        powerOffBtn = (Button) findViewById(R.id.btn_power_off);
        startBtn = (Button) findViewById(R.id.btn_start);
        pauseBtn = (Button) findViewById(R.id.btn_pause);
        refreshBtn = (ImageView) findViewById(R.id.refresh_image);

        //        enable buttons when connected, disable when disconnected
        SetEnableDisableButtons(false);

        txtConn = (TextView) findViewById(R.id.txt_connection_state);
        txtConn.setText("Not Connected");

        //        images initialization
        washImageView = (ImageView) findViewById(R.id.wash_image);
        rinseImageView = (ImageView) findViewById(R.id.rinse_image);
        runImageView = (ImageView) findViewById(R.id.play_image);
        spinImageView = (ImageView) findViewById(R.id.spin_image);
        drainImageView = (ImageView) findViewById(R.id.drain_image);
        endOfWashImageView = (ImageView) findViewById(R.id.end_image);
        lockImageView = (ImageView) findViewById(R.id.lock_image);

        //        et address in textbox field
        txtAddressField = (TextView) findViewById(R.id.textViewAddressField);
        txtAddressField.setText(washmachineIpAddress);

        //        On Click Listener for power_on_btn
        powerOnBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    SendPowerOn();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        //        On Click Listener for power_off_btn
        powerOffBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    SendPowerOff();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        //        On Click Listener for pause_btn
        pauseBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    SendPause();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        //        On Click Listener for start_btn
        startBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    SendStart();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        //        On Click Listener for refresh_btn
        refreshBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    RefreshConnection();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        //        Start connection
        StartConnection();
    }

    private void StartThread(){
        thr = new Thread(new Runnable() {

            @SuppressLint("SetTextI18n")
            public void run() {
                try {
                    while(socket.isConnected()) {
                        byte[] resultBuff = new byte[0];
                        byte[] buff = new byte[1024];
                        int k = -1;
                        String[] parts_input_string;
                        while((k = socket.getInputStream().read(buff, 0, buff.length)) > -1 ) {
                            byte[] tbuff = new byte[resultBuff.length + k]; // temp buffer size = bytes already read + bytes last read
                            System.arraycopy(resultBuff, 0, tbuff, 0, resultBuff.length); // copy previous bytes
                            System.arraycopy(buff, 0, tbuff, resultBuff.length, k);  // copy current lot
                            resultBuff = tbuff;
                            String se = new String(resultBuff);
                            parts_input_string = se.split("_");
                            if ((parts_input_string.length == 3) && parts_input_string[0].equals(washmashineName) ) {
                                switch (parts_input_string[1]) {
                                    case washLedStatusInput:
                                        SetLedIconStatus(parts_input_string,
                                                washImageView,
                                                R.drawable.wash,
                                                R.drawable.wash_green,
                                                washLedStatusInput);
                                        break;

                                    case rinseLedStatusInput:
                                        SetLedIconStatus(parts_input_string,
                                                rinseImageView,
                                                R.drawable.rinse,
                                                R.drawable.rinse_green,
                                                rinseLedStatusInput);
                                        break;


                                    case spinLedStatusInput:
                                        SetLedIconStatus(parts_input_string,
                                                spinImageView,
                                                R.drawable.spin,
                                                R.drawable.spin_green,
                                                spinLedStatusInput);
                                        break;

                                    case drainLedStatusInput:
                                        SetLedIconStatus(parts_input_string,
                                                drainImageView,
                                                R.drawable.drain,
                                                R.drawable.drain_green,
                                                drainLedStatusInput);
                                        break;

                                    case endOfWashLedStatusInput:
                                        SetLedIconStatus(parts_input_string,
                                                endOfWashImageView,
                                                R.drawable.end,
                                                R.drawable.end_green,
                                                endOfWashLedStatusInput);
                                        break;

                                    case lockLedStatusInput:
                                        SetLedIconStatus(parts_input_string,
                                                lockImageView,
                                                R.drawable.lock,
                                                R.drawable.lock_green,
                                                lockLedStatusInput);
                                        break;

                                    case pauseLedStatusInput:
                                        pauseState = Boolean.parseBoolean(parts_input_string[2]);
                                        SetLedIconRunPauseStatus(pauseLedStatusInput);
                                        break;

                                    case runLedStatusInput:
                                        runState = Boolean.parseBoolean(parts_input_string[2]);
                                        SetLedIconRunPauseStatus(runLedStatusInput);
                                        break;

                                    default:
                                        System.out.println("no match");
                                }
                            }
                            resultBuff = new byte[0];
                            buff = new byte[1024];
                        }
                        Thread.sleep(100);
                    }

                } catch(IOException | InterruptedException ignored) {

                }
            }
        });
    }

    private void SetEnableDisableButtons(Boolean state) {
        powerOnBtn.setEnabled(state);
        powerOffBtn.setEnabled(state);
        startBtn.setEnabled(state);
        pauseBtn.setEnabled(state);
    }

    private void SetLedIconStatus(final String[] input_string_led,
                                  final ImageView image_view,
                                  final int image_name_gray,
                                  final int image_name_green,
                                  final String message)
    {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (!Boolean.parseBoolean(input_string_led[2])) {  // LED OFF
                    image_view.setImageResource(image_name_gray);
                    System.out.println(message + " OFF");
                }
                else if (Boolean.parseBoolean(input_string_led[2])) {  // LED ON
                    image_view.setImageResource(image_name_green);
                    System.out.println(message + " ON");
                }
            }
        });
    }

    private void SetLedIconRunPauseStatus(final String message)
    {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (runState) {  // LED GREEN
                    runImageView.setImageResource(R.drawable.play_green);
                    System.out.println(message + " GREEN_ON");
                }

                else if (pauseState) {  // LED ORANGE
                    runImageView.setImageResource(R.drawable.play_orange);
                    System.out.println(message + " ORANGE_ON");
                }
                else {  // LED OFF
                    runImageView.setImageResource(R.drawable.play);
                    System.out.println(message + " OFF");
                }
            }
        });
    }

    private void RefreshConnection() throws IOException {
        if (socket.isConnected()) {
            thr.interrupt();
            thr = null;
            socket.close();
        }
        StartConnection();
    }

    private void SendPowerOn() throws IOException {
        SendMessage(powerOnStr);
    }

    private void SendStart() throws IOException {
        SendMessage(startStr);
    }

    private void SendPowerOff() throws IOException {
        SendMessage(powerOffStr);
    }

    private void SendPause() throws IOException {
        SendMessage(pauseStr);
    }
    private void SendMessage(String message) throws IOException {
        if (socket.isConnected()) {
            OutputStream output = socket.getOutputStream();
            byte[] data = message.getBytes();
            output.write(data);
        }
        else {
            RefreshConnection();
        }
    }

    //    Start connection method
    private void StartConnection()
    {
        socket = null;
        socket = new Socket();
        txtConn.setText("Not Connected");
        SetEnableDisableButtons(false);
        socketAddress = null;
        socketAddress = new InetSocketAddress(washmachineIpAddress, washmashinePortNumber);

        try {
            socket.connect(socketAddress, 2000);
            Thread.sleep(1000);
        } catch (IOException | InterruptedException e) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ie) {
                // Interrupted.
            }
        }
        if (socket.isConnected()){
            StartThread();
            thr.start();
            txtConn.setText("Connected");
            SetEnableDisableButtons(true);
        }
        else
            txtConn.setText("Refresh again - no connection");
    }

    @Override
    public void onBackPressed() {
    DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
    if (drawer.isDrawerOpen(GravityCompat.START))
        drawer.closeDrawer(GravityCompat.START);
    else
        super.onBackPressed();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        //        Settings - disconnect, set new address and connect again
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();

            alertDialog.setTitle("Set address");

            final EditText input = new EditText(MainActivity.this);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT);
            input.setLayoutParams(lp);
            input.setText(washmachineIpAddress);
            alertDialog.setView(input);
            alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "OK",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            txtAddressField.setText(input.getText().toString());
                            washmachineIpAddress = input.getText().toString();
                            SharedPreferences.Editor editor = sharedPref.edit();
                            editor.putString("washmashine_ip_address", washmachineIpAddress);
                            editor.apply();
                            StartConnection();
                            dialog.dismiss();
                        }
                    });
            alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, "CANCEL",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            StartConnection();
                            dialog.dismiss();
                        }
                    });
            alertDialog.show();
        }

        return super.onOptionsItemSelected(item);
    }
}
