/***********************************************************************
 * Copyright (c) 1999-2004 The Apache Software Foundation.             *
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

import org.apache.mailet.GenericRecipientMatcher;
import org.apache.mailet.MailAddress;

import java.util.StringTokenizer;
import java.util.Vector;

/**
 * @version 1.0.0, 24/04/1999
 */
public class UserIs extends GenericRecipientMatcher {
    Vector users = null;

    public void init() {
        StringTokenizer st = new StringTokenizer(getCondition(), ", ", false);
        users = new Vector();
        while (st.hasMoreTokens()) {
            users.add(st.nextToken());
        }
    }

    public boolean matchRecipient(MailAddress recipient) {
        return users.contains(recipient.getUser());
    }
}

