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

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.edgexfoundry.data.ObjectStore;
import org.edgexfoundry.domain.ModbusObject;
import org.edgexfoundry.domain.meta.Device;
import org.edgexfoundry.domain.meta.OperatingState;
import org.edgexfoundry.domain.meta.PropertyValue;
import org.edgexfoundry.domain.meta.ResourceOperation;
import org.edgexfoundry.exception.controller.DataValidationException;
import org.edgexfoundry.support.logging.client.EdgeXLogger;
import org.edgexfoundry.support.logging.client.EdgeXLoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.iotechsys.edgexpert.utils.ReadingValueTransformer;
import com.iotechsys.edgexpert.utils.exception.DataTransformException;

@Service
public class ObjectTransform {

	private final EdgeXLogger logger = EdgeXLoggerFactory.getEdgeXLogger(this.getClass());

	@Autowired
	ObjectStore objectCache;

	// Read current value, then mask and or with the desired set
	public String maskedValue(PropertyValue value, String val, String result) {

		BigInteger intValue = parse(value, val);
		BigInteger resultValue = parse(value, result);

		intValue = intValue.shiftLeft(value.shift());
		BigInteger maskedValue = resultValue.and(value.mask().not());
		maskedValue = maskedValue.or(intValue);
		Integer maskedVal = maskedValue.intValue();

		return String.format("%0" + value.size() + "X", maskedVal);
	}

	public String transform(PropertyValue value, String result) {

		double floatValue;

		if (value.getLSB() != null) {
			BigInteger val = parse(value, result);

			if (!value.mask().equals(BigInteger.ZERO))
				val = val.and(value.mask());

			if (!value.shift().equals(0))
				val = val.shiftRight(value.shift());

			if (value.getSigned() && val.bitLength() == (value.size() * 4)) {
				BigInteger complement = new BigInteger(new String(new char[value.size()]).replace("\0", "F"), 16);
				val = val.subtract(complement);
			}

			if (!objectCache.getTransformData()) {
				int intValue = val.intValue();
				return String.valueOf(intValue);
			}

			floatValue = val.doubleValue();
		} else {
			floatValue = Float.parseFloat(result);
		}

		if (!value.base().equals(0))
			floatValue = Math.pow(value.base(), floatValue);
		floatValue = floatValue * value.scale();
		floatValue = floatValue + value.offset();

		if (value.getType().toLowerCase().equals("f") || value.getType().toLowerCase().equals("float"))
			return String.valueOf(floatValue);
		return String.valueOf(Math.round(floatValue));
	}

	private BigInteger parse(PropertyValue value, String result) {
		BigInteger val = BigInteger.ZERO;

		if (result.startsWith("0x"))
			result = result.substring(2, result.length());
		else
			result = String.format("%0" + value.size() + "X", Integer.decode(result));

		Integer word = value.word() * 2;

		if (word > value.size())
			word = value.size();

		for (int i = 0; i < result.length() / word; i++) {
			Integer start = i * word;
			Integer finish = (i + 1) * word;
			BigInteger thisword = BigInteger.ZERO;

			for (int j = 0; j < word / 2; j++) {
				if (value.LSB()) {
					Integer index = finish - j * 2;
					Integer intval = Integer.decode("0x" + result.substring(index - 2, index));
					thisword = BigInteger.valueOf(intval).add(thisword.shiftLeft(8));
				} else {
					Integer index = start + j * 2;
					Integer intval = Integer.decode("0x" + result.substring(index, index + 2));
					thisword = BigInteger.valueOf(intval).add(thisword.shiftLeft(8));
				}
			}

			val = thisword.add(val.shiftLeft(word * 4));
		}

		return val;
	}

	public String format(PropertyValue value, String arg) {
		BigInteger intValue = parse(value, arg);
		Integer val = intValue.intValue();
		return String.format("%0" + value.size() + "X", val);
	}

	public String transformGetResult(String result, ModbusObject object, Device device, ResourceOperation operation) {
		PropertyValue propValue = object.getProperties().getValue();

		checkAssertion(result, device, propValue);

		if (propValue.scale() != 1.0F) {
			result = String.valueOf((Float.parseFloat(result) * propValue.scale()));
		}

		result = calculateByFunctions(result, operation);

		Map<String, String> mappings = operation.getMappings();
		if("set".equals(operation.getOperation().toLowerCase())) {
			mappings = mappings.entrySet().stream().collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey)); 
		}
		result = mappingResult(result, mappings);

		return result;
	}

	private void checkAssertion(String result, Device device, PropertyValue propValue) {
		// if there is an assertion set for the object on a get command, test it
		// if it fails the assertion, pass error to core services (disable device?)
		if (propValue.getAssertion() != null) {
			if (!result.equals(propValue.getAssertion().toString())) {
				device.setOperatingState(OperatingState.DISABLED);
				throw new DataValidationException("Assertion failed with value: " + result);
			}
		}
	}

	public String calculateByFunctions(String result, ResourceOperation operation) {
		List<String> functions = operation.getTransformFunctions();
		if (functions != null && !functions.isEmpty()) {
			try {
				result = ReadingValueTransformer.transformByFunctions(result, functions);
			} catch (DataTransformException e) {
				logger.error(e.getMessage(), e);
				throw new DataValidationException(e.getMessage());
			}
		}
		return result;
	}

	public String mappingResult(String result, Map<String, String> mappings) {
		if (mappings != null && mappings.containsKey(result))
			result = mappings.get(result);
		return result;
	}

}
