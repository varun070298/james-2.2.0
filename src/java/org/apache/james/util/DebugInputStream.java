/***********************************************************************
 * Copyright (c) 2000-2004 The Apache Software Foundation.             *
 * All rights reserved.                                                *
 * ------------------------------------------------------------------- *
 * Licensed under the Apache License, Version 2.0 (the "License"); you *
 * may not use this file except in compliance with the License. You    *
 * may obtain a copy of the License at:                                *
 *                                                                     *
 *     http://www.apache.org/licenses/LICENSE-2.0                      *
 *                                                                     *
 * Unless required by applicable law or agreed to in writing, software *
 * distributed under the License is distributed on an "AS IS" BASIS,   *
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or     *
 * implied.  See the License for the specific language governing       *
 * permissions and limitations under the License.                      *
 ***********************************************************************/

package org.apache.james.util;

import java.io.IOException;
import java.io.InputStream;

/**
 * Reads data off a stream, printing every byte read to System.err.
 */
public class DebugInputStream extends InputStream {

    /**
     * The input stream being wrapped
     */
    InputStream in = null;

    /**
     * Constructor that takes an InputStream to be wrapped.
     *
     * @param in the InputStream to be wrapped
     */
    public DebugInputStream(InputStream in) {
        this.in = in;
    }

    /**
     * Read a byte off the stream
     *
     * @return the byte read off the stream
     * @throws IOException if an exception is encountered when reading
     */
    public int read() throws IOException {
        int b = in.read();
        System.err.write(b);
        return b;
    }

    /**
     * Close the stream
     *
     * @throws IOException if an exception is encountered when closing
     */
    public void close() throws IOException {
        in.close();
    }
}
