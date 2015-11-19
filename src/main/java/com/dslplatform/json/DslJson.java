package com.dslplatform.json;

import org.w3c.dom.Element;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class DslJson<TContext> {

	protected final TContext context;
	protected final Fallback<TContext> fallback;

	public interface Fallback<TContext> {
		void serialize(Object instance, OutputStream stream) throws IOException;

		Object deserialize(TContext context, Type manifest, byte[] body, final int size) throws IOException;
	}

	static final JsonReader.ReadObject<Object> ObjectReader = new JsonReader.ReadObject<Object>() {
		@Override
		public Object read(JsonReader reader) throws IOException {
			return deserializeObject(reader);
		}
	};
	@SuppressWarnings("rawtypes")
	static final JsonReader.ReadObject<Collection> CollectionReader = new JsonReader.ReadObject<Collection>() {
		@Override
		public Collection read(JsonReader reader) throws IOException {
			return deserializeList(reader);
		}
	};
	@SuppressWarnings("rawtypes")
	static final JsonReader.ReadObject<LinkedHashMap> MapReader = new JsonReader.ReadObject<LinkedHashMap>() {
		@Override
		public LinkedHashMap read(JsonReader reader) throws IOException {
			return deserializeMap(reader);
		}
	};

	public DslJson() {
		this(null, false, false, false, null, ServiceLoader.load(Configuration.class));
	}

	public DslJson(
			final TContext context,
			final boolean androidSpecifics,
			final boolean javaSpecifics,
			final boolean jodaTime,
			final Fallback<TContext> fallback,
			final Iterable<Configuration> serializers) {
		this.context = context;
		this.fallback = fallback;
		registerReader(byte[].class, BinaryConverter.Base64Reader);
		registerWriter(byte[].class, BinaryConverter.Base64Writer);
		registerReader(boolean.class, BoolConverter.BooleanReader);
		registerReader(Boolean.class, BoolConverter.BooleanReader);
		registerWriter(boolean.class, BoolConverter.BooleanWriter);
		registerWriter(Boolean.class, BoolConverter.BooleanWriter);
		if (androidSpecifics) {
			registerAndroidSpecifics(this);
		}
		if (javaSpecifics) {
			registerJavaSpecifics(this);
		}
		if (jodaTime) {
			registerJodaConverters(this);
		}
		registerReader(LinkedHashMap.class, MapReader);
		registerReader(HashMap.class, MapReader);
		registerReader(Map.class, MapReader);
		registerWriter(Map.class, new JsonWriter.WriteObject<Map>() {
			@Override
			public void write(JsonWriter writer, Map value) {
				try {
					serializeMap(value, writer);
				} catch (IOException ex) {
					throw new RuntimeException(ex);
				}
			}
		});
		registerReader(URI.class, NetConverter.UriReader);
		registerWriter(URI.class, NetConverter.UriWriter);
		registerReader(InetAddress.class, NetConverter.AddressReader);
		registerWriter(InetAddress.class, NetConverter.AddressWriter);
		registerReader(double.class, NumberConverter.DoubleReader);
		registerWriter(double.class, NumberConverter.DoubleWriter);
		registerReader(Double.class, NumberConverter.DoubleReader);
		registerWriter(Double.class, NumberConverter.DoubleWriter);
		registerReader(float.class, NumberConverter.FloatReader);
		registerWriter(float.class, NumberConverter.FloatWriter);
		registerReader(Float.class, NumberConverter.FloatReader);
		registerWriter(Float.class, NumberConverter.FloatWriter);
		registerReader(int.class, NumberConverter.IntReader);
		registerWriter(int.class, NumberConverter.IntWriter);
		registerReader(Integer.class, NumberConverter.IntReader);
		registerWriter(Integer.class, NumberConverter.IntWriter);
		registerReader(long.class, NumberConverter.LongReader);
		registerWriter(long.class, NumberConverter.LongWriter);
		registerReader(Long.class, NumberConverter.LongReader);
		registerWriter(Long.class, NumberConverter.LongWriter);
		registerReader(BigDecimal.class, NumberConverter.DecimalReader);
		registerWriter(BigDecimal.class, NumberConverter.DecimalWriter);
		registerReader(String.class, StringConverter.Reader);
		registerWriter(String.class, StringConverter.Writer);
		registerReader(UUID.class, UUIDConverter.Reader);
		registerWriter(UUID.class, UUIDConverter.Writer);
		registerReader(Element.class, XmlConverter.Reader);
		registerWriter(Element.class, XmlConverter.Writer);
		registerReader(Number.class, NumberConverter.NumberReader);

		if (serializers != null) {
			for (Configuration serializer : serializers) {
				serializer.configure(this);
			}
		}
	}

	static void registerAndroidSpecifics(final DslJson json) {
		json.registerReader(android.graphics.PointF.class, AndroidGeomConverter.LocationReader);
		json.registerWriter(android.graphics.PointF.class, AndroidGeomConverter.LocationWriter);
		json.registerReader(android.graphics.Point.class, AndroidGeomConverter.PointReader);
		json.registerWriter(android.graphics.Point.class, AndroidGeomConverter.PointWriter);
		json.registerReader(android.graphics.Rect.class, AndroidGeomConverter.RectangleReader);
		json.registerWriter(android.graphics.Rect.class, AndroidGeomConverter.RectangleWriter);
		json.registerReader(android.graphics.Bitmap.class, AndroidGeomConverter.ImageReader);
		json.registerWriter(android.graphics.Bitmap.class, AndroidGeomConverter.ImageWriter);
	}

	static void registerJodaConverters(final DslJson json) {
		json.registerReader(org.joda.time.LocalDate.class, JodaTimeConverter.LocalDateReader);
		json.registerWriter(org.joda.time.LocalDate.class, JodaTimeConverter.LocalDateWriter);
		json.registerReader(org.joda.time.DateTime.class, JodaTimeConverter.DateTimeReader);
		json.registerWriter(org.joda.time.DateTime.class, JodaTimeConverter.DateTimeWriter);
	}

	static void registerJavaSpecifics(final DslJson json) {
		json.registerReader(java.awt.geom.Point2D.Double.class, JavaGeomConverter.LocationReader);
		json.registerReader(java.awt.geom.Point2D.class, JavaGeomConverter.LocationReader);
		json.registerWriter(java.awt.geom.Point2D.class, JavaGeomConverter.LocationWriter);
		json.registerReader(java.awt.Point.class, JavaGeomConverter.PointReader);
		json.registerWriter(java.awt.Point.class, JavaGeomConverter.PointWriter);
		json.registerReader(java.awt.geom.Rectangle2D.Double.class, JavaGeomConverter.RectangleReader);
		json.registerReader(java.awt.geom.Rectangle2D.class, JavaGeomConverter.RectangleReader);
		json.registerWriter(java.awt.geom.Rectangle2D.class, JavaGeomConverter.RectangleWriter);
		json.registerReader(java.awt.image.BufferedImage.class, JavaGeomConverter.ImageReader);
		json.registerReader(java.awt.Image.class, JavaGeomConverter.ImageReader);
		json.registerWriter(java.awt.Image.class, JavaGeomConverter.ImageWriter);
	}

	protected static boolean isNull(final int size, final byte[] body) {
		return size == 4
				&& body[0] == 'n'
				&& body[1] == 'u'
				&& body[2] == 'l'
				&& body[3] == 'l';
	}

	private final ConcurrentHashMap<Class<?>, JsonReader.ReadJsonObject<JsonObject>> jsonObjectReaders =
			new ConcurrentHashMap<Class<?>, JsonReader.ReadJsonObject<JsonObject>>();

	private final HashMap<Type, JsonReader.ReadObject<?>> jsonReaders = new HashMap<Type, JsonReader.ReadObject<?>>();

	public <T, S extends T> void registerReader(final Class<T> manifest, final JsonReader.ReadObject<S> reader) {
		jsonReaders.put(manifest, reader);
	}

	public void registerReader(final Type manifest, final JsonReader.ReadObject<?> reader) {
		jsonReaders.put(manifest, reader);
	}

	private final HashMap<Type, JsonWriter.WriteObject<?>> jsonWriters = new HashMap<Type, JsonWriter.WriteObject<?>>();

	public <T> void registerWriter(final Class<T> manifest, final JsonWriter.WriteObject<T> writer) {
		writerMap.put(manifest, manifest);
		jsonWriters.put(manifest, writer);
	}

	public void registerWriter(final Type manifest, final JsonWriter.WriteObject<?> writer) {
		jsonWriters.put(manifest, writer);
	}

	private final ConcurrentMap<Class<?>, Class<?>> writerMap = new ConcurrentHashMap<Class<?>, Class<?>>();

	protected final JsonWriter.WriteObject<?> tryFindWriter(final Type manifest) {
		Class<?> found = writerMap.get(manifest);
		if (found != null) {
			return jsonWriters.get(found);
		}
		if (manifest instanceof Class<?> == false) {
			return null;
		}
		Class<?> container = (Class<?>) manifest;
		final ArrayList<Class<?>> signatures = new ArrayList<Class<?>>();
		findAllSignatures(container, signatures);
		for (final Class<?> sig : signatures) {
			final JsonWriter.WriteObject<?> writer = jsonWriters.get(sig);
			if (writer != null) {
				writerMap.putIfAbsent(container, sig);
				return writer;
			}
		}
		return null;
	}

	private static void findAllSignatures(final Class<?> manifest, final ArrayList<Class<?>> found) {
		if (found.contains(manifest)) {
			return;
		}
		found.add(manifest);
		final Class<?> superClass = manifest.getSuperclass();
		if (superClass != null && superClass != Object.class) {
			findAllSignatures(superClass, found);
		}
		for (final Class<?> iface : manifest.getInterfaces()) {
			findAllSignatures(iface, found);
		}
	}

	@SuppressWarnings("unchecked")
	protected final JsonReader.ReadJsonObject<JsonObject> getObjectReader(final Class<?> manifest) {
		try {
			JsonReader.ReadJsonObject<JsonObject> reader = jsonObjectReaders.get(manifest);
			if (reader == null) {
				try {
					reader = (JsonReader.ReadJsonObject<JsonObject>) manifest.getField("JSON_READER").get(null);
				} catch (Exception ignore) {
					//log error!?
					return null;
				}
				jsonObjectReaders.putIfAbsent(manifest, reader);
			}
			return reader;
		} catch (final Exception ignore) {
			return null;
		}
	}

	public void serializeMap(final Map<String, Object> value, final JsonWriter sw) throws IOException {
		sw.writeByte(JsonWriter.OBJECT_START);
		final int size = value.size();
		if (size > 0) {
			final Iterator<Map.Entry<String, Object>> iterator = value.entrySet().iterator();
			Map.Entry<String, Object> kv = iterator.next();
			sw.writeString(kv.getKey());
			sw.writeByte(JsonWriter.SEMI);
			serialize(sw, kv.getValue());
			for (int i = 1; i < size; i++) {
				sw.writeByte(JsonWriter.COMMA);
				kv = iterator.next();
				sw.writeString(kv.getKey());
				sw.writeByte(JsonWriter.SEMI);
				serialize(sw, kv.getValue());
			}
		}
		sw.writeByte(JsonWriter.OBJECT_END);
	}

	public static Object deserializeObject(final JsonReader reader) throws IOException {
		switch (reader.last()) {
			case 'n':
				if (!reader.wasNull()) {
					throw new IOException("Expecting 'null' at position " + reader.positionInStream() + ". Found " + (char) reader.last());
				}
				return null;
			case 't':
				if (!reader.wasTrue()) {
					throw new IOException("Expecting 'true' at position " + reader.positionInStream() + ". Found " + (char) reader.last());
				}
				return true;
			case 'f':
				if (!reader.wasFalse()) {
					throw new IOException("Expecting 'false' at position " + reader.positionInStream() + ". Found " + (char) reader.last());
				}
				return false;
			case '"':
				return reader.readString();
			case '{':
				return deserializeMap(reader);
			case '[':
				return deserializeList(reader);
			default:
				return NumberConverter.deserializeNumber(reader);
		}
	}

	public static ArrayList<Object> deserializeList(final JsonReader reader) throws IOException {
		if (reader.last() != '[') {
			throw new IOException("Expecting '[' at position " + reader.positionInStream() + ". Found " + (char) reader.last());
		}
		byte nextToken = reader.getNextToken();
		if (nextToken == ']') return new ArrayList<Object>(0);
		final ArrayList<Object> res = new ArrayList<Object>(4);
		res.add(deserializeObject(reader));
		while ((nextToken = reader.getNextToken()) == ',') {
			reader.getNextToken();
			res.add(deserializeObject(reader));
		}
		if (nextToken != ']') {
			throw new IOException("Expecting ']' at position " + reader.positionInStream() + ". Found " + (char) nextToken);
		}
		return res;
	}

	public static LinkedHashMap<String, Object> deserializeMap(final JsonReader reader) throws IOException {
		if (reader.last() != '{') {
			throw new IOException("Expecting '{' at position " + reader.positionInStream() + ". Found " + (char) reader.last());
		}
		byte nextToken = reader.getNextToken();
		if (nextToken == '}') return new LinkedHashMap<String, Object>(0);
		final LinkedHashMap<String, Object> res = new LinkedHashMap<String, Object>();
		String key = StringConverter.deserialize(reader);
		nextToken = reader.getNextToken();
		if (nextToken != ':') {
			throw new IOException("Expecting ':' at position " + reader.positionInStream() + ". Found " + (char) nextToken);
		}
		reader.getNextToken();
		res.put(key, deserializeObject(reader));
		while ((nextToken = reader.getNextToken()) == ',') {
			reader.getNextToken();
			key = StringConverter.deserialize(reader);
			nextToken = reader.getNextToken();
			if (nextToken != ':') {
				throw new IOException("Expecting ':' at position " + reader.positionInStream() + ". Found " + (char) nextToken);
			}
			reader.getNextToken();
			res.put(key, deserializeObject(reader));
		}
		if (nextToken != '}') {
			throw new IOException("Expecting '}' at position " + reader.positionInStream() + ". Found " + (char) nextToken);
		}
		return res;
	}

	@SuppressWarnings("unchecked")
	public <TResult> TResult deserialize(
			final Class<TResult> manifest,
			final byte[] body,
			final int size) throws IOException {
		if (isNull(size, body)) {
			return null;
		}
		if (size == 2 && body[0] == '{' && body[1] == '}' && !manifest.isInterface()) {
			try {
				return manifest.newInstance();
			} catch (InstantiationException ignore) {
			} catch (IllegalAccessException e) {
				throw new IOException(e);
			}
		}
		if (JsonObject.class.isAssignableFrom(manifest)) {
			final JsonReader.ReadJsonObject<JsonObject> objectReader = getObjectReader(manifest);
			final JsonReader json = new JsonReader(body, size, context);
			if (objectReader != null && json.getNextToken() == '{') {
				json.getNextToken();
				return (TResult) objectReader.deserialize(json);
			}
		}
		final JsonReader.ReadObject<?> simpleReader = jsonReaders.get(manifest);
		if (simpleReader == null) {
			if (manifest.isArray()) {
				final Class<?> elementManifest = manifest.getComponentType();
				final List<?> list = deserializeList(elementManifest, body, size);
				if (list == null) {
					return null;
				}
				final Object result = Array.newInstance(elementManifest, list.size());
				for (int i = 0; i < list.size(); i++) {
					Array.set(result, i, list.get(i));
				}
				return (TResult) result;
			}
			if (fallback != null) {
				return (TResult) fallback.deserialize(context, manifest, body, size);
			}
			showErrorMessage(manifest);
		}
		final JsonReader json = new JsonReader(body, size, context);
		json.getNextToken();
		if (json.wasNull()) {
			return null;
		}
		final TResult result = (TResult) simpleReader.read(json);
		if (json.getCurrentIndex() > json.length()) {
			throw new IOException("JSON string was not closed with a double quote");
		}
		return result;
	}

	public Object deserialize(
			final Type manifest,
			final byte[] body,
			final int size) throws IOException {
		if (manifest instanceof Class<?>) {
			return deserialize((Class<?>) manifest, body, size);
		}
		if (isNull(size, body)) {
			return null;
		}
		final JsonReader.ReadObject<?> simpleReader = jsonReaders.get(manifest);
		if (simpleReader == null) {
			if (fallback != null) {
				return fallback.deserialize(context, manifest, body, size);
			}
			throw new IOException("Unable to find reader for provided type: " + manifest + " and fallback serialization is not registered.\n" +
					"Try initializing DslJson with custom fallback in case of unsupported objects or register specified type using registerReader into " + getClass());
		}
		final JsonReader json = new JsonReader<TContext>(body, size, context);
		json.getNextToken();
		if (json.wasNull()) {
			return null;
		}
		final Object result = simpleReader.read(json);
		if (json.getCurrentIndex() > json.length()) {
			throw new IOException("JSON string was not closed with a double quote");
		}
		return result;
	}

	private void showErrorMessage(final Class<?> manifest) throws IOException {
		final ArrayList<Class<?>> signatures = new ArrayList<Class<?>>();
		findAllSignatures(manifest, signatures);
		for (final Class<?> sig : signatures) {
			if (jsonReaders.containsKey(sig)) {
				throw new IOException("Unable to find reader for provided type: " + manifest + " and fallback serialization is not registered.\n" +
						"Found reader for: " + sig + " so try deserializing into that instead?\n" +
						"Alternatively, try initializing system with custom fallback or register specified type using registerReader into " + getClass());
			}
		}
		throw new IOException("Unable to find reader for provided type: " + manifest + " and fallback serialization is not registered.\n" +
				"Try initializing DslJson with custom fallback in case of unsupported objects or register specified type using registerReader into " + getClass());
	}

	@SuppressWarnings("unchecked")
	public <TResult> List<TResult> deserializeList(
			final Class<TResult> manifest,
			final byte[] body,
			final int size) throws IOException {
		if (isNull(size, body)) {
			return null;
		}
		if (size == 2 && body[0] == '[' && body[1] == ']') {
			return new ArrayList<TResult>(0);
		}
		final JsonReader json = new JsonReader(body, size, context);
		if (json.getNextToken() != '[') {
			if (json.wasNull()) {
				return null;
			}
			throw new IOException("Expecting '[' as array start. Found: " + (char) json.last());
		}
		if (json.getNextToken() == ']') {
			return new ArrayList<TResult>(0);
		}
		if (JsonObject.class.isAssignableFrom(manifest)) {
			final JsonReader.ReadJsonObject<JsonObject> reader = getObjectReader(manifest);
			if (reader != null) {
				return (List<TResult>) json.deserializeNullableCollection(reader);
			}
		}
		final JsonReader.ReadObject<?> simpleReader = jsonReaders.get(manifest);
		if (simpleReader == null) {
			if (fallback != null) {
				Object array = Array.newInstance(manifest, 0);
				TResult[] result = (TResult[]) fallback.deserialize(context, array.getClass(), body, size);
				if (result == null) {
					return null;
				}
				ArrayList<TResult> list = new ArrayList<TResult>(result.length);
				for (TResult aResult : result) {
					list.add(aResult);
				}
				return list;
			}
			showErrorMessage(manifest);
		}
		return json.deserializeNullableCollection(simpleReader);
	}

	private static final Iterator EmptyIterator = new Iterator() {
		@Override
		public boolean hasNext() {
			return false;
		}

		@Override
		public Object next() {
			return null;
		}
	};

	public <TResult> Iterator<TResult> iterateOver(
			final Class<TResult> manifest,
			final InputStream stream,
			final byte[] buffer) throws IOException {
		int position = JsonStreamReader.readFully(buffer, stream, 0);
		if (isNull(position, buffer)) {
			return null;
		}
		if (position < buffer.length) {
			return deserializeList(manifest, buffer, position).iterator();
		}
		final JsonReader json = new JsonReader<TContext>(buffer, position, context);
		if (json.getNextToken() != '[') {
			if (json.wasNull()) {
				return null;
			}
			throw new IOException("Expecting '[' as array start. Found: " + (char) json.last());
		}
		if (json.getNextToken() == ']') {
			return EmptyIterator;
		}
		if (JsonObject.class.isAssignableFrom(manifest)) {
			final JsonReader.ReadJsonObject<JsonObject> reader = getObjectReader(manifest);
			if (reader != null) {
				return new StreamWithObjectReader(buffer, reader, json, stream);
			}
		}
		final JsonReader.ReadObject<?> simpleReader = jsonReaders.get(manifest);
		if (simpleReader == null) {
			if (fallback != null) {
				return new StreamWithFallback(buffer, stream, json, manifest, fallback, context);
			}
			showErrorMessage(manifest);
		}
		return new StreamWithReader(buffer, simpleReader, json, stream);
	}

	private static class StreamWithObjectReader<T extends JsonObject> implements Iterator<T> {
		private final byte[] buffer;
		private final JsonReader.ReadJsonObject<T> reader;
		private final JsonReader json;
		private final InputStream stream;

		private boolean hasNext;

		public StreamWithObjectReader(
				byte[] buffer,
				JsonReader.ReadJsonObject<T> reader,
				JsonReader json,
				InputStream stream) {
			this.buffer = buffer;
			this.reader = reader;
			this.json = json;
			this.stream = stream;
			hasNext = true;
		}

		@Override
		public boolean hasNext() {
			return hasNext;
		}

		@Override
		public T next() {
			try {
				byte nextToken = json.last();
				final T instance;
				if (nextToken == 'n') {
					if (json.wasNull()) {
						instance = null;
					} else {
						throw new RuntimeException("Expecting 'null' for collection item. Found: " + (char) json.last());
					}
				} else if (nextToken == '{') {
					json.getNextToken();
					instance = reader.deserialize(json);
				} else {
					throw new IOException("Expecting '{' at position " + json.positionInStream() + ". Found " + (char) nextToken);
				}
				hasNext = json.getNextToken() == ',';
				if (!hasNext && json.last() != ']') {
					throw new RuntimeException("Expecting ']' for end of collection. Found: " + (char) json.last());
				}
				final int current = json.getCurrentIndex();
				if (current * 2 > buffer.length) {
					final int len = buffer.length - current;
					System.arraycopy(buffer, current, buffer, 0, len);
					int position = JsonStreamReader.readFully(buffer, stream, len);
					json.reset(position);
				}
				json.getNextToken();
				return instance;
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}

	private static class StreamWithReader<T> implements Iterator<T> {
		private final byte[] buffer;
		private final JsonReader.ReadObject<T> reader;
		private final JsonReader json;
		private final InputStream stream;

		private boolean hasNext;

		public StreamWithReader(
				byte[] buffer,
				JsonReader.ReadObject<T> reader,
				JsonReader json,
				InputStream stream) {
			this.buffer = buffer;
			this.reader = reader;
			this.json = json;
			this.stream = stream;
			hasNext = true;
		}

		@Override
		public boolean hasNext() {
			return hasNext;
		}

		@Override
		public T next() {
			try {
				byte nextToken = json.last();
				final T instance;
				if (nextToken == 'n') {
					if (json.wasNull()) {
						instance = null;
					} else {
						throw new RuntimeException("Expecting 'null' for collection item. Found: " + (char) json.last());
					}
				} else {
					instance = reader.read(json);
				}
				hasNext = json.getNextToken() == ',';
				if (!hasNext && json.last() != ']') {
					throw new RuntimeException("Expecting ']' for end of collection. Found: " + (char) json.last());
				}
				final int current = json.getCurrentIndex();
				if (current * 2 > buffer.length) {
					final int len = buffer.length - current;
					System.arraycopy(buffer, current, buffer, 0, len);
					int position = JsonStreamReader.readFully(buffer, stream, len);
					json.reset(position);
				}
				json.getNextToken();
				return instance;
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}

	private static class StreamWithFallback<T, TContext> implements Iterator<T> {
		private final byte[] buffer;
		private final InputStream stream;
		private final JsonReader json;
		private final Type manifest;
		private final Fallback<TContext> fallback;
		private final TContext context;

		private boolean hasNext;

		public StreamWithFallback(
				byte[] buffer,
				InputStream stream,
				JsonReader json,
				Type manifest,
				Fallback<TContext> fallback,
				TContext context) {
			this.buffer = buffer;
			this.stream = stream;
			this.json = json;
			this.manifest = manifest;
			this.fallback = fallback;
			this.context = context;
			hasNext = true;
		}

		@Override
		public boolean hasNext() {
			return hasNext;
		}

		@Override
		public T next() {
			try {
				byte nextToken = json.last();
				final T instance;
				if (nextToken == 'n') {
					if (json.wasNull()) {
						instance = null;
					} else {
						throw new RuntimeException("Expecting 'null' for collection item. Found: " + (char) json.last());
					}
				} else {
					json.skip();
					instance = (T) fallback.deserialize(context, manifest, buffer, json.getCurrentIndex());
				}
				hasNext = json.getNextToken() == ',';
				if (!hasNext && json.last() != ']') {
					throw new RuntimeException("Expecting ']' for end of collection. Found: " + (char) json.last());
				}
				final int current = json.getCurrentIndex();
				final int len = buffer.length - current;
				System.arraycopy(buffer, current, buffer, 0, len);
				int position = JsonStreamReader.readFully(buffer, stream, len);
				json.reset(position);
				json.getNextToken();
				return instance;
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}

	public <TResult> TResult deserialize(
			final Class<TResult> manifest,
			final InputStream stream,
			final byte[] buffer) throws IOException {
		if (JsonObject.class.isAssignableFrom(manifest)) {
			final JsonReader.ReadJsonObject<JsonObject> objectReader = getObjectReader(manifest);
			final JsonStreamReader json = new JsonStreamReader<TContext>(stream, buffer, context);
			if (objectReader != null && json.getNextToken() == '{') {
				json.getNextToken();
				return (TResult) objectReader.deserialize(json);
			}
		}
		final JsonReader.ReadObject<?> simpleReader = jsonReaders.get(manifest);
		if (simpleReader == null) {
			if (manifest.isArray()) {
				final Class<?> elementManifest = manifest.getComponentType();
				final Iterator<?> iter = iterateOver(elementManifest, stream, buffer);
				if (iter == null) {
					return null;
				}
				final ArrayList<Object> list = new ArrayList<Object>();
				while (iter.hasNext()) {
					list.add(iter.next());
				}
				final Object result = Array.newInstance(elementManifest, list.size());
				for (int i = 0; i < list.size(); i++) {
					Array.set(result, i, list.get(i));
				}
				return (TResult) result;
			}
			showErrorMessage(manifest);
		}
		final JsonStreamReader json = new JsonStreamReader<TContext>(stream, buffer, context);
		json.getNextToken();
		if (json.wasNull()) {
			return null;
		}
		//TODO: ckeck not closed string
		return (TResult) simpleReader.read(json);
	}


	public <T extends JsonObject> void serialize(final JsonWriter writer, final T[] array) {
		if (array == null) {
			writer.writeNull();
			return;
		}
		writer.writeByte(JsonWriter.ARRAY_START);
		if (array.length != 0) {
			T item = array[0];
			if (item != null) {
				item.serialize(writer, false);
			} else {
				writer.writeNull();
			}
			for (int i = 1; i < array.length; i++) {
				writer.writeByte(JsonWriter.COMMA);
				item = array[i];
				if (item != null) {
					item.serialize(writer, false);
				} else {
					writer.writeNull();
				}
			}
		}
		writer.writeByte(JsonWriter.ARRAY_END);
	}

	public <T extends JsonObject> void serialize(final JsonWriter writer, final T[] array, final int len) {
		if (array == null) {
			writer.writeNull();
			return;
		}
		writer.writeByte(JsonWriter.ARRAY_START);
		if (len != 0) {
			T item = array[0];
			if (item != null) {
				item.serialize(writer, false);
			} else {
				writer.writeNull();
			}
			for (int i = 1; i < len; i++) {
				writer.writeByte(JsonWriter.COMMA);
				item = array[i];
				if (item != null) {
					item.serialize(writer, false);
				} else {
					writer.writeNull();
				}
			}
		}
		writer.writeByte(JsonWriter.ARRAY_END);
	}

	public <T extends JsonObject> void serialize(final JsonWriter writer, final List<T> list) {
		if (list == null) {
			writer.writeNull();
			return;
		}
		writer.writeByte(JsonWriter.ARRAY_START);
		if (list.size() != 0) {
			T item = list.get(0);
			if (item != null) {
				item.serialize(writer, false);
			} else {
				writer.writeNull();
			}
			for (int i = 1; i < list.size(); i++) {
				writer.writeByte(JsonWriter.COMMA);
				item = list.get(i);
				if (item != null) {
					item.serialize(writer, false);
				} else {
					writer.writeNull();
				}
			}
		}
		writer.writeByte(JsonWriter.ARRAY_END);
	}

	public <T extends JsonObject> void serialize(final JsonWriter writer, final Collection<T> collection) {
		if (collection == null) {
			writer.writeNull();
			return;
		}
		writer.writeByte(JsonWriter.ARRAY_START);
		if (!collection.isEmpty()) {
			final Iterator<T> it = collection.iterator();
			T item = it.next();
			if (item != null) {
				item.serialize(writer, false);
			} else {
				writer.writeNull();
			}
			while (it.hasNext()) {
				writer.writeByte(JsonWriter.COMMA);
				item = it.next();
				if (item != null) {
					item.serialize(writer, false);
				} else {
					writer.writeNull();
				}
			}
		}
		writer.writeByte(JsonWriter.ARRAY_END);
	}

	@SuppressWarnings("unchecked")
	public <T> boolean serialize(final JsonWriter writer, final Type manifest, final Object value) {
		if (value == null) {
			writer.writeNull();
			return true;
		}
		if (value instanceof JsonObject) {
			((JsonObject) value).serialize(writer, false);
			return true;
		}
		if (value instanceof JsonObject[]) {
			serialize(writer, (JsonObject[]) value);
			return true;
		}
		final JsonWriter.WriteObject<Object> simpleWriter = (JsonWriter.WriteObject<Object>) tryFindWriter(manifest);
		if (simpleWriter != null) {
			simpleWriter.write(writer, value);
			return true;
		}
		Class<?> container = null;
		if (manifest instanceof Class<?>) {
			container = (Class<?>) manifest;
		}
		if (container != null && container.isArray()) {
			if (Array.getLength(value) == 0) {
				writer.writeAscii("[]");
				return true;
			}
			final Class<?> elementManifest = container.getComponentType();
			if (elementManifest.isPrimitive()) {
				if (elementManifest == boolean.class) {
					BoolConverter.serialize((boolean[]) value, writer);
				} else if (elementManifest == int.class) {
					NumberConverter.serialize((int[]) value, writer);
				} else if (elementManifest == long.class) {
					NumberConverter.serialize((long[]) value, writer);
				} else if (elementManifest == byte.class) {
					BinaryConverter.serialize((byte[]) value, writer);
				} else if (elementManifest == short.class) {
					NumberConverter.serialize((short[]) value, writer);
				} else if (elementManifest == float.class) {
					NumberConverter.serialize((float[]) value, writer);
				} else if (elementManifest == double.class) {
					NumberConverter.serialize((double[]) value, writer);
				} else if (elementManifest == char.class) {
					//TODO? char[] !?
					StringConverter.serialize(new String((char[]) value), writer);
				} else {
					return false;
				}
				return true;
			} else {
				final JsonWriter.WriteObject<Object> elementWriter = (JsonWriter.WriteObject<Object>) tryFindWriter(elementManifest);
				if (elementWriter != null) {
					writer.serialize((Object[]) value, elementWriter);
					return true;
				}
			}
		}
		if (value instanceof Collection) {
			final Collection items = (Collection) value;
			if (items.isEmpty()) {
				writer.writeAscii("[]");
				return true;
			}
			Class<?> baseType = null;
			final Iterator iterator = items.iterator();
			//TODO: pick lowest common denominator!?
			do {
				final Object item = iterator.next();
				if (item != null) {
					Class<?> elementType = item.getClass();
					if (elementType != baseType) {
						if (baseType == null || elementType.isAssignableFrom(baseType)) {
							baseType = elementType;
						}
					}
				}
			} while (iterator.hasNext());
			if (baseType == null) {
				writer.writeByte(JsonWriter.ARRAY_START);
				writer.writeNull();
				for (int i = 1; i < items.size(); i++) {
					writer.writeAscii(",null");
				}
				writer.writeByte(JsonWriter.ARRAY_END);
				return true;
			}
			if (JsonObject.class.isAssignableFrom(baseType)) {
				serialize(writer, (Collection<JsonObject>) items);
				return true;
			}
			final JsonWriter.WriteObject<Object> elementWriter = (JsonWriter.WriteObject<Object>) tryFindWriter(baseType);
			if (elementWriter != null) {
				writer.serialize(items, elementWriter);
				return true;
			}
		}
		return false;
	}

	private static final byte[] NULL = new byte[]{'n', 'u', 'l', 'l'};

	public final void serialize(final Object value, final OutputStream stream) throws IOException {
		if (value == null) {
			stream.write(NULL);
			return;
		}
		final JsonWriter jw = new JsonWriter();
		final Class<?> manifest = value.getClass();
		if (!serialize(jw, manifest, value)) {
			if (fallback != null) {
				fallback.serialize(value, stream);
			} else {
				throw new IOException("Unable to serialize provided object. Failed to find serializer for: " + manifest);
			}
		} else {
			jw.toStream(stream);
		}
	}

	public final void serialize(final JsonWriter writer, final Object value) throws IOException {
		if (value == null) {
			writer.writeNull();
			return;
		}
		final Class<?> manifest = value.getClass();
		if (!serialize(writer, manifest, value)) {
			if (fallback != null) {
				ByteArrayOutputStream stream = new ByteArrayOutputStream();
				fallback.serialize(value, stream);
				writer.writeAscii(stream.toByteArray());
			}
			throw new IOException("Unable to serialize provided object. Failed to find serializer for: " + manifest);
		}
	}
}
