/*
 * Copyright 2015 OPS4J Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ops4j.dadl.processor;

import static org.ops4j.dadl.io.Constants.BYTE_SIZE;
import static org.ops4j.dadl.io.Constants.NIBBLE_SIZE;

import java.io.IOException;

import org.ops4j.dadl.exc.MarshalException;
import org.ops4j.dadl.exc.UnmarshalException;
import org.ops4j.dadl.io.BitStreamWriter;
import org.ops4j.dadl.metamodel.gen.DadlType;
import org.ops4j.dadl.metamodel.gen.Element;
import org.ops4j.dadl.metamodel.gen.Enumeration;
import org.ops4j.dadl.metamodel.gen.Justification;
import org.ops4j.dadl.metamodel.gen.LengthUnit;
import org.ops4j.dadl.metamodel.gen.SequenceElement;
import org.ops4j.dadl.metamodel.gen.SimpleType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A marshaller serializes info model objects to a bit stream using the formatting rules of a given
 * DADL model.
 *
 * @author hwellmann
 *
 */
public class SimpleTypeWriter {

    private static Logger log = LoggerFactory.getLogger(SimpleTypeWriter.class);

    private DadlContext context;
    private Evaluator evaluator;

    SimpleTypeWriter(DadlContext context, Evaluator evaluator) {
        this.context = context;
        this.evaluator = evaluator;
    }

    /**
     * @param info
     * @param klass
     * @param field
     * @param writer
     * @throws IOException
     */
    void marshalSimpleField(Object fieldInfo, Element element, SimpleType type,
        BitStreamWriter writer) throws IOException {
        log.debug("writing simple value of type {}", type.getName());
        Object calculatedValue = calculateValue(fieldInfo, element);
        switch (type.getContentType()) {
            case INTEGER:
                marshalIntegerField(calculatedValue, element, type, writer);
                break;
            case TEXT:
                marshalTextField(calculatedValue, element, writer);
                break;
            case OPAQUE:
                marshalOpaqueField(calculatedValue, element, writer);
                break;
            default:
                throw new UnsupportedOperationException("unsupported content type: "
                    + type.getContentType());
        }
    }

    void marshalEnumerationField(Object fieldInfo, Element element, Enumeration enumeration,
        BitStreamWriter writer) throws IOException {
        Object rawValue = evaluator.getEnumerationValue(fieldInfo);
        marshalSimpleField(rawValue, element, enumeration, writer);
    }

    /**
     * @param fieldInfo
     * @param element
     * @param type
     * @return
     */
    private Object calculateValue(Object fieldInfo, Element element) {
        if (element instanceof SequenceElement) {
            SequenceElement seqElem = (SequenceElement) element;
            String expr = seqElem.getOutputValueCalc();
            if (expr != null) {
                Object value = evaluator.evaluate(expr);
                evaluator.setParentProperty(element.getName(), value);
                return value;
            }
        }
        return fieldInfo;
    }

    private void marshalIntegerField(Object fieldInfo, Element element, SimpleType type,
        BitStreamWriter writer) throws IOException {
        switch (type.getRepresentation()) {
            case BINARY:
                writeIntegerValueAsBinary(element, fieldInfo, writer);
                break;
            case TEXT:
                writeIntegerValueAsText(element, fieldInfo, writer);
                break;
            default:
                throw new UnsupportedOperationException("unsupported representation: "
                    + type.getRepresentation());
        }
    }

    private void marshalTextField(Object fieldInfo, Element element, BitStreamWriter writer)
        throws IOException {
        if (fieldInfo instanceof String) {
            String text = (String) fieldInfo;
            verifyLength(element, text.length());
            byte[] bytes = text.getBytes(element.getEncoding());
            writer.write(bytes, 0, bytes.length);
        }
    }

    private void verifyLength(DadlType type, int actualLength) {
        Integer length = evaluator.computeLength(type);
        if (length != null && !length.equals(actualLength)) {
            throw new MarshalException("computed length does not match actual length");
        }

    }

    private void marshalOpaqueField(Object fieldInfo, Element element, BitStreamWriter writer)
        throws IOException {
        if (fieldInfo instanceof byte[]) {
            byte[] bytes = (byte[]) fieldInfo;
            verifyLength(element, bytes.length);
            writer.write(bytes, 0, bytes.length);
        }
    }

    void writeIntegerValueAsBinary(DadlType type, Object info, BitStreamWriter writer)
        throws IOException {
        switch (context.getBinaryNumberRep(type)) {
            case BINARY:
                writeIntegerValueAsStandardBinary(type, info, writer);
                break;
            case BCD:
                writeIntegerValueAsBcdBinary(type, info, writer);
                break;
            default:
                throw new UnsupportedOperationException("unsupported binaryNumberRep = "
                    + type.getBinaryNumberRep());
        }
    }

    private void writeIntegerValueAsStandardBinary(DadlType type, Object info,
        BitStreamWriter writer) throws IOException {

        evaluator.setSelf(info);
        if (context.writeValueViaAdapter(type, info, writer)) {
            return;
        }
        long value = 0;
        if (info instanceof Number) {
            value = ((Number) info).longValue();
        }

        long numBits = evaluator.computeLength(type);
        if (type.getLengthUnit() == LengthUnit.BYTE) {
            numBits *= BYTE_SIZE;
        }
        writer.writeBits(value, (int) numBits);
    }

    private void writeIntegerValueAsBcdBinary(DadlType type, Object info, BitStreamWriter writer)
        throws IOException {
        evaluator.setSelf(info);
        if (context.writeValueViaAdapter(type, info, writer)) {
            return;
        }
        long value = 0;
        if (info instanceof Number) {
            value = ((Number) info).longValue();
        }

        long numBits = evaluator.computeLength(type);
        if (type.getLengthUnit() == LengthUnit.BYTE) {
            numBits *= BYTE_SIZE;
        }
        if (numBits % NIBBLE_SIZE != 0) {
            throw new UnmarshalException("BCD bit length must be divisible by 4");
        }
        long numDigits = numBits / NIBBLE_SIZE;
        String s = Long.toString(value);
        long numPaddingDigits = numDigits - s.length();
        if (numPaddingDigits < 0) {
            throw new UnmarshalException("value too large for " + numDigits + " digits");
        }
        for (int i = 0; i < numPaddingDigits; i++) {
            writer.writeBits(0, NIBBLE_SIZE);
        }
        for (int i = 0; i < s.length(); i++) {
            writer.writeBits(s.charAt(i) - '0', NIBBLE_SIZE);
        }
    }

    private void writeIntegerValueAsText(DadlType type, Object info, BitStreamWriter writer)
        throws IOException {
        evaluator.setSelf(info);
        if (context.writeValueViaAdapter(type, info, writer)) {
            return;
        }
        long value = 0;
        if (info instanceof Number) {
            value = ((Number) info).longValue();
            String s = Long.toString(value);
            int numBytes = evaluator.computeLength(type);
            if (s.length() > numBytes) {
                throw new MarshalException(numBytes + " bytes are not sufficient for value " + s);
            }
            writeTextWithPadding(s, numBytes, type.getTextNumberJustification(),
                type.getTextNumberPadCharacter(), writer);
        }
    }

    private void writeTextWithPadding(String s, int numBytes, Justification justification,
        String padCharacter, BitStreamWriter writer) throws IOException {
        int totalPadding = numBytes - s.length();
        int leftPadding = totalPadding;
        int rightPadding = totalPadding;
        switch (justification) {
            case LEFT:
                leftPadding = 0;
                break;
            case RIGHT:
                rightPadding = 0;
                break;
            case CENTER:
                leftPadding /= 2;
                rightPadding = leftPadding;
                if (leftPadding + rightPadding < totalPadding) {
                    leftPadding++;
                }
                break;
            default:
                throw new IllegalArgumentException(justification.toString());
        }
        for (int i = 0; i < leftPadding; i++) {
            writer.writeBytes(padCharacter);
        }
        writer.writeBytes(s);
        for (int i = 0; i < rightPadding; i++) {
            writer.writeBytes(padCharacter);
        }
    }
}
