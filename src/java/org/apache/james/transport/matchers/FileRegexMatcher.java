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

package org.apache.james.transport.matchers;

import org.apache.oro.text.regex.MalformedPatternException;
import org.apache.james.util.RFC2822Headers;
import javax.mail.MessagingException;

/**
 * Initializes RegexMatcher with regular expressions from a file.
 *
 */
public class FileRegexMatcher extends GenericRegexMatcher {
    public void init() throws MessagingException {
        try {
            java.io.RandomAccessFile patternSource = new java.io.RandomAccessFile(getCondition(), "r");
            int lines = 0;
            while(patternSource.readLine() != null) lines++;
            patterns = new Object[lines][2];
            patternSource.seek(0);
            for (int i = 0; i < lines; i++) {
                String line = patternSource.readLine();
                patterns[i][0] = line.substring(0, line.indexOf(':'));
                patterns[i][1] = line.substring(line.indexOf(':')+1);
            }
            compile(patterns);
        }
        catch (java.io.FileNotFoundException fnfe) {
            throw new MessagingException("Could not locate patterns.", fnfe);
        }
        catch (java.io.IOException ioe) {
            throw new MessagingException("Could not read patterns.", ioe);
        }
        catch(MalformedPatternException mp) {
            throw new MessagingException("Could not initialize regex patterns", mp);
        }
    }
}
