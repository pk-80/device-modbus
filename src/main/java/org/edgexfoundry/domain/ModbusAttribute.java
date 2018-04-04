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
package org.edgexfoundry.domain;

import java.io.Serializable;

import org.edgexfoundry.support.logging.client.EdgeXLogger;
import org.edgexfoundry.support.logging.client.EdgeXLoggerFactory;

import com.google.gson.Gson;

public class ModbusAttribute implements Serializable {

	private static final long serialVersionUID = 1L;
	private final static EdgeXLogger logger = EdgeXLoggerFactory.getEdgeXLogger(ModbusAttribute.class);
	
	// Replace these attributes with the Modbus
	// specific metadata needed by the Modbus Driver
	
	private String primaryTable;
	private String startingAddress;
	private boolean isByteSwap = false;
	private boolean isWordSwap = false;
	
	public ModbusAttribute(Object attributes) {
		try {
			Gson gson = new Gson();
			String jsonString = gson.toJson(attributes);
			ModbusAttribute thisObject = gson.fromJson(jsonString, this.getClass());
			
			this.setPrimaryTable(thisObject.getPrimaryTable());
			this.setAddress(thisObject.getStartingAddress());
			this.setByteSwap(thisObject.isByteSwap());
			this.setWordSwap(thisObject.isWordSwap());
			
		} catch (Exception e) {
			logger.error("Cannot Construct ModbusAttribute: " + e.getMessage());
		}
	}

	public String getPrimaryTable() {
		return primaryTable;
	}

	public void setPrimaryTable(String primaryTable) {
		this.primaryTable = primaryTable;
	}

	public String getStartingAddress() {
		return startingAddress;
	}

	public void setAddress(String startingAddress) {
		this.startingAddress = startingAddress;
	}

	public boolean isByteSwap() {
		return isByteSwap;
	}

	public void setByteSwap(boolean isByteSwap) {
		this.isByteSwap = isByteSwap;
	}

	public boolean isWordSwap() {
		return isWordSwap;
	}

	public void setWordSwap(boolean isWordSwap) {
		this.isWordSwap = isWordSwap;
	}

	@Override
	public String toString() {
		return "ModbusAttribute [primaryTable=" + primaryTable + ", startingAddress=" + startingAddress
				+ ", isByteSwap=" + isByteSwap + ", isWordSwap=" + isWordSwap + "]";
	}
	
}
