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
package org.edgexfoundry.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.edgexfoundry.domain.ModbusObject;
import org.edgexfoundry.domain.core.Reading;
import org.edgexfoundry.domain.meta.Device;
import org.edgexfoundry.domain.meta.ResourceOperation;
import org.edgexfoundry.exception.controller.NotFoundException;
import org.edgexfoundry.handler.CoreDataMessageHandler;
import org.edgexfoundry.modbus.ObjectTransform;
import org.edgexfoundry.support.logging.client.EdgeXLogger;
import org.edgexfoundry.support.logging.client.EdgeXLoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.google.gson.JsonObject;

@Repository
public class ObjectStore {
	private final static EdgeXLogger logger = EdgeXLoggerFactory.getEdgeXLogger(ObjectStore.class);

	@Value("${data.transform:true}")
	private Boolean transformData;

	@Autowired
	private ProfileStore profiles;

	@Autowired
	private ObjectTransform objectTransform;

	@Autowired
	private CoreDataMessageHandler processor;

	@Value("${data.cache.size:1}")
	private int CACHE_SIZE;

	private Map<String, Map<String, List<String>>> objectCache = new HashMap<>();

	private Map<String, Map<String, List<Reading>>> responseCache = new HashMap<>();

	public Boolean getTransformData() {
		return transformData;
	}

	public void setTransformData(Boolean transform) {
		transformData = transform;
	}

	public void putReadings(Device device, ResourceOperation operation, String value) {
		if (value == null || value.equals("") || value.equals("{}"))
			return;

		List<ModbusObject> objectsList = createObjectsList(operation, device);
		Map<String, String> values = new HashMap<>();
		objectsList.stream().map(ModbusObject::getName).forEach(n -> values.put(n, value));

		executePutReadings(device, operation, values, objectsList);
	}

	public void putReadings(Device device, ResourceOperation operation, Map<String, String> values) {
		if (values == null || values.isEmpty())
			return;

		List<ModbusObject> objectsList = createObjectsList(operation, device);

		executePutReadings(device, operation, values, objectsList);
	}

	private void executePutReadings(Device device, ResourceOperation operation, Map<String, String> values,
			List<ModbusObject> objectsList) {
		String deviceId = device.getId();
		List<Reading> readings = new ArrayList<>();

		for (ModbusObject obj : objectsList) {
			String objectName = obj.getName();
			logger.info("Before transformation result:" + values);
			String result = transformResult(values.get(objectName), obj, device, operation);
			logger.info("After transformation result:" + result);

			Reading reading = processor.buildReading(objectName, result, device.getName());
			readings.add(reading);

			synchronized (objectCache) {
				if (objectCache.get(deviceId) == null)
					objectCache.put(deviceId, new HashMap<String, List<String>>());
				if (objectCache.get(deviceId).get(objectName) == null)
					objectCache.get(deviceId).put(objectName, new ArrayList<String>());
				objectCache.get(deviceId).get(objectName).add(0, result);
				if (objectCache.get(deviceId).get(objectName).size() == CACHE_SIZE)
					objectCache.get(deviceId).get(objectName).remove(CACHE_SIZE - 1);
			}
		}

		String operationId = objectsList.stream().map(o -> o.getName()).collect(Collectors.toList()).toString();

		synchronized (responseCache) {
			if (responseCache.get(deviceId) == null)
				responseCache.put(deviceId, new HashMap<String, List<Reading>>());
			responseCache.get(deviceId).put(operationId, readings);
		}
	}

	private List<ModbusObject> createObjectsList(ResourceOperation operation, Device device) {
		Map<String, ModbusObject> objects = profiles.getObjects().get(device.getName());
		List<ModbusObject> objectsList = new ArrayList<ModbusObject>();
		if (operation != null && objects != null) {
			ModbusObject object = objects.get(operation.getObject());

			List<String> deviceResourceReferences = object.getAttributes().getDeviceResourceReferences();
			if (deviceResourceReferences == null || deviceResourceReferences.isEmpty()) {
				if (profiles.descriptorExists(operation.getParameter())) {
					object.setName(operation.getParameter());
					objectsList.add(object);
				} else if (profiles.descriptorExists(object.getName())) {
					objectsList.add(object);
				}
			} else {
				deviceResourceReferences.stream().filter(profiles::descriptorExists).map(objects::get)
						.forEach(objectsList::add);
			}

			if (operation.getSecondary() != null)
				for (String secondary : operation.getSecondary())
					if (profiles.descriptorExists(secondary))
						objectsList.add(objects.get(secondary));
		}

		return objectsList;
	}

	private String transformResult(String result, ModbusObject object, Device device, ResourceOperation operation) {
		return objectTransform.transformGetResult(result, object, device, operation);
	}

	public String get(String deviceId, String object) {
		return get(deviceId, object, 1).get(0);
	}

	private List<String> get(String deviceId, String object, int i) {
		if (objectCache.get(deviceId) == null || objectCache.get(deviceId).get(object) == null
				|| objectCache.get(deviceId).get(object).size() < i)
			return null;
		return objectCache.get(deviceId).get(object).subList(0, i);
	}

	public JsonObject get(Device device, ResourceOperation operation) {
		JsonObject jsonObject = new JsonObject();
		List<ModbusObject> objectsList = createObjectsList(operation, device);
		for (ModbusObject obj : objectsList) {
			String objectName = obj.getName();
			jsonObject.addProperty(objectName, get(device.getId(), objectName));
		}
		return jsonObject;
	}

	public List<Reading> getResponses(Device device, ResourceOperation operation) {
		String deviceId = device.getId();
		List<ModbusObject> objectsList = createObjectsList(operation, device);
		if (objectsList == null)
			throw new NotFoundException("device", deviceId);
		String operationId = objectsList.stream().map(o -> o.getName()).collect(Collectors.toList()).toString();
		if (responseCache.get(deviceId) == null || responseCache.get(deviceId).get(operationId) == null)
			return new ArrayList<Reading>();
		return responseCache.get(deviceId).get(operationId);
	}

}
