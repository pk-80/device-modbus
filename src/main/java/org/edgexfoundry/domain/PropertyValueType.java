package org.edgexfoundry.domain;

public enum PropertyValueType {
    FLOAT32(2), FLOAT64(4), UINT16(1), UINT32(2), UINT64(4);

    int registerQuantity;

    PropertyValueType(int registerQuantity) {
        this.registerQuantity = registerQuantity;
    }

    public int getRegisterQuantity() {
        return registerQuantity;
    }
}
