package org.edgexfoundry.domain;

import java.io.Serializable;

import org.edgexfoundry.support.logging.client.EdgeXLogger;
import org.edgexfoundry.support.logging.client.EdgeXLoggerFactory;

import com.google.gson.Gson;

public class ModbusLocation implements Serializable {

	private static final long serialVersionUID = 1L;
	private final static EdgeXLogger logger = EdgeXLoggerFactory.getEdgeXLogger(ModbusAttribute.class);

	private Integer baseAddress = 0;

	public ModbusLocation(Object location) {
		if (location == null) {
			return;
		}
		
		try {
			Gson gson = new Gson();
			String jsonString = gson.toJson(location);
			ModbusLocation thisObject = gson.fromJson(jsonString, this.getClass());

			if (thisObject.getBaseAddress() != null) {
				this.setBaseAddress(thisObject.getBaseAddress());
			}
		} catch (Exception e) {
			logger.debug(e.getMessage(), e);
			logger.error("Cannot Construct ModbusLocation: " + e.getMessage());
		}
	}

	public Integer getBaseAddress() {
		return baseAddress;
	}

	public void setBaseAddress(Integer baseAddress) {
		this.baseAddress = baseAddress;
	}

}
