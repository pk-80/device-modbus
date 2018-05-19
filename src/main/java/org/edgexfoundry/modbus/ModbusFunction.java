package org.edgexfoundry.modbus;

import org.edgexfoundry.data.ProfileStore;
import org.edgexfoundry.domain.ModbusAttribute;
import org.edgexfoundry.domain.ModbusDevice;
import org.edgexfoundry.domain.ModbusObject;
import org.edgexfoundry.domain.ModbusValueType;
import org.edgexfoundry.domain.PrimaryTable;
import org.edgexfoundry.exception.controller.DataValidationException;
import org.edgexfoundry.exception.controller.ServiceException;
import org.edgexfoundry.support.logging.client.EdgeXLogger;
import org.edgexfoundry.support.logging.client.EdgeXLoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import com.ghgande.j2mod.modbus.io.ModbusSerialTransaction;
import com.ghgande.j2mod.modbus.io.ModbusTCPTransaction;
import com.ghgande.j2mod.modbus.io.ModbusTransaction;
import com.ghgande.j2mod.modbus.msg.ModbusRequest;
import com.ghgande.j2mod.modbus.net.SerialConnection;
import com.ghgande.j2mod.modbus.net.TCPMasterConnection;

abstract class ModbusFunction {

	private final static EdgeXLogger logger = EdgeXLoggerFactory.getEdgeXLogger(ModbusFunction.class);

	@Autowired
	@Lazy
	protected ProfileStore profileStore;

	protected ModbusValueType getModbusValueType(ModbusObject object) {
		ModbusValueType propertyValueType;
		try {
			propertyValueType = ModbusValueType.valueOf(object.getProperties().getValue().getType());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			throw new DataValidationException(
					"Modbus Value Type definition error. Please identify FLOAT32, FLOAT64, INT16, INT32, or INT64");
		}
		return propertyValueType;
	}

	protected PrimaryTable getPrimaryTable(ModbusObject object) {
		PrimaryTable primaryTable;
		try {
			primaryTable = PrimaryTable.valueOf(object.getAttributes().getPrimaryTable());
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			throw new DataValidationException(
					"Modbus Primary Table definition error. Please identify DISCRETES_INPUT, COILS, INPUT_REGISTERS, or HOLDING_REGISTER");
		}
		return primaryTable;
	}

	protected ModbusTransaction createModbusTransaction(Object connection, ModbusRequest req) throws Exception {
		ModbusTransaction transaction = null;
		if (connection instanceof TCPMasterConnection) {
			TCPMasterConnection con = (TCPMasterConnection) connection;
			if (!con.isConnected()) {
				con.connect();
			}
			transaction = new ModbusTCPTransaction(con);
		} else if (connection instanceof SerialConnection) {
			req.setHeadless();
			SerialConnection con = (SerialConnection) connection;
			if (!con.isOpen()) {
				if (!con.open()) {
					throw new ServiceException(
							new IllegalStateException("Modbus RTU Connection cannot be opened: " + con.toString()));
				}
			}
			transaction = new ModbusSerialTransaction(con);
		}
		transaction.setRequest(req);
		return transaction;
	}

	protected byte[] sortLongByteForINT32(byte[] dataBytes) {
		if (dataBytes.length == 8) {
			byte[] newDataBytes = new byte[4];
			newDataBytes[0] = dataBytes[2];
			newDataBytes[1] = dataBytes[3];
			newDataBytes[2] = dataBytes[6];
			newDataBytes[3] = dataBytes[7];
			return newDataBytes;
		} else {
			return dataBytes;
		}
	}

	protected byte[] swap32BitDataBytes(ModbusAttribute attributes, byte[] newDataBytes) {
		if (attributes.isByteSwap()) {
			newDataBytes = this.swapByteFor32Bit(newDataBytes);
		}
		if (!attributes.isWordSwap()) {
			newDataBytes = this.swapWordFor32Bit(newDataBytes);
		}
		return newDataBytes;
	}

	protected byte[] swapByteFor32Bit(byte[] dataBytes) {
		byte[] newDataBytes = new byte[4];
		if (dataBytes.length >= 4) {
			newDataBytes[0] = dataBytes[1];
			newDataBytes[1] = dataBytes[0];
			newDataBytes[2] = dataBytes[3];
			newDataBytes[3] = dataBytes[2];
		}
		return newDataBytes;
	}

	protected byte[] swapWordFor32Bit(byte[] dataBytes) {
		byte[] newDataBytes = new byte[4];
		if (dataBytes.length >= 4) {
			newDataBytes[0] = dataBytes[2];
			newDataBytes[1] = dataBytes[3];
			newDataBytes[2] = dataBytes[0];
			newDataBytes[3] = dataBytes[1];
		}
		return newDataBytes;
	}

	protected int getReferencedAddress(ModbusObject object, ModbusDevice device) {
		int startingAddress = object.getAttributes().getStartingAddress() - 1;
		int baseAddress = 0;
		if (device.getLocation() != null && device.getLocation().getBaseAddress() != null) {
			baseAddress = device.getLocation().getBaseAddress();
		}

		return baseAddress + startingAddress;
	}

	protected void closeConnection(Object con) {
		if (con instanceof TCPMasterConnection) {
			TCPMasterConnection tcpCon = (TCPMasterConnection) con;
			if (tcpCon.isConnected()){
				tcpCon.close();
			}
		} else if (con instanceof SerialConnection) {
			SerialConnection serialCon = (SerialConnection) con;
			if (serialCon.isOpen()){
				serialCon.close();
			}
		}
	}

}
