package org.edgexfoundry.domain;

public enum PropertyValueType {
    FLOAT32(2), FLOAT64(4), INT16(1), INT32(2), INT64(4);

    int registerQuantity;

    PropertyValueType(int registerQuantity) {
        this.registerQuantity = registerQuantity;
    }

    public int getRegisterQuantity() {
        return registerQuantity;
    }
}
