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

import org.apache.mailet.GenericMatcher;
import org.apache.mailet.Mail;

import javax.mail.internet.MimeMessage;
import java.util.Collection;

/**
 * Matches mail with a header set by Fetchpop X-fetched-from <br>
 * fetchpop sets X-fetched-by to the "name" of the fetchpop fetch task.<br>
 * This is used to match all mail fetched from a specific pop account.
 * Once the condition is met the header is stripped from the message to prevent looping if the mail is re-inserted into the spool.
 * 
 * $Id: FetchedFrom.java,v 1.2.4.3 2004/03/15 03:54:21 noel Exp $
 */

public class FetchedFrom extends GenericMatcher {
    public Collection match(Mail mail) throws javax.mail.MessagingException {
        MimeMessage message = mail.getMessage();
        String fetch = message.getHeader("X-fetched-from", null);
        if (fetch != null && fetch.equals(getCondition())) {
            mail.getMessage().removeHeader("X-fetched-from");
            return mail.getRecipients();
        }
        return null;
    }
}
