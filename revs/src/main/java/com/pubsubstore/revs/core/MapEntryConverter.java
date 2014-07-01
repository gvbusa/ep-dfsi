package com.pubsubstore.revs.core;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;

import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;

public class MapEntryConverter implements Converter {

	@SuppressWarnings("rawtypes")
	public boolean canConvert(Class clazz) {
		return AbstractMap.class.isAssignableFrom(clazz);
	}

	public void marshal(Object value, HierarchicalStreamWriter writer,
			MarshallingContext context) {
		// noinspection unchecked
		@SuppressWarnings("unchecked")
		AbstractMap<String, String> map = (AbstractMap<String, String>) value;
		for (Map.Entry<String, String> entry : map.entrySet()) {
			// noinspection RedundantStringToString
			writer.startNode(entry.getKey().toString());
			// noinspection RedundantStringToString
			writer.setValue(entry.getValue().toString());
			writer.endNode();
		}
	}

	public Object unmarshal(HierarchicalStreamReader reader,
			UnmarshallingContext context) {
		Map<String, String> map = new HashMap<String, String>();

		while (reader.hasMoreChildren()) {
			reader.moveDown();
			map.put(reader.getNodeName(), reader.getValue());
			reader.moveUp();
		}
		return map;
	}
}