#include "BluetoothSerial.h"

BluetoothSerial SerialBT;

#define ECG_INPUT 34

//Timing variable
unsigned long lastMeasure = 0;

short ECGValue = 0;

void setup(){
  SerialBT.begin("ESP-ECG");
  lastMeasure = millis();
}


void loop(){

  if(millis() - 2 > lastMeasure){
    ECGValue = analogRead(ECG_INPUT);
    SerialBT.write(highByte(ECGValue));
    SerialBT.write(lowByte(ECGValue));
    lastMeasure = millis();
  }
}
