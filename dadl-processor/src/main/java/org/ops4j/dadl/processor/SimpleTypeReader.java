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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

import org.ops4j.dadl.exc.UnmarshalException;
import org.ops4j.dadl.io.BitStreamReader;
import org.ops4j.dadl.metamodel.gen.DadlType;
import org.ops4j.dadl.metamodel.gen.Element;
import org.ops4j.dadl.metamodel.gen.Enumeration;
import org.ops4j.dadl.metamodel.gen.LengthKind;
import org.ops4j.dadl.metamodel.gen.LengthUnit;
import org.ops4j.dadl.metamodel.gen.SimpleType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads values of simple types from a bit stream using the formatting rules of a given DADL model.
 *
 * @author hwellmann
 *
 */
public class SimpleTypeReader {

    private static Logger log = LoggerFactory.getLogger(SimpleTypeReader.class);

    private DadlContext context;
    private Evaluator evaluator;

    SimpleTypeReader(DadlContext context, Evaluator evaluator) {
        this.context = context;
        this.evaluator = evaluator;
    }

    @SuppressWarnings("unchecked")
    <T> T readEnumerationValue(Enumeration enumeration, Element element, Class<T> klass,
        BitStreamReader reader) throws IOException {
        log.debug("reading simple value of type {}", enumeration.getName());
        Object info = context.readValueViaAdapter(enumeration, Object.class, reader);
        if (info != null) {
            return (T) info;
        }
        switch (enumeration.getContentType()) {
            case INTEGER:
                info = readIntegerValue(enumeration, element, klass, reader);
                break;
            case TEXT:
                info = readTextValue(enumeration, element, reader);
                break;
            default:
                throw new UnsupportedOperationException(enumeration.getContentType().toString());
        }
        info = evaluator.setSelfEnumeration(info, klass);
        evaluator.checkDiscriminator(info, element);
        return (T) info;
    }

    @SuppressWarnings("unchecked")
    <T> T readSimpleValue(SimpleType simpleType, Element element, Class<T> klass,
        BitStreamReader reader) throws IOException {
        log.debug("reading simple value of type {}", simpleType.getName());
        Object info = context.readValueViaAdapter(simpleType, Object.class, reader);
        if (info != null) {
            return (T) info;
        }
        switch (simpleType.getContentType()) {
            case INTEGER:
                info = readIntegerValue(simpleType, element, klass, reader);
                break;
            case TEXT:
                info = readTextValue(simpleType, element, reader);
                break;
            case OPAQUE:
                info = readOpaqueValue(simpleType, element, reader);
                break;
            default:
                throw new UnsupportedOperationException(simpleType.getContentType().toString());
        }
        evaluator.setSelf(info);
        evaluator.checkDiscriminator(info, element);
        return (T) info;
    }

    Number readIntegerValue(SimpleType simpleType, Element element, Class<?> klass, BitStreamReader reader)
        throws IOException {
        switch (simpleType.getRepresentation()) {
            case BINARY:
                return readIntegerValueAsBinary(simpleType, klass, reader);
            case TEXT:
                return readIntegerValueAsText(simpleType, element, klass, reader);
            default:
                throw new IllegalStateException();
        }
    }

    Number readIntegerValueAsBinary(SimpleType simpleType, Class<?> klass,
        BitStreamReader reader) throws IOException {
        switch (simpleType.getBinaryNumberRep()) {
            case BINARY:
                return readIntegerValueAsStandardBinary(simpleType, klass, reader);
            case BCD:
                return readIntegerValueAsBcdBinary(simpleType, klass, reader);
            default:
                throw new UnsupportedOperationException("unsupported binaryNumberRep = " + simpleType.getBinaryNumberRep());
        }
    }

    Number readIntegerValueAsStandardBinary(SimpleType simpleType, Class<?> klass,
        BitStreamReader reader) throws IOException {
        int numBits = evaluator.computeLength(simpleType);
        if (simpleType.getLengthUnit() == LengthUnit.BYTE) {
            numBits *= 8;
        }
        long value;
        if (simpleType.isUnsigned()) {
            value = reader.readBits(numBits);
        }
        else {
            value = reader.readSignedBits(numBits);
        }
        return convertLong(value, klass);
    }

    Number readIntegerValueAsBcdBinary(SimpleType simpleType, Class<?> klass,
        BitStreamReader reader) throws IOException {
        int numBits = evaluator.computeLength(simpleType);
        if (simpleType.getLengthUnit() == LengthUnit.BYTE) {
            numBits *= 8;
        }
        if (numBits % 4 != 0) {
            throw new UnmarshalException("BCD bit length must be divisible by 4");
        }
        int numDigits = numBits / 4;
        long value = 0;
        for (int i = 0; i < numDigits; i++) {
            value *= 10;
            long digit = reader.readBits(4);
            // TODO signed numbers, assume non-negative for now
            if (digit > 9) {
                throw new UnmarshalException("illegal digit: " + digit);
            }
            value += digit;
        }

        return convertLong(value, klass);
    }

    Number readIntegerValueAsText(SimpleType type, Element element, Class<?> klass,
        BitStreamReader reader) throws IOException {
        if (type.getLengthKind() == LengthKind.EXPLICIT) {
            long length = evaluator.computeLength(element);
            byte[] bytes = new byte[(int) length];
            reader.read(bytes);
            String s = new String(bytes, StandardCharsets.UTF_8);
            return convertLong(Long.parseLong(s), klass);
        }
        throw new UnsupportedOperationException();
    }

    String readTextValue(SimpleType type, DadlType representation, BitStreamReader reader)
        throws IOException {
        if (type.getLengthKind() == LengthKind.EXPLICIT) {
            long length = evaluator.computeLength(representation);
            byte[] bytes = new byte[(int) length];
            reader.read(bytes);
            try {
                return new String(bytes, representation.getEncoding());
            }
            catch (UnsupportedEncodingException exc) {
                throw new UnmarshalException(exc);
            }
        }
        throw new UnsupportedOperationException();
    }

    byte[] readOpaqueValue(SimpleType type, DadlType representation, BitStreamReader reader)
        throws IOException {
        if (type.getLengthKind() == LengthKind.EXPLICIT) {
            long length = evaluator.computeLength(representation);
            byte[] bytes = new byte[(int) length];
            reader.read(bytes);
            return bytes;
        }
        throw new UnsupportedOperationException();
    }

    Number convertLong(long value, Class<?> klass) {
        if (Integer.class.isAssignableFrom(klass)) {
            return Integer.valueOf((int) value);
        }
        if (Short.class.isAssignableFrom(klass)) {
            return Short.valueOf((short) value);
        }
        if (Byte.class.isAssignableFrom(klass)) {
            return Byte.valueOf((byte) value);
        }
        return value;
    }
}