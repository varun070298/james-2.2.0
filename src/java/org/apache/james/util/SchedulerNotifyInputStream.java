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

import org.apache.avalon.cornerstone.services.scheduler.TimeScheduler;

import java.io.IOException;
import java.io.InputStream;

/**
 * This will reset the scheduler each time a certain amount of data has
 * been transfered.  This allows us to keep the timeout settings low, while
 * not timing out during large data transfers.
 */
public class SchedulerNotifyInputStream extends InputStream {

    /**
     * The wrapped InputStream
     */
    InputStream in = null;

    /**
     * The scheduler managing the trigger to be reset by this stream
     */
    TimeScheduler scheduler = null;

    /**
     * The name of the trigger
     */
    String triggerName = null;

    /**
     * The number of bytes that need to be read before the counter is reset.
     */
    int lengthReset = 0;

    /**
     * The number of bytes read since the counter was last reset
     */
    int readCounter = 0;

    /**
     * @param in the InputStream to be wrapped by this stream
     * @param scheduler the TimeScheduler managing the trigger to be reset by this stream
     * @param triggerName the name of the particular trigger to be reset by this stream
     * @param lengthReset the number of bytes to be read in between trigger resets
     */
    public SchedulerNotifyInputStream(InputStream in,
            TimeScheduler scheduler, String triggerName, int lengthReset) {
        this.in = in;
        this.scheduler = scheduler;
        this.triggerName = triggerName;
        this.lengthReset = lengthReset;

        readCounter = 0;
    }

    /**
     * Read an array of bytes from the stream
     *
     * @param b the array of bytes to read from the stream
     * @param off the index in the array where we start writing
     * @param len the number of bytes of the array to read
     *
     * @return the number of bytes read
     *
     * @throws IOException if an exception is encountered when reading
     */
    public int read(byte[] b, int off, int len) throws IOException {
        int l = in.read(b, off, len);
        readCounter += l;

        if (readCounter > lengthReset) {
            readCounter -= lengthReset;
            scheduler.resetTrigger(triggerName);
        }

        return l;
    }

    /**
     * Read a byte from the stream
     *
     * @return the byte read from the stream
     * @throws IOException if an exception is encountered when reading
     */
    public int read() throws IOException {
        int b = in.read();
        readCounter++;

        if (readCounter > lengthReset) {
            readCounter -= lengthReset;
            scheduler.resetTrigger(triggerName);
        }

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
