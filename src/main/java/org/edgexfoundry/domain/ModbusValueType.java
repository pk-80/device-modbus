package org.edgexfoundry.domain;

public enum ModbusValueType {
    FLOAT32(2), FLOAT64(4), INT16(1), INT32(2), INT64(4) , BOOLEAN(1) ;

    int length;

    ModbusValueType(int length) {
        this.length = length;
    }

    public int getLength() {
        return length;
    }
}
