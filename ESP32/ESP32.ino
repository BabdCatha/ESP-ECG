#include "BluetoothSerial.h"

BluetoothSerial SerialBT;

void setup(){
  SerialBT.begin("ESP-ECG");
}


void loop(){
  SerialBT.println("message");
}
