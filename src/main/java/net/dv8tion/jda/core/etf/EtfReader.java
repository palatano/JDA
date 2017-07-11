package net.dv8tion.jda.core.etf;

import java.io.Closeable;
import java.io.DataInput;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import net.dv8tion.jda.core.etf.utils.BoundedDataInput;
import net.dv8tion.jda.core.etf.utils.ByteArrayDataInput;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 
 * @author sedmelluq
 *
 */
public class EtfReader implements Closeable {
  private final byte[] smallTextBuffer;
  private final Inflater permanentInflater;

  public EtfReader(boolean threadSafe) {
    this.smallTextBuffer = threadSafe ? null : new byte[512];
    this.permanentInflater = threadSafe ? null : new Inflater();
  }

  public JSONObject readMessage(BoundedDataInput input) {
    try {
      return readTerm(input);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private JSONObject readTerm(BoundedDataInput input) throws IOException {
    int termHeader = input.readUnsignedByte();
        if (termHeader != 131) {
      throw new IllegalStateException("Should start with a term header.");
    }

    Object element = readObject(input);

    if (!(element instanceof JSONObject)) {
      throw new IllegalStateException("Root element must be a map.");
    }

    return (JSONObject) element;
  }

  private Object readObject(BoundedDataInput input) throws IOException {
    int tag = input.readUnsignedByte();
    switch (tag) {
      case EtfTag.SMALL_INTEGER_EXT:
        return input.readUnsignedByte();
      case EtfTag.COMPRESSED:
        return readCompressedObject(input);
      case EtfTag.INTEGER_EXT:
        return input.readInt();
      case EtfTag.ATOM_EXT:
        return readAtomElement(input);
      case EtfTag.NIL_EXT:
        return new JSONArray();
      case EtfTag.STRING_EXT:
        return readByteListElement(input);
      case EtfTag.LIST_EXT:
        return readListElement(input);
      case EtfTag.BINARY_EXT:
        return readBinaryStringElement(input);
      case EtfTag.SMALL_BIG_EXT:
        return readSmallBigInteger(input);
      case EtfTag.MAP_EXT:
        return readMapElement(input);
      default:
        throw new RuntimeException("Unsupported tag: " + tag);
    }
  }

  private Object readCompressedObject(BoundedDataInput input) throws IOException {
    int uncompressedSize = input.readInt();
    int compressedSize = input.remaining();

    Inflater inflater = permanentInflater != null ? permanentInflater : new Inflater();

    try {
      byte[] buffer = byteBuffer(compressedSize);
      input.readFully(buffer, 0, compressedSize);
      inflater.setInput(buffer, 0, compressedSize);

      byte[] uncompressed = new byte[uncompressedSize];
      int length = inflater.inflate(uncompressed, 0, uncompressedSize);

      if (length != uncompressedSize || !inflater.finished()) {
        throw new IllegalStateException("Should have processed everything.");
      }
      return readObject(new ByteArrayDataInput(uncompressed));
    } catch (DataFormatException e) {
      throw new IOException(e);
    } finally {
      if (inflater != permanentInflater) {
        inflater.end();
      } else {
        inflater.reset();
      }
    }
  }

  private String readBinaryStringElement(DataInput input) throws IOException {
    int length = input.readInt();

    byte[] bytes = byteBuffer(length);
    input.readFully(bytes, 0, length);

    return new String(bytes, 0, length, StandardCharsets.UTF_8);
  }

  private Number readSmallBigInteger(DataInput input) throws IOException {
    int length = input.readUnsignedByte();
    int sign = input.readByte();

    if (length <= 8) {
      return readLong(input, sign, length);
    } else {
      return readBigInteger(input, sign, length);
    }
  }

  private long readLong(DataInput input, int sign, long length) throws IOException {
    long value = 0;

    // sign is lost
    for (int i = 0; i < length; i++) {
      value |= (((long) input.readUnsignedByte()) << (i << 3));
    }

    return sign == 0 ? value : -value;
  }

  public BigInteger readBigInteger(DataInput input, int sign, long length) throws IOException {
    throw new UnsupportedOperationException();
  }

  private Object readAtomElement(DataInput input) throws IOException {
    int length = input.readUnsignedShort();

    byte[] bytes = byteBuffer(length);
    input.readFully(bytes, 0, length);

    String result = new String(bytes, 0, length, StandardCharsets.UTF_8);

    if ("nil".equals(result)) {
      return JSONObject.NULL;
    } else if ("true".equals(result)) {
      return true;
    } else if ("false".equals(result)) {
      return false;
    } else {
      return result;
    }
  }

  private JSONObject readMapElement(BoundedDataInput input) throws IOException {
    int arity = input.readInt();
    JSONObject object = new JSONObject();

    for (int i = 0; i < arity; i++) {
      Object key = readObject(input);
      Object value = readObject(input);

      if (!(key instanceof String)) {
        throw new IllegalStateException("Map key must be a string.");
      }

      object.put((String) key, value);
    }

    return object;
  }

  private List<Integer> readByteListElement(DataInput input) throws IOException {
    int length = input.readUnsignedShort();
    List<Integer> list = new ArrayList<>(length);

    for (int i = 0; i < length; i++) {
      list.add(input.readUnsignedByte());
    }

    return list;
  }

  private JSONArray readListElement(BoundedDataInput input) throws IOException {
    int length = input.readInt();
    JSONArray list = new JSONArray();

    for (int i = 0; i < length; i++) {
      list.put(readObject(input));
    }

    readObject(input);
    return list;
  }

  private byte[] byteBuffer(int size) {
    return smallTextBuffer != null && smallTextBuffer.length >= size ? smallTextBuffer : new byte[size];
  }

  @Override
  public void close() throws IOException {
    if (permanentInflater != null) {
      permanentInflater.end();
    }
  }
}