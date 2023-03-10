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

package org.apache.james.transport.mailets;

import org.apache.mailet.GenericMailet;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.MailetContext;

import javax.mail.MessagingException;
import java.util.Collection;
import java.util.Iterator;
import java.util.Vector;
//import com.workingdogs.town.*;

/**
 * Rewrites recipient addresses to make sure email for the postmaster is
 * always handled.  This mailet is silently inserted at the top of the root
 * spool processor.  All recipients mapped to postmaster@<servernames> are
 * changed to the postmaster account as specified in the server conf.
 *
 */
public class PostmasterAlias extends GenericMailet {

    /**
     * Make sure that a message that is addressed to a postmaster alias is always
     * sent to the postmaster address, regardless of delivery to other recipients.
     *
     * @param mail the mail to process
     *
     * @throws MessagingException if an error is encountered while modifying the message
     */
    public void service(Mail mail) throws MessagingException {
        Collection recipients = mail.getRecipients();
        Collection recipientsToRemove = null;
        MailetContext mailetContext = getMailetContext();
        boolean postmasterAddressed = false;

        for (Iterator i = recipients.iterator(); i.hasNext(); ) {
            MailAddress addr = (MailAddress)i.next();
            if (addr.getUser().equalsIgnoreCase("postmaster") &&
                mailetContext.isLocalServer(addr.getHost())) {
                //Should remove this address... we want to replace it with
                //  the server's postmaster address
                if (recipientsToRemove == null) {
                    recipientsToRemove = new Vector();
                }
                recipientsToRemove.add(addr);
                //Flag this as having found the postmaster
                postmasterAddressed = true;
            }
        }
        if (postmasterAddressed) {
            recipients.removeAll(recipientsToRemove);
            recipients.add(getMailetContext().getPostmaster());
        }
    }

    /**
     * Return a string describing this mailet.
     *
     * @return a string describing this mailet
     */
    public String getMailetInfo() {
        return "Postmaster aliasing mailet";
    }
}
