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
import java.util.HashMap;

import javax.xml.bind.DatatypeConverter;

import com.ghgande.j2mod.modbus.io.ModbusTransaction;
import com.ghgande.j2mod.modbus.msg.*;
import com.ghgande.j2mod.modbus.util.ModbusUtil;
import org.edgexfoundry.domain.ModbusDevice;
import org.edgexfoundry.domain.ModbusObject;
import org.edgexfoundry.domain.PrimaryTable;
import org.edgexfoundry.domain.PropertyValueType;
import org.edgexfoundry.domain.meta.Addressable;
import org.edgexfoundry.domain.meta.Protocol;
import org.edgexfoundry.exception.BadCommandRequestException;
import org.edgexfoundry.support.logging.client.EdgeXLogger;
import org.edgexfoundry.support.logging.client.EdgeXLoggerFactory;

import com.ghgande.j2mod.modbus.ModbusIOException;
import com.ghgande.j2mod.modbus.io.ModbusSerialTransaction;
import com.ghgande.j2mod.modbus.io.ModbusTCPTransaction;
import com.ghgande.j2mod.modbus.net.SerialConnection;
import com.ghgande.j2mod.modbus.net.TCPMasterConnection;
import com.ghgande.j2mod.modbus.procimg.SimpleRegister;
import com.ghgande.j2mod.modbus.util.SerialParameters;

public class ModbusConnection {

	private static HashMap<String, Object> connections;
	private final static EdgeXLogger logger = EdgeXLoggerFactory.getEdgeXLogger(ModbusConnection.class);


	public ModbusConnection(){
		connections = new HashMap<String, Object>();
	}

	public Object getModbusConnection(Addressable addressable){
		Object connection = null;
		synchronized(connections) {
			if(connections.containsKey(addressable.getAddress())){
				connection = connections.get(addressable.getAddress());
			}
			else{
				if(addressable.getProtocol() == Protocol.HTTP){
					logger.info("creating TCP connection");
					connection = createTCPConnection(addressable);
				}
				else /*if(addressable.getProtocol() == Protocol.OTHER)*/{
					connection = createSerialConnection(addressable);
				}
				connections.put(addressable.getAddress(), connection);
			}
		}
		return connection;
	}

	private Object createSerialConnection(Addressable addressable) {
		SerialConnection con = null;
		try
		{
			SerialParameters params = new SerialParameters();
			String address = addressable.getAddress();
			String[] serialParams = address.split(",");
			if(serialParams.length > 0)
			{
				if(serialParams[0] != null){
					params.setPortName(serialParams[0].trim());
					logger.info("Port:" + serialParams[0].trim());
				}
				if(serialParams[1] != null){
					params.setBaudRate(Integer.parseInt(serialParams[1].trim()));
					logger.info("BaudRate:" + serialParams[1].trim());
				}
				if(serialParams[2] != null){

					params.setDatabits(Integer.parseInt(serialParams[2].trim()));
					logger.info("Data Bits:" + serialParams[2].trim());

				}
				if(serialParams[3] != null){
					params.setStopbits(Integer.parseInt(serialParams[3].trim()));
					logger.info("Stop Bitse:" + serialParams[3].trim());


				}
				if(serialParams[4] != null){
					params.setParity(Integer.parseInt(serialParams[4].trim()));
					logger.info("Parity:" + serialParams[4].trim());

				}


				params.setEncoding("rtu");
				params.setEcho(false);
			}
			con = new SerialConnection(params);
			con.setTimeout(100);
			con.open();

		}catch(Exception e){
			logger.error("Exception in creating Serial connection:" + e);
		}
		return con;
	}

	private Object createTCPConnection(Addressable addressable) {

		TCPMasterConnection con = null;
		try{
			InetAddress addr = 	InetAddress.getByName(addressable.getAddress());
			con = new TCPMasterConnection(addr);
			con.setPort(addressable.getPort());
			//con.connect();
			logger.info("Created TCP Connection");
		}catch(Exception e){
			logger.error("Exception in creating TCP connection:" + e);
		}
		return con;
	}

	public String getValue(Object connection, Addressable addressable, ModbusObject object, ModbusDevice device, int retryCount) {
		String result = "";

		int unitId = Integer.valueOf(addressable.getPath());
		PrimaryTable primaryTable = PrimaryTable.valueOf(object.getAttributes().getPrimaryTable());
		PropertyValueType propertyValueType = PropertyValueType.valueOf(object.getProperties().getValue().getType());

		int startedAddress = Integer.parseInt(object.getAttributes().getAddress()) - 1 ;
		int baseAddress = 0;
		if (device.getLocation()!=null && device.getLocation().getBaseAddress()!=null) {
			baseAddress = device.getLocation().getBaseAddress();
		}

//		ReadMultipleRegistersRequest req = new ReadMultipleRegistersRequest(baseAddress + startedAddress, 1);
		ModbusRequest req = this.prepareReadingRequest(primaryTable, propertyValueType, baseAddress + startedAddress);
		req.setUnitID(unitId);

		try {
			ModbusTransaction transaction = this.createModbusTransaction(connection, req);
			transaction.execute();

			ModbusRequest request = transaction.getRequest();
			ModbusResponse response = transaction.getResponse();

			logger.info("Request (Hex) : " + request.getHexMessage());
			logger.info("Response(Hex) : " + response.getHexMessage());

			byte[] dataBytes = this.fetchDataBytes(response);

			result = this.parseDataBytes(propertyValueType,dataBytes);

		}catch(ModbusIOException ioe){
			retryCount ++;
			if(retryCount < 3){
				logger.warn("Cannot get the value:" + ioe.getMessage() + ",count:" + retryCount);
				getValue(connection, addressable, object, device, retryCount);
			}
			else{
				throw new BadCommandRequestException(ioe.getMessage());
			}

		}catch(Exception e){
			logger.error("General Exception e:" + e.getMessage());
			throw new BadCommandRequestException(e.getMessage());
		}finally{
			if(connection instanceof TCPMasterConnection){
				((TCPMasterConnection) connection).close();
			}else if(connection instanceof SerialConnection){
				((SerialConnection) connection).close();
			}
		}

		return result;
	}

	private ModbusRequest prepareReadingRequest(PrimaryTable primaryTable, PropertyValueType propertyValueType, int startingAddress) {
		ModbusRequest modbusRequest = null ;
		switch (primaryTable) {
			case DISCRETES_INPUT:
				modbusRequest = new ReadInputDiscretesRequest(startingAddress, propertyValueType.getRegisterQuantity());
				break;
			case COILS:
				modbusRequest = new ReadCoilsRequest(startingAddress, propertyValueType.getRegisterQuantity());
				break;
			case INPUT_REGISTERS:
				modbusRequest = new ReadInputRegistersRequest(startingAddress, propertyValueType.getRegisterQuantity());
				break;
			case HOLDING_REGISTER:
				modbusRequest = new ReadMultipleRegistersRequest(startingAddress, propertyValueType.getRegisterQuantity());
				break;
		}
		System.out.println(String.format("【 Function code : %s 】【starting address : %s 】", modbusRequest.getFunctionCode(), startingAddress));
		return modbusRequest;
	}

	private ModbusTransaction createModbusTransaction(Object connection, ModbusRequest req) throws Exception {
		ModbusTransaction transaction = null ;
		if(connection instanceof TCPMasterConnection){
			TCPMasterConnection con = (TCPMasterConnection)connection;
			if(!con.isConnected()){
				con.connect();
			}
			transaction = new ModbusTCPTransaction(con);
		}else if(connection instanceof SerialConnection){
			req.setHeadless();
			SerialConnection con = (SerialConnection)connection;
			if(!con.isOpen()) {
				con.open();
			}
			transaction = new ModbusSerialTransaction(con);
		}
		transaction.setRequest(req);
		return transaction;
	}

	private byte[] fetchDataBytes(ModbusResponse response) {
		byte[] responseData = response.getMessage();
		byte[] dataBytes = new byte[responseData.length-1];

		for (int i = 0; i < responseData.length; i++) {
			if(i==0){
				logger.info("The number of data bytes : " + responseData[i]);
			}else{
				logger.info("Data byte -> " + responseData[i]);
				dataBytes[i - 1] = responseData[i];
			}
		}
		return dataBytes;
	}

	private String parseDataBytes(PropertyValueType propertyValueType , byte[] dataByte) {
		switch (propertyValueType){
			case INT16:
				return Short.toString(ModbusUtil.registerToShort(dataByte));
			case INT32:
				return Integer.toString(ModbusUtil.registersToInt(dataByte));
			case INT64:
				return Long.toString(ModbusUtil.registersToLong(dataByte));
			case FLOAT32:
				return Float.toString(ModbusUtil.registersToFloat(dataByte));
			case FLOAT64:
				return Double.toString(ModbusUtil.registersToDouble(dataByte));
			default:
				throw new RuntimeException("Mismatch property value type");
		}
	}

	public String setValue(Object connection, Addressable addressable, ModbusObject object, String value, ModbusDevice device, int retryCount) {
		String result = "";
		String scaledValue = "";
		if(value != null){
			float scale = Float.parseFloat(object.getProperties().getValue().getScale());
			Float newValue = (Integer.parseInt(value)/scale);

			scaledValue = newValue.intValue() + "" ;
		}
		
		int startedAddress = Integer.parseInt(object.getAttributes().getHoldingRegister());
		int baseAddress = 0;
		if (device.getLocation()!=null && device.getLocation().getBaseAddress()!=null) {
			baseAddress = device.getLocation().getBaseAddress();
		}
		
		ModbusRequest req = new WriteSingleRegisterRequest(baseAddress + startedAddress, new SimpleRegister(Integer.parseInt(scaledValue)));
		if(connection instanceof TCPMasterConnection){
			TCPMasterConnection con = (TCPMasterConnection)connection;
			logger.info("Setting value here scale:" + object.getProperties().getValue().getScale() + ", property:" + object.getName() + "Value:" + value);
			try{
				req.setUnitID(Integer.valueOf(addressable.getPath()));
				con.connect();
				ModbusTCPTransaction transaction = new ModbusTCPTransaction(con);
				transaction.setRequest(req);
				transaction.execute();
				result = transaction.getResponse().getHexMessage();
				DatatypeConverter.parseHexBinary(result.replaceAll(" ", ""));
				logger.info("After setting value:" + result);
				result = scaledValue;
			}catch(ModbusIOException ioe){

				retryCount ++;
				if(retryCount < 3){
					logger.error("Cannot set the value:" + ioe.getMessage() + ",count:" + retryCount);
					setValue(connection, addressable, object, value, device, retryCount);
				}
				else{

					throw new BadCommandRequestException(ioe.getMessage());
				}

			}
			catch(Exception e){
				logger.error("Cannot set the value general Exception:" + e.getMessage());
				throw new BadCommandRequestException(e.getMessage());
			}

			finally{
				//transaction = null;
				con.close();
			}
		}else if(connection instanceof SerialConnection){
			SerialConnection con = (SerialConnection)connection;
			logger.info("Setting value here scale:" + object.getProperties().getValue().getScale() + ", property:" + object.getName() + "Value:" + value);
			try{
				req.setUnitID(Integer.valueOf(addressable.getPath()));
				con.open();
				ModbusSerialTransaction transaction = new ModbusSerialTransaction(con);
				transaction.setRequest(req);
				transaction.execute();
				result = transaction.getResponse().getHexMessage();
				DatatypeConverter.parseHexBinary(result.replaceAll(" ", ""));
				logger.info("After setting value:" + result);
				result = scaledValue;
			}catch(ModbusIOException ioe){

				retryCount ++;
				if(retryCount < 3){
					logger.error("Cannot set the value:" + ioe.getMessage() + ",count:" + retryCount);
					setValue(connection, addressable, object, value, device, retryCount);
				}
				else{

					throw new BadCommandRequestException(ioe.getMessage());
				}

			}
			catch(Exception e){
				logger.error("Cannot set the value general Exception:" + e.getMessage());
				throw new BadCommandRequestException(e.getMessage());
			}

			finally{
				//transaction = null;
				con.close();
			}
		}

		return result;
	}
}
