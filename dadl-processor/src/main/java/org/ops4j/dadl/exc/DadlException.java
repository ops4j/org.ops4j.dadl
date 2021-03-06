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
package org.ops4j.dadl.exc;

/**
 * Base class of all DADL exceptions. In most cases, more specific exceptions will be thrown.
 *
 * @author Harald Wellmann
 *
 */
public class DadlException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates an empty exception.
     */
    public DadlException() {
    }

    /**
     * Creates an exception with the given message.
     *
     * @param message
     *            exception message
     */
    public DadlException(String message) {
        super(message);
    }

    /**
     * Creates an exception with the given message and cause.
     *
     * @param message
     *            exception message
     * @param cause
     *            cause of this exception
     */
    public DadlException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates an exception with the given cause.
     *
     * @param cause
     *            cause of this exception
     */
    public DadlException(Throwable cause) {
        super(cause);
    }
}
