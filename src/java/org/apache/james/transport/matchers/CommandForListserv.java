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

import org.apache.mailet.GenericRecipientMatcher;
import org.apache.mailet.MailAddress;

import javax.mail.MessagingException;

/**
 * Returns positive if the recipient is a command for a listserv.  For example,
 * if my listserv is james@list.working-dogs.com, this matcher will return true
 * for james-on@list.working-dogs.com and james-off@list.working-dogs.com.
 *
 */
public class CommandForListserv extends GenericRecipientMatcher {

    private MailAddress listservAddress;

    public void init() throws MessagingException {
        listservAddress = new MailAddress(getCondition());
    }

    public boolean matchRecipient(MailAddress recipient) {
        if (recipient.getHost().equals(listservAddress.getHost())) {
            if (recipient.getUser().equals(listservAddress.getUser() + "-on")
                || recipient.getUser().equals(listservAddress.getUser() + "-off")) {
                return true;
            }
        }
        return false;
    }
}
