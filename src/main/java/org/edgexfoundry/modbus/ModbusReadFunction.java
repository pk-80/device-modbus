package org.edgexfoundry.modbus;

import java.util.LinkedHashMap;
import java.util.Map;

import org.edgexfoundry.domain.ModbusAttribute;
import org.edgexfoundry.domain.ModbusDevice;
import org.edgexfoundry.domain.ModbusObject;
import org.edgexfoundry.domain.ModbusValueType;
import org.edgexfoundry.domain.PrimaryTable;
import org.edgexfoundry.domain.meta.Addressable;
import org.edgexfoundry.domain.meta.PropertyValue;
import org.edgexfoundry.exception.BadCommandRequestException;
import org.edgexfoundry.exception.controller.DataValidationException;
import org.edgexfoundry.support.logging.client.EdgeXLogger;
import org.edgexfoundry.support.logging.client.EdgeXLoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Repository;

import com.ghgande.j2mod.modbus.ModbusIOException;
import com.ghgande.j2mod.modbus.io.ModbusTransaction;
import com.ghgande.j2mod.modbus.msg.ModbusRequest;
import com.ghgande.j2mod.modbus.msg.ModbusResponse;
import com.ghgande.j2mod.modbus.msg.ReadCoilsRequest;
import com.ghgande.j2mod.modbus.msg.ReadInputDiscretesRequest;
import com.ghgande.j2mod.modbus.msg.ReadInputRegistersRequest;
import com.ghgande.j2mod.modbus.msg.ReadMultipleRegistersRequest;
import com.ghgande.j2mod.modbus.util.ModbusUtil;

@Repository
@Scope(BeanDefinition.SCOPE_SINGLETON)
class ModbusReadFunction extends ModbusFunction {

	private final static EdgeXLogger logger = EdgeXLoggerFactory.getEdgeXLogger(ModbusReadFunction.class);

	String readValue(Object connection, Addressable addressable, ModbusObject object, ModbusDevice device,
			int retryCount) {
		String result = "";

		int unitId = Integer.valueOf(addressable.getPath());
		PrimaryTable primaryTable = super.getPrimaryTable(object);
		ModbusValueType propertyValueType = super.getModbusValueType(object);
		int startingAddress = super.getReferencedAddress(object, device);

		ModbusRequest req = this.prepareReadingRequest(primaryTable, startingAddress, propertyValueType.getLength());
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
				return this.readValue(connection, addressable, object, device, retryCount);
			} else {
				logger.debug(ioe.getMessage(), ioe);
				throw new BadCommandRequestException(ioe.getMessage());
			}

		} catch (Exception e) {
			logger.debug(e.getMessage(), e);
			logger.error("General Exception e:" + e.getMessage());
			throw new BadCommandRequestException(e.getMessage());
		} finally {
			super.closeConnection(connection);
		}

		return result;
	}

	Map<String, String> readValues(Object connection, Addressable addressable, ModbusObject object, ModbusDevice device,
			int retryCount) {
		Map<String, String> result = new LinkedHashMap<>();

		ModbusAttribute mAttr = object.getAttributes();
		if (mAttr.getDeviceResourceReferences().size() == 0 || mAttr.getLength() == 0) {
			throw new DataValidationException(
					"Device resource references is empty or Modbus reading block length is 0. " + mAttr.toString());
		}

		int unitId = Integer.valueOf(addressable.getPath());
		PrimaryTable primaryTable = super.getPrimaryTable(object);
		int startingAddress = super.getReferencedAddress(object, device);

		ModbusRequest req = this.prepareReadingRequest(primaryTable, startingAddress, mAttr.getLength());
		req.setUnitID(unitId);

		byte[] dataBytes;
		try {
			ModbusTransaction transaction = super.createModbusTransaction(connection, req);
			transaction.execute();

			ModbusRequest request = transaction.getRequest();
			ModbusResponse response = transaction.getResponse();

			logger.debug("Request (Hex) : " + request.getHexMessage());
			logger.debug("Response(Hex) : " + response.getHexMessage());
			dataBytes = this.fetchDataBytes(response);
		} catch (ModbusIOException ioe) {
			retryCount++;
			if (retryCount < 3) {
				logger.warn("Cannot get the value:" + ioe.getMessage() + ",count:" + retryCount);
				return this.readValues(connection, addressable, object, device, retryCount);
			} else {
				logger.debug(ioe.getMessage(), ioe);
				throw new BadCommandRequestException(ioe.getMessage());
			}

		} catch (Exception e) {
			logger.debug(e.getMessage(), e);
			logger.error("General Exception e:" + e.getMessage());
			throw new BadCommandRequestException(e.getMessage());
		} finally {
			super.closeConnection(connection);
		}

		Map<String, ModbusObject> modbusObjects = profileStore.getObjects().get(device.getName());
		for (String modbusObjectName : mAttr.getDeviceResourceReferences()) {
			ModbusObject modbusObject = modbusObjects.get(modbusObjectName);
			int relativeStartingAddress = 2 * (modbusObject.getAttributes().getStartingAddress()
					- mAttr.getStartingAddress());
			ModbusValueType valueType = super.getModbusValueType(modbusObject);
			if (relativeStartingAddress + valueType.getLength() > dataBytes.length) {
				logger.error(String.format("%s is not under the scope of %s", modbusObjectName, object.getName()));
				continue;
			}

			logger.debug(String.format("Reading %s inside %s, relative starting addreSs is %d, data type is %s",
					modbusObjectName, object.getName(), relativeStartingAddress, valueType.toString()));
			byte[] thisObjectDataBytes = this.extractDataBytes(dataBytes, relativeStartingAddress, valueType);
			String readingValue = this.translateResponseDataBytes(valueType, modbusObject, thisObjectDataBytes);
			result.put(modbusObjectName, readingValue);
		}

		return result;
	}

	private ModbusRequest prepareReadingRequest(PrimaryTable primaryTable, int startingAddress, int length) {
		ModbusRequest modbusRequest = null;
		switch (primaryTable) {
		case DISCRETES_INPUT:
			modbusRequest = new ReadInputDiscretesRequest(startingAddress, length);
			break;
		case COILS:
			modbusRequest = new ReadCoilsRequest(startingAddress, length);
			break;
		case INPUT_REGISTERS:
			modbusRequest = new ReadInputRegistersRequest(startingAddress, length);
			break;
		case HOLDING_REGISTERS:
			modbusRequest = new ReadMultipleRegistersRequest(startingAddress, length);
			break;
		default:

		}
		logger.debug(String.format("[ Function code : %s ][ starting address : %s ]", modbusRequest.getFunctionCode(),
				startingAddress));
		return modbusRequest;
	}

	private byte[] fetchDataBytes(ModbusResponse response) {
		byte[] responseData = response.getMessage();
		byte[] dataBytes = new byte[responseData.length - 1];

		for (int i = 0; i < responseData.length; i++) {
			if (i == 0) {
				logger.debug("The number of data bytes : " + ModbusUtil.unsignedByteToInt(responseData[i]));
			} else {
				logger.debug("Data byte -> " + ModbusUtil.unsignedByteToInt(responseData[i]));
				dataBytes[i - 1] = responseData[i];
			}
		}
		return dataBytes;
	}

	private String translateResponseDataBytes(ModbusValueType valueType, ModbusObject object, byte[] dataBytes) {
		PropertyValue propertyValue = object.getProperties().getValue();
		ModbusAttribute attributes = object.getAttributes();
		byte[] newDataBytes = dataBytes;
		logger.debug(String.format("translateResponseDataBytes with valueType: %s, and date bytes: %s",
				valueType.toString(), ModbusUtil.toHex(dataBytes)));

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
			return Byte.toString(dataBytes[0]);
		default:
			throw new DataValidationException("Mismatched property value type");
		}
	}

	private byte[] extractDataBytes(byte[] blockDataBytes, int startingIndex, ModbusValueType valueType) {
		byte[] result = new byte[valueType.getLength() * 2];
		for (int i = 0; i < result.length; i++) {
			logger.debug(String.format("Extracting %d address to new data byte index %d, the value is %x",
					startingIndex + i, i, blockDataBytes[startingIndex + i]));
			result[i] = blockDataBytes[startingIndex + i];
		}
		return result;
	}

}
