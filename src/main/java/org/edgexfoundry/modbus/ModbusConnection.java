/*******************************************************************************
 * Copyright 2016-2017 Dell Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * @microservice:  device-modbus
 * @author: Anantha Boyapalle, Dell
 * @version: 1.0.0
 *******************************************************************************/
package org.edgexfoundry.modbus;

import java.net.InetAddress;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.bind.DatatypeConverter;

import com.ghgande.j2mod.modbus.procimg.Register;
import com.ghgande.j2mod.modbus.procimg.SimpleInputRegister;
import org.edgexfoundry.domain.ModbusAttribute;
import org.edgexfoundry.domain.ModbusDevice;
import org.edgexfoundry.domain.ModbusObject;
import org.edgexfoundry.domain.ModbusValueType;
import org.edgexfoundry.domain.PrimaryTable;
import org.edgexfoundry.domain.meta.Addressable;
import org.edgexfoundry.domain.meta.PropertyValue;
import org.edgexfoundry.domain.meta.Protocol;
import org.edgexfoundry.exception.BadCommandRequestException;
import org.edgexfoundry.exception.controller.DataValidationException;
import org.edgexfoundry.support.logging.client.EdgeXLogger;
import org.edgexfoundry.support.logging.client.EdgeXLoggerFactory;

import com.ghgande.j2mod.modbus.ModbusIOException;
import com.ghgande.j2mod.modbus.io.ModbusSerialTransaction;
import com.ghgande.j2mod.modbus.io.ModbusTCPTransaction;
import com.ghgande.j2mod.modbus.io.ModbusTransaction;
import com.ghgande.j2mod.modbus.msg.ModbusRequest;
import com.ghgande.j2mod.modbus.msg.ModbusResponse;
import com.ghgande.j2mod.modbus.msg.ReadCoilsRequest;
import com.ghgande.j2mod.modbus.msg.ReadInputDiscretesRequest;
import com.ghgande.j2mod.modbus.msg.ReadInputRegistersRequest;
import com.ghgande.j2mod.modbus.msg.ReadMultipleRegistersRequest;
import com.ghgande.j2mod.modbus.msg.WriteMultipleCoilsRequest;
import com.ghgande.j2mod.modbus.msg.WriteMultipleRegistersRequest;
import com.ghgande.j2mod.modbus.net.SerialConnection;
import com.ghgande.j2mod.modbus.net.TCPMasterConnection;
import com.ghgande.j2mod.modbus.util.ModbusUtil;
import com.ghgande.j2mod.modbus.util.SerialParameters;

public class ModbusConnection {

	private static ConcurrentHashMap<String, Object> connections;
	private final static EdgeXLogger logger = EdgeXLoggerFactory.getEdgeXLogger(ModbusConnection.class);

	public ModbusConnection() {
		connections = new ConcurrentHashMap<String, Object>();
	}

	public Object getModbusConnection(Addressable addressable) {
		Object connection = null;
		if (connections.containsKey(addressable.getBaseURL())) {
			connection = connections.get(addressable.getBaseURL());
		} else {
			if (addressable.getProtocol() == Protocol.HTTP || addressable.getProtocol() == Protocol.TCP) {
				logger.info("creating TCP connection");
				connection = createTCPConnection(addressable);
			} else /* if(addressable.getProtocol() == Protocol.OTHER) */ {
				connection = createSerialConnection(addressable);
			}
			connections.put(addressable.getBaseURL(), connection);
		}
		return connection;
	}

	private Object createSerialConnection(Addressable addressable) {
		SerialConnection con = null;
		try {
			SerialParameters params = new SerialParameters();
			String address = addressable.getAddress();
			String[] serialParams = address.split(",");
			if (serialParams.length > 0) {
				if (serialParams[0] != null) {
					params.setPortName(serialParams[0].trim());
					logger.info("Port:" + serialParams[0].trim());
				}
				if (serialParams[1] != null) {
					params.setBaudRate(Integer.parseInt(serialParams[1].trim()));
					logger.info("BaudRate:" + serialParams[1].trim());
				}
				if (serialParams[2] != null) {

					params.setDatabits(Integer.parseInt(serialParams[2].trim()));
					logger.info("Data Bits:" + serialParams[2].trim());

				}
				if (serialParams[3] != null) {
					params.setStopbits(Integer.parseInt(serialParams[3].trim()));
					logger.info("Stop Bitse:" + serialParams[3].trim());

				}
				if (serialParams[4] != null) {
					params.setParity(Integer.parseInt(serialParams[4].trim()));
					logger.info("Parity:" + serialParams[4].trim());

				}

				params.setEncoding("rtu");
				params.setEcho(false);
			}
			con = new SerialConnection(params);
			// con.setTimeout(100); 100ms timeout is too short  
			// con.open();
			logger.info("Created Modbus RTU Connection");
		} catch (Exception e) {
			logger.debug(e.getMessage(), e);
			logger.error("Exception in creating Serial connection:" + e.getMessage());
		}
		return con;
	}

	private Object createTCPConnection(Addressable addressable) {

		TCPMasterConnection con = null;
		try {
			InetAddress addr = InetAddress.getByName(addressable.getAddress());
			con = new TCPMasterConnection(addr);
			con.setPort(addressable.getPort());
			// con.connect();
			logger.info("Created Modbus TCP Connection");
		} catch (Exception e) {
			logger.debug(e.getMessage(), e);
			logger.error("Exception in creating TCP connection:" + e);
		}
		return con;
	}

	public String getValue(Object connection, Addressable addressable, ModbusObject object, ModbusDevice device,
			int retryCount) {
		String result = "";

		int unitId = Integer.valueOf(addressable.getPath());

		PrimaryTable primaryTable = this.getPrimaryTable(object);

		ModbusValueType propertyValueType = this.getModbusValueType(object);

		int referenceAddress = this.getReferenceAddress(object, device);

		// ReadMultipleRegistersRequest req = new
		// ReadMultipleRegistersRequest(baseAddress + startedAddress, 1);
		ModbusRequest req = this.prepareReadingRequest(primaryTable, propertyValueType, referenceAddress);
		req.setUnitID(unitId);

		try {
			ModbusTransaction transaction = this.createModbusTransaction(connection, req);
			transaction.execute();

			ModbusRequest request = transaction.getRequest();
			ModbusResponse response = transaction.getResponse();

			logger.debug("Request (Hex) : " + request.getHexMessage());
			logger.debug("Response(Hex) : " + response.getHexMessage());

			byte[] dataBytes = this.fetchDataBytes(response);

			result = this.translateResponseDataBytes(propertyValueType, object, dataBytes);

		} catch (ModbusIOException ioe) {
			retryCount++;
			if (retryCount < 3) {
				logger.warn("Cannot get the value:" + ioe.getMessage() + ",count:" + retryCount);
				getValue(connection, addressable, object, device, retryCount);
			} else {
				logger.debug(ioe.getMessage(), ioe);
				throw new BadCommandRequestException(ioe.getMessage());
			}

		} catch (Exception e) {
			logger.debug(e.getMessage(), e);
			logger.error("General Exception e:" + e.getMessage());
			throw new BadCommandRequestException(e.getMessage());
		} finally {
			if (connection instanceof TCPMasterConnection) {
				((TCPMasterConnection) connection).close();
			} else if (connection instanceof SerialConnection) {
				((SerialConnection) connection).close();
			}
		}

		return result;
	}

	private ModbusValueType getModbusValueType(ModbusObject object) {
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

	private PrimaryTable getPrimaryTable(ModbusObject object) {
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

	private ModbusRequest prepareReadingRequest(PrimaryTable primaryTable, ModbusValueType valueType,
			int referenceAddress) {
		ModbusRequest modbusRequest = null;
		switch (primaryTable) {
		case DISCRETES_INPUT:
			modbusRequest = new ReadInputDiscretesRequest(referenceAddress, valueType.getLength());
			break;
		case COILS:
			modbusRequest = new ReadCoilsRequest(referenceAddress, valueType.getLength());
			break;
		case INPUT_REGISTERS:
			modbusRequest = new ReadInputRegistersRequest(referenceAddress, valueType.getLength());
			break;
		case HOLDING_REGISTERS:
			modbusRequest = new ReadMultipleRegistersRequest(referenceAddress, valueType.getLength());
			break;
		default:

		}
		logger.debug(String.format("[ Function code : %s ][ starting address : %s ]", modbusRequest.getFunctionCode(),
				referenceAddress));
		return modbusRequest;
	}

	private ModbusTransaction createModbusTransaction(Object connection, ModbusRequest req) throws Exception {
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
				con.open();
			}
			transaction = new ModbusSerialTransaction(con);
		}
		transaction.setRequest(req);
		return transaction;
	}

	private byte[] fetchDataBytes(ModbusResponse response) {
		byte[] responseData = response.getMessage();
		byte[] dataBytes = new byte[responseData.length - 1];

		for (int i = 0; i < responseData.length; i++) {
			if (i == 0) {
				logger.debug("The number of data bytes : " + responseData[i]);
			} else {
				logger.debug("Data byte -> " + responseData[i]);
				dataBytes[i - 1] = responseData[i];
			}
		}
		return dataBytes;
	}

	private String translateResponseDataBytes(ModbusValueType valueType, ModbusObject object, byte[] dataBytes) {
		PropertyValue propertyValue = object.getProperties().getValue();
		ModbusAttribute attributes = object.getAttributes();
		byte[] newDataBytes = dataBytes;
		switch (valueType) {
		case INT16:
			if (propertyValue.getSigned()) {
				return Short.toString(ModbusUtil.registerToShort(dataBytes));
			} else {
				return Integer.toString(ModbusUtil.registerToUnsignedShort(dataBytes));
			}
		case INT32:
			newDataBytes = sortLongByteForINT32(dataBytes);
			newDataBytes = swap32BitDataBytes(attributes, newDataBytes);
			return Integer.toString(ModbusUtil.registersToInt(newDataBytes));
		case INT64:
			return Long.toString(ModbusUtil.registersToLong(dataBytes));
		case FLOAT32:
			newDataBytes = swap32BitDataBytes(attributes, dataBytes);
			return Float.toString(ModbusUtil.registersToFloat(newDataBytes));
		case FLOAT64:
			return Double.toString(ModbusUtil.registersToDouble(dataBytes));
		case BOOLEAN:
			return Integer.toString(((Byte)dataBytes[0]).intValue());
		default:
			throw new DataValidationException("Mismatch property value type");
		}
	}

	private byte[] sortLongByteForINT32(byte[] dataBytes) {
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
	
	private byte[] swap32BitDataBytes(ModbusAttribute attributes, byte[] newDataBytes) {
		if (attributes.isByteSwap()) {
			newDataBytes = this.swapByteFor32Bit(newDataBytes);
		}
		if (attributes.isWordSwap()){
			newDataBytes = this.swapWordFor32Bit(newDataBytes);
		}
		return newDataBytes;
	}
	
	private byte[] swapByteFor32Bit(byte[] dataBytes) {
		byte[] newDataBytes = new byte[4];
		if (dataBytes.length >= 4) {
			newDataBytes[0] = dataBytes[1];
			newDataBytes[1] = dataBytes[0];
			newDataBytes[2] = dataBytes[3];
			newDataBytes[3] = dataBytes[2];
		}
		return newDataBytes;
	}
	
	private byte[] swapWordFor32Bit(byte[] dataBytes) {
		byte[] newDataBytes = new byte[4];
		if (dataBytes.length >= 4) {
			newDataBytes[0] = dataBytes[2];
			newDataBytes[1] = dataBytes[3];
			newDataBytes[2] = dataBytes[0];
			newDataBytes[3] = dataBytes[1];
		}
		return newDataBytes;
	}

	public String setValue(Object connection, Addressable addressable, ModbusObject object, String value,
			ModbusDevice device, int retryCount) {
		PrimaryTable primaryTable = this.getPrimaryTable(object);
		ModbusValueType propertyValueType = this.getModbusValueType(object);
		String result = "";
		String scaledValue = "";
		Register[] registers = null ;
		if (value != null) {
			float scale = Float.parseFloat(object.getProperties().getValue().getScale());
			Float newValue = (Integer.parseInt(value) / scale);

			scaledValue = newValue.intValue() + "";
			byte[] requestData = this.translateResponseDataBytes(propertyValueType,object,newValue);
			registers = this.prepareRegisters(propertyValueType,requestData);
		}


		logger.info("Setting value here scale:" + object.getProperties().getValue().getScale() + ", property:"
				+ object.getName() + "Value:" + value);


		int referenceAddress = this.getReferenceAddress(object, device);

		ModbusRequest req = this.prepareWritingRequest(primaryTable,propertyValueType,referenceAddress);

		if(req instanceof WriteMultipleRegistersRequest){
			((WriteMultipleRegistersRequest) req).setRegisters(registers);
		}else if(req instanceof WriteMultipleCoilsRequest){
//			((WriteMultipleCoilsRequest) req).setCoils(registers);
		}

		try {
			ModbusTransaction transaction = this.createModbusTransaction(connection,req);
			transaction.execute();

			ModbusRequest request = transaction.getRequest();
			ModbusResponse response = transaction.getResponse();

			logger.debug("Request (Hex) : " + request.getHexMessage());
			logger.debug("Response(Hex) : " + response.getHexMessage());

			byte[] dataBytes = this.fetchDataBytes(response);

			result = response.getHexMessage();
			DatatypeConverter.parseHexBinary(result.replaceAll(" ", ""));
			logger.info("After setting value:" + result);
			logger.info("After setting value:" + ModbusUtil.registersToInt(response.getMessage()));
			result = scaledValue;

		}catch (ModbusIOException ioe) {
			retryCount++;
			if (retryCount < 3) {
				logger.error("Cannot set the value:" + ioe.getMessage() + ",count:" + retryCount);
				setValue(connection, addressable, object, value, device, retryCount);
			} else {
				throw new BadCommandRequestException(ioe.getMessage());
			}
		} catch (Exception e) {
			logger.debug(e.getMessage(), e);
			logger.error("Cannot set the value general Exception:" + e.getMessage());
			throw new BadCommandRequestException(e.getMessage());
		} finally {
			if (connection instanceof TCPMasterConnection) {
				((TCPMasterConnection) connection).close();
			} else if (connection instanceof SerialConnection) {
				((SerialConnection) connection).close();
			}
		}

		return result;
	}

	private int getReferenceAddress(ModbusObject object, ModbusDevice device) {
		int startingAddress = Integer.parseInt(object.getAttributes().getStartingAddress()) - 1;
		int baseAddress = 0;
		if (device.getLocation() != null && device.getLocation().getBaseAddress() != null) {
			baseAddress = device.getLocation().getBaseAddress();
		}

		return baseAddress + startingAddress;
	}

	private byte[] translateResponseDataBytes(ModbusValueType valueType, ModbusObject object, Float value ) {
		PropertyValue propertyValue = object.getProperties().getValue();
		ModbusAttribute attributes = object.getAttributes();
		byte[] requestDataBytes;
		switch (valueType) {
			case INT16:
				if (propertyValue.getSigned()) {
					return ModbusUtil.shortToRegister(value.shortValue());
				} else {
					return ModbusUtil.unsignedShortToRegister(value.shortValue());
				}
			case INT32:
				requestDataBytes = ModbusUtil.intToRegisters(value.intValue());
				requestDataBytes = sortLongByteForINT32(requestDataBytes);
				requestDataBytes = swap32BitDataBytes(attributes, requestDataBytes);
				return requestDataBytes;
			case INT64:
				return ModbusUtil.longToRegisters(value.longValue());
			case FLOAT32:
				requestDataBytes = ModbusUtil.floatToRegisters(value);
				requestDataBytes = swap32BitDataBytes(attributes, requestDataBytes);
				return requestDataBytes;
			case FLOAT64:
				return ModbusUtil.doubleToRegisters(value.doubleValue());
			default:
				throw new DataValidationException("Mismatch property value type");
		}
	}

	private Register[] prepareRegisters(ModbusValueType valueType, byte[] requestData ) {
		Register[] registers = new Register[valueType.getLength()];
		for(int i =0 ; i< valueType.getLength() ; i++ ){
			registers[i] = new SimpleInputRegister(requestData[i*2],requestData[i*2+1]);
		}
		return registers;
	}

	private ModbusRequest prepareWritingRequest(PrimaryTable primaryTable, ModbusValueType valueType,
												int referenceAddress) {
		ModbusRequest modbusRequest = null;
		switch (primaryTable) {
			case COILS:
				modbusRequest = new WriteMultipleCoilsRequest(referenceAddress, valueType.getLength());
				break;
			case HOLDING_REGISTERS:
				WriteMultipleRegistersRequest request = new WriteMultipleRegistersRequest();
				request.setReference(referenceAddress);
				request.setDataLength(valueType.getLength());
				modbusRequest = request;
				break;
			default:

		}
		logger.debug(String.format("[ Function code : %s ][ starting address : %s ]", modbusRequest.getFunctionCode(),
				referenceAddress));
		return modbusRequest;
	}
}
