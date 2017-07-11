package net.dv8tion.jda.core.etf.utils;

import java.io.DataInput;

/**
 * 
 * @author sedmelluq
 *
 */
public interface BoundedDataInput extends DataInput {
  int remaining();
}