package net.dv8tion.jda.core.etf.utils;

import java.io.IOException;

/**
 * 
 * @author sedmelluq
 *
 */
public class ByteArrayDataInput implements BoundedDataInput {
  private final byte[] data;
  private int position;

  public ByteArrayDataInput(byte[] data) {
    this.data = data;
  }

  @Override
  public int remaining() {
    return data.length - position;
  }

  @Override
  public void readFully(byte[] buffer) throws IOException {
    System.arraycopy(data, position, buffer, 0, buffer.length);
    position += buffer.length;
  }

  @Override
  public void readFully(byte[] buffer, int offset, int length) throws IOException {
    System.arraycopy(data, position, buffer, offset, length);
    position += length;
  }

  @Override
  public int skipBytes(int count) throws IOException {
    position += count;
    return count;
  }

  @Override
  public boolean readBoolean() throws IOException {
    return data[position++] != 0;
  }

  @Override
  public byte readByte() throws IOException {
    return data[position++];
  }

  @Override
  public int readUnsignedByte() throws IOException {
    return data[position++] & 0xFF;
  }

  @Override
  public short readShort() throws IOException {
    return (short) readUnsignedShort();
  }

  @Override
  public int readUnsignedShort() throws IOException {
    return (data[position++] << 8) | (data[position++] << 0);
  }

  @Override
  public char readChar() throws IOException {
    return (char) readUnsignedShort();
  }

  @Override
  public int readInt() throws IOException {
    return (((data[position++] & 0xFF) << 24) | ((data[position++] & 0xFF) << 16) | ((data[position++] & 0xFF) << 8) | ((data[position++] & 0xFF) << 0));
  }

  @Override
  public long readLong() throws IOException {
    return (((long) data[position++] << 56) | ((long) data[position++] << 48) | ((long) data[position++] << 40) | ((long) data[position++] << 32)) |
        (((long) data[position++] << 24) | ((long) data[position++] << 16) | ((long) data[position++] << 8) | ((long) data[position++] << 0));
  }

  @Override
  public float readFloat() throws IOException {
    return Float.intBitsToFloat(readInt());
  }

  @Override
  public double readDouble() throws IOException {
    return Double.longBitsToDouble(readLong());
  }

  @Override
  public String readLine() throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public String readUTF() throws IOException {
    throw new UnsupportedOperationException();
  }
}