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

public class MainActivity extends AppCompatActivity {

    static int points = 1000;

    LineChart graph;
    Description description;
    Entry[] dataList = new Entry[points];
    int[] inputs = new int[points];

    int numberOfCoefficients = 5;
    float[] coeffB = {0.00223489f, 0.00893957f, 0.01340935f, 0.00893957f, 0.00223489f};
    float[] coeffA = {1f, -2.69261099f, 2.86739911f, -1.40348467f, 0.26445482f};

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
        graph.setDescription(description);

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

                                if(b == delimiter) {
                                    byte[] encodedBytes = new byte[readBufferPosition];
                                    System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);

                                    final ByteBuffer bb = ByteBuffer.wrap(encodedBytes);
                                    bb.order(ByteOrder.BIG_ENDIAN);
                                    int data = 0;
                                    if(bb.remaining() == 2) {
                                        data = bb.getShort();
                                    }else{
                                        data = inputs[(((position-1)%points)+points)%points];
                                        //Log.e("ass", "ass");
                                    }

                                    readBufferPosition = 0;
                                    inputs[position] = data;
                                    handler.post(new Runnable() {
                                        public void run() {

                                            //Add the received data
                                            try {

                                                //Filtering the output
                                                float valueB = 0f;
                                                float valueA = 0f;
                                                for(int i = 1; i < numberOfCoefficients+1; i++){
                                                    valueB += coeffB[numberOfCoefficients-i] * inputs[(((position-i+1)%points)+points)%points];
                                                }
                                                for(int i = 1; i < numberOfCoefficients; i++){
                                                    valueA += coeffA[numberOfCoefficients-i] * dataList[(((position-i)%points)+points)%points].getY();
                                                }


                                                dataList[position] = new Entry(position, (valueB - valueA));
                                                //Log.e("value", String.valueOf((valueB - valueA)));
                                            }catch(Exception e){
                                                dataList[position] = new Entry(position, 10000);
                                            }
                                            position++;
                                            position%=points;

                                            LineDataSet dataSet = new LineDataSet(Arrays.asList(dataList.clone()), "ECG");
                                            dataSet.setColor(ColorTemplate.getHoloBlue());
                                            dataSet.setDrawCircles(false);

                                            LineData lineData = new LineData(dataSet);

                                            graph.setData(lineData);
                                            graph.invalidate();

                                        }
                                    });
                                }
                                else {
                                    readBuffer[readBufferPosition++] = b;
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

}