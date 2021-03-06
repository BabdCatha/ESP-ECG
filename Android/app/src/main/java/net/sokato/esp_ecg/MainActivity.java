package net.sokato.esp_ecg;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Color;
import android.os.Bundle;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Button;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.utils.ColorTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import uk.me.berndporr.iirj.Butterworth;

public class MainActivity extends AppCompatActivity {

    static int points = 500;

    LineChart graph;
    Description description;
    Entry[] dataList = new Entry[points];
    //int[] inputs = new int[points];
    int[] inputs = {0,0,0,0, 0};
    int[] outputs = {0,0,0,0, 0};
    int posB = 0;
    int posA = 0;
    int filterPos = 0;

    Butterworth butterworth = new Butterworth();

    final Object mutex = new Object();

    double a0 = 0.20655953953361905;
    double a1 = 0.4131190790672381;
    double a2 = 0.20655953953361905;
    double b1 = -0.36950493743858204;
    double b2 = 0.19574309557305825;

    double[] Y = {0, 0};
    double[] X = {0, 0, 0};

    int position = 0;
    float timeAxis = 0;

    //Bluetooth variables and objects
    BluetoothAdapter mBluetoothAdapter;
    BluetoothSocket mmSocket;
    BluetoothDevice mmDevice;
    OutputStream mmOutputStream;
    InputStream mmInputStream;
    Thread workerThread;
    byte[] readBuffer;
    int readBufferPosition;
    int counter;
    volatile boolean stopWorker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        for(int i = 0; i < points; i++){
            dataList[i] = new Entry(i, 0);
            //inputs[i] = 40;
        }

        findBT();
        try {
            openBT();
        }catch(IOException IOexception){
            //test.setText("error lol");
        }

        graph = findViewById(R.id.graph);
        description = graph.getDescription();
        description.setEnabled(false);
        graph.getAxisLeft().setAxisMinimum(0);
        graph.getAxisLeft().setAxisMaximum(4096);
        graph.getAxisRight().setEnabled(false);
        graph.setDescription(description);

        butterworth.lowPass(4, 200, 40);

    }

    void findBT() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if(mBluetoothAdapter == null)
        {
            //test.setText("No bluetooth adapter available");
        }

        if(!mBluetoothAdapter.isEnabled())
        {
            Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBluetooth, 0); //TODO : https://stackoverflow.com/questions/62671106/onactivityresult-method-is-deprecated-what-is-the-alternative
        }

        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        if(pairedDevices.size() > 0)
        {
            for(BluetoothDevice device : pairedDevices)
            {
                if(device.getName().equals("ESP-ECG"))
                {
                    mmDevice = device;
                    break;
                }
            }
        }
        //test.setText("Bluetooth Device Found");
    }

    void openBT() throws IOException {
        UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); //Standard SerialPortService ID
        mmSocket = mmDevice.createRfcommSocketToServiceRecord(uuid);
        mmSocket.connect();
        mmOutputStream = mmSocket.getOutputStream();
        mmInputStream = mmSocket.getInputStream();

        beginListenForData();
    }

    void beginListenForData() {
        final Handler handler = new Handler();
        final byte delimiter = 10; //This is the ASCII code for a newline character

        stopWorker = false;
        readBufferPosition = 0;
        readBuffer = new byte[1024];
        workerThread = new Thread(new Runnable() {
            public void run() {
                while(!Thread.currentThread().isInterrupted() && !stopWorker) {
                    try {
                        int bytesAvailable = mmInputStream.available();
                        if(bytesAvailable > 0) {
                            byte[] packetBytes = new byte[bytesAvailable];
                            mmInputStream.read(packetBytes);
                            for(int i=0;i<bytesAvailable;i++) {
                                byte b = packetBytes[i];
                                readBuffer[readBufferPosition++] = b;
                                if(readBufferPosition == 2) {
                                    byte[] encodedBytes = new byte[readBufferPosition];
                                    System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);

                                    final ByteBuffer bb = ByteBuffer.wrap(encodedBytes);
                                    bb.order(ByteOrder.BIG_ENDIAN);
                                    int data = 0;
                                    if(bb.remaining() == 2) {
                                        data = bb.getShort();
                                    }

                                    final int data2 = data;

                                    readBufferPosition = 0;

                                    handler.post(() -> {

                                        int tempPos = getPosition();
                                        //int value = data2;
                                        //int value = (int)butterworth.filter(data2);
                                        int value = filter(data2);
                                        dataList[tempPos] = new Entry(tempPos, value);
                                        //Log.e("app", String.valueOf(position));

                                        incrementPosition();

                                        LineDataSet dataSet = new LineDataSet(Arrays.asList(dataList.clone()), "ECG");
                                        dataSet.setColor(ColorTemplate.getHoloBlue());
                                        dataSet.setDrawCircles(false);

                                        LineData lineData = new LineData(dataSet);

                                        graph.setData(lineData);
                                        graph.invalidate();

                                    });

                                }
                            }
                        }
                    }
                    catch (IOException ex) {
                        stopWorker = true;
                    }
                }
            }
        });

        workerThread.start();
    }

    int filter(int input){
        double res = 0;

        X[0] = input;

        res += a0*X[0];
        res += a1*X[1];
        res += a2*X[2];
        res += b1*Y[0];
        res += b2*Y[1];

        Y[1] = Y[0];
        Y[0] = res;

        X[2] = X[1];
        X[1] = X[0];

        return (int)res;

    }

    synchronized void incrementPosition(){
        position++;
        position%=points;
    }

    synchronized int getPosition(){
        return position;
    }

}