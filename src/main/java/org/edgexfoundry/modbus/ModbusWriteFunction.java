package org.edgexfoundry.modbus;

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
import com.ghgande.j2mod.modbus.msg.WriteMultipleCoilsRequest;
import com.ghgande.j2mod.modbus.msg.WriteMultipleRegistersRequest;
import com.ghgande.j2mod.modbus.procimg.Register;
import com.ghgande.j2mod.modbus.procimg.SimpleInputRegister;
import com.ghgande.j2mod.modbus.util.ModbusUtil;

@Repository
@Scope(BeanDefinition.SCOPE_SINGLETON)
class ModbusWriteFunction extends ModbusFunction {

	private final static EdgeXLogger logger = EdgeXLoggerFactory.getEdgeXLogger(ModbusWriteFunction.class);
	
	String writeValue(Object connection, Addressable addressable, ModbusObject object, String value,
			ModbusDevice device, int retryCount) {
		PrimaryTable primaryTable = this.getPrimaryTable(object);
		ModbusValueType propertyValueType = this.getModbusValueType(object);
		Register[] registers = null;
		if (value != null) {
			byte[] requestData = this.prepareWritingDataBytes(propertyValueType, object, value);
			registers = this.prepareRegisters(propertyValueType, requestData);
		} else {
			throw new DataValidationException("Setting value- property:" + object.getName() + ", but value is null");
		}

		logger.info("Setting value- property:" + object.getName() + ", Value:" + value);

		int startingAddress = this.getReferencedAddress(object, device);

		ModbusRequest req = this.prepareWritingRequest(primaryTable, propertyValueType, startingAddress);

		if (req instanceof WriteMultipleRegistersRequest) {
			((WriteMultipleRegistersRequest) req).setRegisters(registers);
		} else if (req instanceof WriteMultipleCoilsRequest) {
			boolean coilStatus = registers[0].getValue() > 0 ? true : false;
			((WriteMultipleCoilsRequest) req).setCoilStatus(0, coilStatus);
		}

		try {
			ModbusTransaction transaction = this.createModbusTransaction(connection, req);
			transaction.execute();

			ModbusRequest request = transaction.getRequest();
			ModbusResponse response = transaction.getResponse();
			logger.debug("Request (Hex) : " + request.getHexMessage());
			logger.debug("Response(Hex) : " + response.getHexMessage());
		} catch (ModbusIOException ioe) {
			retryCount++;
			if (retryCount < 3) {
				logger.error("Cannot set the value:" + ioe.getMessage() + ",count:" + retryCount);
				return writeValue(connection, addressable, object, value, device, retryCount);
			} else {
				throw new BadCommandRequestException(ioe.getMessage());
			}
		} catch (Exception e) {
			logger.debug(e.getMessage(), e);
			logger.error("Cannot set the value general Exception:" + e.getMessage());
			throw new BadCommandRequestException(e.getMessage());
		} finally {
			super.closeConnection(connection);
		}

		return value;
	}
	
	private byte[] prepareWritingDataBytes(ModbusValueType valueType, ModbusObject object, String value) {
		PropertyValue propertyValue = object.getProperties().getValue();
		ModbusAttribute attributes = object.getAttributes();
		byte[] requestDataBytes;
		switch (valueType) {
		case INT16:
			value = stripDecimal(value);
			if (propertyValue.getSigned()) {
				return ModbusUtil.shortToRegister(Short.parseShort(value));
			} else {
				return ModbusUtil.unsignedShortToRegister(Short.parseShort(value));
			}
		case INT32:
			value = stripDecimal(value);
			requestDataBytes = ModbusUtil.intToRegisters(Integer.parseInt(value));
			requestDataBytes = sortLongByteForINT32(requestDataBytes);
			requestDataBytes = swap32BitDataBytes(attributes, requestDataBytes);
			return requestDataBytes;
		case INT64:
			value = stripDecimal(value);
			return ModbusUtil.longToRegisters(Long.parseLong(value));
		case FLOAT32:
			requestDataBytes = ModbusUtil.floatToRegisters(Float.parseFloat(value));
			requestDataBytes = swap32BitDataBytes(attributes, requestDataBytes);
			return requestDataBytes;
		case FLOAT64:
			return ModbusUtil.doubleToRegisters(Double.parseDouble(value));
		case BOOLEAN:
			return new byte[] { 0, Byte.parseByte(value) };
		default:
			throw new DataValidationException("Mismatch property value type");
		}
	}

	private String stripDecimal(String s) {
		if (s.contains(".")) {
			s = s.substring(0, s.indexOf("."));
		}
		return s;
	}
	
	private Register[] prepareRegisters(ModbusValueType valueType, byte[] requestData) {
		Register[] registers = new Register[valueType.getLength()];
		for (int i = 0; i < valueType.getLength(); i++) {
			registers[i] = new SimpleInputRegister(requestData[i * 2], requestData[i * 2 + 1]);
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
