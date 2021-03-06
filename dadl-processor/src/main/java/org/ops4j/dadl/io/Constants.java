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
package org.ops4j.dadl.io;


/**
 * Constant values.
 * @author hwellmann
 *
 */
public class Constants  {

    /** Number of bits per half-byte. */
    public static final int NIBBLE_SIZE = 4;

    /** Number of bits per byte. */
    public static final int BYTE_SIZE = 8;

    /** Number of bits per short. */
    public static final int SHORT_SIZE = 16;

    /** Number of bits per int. */
    public static final int INT_SIZE = 32;

    /** Number of bits per long. */
    public static final int LONG_SIZE = 64;

    /** Decimal base. */
    public static final int DEC_BASE = 10;

    /** Hexadecimal base. */
    public static final int HEX_BASE = 16;


    private Constants() {
        // hidden utility class constructor
    }
}
