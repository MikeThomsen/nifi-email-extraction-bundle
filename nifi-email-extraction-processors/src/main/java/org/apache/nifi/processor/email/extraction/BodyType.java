/**
 * Autogenerated by Avro
 *
 * DO NOT EDIT DIRECTLY
 */
package org.apache.nifi.processor.email.extraction;
@SuppressWarnings("all")
@org.apache.avro.specific.AvroGenerated
public enum BodyType {
  HTML, PLAIN, RTF  ;
  public static final org.apache.avro.Schema SCHEMA$ = new org.apache.avro.Schema.Parser().parse("{\"type\":\"enum\",\"name\":\"BodyType\",\"namespace\":\"org.apache.nifi.processor.email.extraction\",\"symbols\":[\"HTML\",\"PLAIN\",\"RTF\"]}");
  public static org.apache.avro.Schema getClassSchema() { return SCHEMA$; }
}