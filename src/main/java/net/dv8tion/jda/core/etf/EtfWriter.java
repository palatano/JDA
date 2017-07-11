package net.dv8tion.jda.core.etf;

import java.io.ByteArrayOutputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 
 * @author sedmelluq
 *
 */
public class EtfWriter {
  private final byte[] smallTextBuffer;

  public EtfWriter(boolean threadSafe) {
    this.smallTextBuffer = threadSafe ? null : new byte[512];
  }

  public void writeMessage(JSONObject message, DataOutput output) {
    try {
      output.write(131);
      writeObject(message, output);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

    public byte[] writeMessage(JSONObject message)
    {
        try
        {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            writeMessage(message, new DataOutputStream(stream));
            stream.flush();
            return stream.toByteArray();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

  private void writeObject(Object object, DataOutput output) throws IOException {
        if (object instanceof String) {
      writeStringBytes((String) object, output);
    } else if (object instanceof JSONObject) {
      writeMap((JSONObject) object, output);
    } else if (object instanceof Number) {
      if (object instanceof Byte) {
        output.write(EtfTag.SMALL_INTEGER_EXT);
        output.write((byte) object);
      } else if (object instanceof Long) {
        writeLong((Long) object, output);
      } else if (object instanceof Double || object instanceof Float) {
        output.write(EtfTag.NEW_FLOAT_EXT);
        output.writeFloat(((Number) object).floatValue());
      } else {
        output.write(EtfTag.INTEGER_EXT);
        output.writeInt(((Number) object).intValue());
      }
    } else if (object instanceof JSONArray) {
      writeList((JSONArray) object, output);
    } else if (object instanceof Boolean) {
      writeStringAtom(object == Boolean.TRUE ? "true" : "false", output);
    } else if (JSONObject.NULL.equals(object)) {
      writeStringAtom("nil", output);
    } else {
      throw new IllegalArgumentException("Invalid object type to write: " + object.getClass().getName());
    }
  }

  private void writeStringAtom(String string, DataOutput output) throws IOException {
    int length = string.length();
    output.write(EtfTag.ATOM_EXT);
    output.writeShort(length);

    byte[] buffer = byteBuffer(length);
    char character;

    for (int i = 0; i < length; i++) {
      character = string.charAt(i);

      if ((character >= 0x0001) && (character <= 0x007F)) {
        buffer[i] = (byte) character;
      } else {
        throw new IllegalStateException("Cannot have non-ascii characters in string atoms.");
      }
    }

    output.write(buffer, 0, length);
  }

  private void writeStringBytes(String string, DataOutput output) throws IOException {
    output.write(EtfTag.BINARY_EXT);

    int length = string.length();
    int binaryLength = 0;
    char character;

    for (int i = 0; i < length; i++) {
      character = string.charAt(i);
      if ((character >= 0x0001) && (character <= 0x007F)) {
        binaryLength++;
      } else if (i > 0x07FF) {
        binaryLength += 3;
      } else {
        binaryLength += 2;
      }
    }

    output.writeInt(binaryLength);

    byte[] buffer = byteBuffer(binaryLength);
    int position = 0;

    for (int i = 0; i < length; i++) {
      character = string.charAt(i);

      if ((character >= 0x0001) && (character <= 0x007F)) {
        buffer[position++] = (byte) character;
      } else if (character > 0x07FF) {
        buffer[position++] = (byte) (0xE0 | ((character >> 12) & 0x0F));
        buffer[position++] = (byte) (0x80 | ((character >>  6) & 0x3F));
        buffer[position++] = (byte) (0x80 | (character & 0x3F));
      } else {
        buffer[position++] = (byte) (0xC0 | ((character >> 6) & 0x1F));
        buffer[position++] = (byte) (0x80 | (character & 0x3F));
      }
    }

    output.write(buffer, 0, binaryLength);
  }

  private void writeMap(JSONObject object, DataOutput output) throws IOException {
    output.write(EtfTag.MAP_EXT);
    output.writeInt(object.length());

    for (String key : object.keySet()) {
        if (!(key instanceof String)) {
            throw new IllegalArgumentException("Map key must be a string.");
          }
      Object value = object.get(key);
      writeObject(key, output);
      writeObject(value, output);
    }
  }

  private void writeLong(long value, DataOutput output) throws IOException {
    output.write(EtfTag.SMALL_BIG_EXT);
    int bytes = numberOfBytes(value);

    output.write(bytes);
    output.write(0);

    for (int i = 0; i < bytes; i++) {
      output.write((int) (value & 0xFF));
      value >>>= 8;
    }
  }

  private static int numberOfBytes(long value) {
    if (value == 0) {
      return 0;
    }

    int bytes = 8;
    int x = (int) (value >>> 32);

    if (x == 0) {
      bytes -= 4;
      x = (int) value;
    }

    if (x >>> 16 == 0) {
      bytes -= 2;
      x <<= 16;
    }

    if (x >>> 24 == 0) {
      bytes -= 1;
    }

    return bytes;
  }

  private void writeList(JSONArray list, DataOutput output) throws IOException {
    output.write(EtfTag.LIST_EXT);
    output.writeInt(list.length());

    for (Object element : list) {
      writeObject(element, output);
    }

    output.write(EtfTag.NIL_EXT);
  }

  private byte[] byteBuffer(int size) {
    return smallTextBuffer != null && smallTextBuffer.length >= size ? smallTextBuffer : new byte[size];
  }
}