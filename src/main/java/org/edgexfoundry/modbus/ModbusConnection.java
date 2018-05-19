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
import java.util.LinkedHashMap;
import java.util.Map;

import org.edgexfoundry.domain.ModbusDevice;
import org.edgexfoundry.domain.ModbusObject;
import org.edgexfoundry.domain.meta.Addressable;
import org.edgexfoundry.domain.meta.Protocol;
import org.edgexfoundry.support.logging.client.EdgeXLogger;
import org.edgexfoundry.support.logging.client.EdgeXLoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import com.ghgande.j2mod.modbus.net.SerialConnection;
import com.ghgande.j2mod.modbus.net.TCPMasterConnection;
import com.ghgande.j2mod.modbus.util.SerialParameters;

@Service
@Scope(BeanDefinition.SCOPE_SINGLETON)
public class ModbusConnection {

	private final static EdgeXLogger logger = EdgeXLoggerFactory.getEdgeXLogger(ModbusConnection.class);

	@Value("${modbus.rtu.timeout:3000}")
	private int modbus_rtu_timeout;

	@Autowired
	private ModbusReadFunction readFunction;

	@Autowired
	private ModbusWriteFunction writeFunction;

	public ModbusConnection() {
	}

	public Object getModbusConnection(Addressable addressable) {
		Object connection = null;
		if (addressable.getProtocol() == Protocol.HTTP || addressable.getProtocol() == Protocol.TCP) {
			logger.info("creating TCP connection");
			connection = createTCPConnection(addressable);
		} else /* if(addressable.getProtocol() == Protocol.OTHER) */ {
			connection = createSerialConnection(addressable);
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
			con.setTimeout(modbus_rtu_timeout);
			// con.open();
			logger.info("Created Modbus RTU Connection for " + addressable.toString());
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
			logger.info("Created Modbus TCP Connection for " + addressable.toString());
		} catch (Exception e) {
			logger.debug(e.getMessage(), e);
			logger.error("Exception in creating TCP connection:" + e);
		}
		return con;
	}

	public String getValue(Object connection, Addressable addressable, ModbusObject object, ModbusDevice device,
			int retryCount) {
		return readFunction.readValue(connection, addressable, object, device, retryCount);
	}

	public Map<String, String> getValues(Object connection, Addressable addressable, ModbusObject object,
			ModbusDevice device, int retryCount) {
		return readFunction.readValues(connection, addressable, object, device, retryCount);
	}

	public String setValue(Object connection, Addressable addressable, ModbusObject object, String value,
			ModbusDevice device, int retryCount) {
		return writeFunction.writeValue(connection, addressable, object, value, device, retryCount);
	}

	public Map<String, String> setValues(Object connection, Addressable addressable, ModbusObject object,
			Map<String, String> value, ModbusDevice device, int retryCount) {
		return new LinkedHashMap<>();
	}
}
