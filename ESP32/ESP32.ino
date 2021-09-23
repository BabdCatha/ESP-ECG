#include "BluetoothSerial.h"

BluetoothSerial SerialBT;

#define ECG_INPUT 34
short test = 0;

//Timing variable
unsigned long lastMeasure = 0;

short ECGValue = 0;

void setup(){
  //Serial.begin(115200);
  SerialBT.begin("ESP-ECG");
  lastMeasure = millis();
}


void loop(){

  if(millis() - 2 > lastMeasure){
    ECGValue = analogRead(ECG_INPUT);
    //SerialBT.println(ECGValue);
    //SerialBT.write(ECGValue);
    SerialBT.write(highByte(ECGValue));
    SerialBT.write(lowByte(ECGValue));
    //SerialBT.write(10); //delimiter
    lastMeasure = millis();
  }
}
