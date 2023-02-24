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

import java.util.Enumeration;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.james.core.MailImpl;
import org.apache.mailet.GenericMailet;
import org.apache.mailet.Mail;

/**
 * Logs Message Headers.
 * If the "passThrough" in confs is true the mail will be left untouched in
 * the pipe. If false will be destroyed.  Default is true.
 *
 * @version This is $Revision: 1.8.4.2 $
 */
public class LogHeaders extends GenericMailet {

    /**
     * Whether this mailet should allow mails to be processed by additional mailets
     * or mark it as finished.
     */
    private boolean passThrough = true;

    /**
     * Initialize the mailet, loading configuration information.
     */
    public void init() {
        try {
            passThrough = (getInitParameter("passThrough") == null) ? true : new Boolean(getInitParameter("debug")).booleanValue();
        } catch (Exception e) {
            // Ignore exception, default to true
        }
    }

    /**
     * Log a particular message
     *
     * @param mail the mail to process
     */
    public void service(Mail genericmail) {
        MailImpl mail = (MailImpl)genericmail;
        log(new StringBuffer(160).append("Logging mail ").append(mail.getName()).toString());
        try {
            log(getMessageHeaders(mail.getMessage()));
        }
        catch (MessagingException e) {
            log("Error logging headers.");
        }
        if (!passThrough) {
            mail.setState(Mail.GHOST);
        }
    }

    /**
     * Utility method for obtaining a string representation of a
     * Message's headers
     */
    private String getMessageHeaders(MimeMessage message) throws MessagingException {
        Enumeration heads = message.getAllHeaderLines();
        StringBuffer headBuffer = new StringBuffer(1024).append("\n");
        while(heads.hasMoreElements()) {
            headBuffer.append(heads.nextElement().toString()).append("\n");
        }
        return headBuffer.toString();
    }

    /**
     * Return a string describing this mailet.
     *
     * @return a string describing this mailet
     */
    public String getMailetInfo() {
        return "LogHeaders Mailet";
    }
}
