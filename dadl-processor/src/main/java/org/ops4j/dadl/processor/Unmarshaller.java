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
import static org.ops4j.dadl.io.Constants.HEX_BASE;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.List;

import org.ops4j.dadl.exc.Exceptions;
import org.ops4j.dadl.exc.UnmarshalException;
import org.ops4j.dadl.io.BitStreamReader;
import org.ops4j.dadl.io.ByteArrayBitStreamReader;
import org.ops4j.dadl.io.Constants;
import org.ops4j.dadl.metamodel.gen.Choice;
import org.ops4j.dadl.metamodel.gen.DadlType;
import org.ops4j.dadl.metamodel.gen.Discriminator;
import org.ops4j.dadl.metamodel.gen.Element;
import org.ops4j.dadl.metamodel.gen.Enumeration;
import org.ops4j.dadl.metamodel.gen.LengthField;
import org.ops4j.dadl.metamodel.gen.LengthKind;
import org.ops4j.dadl.metamodel.gen.LengthUnit;
import org.ops4j.dadl.metamodel.gen.Sequence;
import org.ops4j.dadl.metamodel.gen.SequenceElement;
import org.ops4j.dadl.metamodel.gen.SimpleType;
import org.ops4j.dadl.metamodel.gen.Tag;
import org.ops4j.dadl.metamodel.gen.TaggedSequence;
import org.ops4j.dadl.metamodel.gen.TestKind;
import org.ops4j.dadl.model.ValidatedModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An unmarshaller deserializes info model objects from a bit stream using the formatting rules of a
 * given DADL model.
 *
 * @author hwellmann
 *
 */
public class Unmarshaller {

    private static Logger log = LoggerFactory.getLogger(Unmarshaller.class);

    private DadlContext context;
    private ValidatedModel model;
    private Evaluator evaluator;
    private SimpleTypeReader simpleTypeReader;

    Unmarshaller(DadlContext context, ValidatedModel model) {
        this.context = context;
        this.model = model;
        this.evaluator = new Evaluator();
        this.simpleTypeReader = new SimpleTypeReader(context, evaluator);
    }

    /**
     * Unmarshals the given byte array into an info model object of the given class. The class must
     * be mapped to a type in the current DADL model.
     *
     * @param bytes
     *            byte array
     * @param klass
     *            info model class
     * @return instance of model class
     * @throws IOException
     *             on read error
     */
    public <T> T unmarshal(byte[] bytes, Class<T> klass) throws IOException {
        return unmarshal(bytes, 0, bytes.length, klass);
    }

    /**
     * Unmarshals the given byte array into an info model object of the given class. The class must
     * be mapped to a type in the current DADL model.
     *
     * @param bytes
     *            byte array
     * @param offset
     *            offset of first byte to be read
     * @param length
     *            number of bytes to be read
     * @param klass
     *            info model class
     * @return instance of model class
     * @throws IOException
     *             on read error
     */
    public <T> T unmarshal(byte[] bytes, int offset, int length, Class<T> klass) throws IOException {
        String typeName = klass.getSimpleName();
        DadlType type = model.getType(typeName);
        try (BitStreamReader reader = new ByteArrayBitStreamReader(bytes, offset, length)) {
            return unmarshal(type, klass, reader);
        }
    }

    private <T> T unmarshal(DadlType type, Class<T> klass, BitStreamReader reader)
        throws IOException {
        long startPos = reader.getBitPosition();
        T info = context.readValueViaAdapter(type, reader);
        if (info == null) {
            info = newInstance(klass);
            evaluator.setSelf(info);
            if (type instanceof Sequence) {
                info = unmarshalSequence(info, (Sequence) type, klass, reader);
            }
            else if (type instanceof TaggedSequence) {
                info = unmarshalTaggedSequence(info, (TaggedSequence) type, klass, reader);
            }
            else if (type instanceof Choice) {
                info = unmarshalChoice(info, (Choice) type, klass, reader);
            }
            else {
                throw new UnmarshalException("cannot unmarshal type " + klass.getName());
            }
        }
        skipPadding(type, startPos, reader);
        return info;
    }

    private void skipPadding(DadlType type, long startPos, BitStreamReader reader)
        throws IOException {
        boolean hasExactLength = (type.getLengthKind() == LengthKind.EXPLICIT);
        boolean hasMinLength = (type.getMinLength() != null);
        if (!(hasExactLength || hasMinLength)) {
            return;
        }

        long numBits = hasExactLength ? evaluator.computeLength(type) : evaluator
            .computeMinLength(type);
        if (type.getLengthUnit() == LengthUnit.BYTE) {
            numBits *= BYTE_SIZE;
        }
        long actualNumBits = reader.getBitPosition() - startPos;
        if (actualNumBits == numBits) {
            return;
        }
        if (actualNumBits > numBits) {
            if (hasMinLength) {
                return;
            }
            throw new UnmarshalException("actual length of " + type.getName()
                + " exceeds explicit length of " + numBits + " bits");
        }
        long paddingBits = numBits - actualNumBits;
        reader.skipBits(paddingBits);
    }

    private <T> T newInstance(Class<T> klass) {
        try {
            return klass.newInstance();
        }
        catch (InstantiationException | IllegalAccessException exc) {
            throw new UnmarshalException("cannot instantiate " + klass.getName(), exc);
        }
    }

    private <T> T unmarshalSequence(T info, Sequence sequence, Class<T> klass,
        BitStreamReader reader) throws IOException {
        log.debug("unmarshalling sequence {}", sequence.getName());
        evaluator.pushStack();
        try {
            for (SequenceElement element : sequence.getElement()) {
                unmarshalSequenceField(klass, element, reader);
            }
        }
        finally {
            evaluator.popStack();
        }
        return info;
    }

    private <T> T unmarshalTaggedSequence(T info, TaggedSequence sequence, Class<T> klass,
        BitStreamReader reader) throws IOException {
        log.debug("unmarshalling tagged sequence {}", sequence.getName());
        evaluator.pushStack();
        try {
            Tag tag = sequence.getTag();
            if (tag != null) {
                unmarshalTag(tag, reader);
            }
            LengthField lengthField = sequence.getLengthField();
            if (lengthField != null) {
                long length = unmarshalLengthField(lengthField, reader);
                evaluator.setVariable("$length", length);
                long start = reader.getBitPosition();
                long end = start + length * Constants.BYTE_SIZE;
                evaluator.setVariable("$end", end);
            }
            for (SequenceElement element : sequence.getElement()) {
                unmarshalSequenceField(klass, element, reader);
            }
        }
        finally {
            evaluator.popStack();
        }
        return info;
    }

    /**
     * @param tag
     * @param reader
     * @throws IOException
     */
    private void unmarshalTag(Tag tag, BitStreamReader reader) throws IOException {
        String typeName = tag.getType();
        Object type = model.getType(typeName);
        if (type instanceof SimpleType) {
            SimpleType simpleType = (SimpleType) type;
            long actualTag = simpleTypeReader.readSimpleValue(simpleType, null, Long.class, reader);
            long expectedTag = getExpectedValue(tag);
            log.debug("unmarshalling tag {}", expectedTag);
            if (actualTag != expectedTag) {
                String msg = String.format("tag mismatch: actual = %X, expected = %X", actualTag,
                    expectedTag);
                throw new AssertionError(msg);
            }
        }
        else {
            throw new UnmarshalException("tag type is not a simple type: " + typeName);
        }
    }

    private long unmarshalLengthField(LengthField lengthField, BitStreamReader reader)
        throws IOException {
        DadlType type = model.getType(lengthField.getType());
        if (type instanceof SimpleType) {
            SimpleType simpleType = (SimpleType) type;
            Long lengthValue = simpleTypeReader.readSimpleValue(simpleType, null, Long.class,
                reader);
            log.debug("unmarshalled length field with value {}", lengthValue);
            return lengthValue;
        }
        throw new UnmarshalException("length field must have simple type");
    }

    private long getExpectedValue(Tag tag) {
        return Long.parseUnsignedLong(tag.getHexValue(), HEX_BASE);
    }

    private void unmarshalSequenceField(Class<?> klass, SequenceElement element,
        BitStreamReader reader) throws IOException {
        if (log.isDebugEnabled()) {
            log.debug("unmarshalling sequence element {}", element.getName());
            log.debug("end = {}", evaluator.getVariable("$end", Long.class));
        }
        try {
            Field field = klass.getDeclaredField(element.getName());
            if (model.isList(element)) {
                ParameterizedType type = (ParameterizedType) field.getGenericType();
                Class<?> elementClass = (Class<?>) type.getActualTypeArguments()[0];
                unmarshalSequenceListField(elementClass, element, reader);
            }
            else if (model.isOptional(element)) {
                Long end = evaluator.getVariable("$end", Long.class);
                long pos = reader.getBitPosition();
                if (end == null || pos < end) {
                    unmarshalOptionalSequenceField(field.getType(), element, reader);
                }
            }
            else {
                Object fieldValue = unmarshalSequenceIndividualField(field.getType(), element,
                    reader);
                checkDiscriminator(element);
                evaluator.setParentProperty(element.getName(), fieldValue);
            }
        }
        catch (NoSuchFieldException | SecurityException exc) {
            throw Exceptions.unchecked(exc);
        }
    }

    private void unmarshalOptionalSequenceField(Class<?> klass, SequenceElement element,
        BitStreamReader reader) throws IOException {
        long startPos = reader.getBitPosition();
        try {
            Object fieldValue = unmarshalSequenceIndividualField(klass, element,
                reader);
            checkDiscriminator(element);
            evaluator.setParentProperty(element.getName(), fieldValue);
        }
        catch (AssertionError | Exception exc) {
            reader.setBitPosition(startPos);
        }
    }

    private void unmarshalSequenceListField(Class<?> klass, SequenceElement element,
        BitStreamReader reader) throws IOException {
        switch (element.getOccursCountKind()) {
            case EXPRESSION:
                unmarshalSequenceListFieldByExpression(klass, element, reader);
                break;
            case PARSED:
                unmarshalSequenceListFieldParsed(klass, element, reader);
                break;
            case END_OF_PARENT:
                unmarshalSequenceListFieldEndOfParent(klass, element, reader);
                break;
            default:
                throw new UnsupportedOperationException(element.getOccursCountKind().toString());

        }
    }

    private void unmarshalSequenceListFieldByExpression(Class<?> klass, SequenceElement element,
        BitStreamReader reader) throws IOException {
        Long numItems = evaluator.evaluate(element.getOccursCount(), Long.class);

        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) evaluator.getParentProperty(element.getName());

        for (long i = 0; i < numItems; i++) {
            Object fieldValue = unmarshalSequenceIndividualField(klass, element, reader);
            list.add(fieldValue);
        }
    }

    private void unmarshalSequenceListFieldParsed(Class<?> klass, SequenceElement element,
        BitStreamReader reader) throws IOException {

        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) evaluator.getParentProperty(element.getName());

        while (true) {
            long startPos = reader.getBitPosition();
            try {
                Object fieldValue = unmarshalSequenceIndividualField(klass, element, reader);
                list.add(fieldValue);
            }
            catch (AssertionError | Exception exc) {
                reader.setBitPosition(startPos);
                break;
            }
        }
    }

    private void unmarshalSequenceListFieldEndOfParent(Class<?> klass, SequenceElement element,
        BitStreamReader reader) throws IOException {

        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) evaluator.getParentProperty(element.getName());

        long end = evaluator.getEndOfParent();
        long startPos;
        while ((startPos = reader.getBitPosition()) < end) {
            try {
                Object fieldValue = unmarshalSequenceIndividualField(klass, element, reader);
                list.add(fieldValue);
            }
            catch (AssertionError | Exception exc) {
                reader.setBitPosition(startPos);
                break;
            }
        }
    }

    private Object unmarshalSequenceIndividualField(Class<?> klass, Element element,
        BitStreamReader reader) throws IOException {
        DadlType fieldType = model.getType(element.getType());
        if (fieldType instanceof Enumeration) {
            return simpleTypeReader.readEnumerationValue((Enumeration) fieldType, element, klass,
                reader);
        }
        else if (fieldType instanceof SimpleType) {
            return simpleTypeReader.readSimpleValue((SimpleType) fieldType, element, klass, reader);
        }
        else {
            return unmarshal(fieldType, klass, reader);
        }
    }

    private <T> T unmarshalChoice(T info, Choice choice, Class<T> klass,
        BitStreamReader reader) throws IOException {
        log.debug("unmarshalling choice {}", choice.getName());
        boolean branchMatched = false;
        evaluator.pushStack();

        long startPos = reader.getBitPosition();
        try {
            for (Element element : choice.getElement()) {
                log.debug("trying branch {}", element.getName());
                try {
                    branchMatched = unmarshalChoiceElement(element, klass, reader);
                    break;
                }
                catch (AssertionError | Exception exc) {
                    reader.setBitPosition(startPos);
                }
            }
            if (!branchMatched) {
                throw new UnmarshalException("no branch matched on " + klass.getName());
            }
            return info;
        }
        finally {
            evaluator.popStack();
        }
    }

    private <T> boolean unmarshalChoiceElement(Element element, Class<T> klass,
        BitStreamReader reader) throws NoSuchFieldException, IOException {
        boolean branchMatched;
        String fieldName = element.getName();
        Field field = klass.getDeclaredField(fieldName);
        DadlType fieldType = model.getType(element.getType());

        Object fieldValue;
        if (fieldType instanceof SimpleType) {
            fieldValue = simpleTypeReader.readSimpleValue((SimpleType) fieldType,
                element, field.getType(), reader);
        }
        else {
            fieldValue = unmarshal(fieldType, field.getType(), reader);
            checkDiscriminator(element);
        }
        evaluator.setParentProperty(fieldName, fieldValue);
        branchMatched = true;
        log.debug("matched branch {}", element.getName());
        return branchMatched;
    }

    private void checkDiscriminator(DadlType type) {
        if (type == null) {
            return;
        }
        Discriminator discriminator = type.getDiscriminator();
        if (discriminator == null) {
            return;
        }
        if (discriminator.getTestKind() == TestKind.PATTERN) {
            throw new UnsupportedOperationException(discriminator.getTestKind().toString());
        }
        String test = discriminator.getTest();
        boolean satisfied = evaluator.evaluate(test, Boolean.class);
        if (!satisfied) {
            String msg = discriminator.getMessage();
            if (msg == null) {
                msg = String.format("%s not satisfied on %s", test, type.getName());
            }
            throw new AssertionError(msg);
        }
    }
}
