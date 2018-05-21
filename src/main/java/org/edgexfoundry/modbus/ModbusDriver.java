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

import java.util.List;
import java.util.Map;

import org.edgexfoundry.data.DeviceStore;
import org.edgexfoundry.data.ObjectStore;
import org.edgexfoundry.data.ProfileStore;
import org.edgexfoundry.domain.ModbusDevice;
import org.edgexfoundry.domain.ModbusObject;
import org.edgexfoundry.domain.ScanList;
import org.edgexfoundry.domain.meta.Addressable;
import org.edgexfoundry.domain.meta.ResourceOperation;
import org.edgexfoundry.exception.controller.ServiceException;
import org.edgexfoundry.handler.ModbusHandler;
import org.edgexfoundry.support.logging.client.EdgeXLogger;
import org.edgexfoundry.support.logging.client.EdgeXLoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ModbusDriver {

	private final static EdgeXLogger logger = EdgeXLoggerFactory.getEdgeXLogger(ModbusDriver.class);

	@Autowired
	ProfileStore profiles;

	@Autowired
	DeviceStore devices;

	@Autowired
	ObjectStore objectCache;

	@Autowired
	ModbusHandler handler;

	@Autowired
	ModbusConnection modbusConInstance;

	public ScanList discover() {
		ScanList scan = new ScanList();
		return scan;
	}

	// operation is get or set
	// Device to be written to
	// Modbus Object to be written to
	// value is string to be written or null
	public void process(ResourceOperation operation, ModbusDevice device, ModbusObject object, String value,
			String transactionId, String opId) {

		// TODO 2: [Optional] Modify this processCommand call to pass any
		// additional
		// required metadata from the profile to the driver stack
		try {
			List<String> deviceResourceReferences = object.getAttributes().getDeviceResourceReferences();
			if (deviceResourceReferences == null || deviceResourceReferences.isEmpty()) {
				String result = processCommand(operation.getOperation(), device.getAddressable(), object, value,
						device);
				logger.info("Putting result:" + result);
				objectCache.putReadings(device, operation, result);
			} else {
				Map<String, String> result = processCommandWithMultipleValues(operation.getOperation(),
						device.getAddressable(), object, null, device);
				logger.info("Putting result:" + result);
				objectCache.putReadings(device, operation, result);
			}
			
			handler.completeTransaction(transactionId, opId, objectCache.getResponses(device, operation));
		} catch (Exception e) {
			logger.error("ModbusDriver process Exception e:" + e.getMessage());
			logger.debug(e.getMessage(), e);
			handler.failTransaction(transactionId, new ServiceException(e));
			Thread.currentThread().interrupt();
		}

	}

	// Modify this function as needed to pass necessary metadata from the device
	// and
	// its profile to the driver interface
	public String processCommand(String operation, Addressable addressable, ModbusObject object, String value,
			ModbusDevice device) {
		logger.info("ProcessCommand: " + operation + ", addressable:" + addressable + ", attributes:"
				+ object.getAttributes().toString() + ", value: " + value);
		String result = "";
		Object connection = modbusConInstance.getModbusConnection(addressable);
		if (operation.toLowerCase().equals("get")) {
			logger.info("Getting value");
			result = modbusConInstance.getValue(connection, addressable, object, device, 0);
			logger.info("Getting value result finally:" + result);
		} else {
			logger.info("Setting value");
			result = modbusConInstance.setValue(connection, addressable, object, value, device, 0);
		}
		logger.info("Returning result:" + result);
		return result;
	}

	public Map<String, String> processCommandWithMultipleValues(String operation, Addressable addressable,
			ModbusObject object, Map<String, String> values, ModbusDevice device) {
		logger.info("ProcessCommand: " + operation + ", addressable:" + addressable + ", attributes:"
				+ object.getAttributes().toString());
		Map<String, String> result;
		Object connection = modbusConInstance.getModbusConnection(addressable);
		if (operation.toLowerCase().equals("get")) {
			logger.info("Getting value");
			result = modbusConInstance.getValues(connection, addressable, object, device, 0);
			logger.info("Getting value result finally:" + result);
		} else {
			logger.info("Setting value");
			result = modbusConInstance.setValues(connection, addressable, object, values, device, 0);
		}
		logger.info("Returning result:" + result);
		return result;
	}

	public void initialize() {

	}

	public void disconnectDevice(Addressable address) {

	}

	@SuppressWarnings("unused")
	private void receive() {
		// TODO 7: [Optional] Fill with your own implementation for handling
		// asynchronous data from the driver layer to the device service
		ModbusDevice device = null;
		String result = "";
		ResourceOperation operation = null;

		objectCache.putReadings(device, operation, result);
	}

}
