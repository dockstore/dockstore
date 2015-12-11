package io.dockstore.client.cli.cwl;

/**
 * Autogenerated by Avro
 * 
 * DO NOT EDIT DIRECTLY
 */
@SuppressWarnings("all")
@org.apache.avro.specific.AvroGenerated
public class InputParameter extends org.apache.avro.specific.SpecificRecordBase implements org.apache.avro.specific.SpecificRecord {
  public static final org.apache.avro.Schema SCHEMA$ = new org.apache.avro.Schema.Parser().parse("{\"type\":\"record\",\"name\":\"InputParameter\",\"fields\":[{\"name\":\"type\",\"type\":[\"null\",{\"type\":\"enum\",\"name\":\"CWLType\",\"symbols\":[\"File\"],\"extends\":\"https://w3id.org/cwl/salad#PrimitiveType\",\"symbol\":[\"https://w3id.org/cwl/salad#null\",\"http://www.w3.org/2001/XMLSchema#boolean\",\"http://www.w3.org/2001/XMLSchema#int\",\"http://www.w3.org/2001/XMLSchema#long\",\"http://www.w3.org/2001/XMLSchema#float\",\"http://www.w3.org/2001/XMLSchema#double\",\"http://www.w3.org/2001/XMLSchema#string\",\"https://w3id.org/cwl/cwl#File\"]},{\"type\":\"record\",\"name\":\"InputRecordSchema\",\"fields\":[{\"name\":\"type\",\"type\":{\"type\":\"enum\",\"name\":\"Record_symbol\",\"symbols\":[\"record\"]},\"doc\":\"Must be `record`\",\"jsonldPredicate\":{\"_type\":\"@vocab\",\"_id\":\"https://w3id.org/cwl/salad#type\"},\"inherited_from\":\"https://w3id.org/cwl/salad#RecordSchema\"},{\"name\":\"fields\",\"type\":[\"null\",{\"type\":\"array\",\"items\":{\"type\":\"record\",\"name\":\"InputRecordField\",\"fields\":[{\"name\":\"name\",\"type\":\"string\",\"doc\":\"The name of the field\\n\",\"jsonldPredicate\":\"@id\",\"inherited_from\":\"https://w3id.org/cwl/salad#RecordField\"},{\"name\":\"doc\",\"type\":[\"null\",\"string\"],\"doc\":\"A documentation string for this field\\n\",\"jsonldPredicate\":\"sld:doc\",\"inherited_from\":\"https://w3id.org/cwl/salad#RecordField\"},{\"name\":\"type\",\"type\":[{\"type\":\"enum\",\"name\":\"PrimitiveType\",\"doc\":\"Salad data types are based on Avro schema declarations.  Refer to the\\n[Avro schema declaration documentation](https://avro.apache.org/docs/current/spec.html#schemas) for\\ndetailed information.\\n\\n## Simple types\\n\\n* **null**: no value\\n* **boolean**: a binary value\\n* **int**: 32-bit signed integer\\n* **long**: 64-bit signed integer\\n* **float**: single precision (32-bit) IEEE 754 floating-point number\\n* **double**: double precision (64-bit) IEEE 754 floating-point number\\n* **string**: Unicode character sequence\\n\",\"symbols\":[\"null\",\"boolean\",\"int\",\"long\",\"float\",\"double\",\"string\"]},\"InputRecordSchema\",{\"type\":\"record\",\"name\":\"InputEnumSchema\",\"fields\":[{\"name\":\"type\",\"type\":{\"type\":\"enum\",\"name\":\"Enum_symbol\",\"symbols\":[\"enum\"]},\"doc\":\"Must be `enum`\",\"jsonldPredicate\":{\"_type\":\"@vocab\",\"_id\":\"https://w3id.org/cwl/salad#type\"},\"inherited_from\":\"https://w3id.org/cwl/salad#EnumSchema\"},{\"name\":\"symbols\",\"type\":[{\"type\":\"array\",\"items\":\"string\"}],\"doc\":\"Defines the set of valid symbols.\",\"jsonldPredicate\":{\"_type\":\"@id\",\"_id\":\"https://w3id.org/cwl/salad#symbols\",\"identity\":true},\"inherited_from\":\"https://w3id.org/cwl/salad#EnumSchema\"},{\"name\":\"inputBinding\",\"type\":[\"null\",{\"type\":\"record\",\"name\":\"Binding\",\"fields\":[{\"name\":\"loadContents\",\"type\":[\"null\",\"boolean\"],\"doc\":\"Only applies when `type` is `File`.  Read up to the first 64 KiB of text from the file and place it in the\\n\\\"contents\\\" field of the file object for manipulation by expressions.\\n\"},{\"name\":\"secondaryFiles\",\"type\":[\"null\",\"string\",{\"type\":\"enum\",\"name\":\"Expression\",\"doc\":\"Not a real type.  Indicates that a field must allow expressions.\\n\",\"symbols\":[\"ExpressionPlaceholder\"],\"docAfter\":\"https://w3id.org/cwl/cwl#ExpressionTool\"},{\"type\":\"array\",\"items\":[\"string\",\"Expression\"]}],\"doc\":\"Only applies when `type` is `File`.  Describes files that must be\\nincluded alongside the primary file.\\n\\nIf the value is an expression, the context of the expression is the input\\nor output File parameter to which this binding applies.\\n\\nIf the value is a string, it specifies that the following pattern\\nshould be applied to the primary file:\\n\\n  1. If string begins with one or more caret `^` characters, for each\\n    caret, remove the last file extension from the path (the last\\n    period `.` and all following characters).  If there are no file\\n    extensions, the path is unchanged.\\n  2. Append the remainder of the string to the end of the file path.\\n\",\"jsonldPredicate\":\"cwl:secondaryFiles\"}],\"docParent\":\"https://w3id.org/cwl/cwl#Parameter\"}],\"jsonldPredicate\":\"cwl:inputBinding\"}],\"docParent\":\"https://w3id.org/cwl/cwl#InputParameter\",\"extends\":\"https://w3id.org/cwl/salad#EnumSchema\"},{\"type\":\"record\",\"name\":\"InputArraySchema\",\"fields\":[{\"name\":\"type\",\"type\":{\"type\":\"enum\",\"name\":\"Array_symbol\",\"symbols\":[\"array\"]},\"doc\":\"Must be `array`\",\"jsonldPredicate\":{\"_type\":\"@vocab\",\"_id\":\"https://w3id.org/cwl/salad#type\"},\"inherited_from\":\"https://w3id.org/cwl/salad#ArraySchema\"},{\"name\":\"items\",\"type\":[\"PrimitiveType\",\"InputRecordSchema\",\"InputEnumSchema\",\"InputArraySchema\",\"string\",{\"type\":\"array\",\"items\":[\"PrimitiveType\",\"InputRecordSchema\",\"InputEnumSchema\",\"InputArraySchema\",\"string\"]}],\"doc\":\"Defines the type of the array elements.\",\"jsonldPredicate\":{\"_type\":\"@vocab\",\"_id\":\"https://w3id.org/cwl/salad#items\"},\"inherited_from\":\"https://w3id.org/cwl/salad#ArraySchema\"},{\"name\":\"inputBinding\",\"type\":[\"null\",\"Binding\"],\"jsonldPredicate\":\"cwl:inputBinding\"}],\"docParent\":\"https://w3id.org/cwl/cwl#InputParameter\",\"extends\":\"https://w3id.org/cwl/salad#ArraySchema\",\"specialize\":[{\"specializeFrom\":\"https://w3id.org/cwl/salad#RecordSchema\",\"specializeTo\":\"https://w3id.org/cwl/cwl#InputRecordSchema\"},{\"specializeFrom\":\"https://w3id.org/cwl/salad#EnumSchema\",\"specializeTo\":\"https://w3id.org/cwl/cwl#InputEnumSchema\"},{\"specializeFrom\":\"https://w3id.org/cwl/salad#ArraySchema\",\"specializeTo\":\"https://w3id.org/cwl/cwl#InputArraySchema\"}]},\"string\",{\"type\":\"array\",\"items\":[\"PrimitiveType\",\"InputRecordSchema\",\"InputEnumSchema\",\"InputArraySchema\",\"string\"]}],\"doc\":\"The field type\\n\",\"jsonldPredicate\":{\"_type\":\"@vocab\",\"_id\":\"https://w3id.org/cwl/salad#type\"},\"inherited_from\":\"https://w3id.org/cwl/salad#RecordField\"},{\"name\":\"inputBinding\",\"type\":[\"null\",\"Binding\"],\"jsonldPredicate\":\"cwl:inputBinding\"}],\"extends\":\"https://w3id.org/cwl/salad#RecordField\",\"specialize\":[{\"specializeFrom\":\"https://w3id.org/cwl/salad#RecordSchema\",\"specializeTo\":\"https://w3id.org/cwl/cwl#InputRecordSchema\"},{\"specializeFrom\":\"https://w3id.org/cwl/salad#EnumSchema\",\"specializeTo\":\"https://w3id.org/cwl/cwl#InputEnumSchema\"},{\"specializeFrom\":\"https://w3id.org/cwl/salad#ArraySchema\",\"specializeTo\":\"https://w3id.org/cwl/cwl#InputArraySchema\"}]}}],\"doc\":\"Defines the fields of the record.\",\"jsonldPredicate\":\"sld:fields\",\"inherited_from\":\"https://w3id.org/cwl/salad#RecordSchema\"}],\"docParent\":\"https://w3id.org/cwl/cwl#InputParameter\",\"extends\":\"https://w3id.org/cwl/salad#RecordSchema\",\"specialize\":[{\"specializeFrom\":\"https://w3id.org/cwl/salad#RecordField\",\"specializeTo\":\"https://w3id.org/cwl/cwl#InputRecordField\"}]},\"InputEnumSchema\",\"InputArraySchema\",\"string\",{\"type\":\"array\",\"items\":[\"CWLType\",\"InputRecordSchema\",\"InputEnumSchema\",\"InputArraySchema\",\"string\"]}],\"doc\":\"Specify valid types of data that may be assigned to this parameter.\\n\",\"jsonldPredicate\":{\"_type\":\"@vocab\",\"_id\":\"https://w3id.org/cwl/salad#type\"},\"inherited_from\":\"https://w3id.org/cwl/cwl#Parameter\"},{\"name\":\"label\",\"type\":[\"null\",\"string\"],\"doc\":\"A short, human-readable label of this parameter object.\",\"jsonldPredicate\":\"rdfs:label\",\"inherited_from\":\"https://w3id.org/cwl/cwl#Parameter\"},{\"name\":\"description\",\"type\":[\"null\",\"string\"],\"doc\":\"A long, human-readable description of this parameter object.\",\"jsonldPredicate\":\"rdfs:comment\",\"inherited_from\":\"https://w3id.org/cwl/cwl#Parameter\"},{\"name\":\"streamable\",\"type\":[\"null\",\"boolean\"],\"doc\":\"Currently only applies if `type` is `File`.  A value of `true`\\nindicates that the file is read or written sequentially without\\nseeking.  An implementation may use this flag to indicate whether it is\\nvalid to stream file contents using a named pipe.  Default: `false`.\\n\",\"inherited_from\":\"https://w3id.org/cwl/cwl#Parameter\"},{\"name\":\"default\",\"type\":[\"null\",{\"type\":\"enum\",\"name\":\"Any\",\"doc\":\"The **Any** type validates for any non-null value.\\n\",\"symbols\":[\"Any\"]}],\"doc\":\"The default value for this parameter if not provided in the input\\nobject.\\n\",\"jsonldPredicate\":\"cwl:default\",\"inherited_from\":\"https://w3id.org/cwl/cwl#Parameter\"},{\"name\":\"id\",\"type\":\"string\",\"doc\":\"The unique identifier for this parameter object.\",\"jsonldPredicate\":\"@id\"},{\"name\":\"inputBinding\",\"type\":[\"null\",\"Binding\"],\"doc\":\"Describes how to handle the inputs of a process and convert them\\ninto a concrete form for execution, such as command line parameters.\\n\",\"jsonldPredic","ate\":\"cwl:inputBinding\"}],\"docAfter\":\"https://w3id.org/cwl/cwl#Parameter\",\"extends\":\"https://w3id.org/cwl/cwl#Parameter\",\"specialize\":[{\"specializeFrom\":\"https://w3id.org/cwl/salad#RecordSchema\",\"specializeTo\":\"https://w3id.org/cwl/cwl#InputRecordSchema\"},{\"specializeFrom\":\"https://w3id.org/cwl/salad#EnumSchema\",\"specializeTo\":\"https://w3id.org/cwl/cwl#InputEnumSchema\"},{\"specializeFrom\":\"https://w3id.org/cwl/salad#ArraySchema\",\"specializeTo\":\"https://w3id.org/cwl/cwl#InputArraySchema\"}]}");
  public static org.apache.avro.Schema getClassSchema() { return SCHEMA$; }
  /** Specify valid types of data that may be assigned to this parameter.
 */
  @Deprecated public java.lang.Object type;
  /** A short, human-readable label of this parameter object. */
  @Deprecated public java.lang.CharSequence label;
  /** A long, human-readable description of this parameter object. */
  @Deprecated public java.lang.CharSequence description;
  /** Currently only applies if `type` is `File`.  A value of `true`
indicates that the file is read or written sequentially without
seeking.  An implementation may use this flag to indicate whether it is
valid to stream file contents using a named pipe.  Default: `false`.
 */
  @Deprecated public java.lang.Boolean streamable;
  /** The default value for this parameter if not provided in the input
object.
 */
  @Deprecated public Any default$;
  /** The unique identifier for this parameter object. */
  @Deprecated public java.lang.CharSequence id;
  /** Describes how to handle the inputs of a process and convert them
into a concrete form for execution, such as command line parameters.
 */
  @Deprecated public Binding inputBinding;

  /**
   * Default constructor.  Note that this does not initialize fields
   * to their default values from the schema.  If that is desired then
   * one should use <code>newBuilder()</code>. 
   */
  public InputParameter() {}

  /**
   * All-args constructor.
   */
  public InputParameter(java.lang.Object type, java.lang.CharSequence label, java.lang.CharSequence description, java.lang.Boolean streamable, Any default$, java.lang.CharSequence id, Binding inputBinding) {
    this.type = type;
    this.label = label;
    this.description = description;
    this.streamable = streamable;
    this.default$ = default$;
    this.id = id;
    this.inputBinding = inputBinding;
  }

  public org.apache.avro.Schema getSchema() { return SCHEMA$; }
  // Used by DatumWriter.  Applications should not call. 
  public java.lang.Object get(int field$) {
    switch (field$) {
    case 0: return type;
    case 1: return label;
    case 2: return description;
    case 3: return streamable;
    case 4: return default$;
    case 5: return id;
    case 6: return inputBinding;
    default: throw new org.apache.avro.AvroRuntimeException("Bad index");
    }
  }
  // Used by DatumReader.  Applications should not call. 
  @SuppressWarnings(value="unchecked")
  public void put(int field$, java.lang.Object value$) {
    switch (field$) {
    case 0: type = (java.lang.Object)value$; break;
    case 1: label = (java.lang.CharSequence)value$; break;
    case 2: description = (java.lang.CharSequence)value$; break;
    case 3: streamable = (java.lang.Boolean)value$; break;
    case 4: default$ = (Any)value$; break;
    case 5: id = (java.lang.CharSequence)value$; break;
    case 6: inputBinding = (Binding)value$; break;
    default: throw new org.apache.avro.AvroRuntimeException("Bad index");
    }
  }

  /**
   * Gets the value of the 'type' field.
   * Specify valid types of data that may be assigned to this parameter.
   */
  public java.lang.Object getType() {
    return type;
  }

  /**
   * Sets the value of the 'type' field.
   * Specify valid types of data that may be assigned to this parameter.
   * @param value the value to set.
   */
  public void setType(java.lang.Object value) {
    this.type = value;
  }

  /**
   * Gets the value of the 'label' field.
   * A short, human-readable label of this parameter object.   */
  public java.lang.CharSequence getLabel() {
    return label;
  }

  /**
   * Sets the value of the 'label' field.
   * A short, human-readable label of this parameter object.   * @param value the value to set.
   */
  public void setLabel(java.lang.CharSequence value) {
    this.label = value;
  }

  /**
   * Gets the value of the 'description' field.
   * A long, human-readable description of this parameter object.   */
  public java.lang.CharSequence getDescription() {
    return description;
  }

  /**
   * Sets the value of the 'description' field.
   * A long, human-readable description of this parameter object.   * @param value the value to set.
   */
  public void setDescription(java.lang.CharSequence value) {
    this.description = value;
  }

  /**
   * Gets the value of the 'streamable' field.
   * Currently only applies if `type` is `File`.  A value of `true`
indicates that the file is read or written sequentially without
seeking.  An implementation may use this flag to indicate whether it is
valid to stream file contents using a named pipe.  Default: `false`.
   */
  public java.lang.Boolean getStreamable() {
    return streamable;
  }

  /**
   * Sets the value of the 'streamable' field.
   * Currently only applies if `type` is `File`.  A value of `true`
indicates that the file is read or written sequentially without
seeking.  An implementation may use this flag to indicate whether it is
valid to stream file contents using a named pipe.  Default: `false`.
   * @param value the value to set.
   */
  public void setStreamable(java.lang.Boolean value) {
    this.streamable = value;
  }

  /**
   * Gets the value of the 'default$' field.
   * The default value for this parameter if not provided in the input
object.
   */
  public Any getDefault$() {
    return default$;
  }

  /**
   * Sets the value of the 'default$' field.
   * The default value for this parameter if not provided in the input
object.
   * @param value the value to set.
   */
  public void setDefault$(Any value) {
    this.default$ = value;
  }

  /**
   * Gets the value of the 'id' field.
   * The unique identifier for this parameter object.   */
  public java.lang.CharSequence getId() {
    return id;
  }

  /**
   * Sets the value of the 'id' field.
   * The unique identifier for this parameter object.   * @param value the value to set.
   */
  public void setId(java.lang.CharSequence value) {
    this.id = value;
  }

  /**
   * Gets the value of the 'inputBinding' field.
   * Describes how to handle the inputs of a process and convert them
into a concrete form for execution, such as command line parameters.
   */
  public Binding getInputBinding() {
    return inputBinding;
  }

  /**
   * Sets the value of the 'inputBinding' field.
   * Describes how to handle the inputs of a process and convert them
into a concrete form for execution, such as command line parameters.
   * @param value the value to set.
   */
  public void setInputBinding(Binding value) {
    this.inputBinding = value;
  }

  /** Creates a new InputParameter RecordBuilder */
  public static InputParameter.Builder newBuilder() {
    return new InputParameter.Builder();
  }
  
  /** Creates a new InputParameter RecordBuilder by copying an existing Builder */
  public static InputParameter.Builder newBuilder(InputParameter.Builder other) {
    return new InputParameter.Builder(other);
  }
  
  /** Creates a new InputParameter RecordBuilder by copying an existing InputParameter instance */
  public static InputParameter.Builder newBuilder(InputParameter other) {
    return new InputParameter.Builder(other);
  }
  
  /**
   * RecordBuilder for InputParameter instances.
   */
  public static class Builder extends org.apache.avro.specific.SpecificRecordBuilderBase<InputParameter>
    implements org.apache.avro.data.RecordBuilder<InputParameter> {

    private java.lang.Object type;
    private java.lang.CharSequence label;
    private java.lang.CharSequence description;
    private java.lang.Boolean streamable;
    private Any default$;
    private java.lang.CharSequence id;
    private Binding inputBinding;

    /** Creates a new Builder */
    private Builder() {
      super(InputParameter.SCHEMA$);
    }
    
    /** Creates a Builder by copying an existing Builder */
    private Builder(InputParameter.Builder other) {
      super(other);
      if (isValidValue(fields()[0], other.type)) {
        this.type = data().deepCopy(fields()[0].schema(), other.type);
        fieldSetFlags()[0] = true;
      }
      if (isValidValue(fields()[1], other.label)) {
        this.label = data().deepCopy(fields()[1].schema(), other.label);
        fieldSetFlags()[1] = true;
      }
      if (isValidValue(fields()[2], other.description)) {
        this.description = data().deepCopy(fields()[2].schema(), other.description);
        fieldSetFlags()[2] = true;
      }
      if (isValidValue(fields()[3], other.streamable)) {
        this.streamable = data().deepCopy(fields()[3].schema(), other.streamable);
        fieldSetFlags()[3] = true;
      }
      if (isValidValue(fields()[4], other.default$)) {
        this.default$ = data().deepCopy(fields()[4].schema(), other.default$);
        fieldSetFlags()[4] = true;
      }
      if (isValidValue(fields()[5], other.id)) {
        this.id = data().deepCopy(fields()[5].schema(), other.id);
        fieldSetFlags()[5] = true;
      }
      if (isValidValue(fields()[6], other.inputBinding)) {
        this.inputBinding = data().deepCopy(fields()[6].schema(), other.inputBinding);
        fieldSetFlags()[6] = true;
      }
    }
    
    /** Creates a Builder by copying an existing InputParameter instance */
    private Builder(InputParameter other) {
            super(InputParameter.SCHEMA$);
      if (isValidValue(fields()[0], other.type)) {
        this.type = data().deepCopy(fields()[0].schema(), other.type);
        fieldSetFlags()[0] = true;
      }
      if (isValidValue(fields()[1], other.label)) {
        this.label = data().deepCopy(fields()[1].schema(), other.label);
        fieldSetFlags()[1] = true;
      }
      if (isValidValue(fields()[2], other.description)) {
        this.description = data().deepCopy(fields()[2].schema(), other.description);
        fieldSetFlags()[2] = true;
      }
      if (isValidValue(fields()[3], other.streamable)) {
        this.streamable = data().deepCopy(fields()[3].schema(), other.streamable);
        fieldSetFlags()[3] = true;
      }
      if (isValidValue(fields()[4], other.default$)) {
        this.default$ = data().deepCopy(fields()[4].schema(), other.default$);
        fieldSetFlags()[4] = true;
      }
      if (isValidValue(fields()[5], other.id)) {
        this.id = data().deepCopy(fields()[5].schema(), other.id);
        fieldSetFlags()[5] = true;
      }
      if (isValidValue(fields()[6], other.inputBinding)) {
        this.inputBinding = data().deepCopy(fields()[6].schema(), other.inputBinding);
        fieldSetFlags()[6] = true;
      }
    }

    /** Gets the value of the 'type' field */
    public java.lang.Object getType() {
      return type;
    }
    
    /** Sets the value of the 'type' field */
    public InputParameter.Builder setType(java.lang.Object value) {
      validate(fields()[0], value);
      this.type = value;
      fieldSetFlags()[0] = true;
      return this; 
    }
    
    /** Checks whether the 'type' field has been set */
    public boolean hasType() {
      return fieldSetFlags()[0];
    }
    
    /** Clears the value of the 'type' field */
    public InputParameter.Builder clearType() {
      type = null;
      fieldSetFlags()[0] = false;
      return this;
    }

    /** Gets the value of the 'label' field */
    public java.lang.CharSequence getLabel() {
      return label;
    }
    
    /** Sets the value of the 'label' field */
    public InputParameter.Builder setLabel(java.lang.CharSequence value) {
      validate(fields()[1], value);
      this.label = value;
      fieldSetFlags()[1] = true;
      return this; 
    }
    
    /** Checks whether the 'label' field has been set */
    public boolean hasLabel() {
      return fieldSetFlags()[1];
    }
    
    /** Clears the value of the 'label' field */
    public InputParameter.Builder clearLabel() {
      label = null;
      fieldSetFlags()[1] = false;
      return this;
    }

    /** Gets the value of the 'description' field */
    public java.lang.CharSequence getDescription() {
      return description;
    }
    
    /** Sets the value of the 'description' field */
    public InputParameter.Builder setDescription(java.lang.CharSequence value) {
      validate(fields()[2], value);
      this.description = value;
      fieldSetFlags()[2] = true;
      return this; 
    }
    
    /** Checks whether the 'description' field has been set */
    public boolean hasDescription() {
      return fieldSetFlags()[2];
    }
    
    /** Clears the value of the 'description' field */
    public InputParameter.Builder clearDescription() {
      description = null;
      fieldSetFlags()[2] = false;
      return this;
    }

    /** Gets the value of the 'streamable' field */
    public java.lang.Boolean getStreamable() {
      return streamable;
    }
    
    /** Sets the value of the 'streamable' field */
    public InputParameter.Builder setStreamable(java.lang.Boolean value) {
      validate(fields()[3], value);
      this.streamable = value;
      fieldSetFlags()[3] = true;
      return this; 
    }
    
    /** Checks whether the 'streamable' field has been set */
    public boolean hasStreamable() {
      return fieldSetFlags()[3];
    }
    
    /** Clears the value of the 'streamable' field */
    public InputParameter.Builder clearStreamable() {
      streamable = null;
      fieldSetFlags()[3] = false;
      return this;
    }

    /** Gets the value of the 'default$' field */
    public Any getDefault$() {
      return default$;
    }
    
    /** Sets the value of the 'default$' field */
    public InputParameter.Builder setDefault$(Any value) {
      validate(fields()[4], value);
      this.default$ = value;
      fieldSetFlags()[4] = true;
      return this; 
    }
    
    /** Checks whether the 'default$' field has been set */
    public boolean hasDefault$() {
      return fieldSetFlags()[4];
    }
    
    /** Clears the value of the 'default$' field */
    public InputParameter.Builder clearDefault$() {
      default$ = null;
      fieldSetFlags()[4] = false;
      return this;
    }

    /** Gets the value of the 'id' field */
    public java.lang.CharSequence getId() {
      return id;
    }
    
    /** Sets the value of the 'id' field */
    public InputParameter.Builder setId(java.lang.CharSequence value) {
      validate(fields()[5], value);
      this.id = value;
      fieldSetFlags()[5] = true;
      return this; 
    }
    
    /** Checks whether the 'id' field has been set */
    public boolean hasId() {
      return fieldSetFlags()[5];
    }
    
    /** Clears the value of the 'id' field */
    public InputParameter.Builder clearId() {
      id = null;
      fieldSetFlags()[5] = false;
      return this;
    }

    /** Gets the value of the 'inputBinding' field */
    public Binding getInputBinding() {
      return inputBinding;
    }
    
    /** Sets the value of the 'inputBinding' field */
    public InputParameter.Builder setInputBinding(Binding value) {
      validate(fields()[6], value);
      this.inputBinding = value;
      fieldSetFlags()[6] = true;
      return this; 
    }
    
    /** Checks whether the 'inputBinding' field has been set */
    public boolean hasInputBinding() {
      return fieldSetFlags()[6];
    }
    
    /** Clears the value of the 'inputBinding' field */
    public InputParameter.Builder clearInputBinding() {
      inputBinding = null;
      fieldSetFlags()[6] = false;
      return this;
    }

    @Override
    public InputParameter build() {
      try {
        InputParameter record = new InputParameter();
        record.type = fieldSetFlags()[0] ? this.type : (java.lang.Object) defaultValue(fields()[0]);
        record.label = fieldSetFlags()[1] ? this.label : (java.lang.CharSequence) defaultValue(fields()[1]);
        record.description = fieldSetFlags()[2] ? this.description : (java.lang.CharSequence) defaultValue(fields()[2]);
        record.streamable = fieldSetFlags()[3] ? this.streamable : (java.lang.Boolean) defaultValue(fields()[3]);
        record.default$ = fieldSetFlags()[4] ? this.default$ : (Any) defaultValue(fields()[4]);
        record.id = fieldSetFlags()[5] ? this.id : (java.lang.CharSequence) defaultValue(fields()[5]);
        record.inputBinding = fieldSetFlags()[6] ? this.inputBinding : (Binding) defaultValue(fields()[6]);
        return record;
      } catch (Exception e) {
        throw new org.apache.avro.AvroRuntimeException(e);
      }
    }
  }
}
