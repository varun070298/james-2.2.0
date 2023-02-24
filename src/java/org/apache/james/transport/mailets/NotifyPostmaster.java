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
import org.apache.mailet.MailetException;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.Collection;
import java.util.Iterator;

/**
 * <P>Sends a notification message to the Postmaster.</P>
 * <P>A sender of the notification message can optionally be specified.
 * If one is not specified, the postmaster's address will be used.<BR>
 * The "To:" header of the notification message can be set to "unaltered";
 * if missing will be set to the postmaster.<BR>
 * A notice text can be specified, and in such case will be inserted into the
 * notification inline text.<BR>
 * If the notified message has an "error message" set, it will be inserted into the
 * notification inline text. If the <CODE>attachStackTrace</CODE> init parameter
 * is set to true, such error message will be attached to the notification message.<BR>
 * The notified messages are attached in their entirety (headers and
 * content) and the resulting MIME part type is "message/rfc822".</P>
 * <P>Supports the <CODE>passThrough</CODE> init parameter (true if missing).</P>
 *
 * <P>Sample configuration:</P>
 * <PRE><CODE>
 * &lt;mailet match="All" class="NotifyPostmaster">
 *   &lt;sender&gt;<I>an address or postmaster or sender or unaltered, default=postmaster</I>&lt;/sender&gt;
 *   &lt;attachError&gt;<I>true or false, default=false</I>&lt;/attachError&gt;
 *   &lt;message&gt;<I>notice attached to the original message text (optional)</I>&lt;/message&gt;
 *   &lt;prefix&gt;<I>optional subject prefix prepended to the original message, default="Re:"</I>&lt;/prefix&gt;
 *   &lt;inline&gt;<I>see {@link Resend}, default=none</I>&lt;/inline&gt;
 *   &lt;attachment&gt;<I>see {@link Resend}, default=message</I>&lt;/attachment&gt;
 *   &lt;passThrough&gt;<I>true or false, default=true</I>&lt;/passThrough&gt;
 *   &lt;fakeDomainCheck&gt;<I>true or false, default=true</I>&lt;/fakeDomainCheck&gt;
 *   &lt;to&gt;<I>unaltered (optional, defaults to postmaster)</I>&lt;/to&gt;
 *   &lt;debug&gt;<I>true or false, default=false</I>&lt;/debug&gt;
 * &lt;/mailet&gt;
 * </CODE></PRE>
 *
 * <P>The behaviour of this mailet is equivalent to using Resend with the following
 * configuration:</P>
 * <PRE><CODE>
 * &lt;mailet match="All" class="Resend">
 *   &lt;sender&gt;<I>an address or postmaster or sender or unaltered</I>&lt;/sender&gt;
 *   &lt;attachError&gt;<I>true or false</I>&lt;/attachError&gt;
 *   &lt;message&gt;<I><B>dynamically built</B></I>&lt;/message&gt;
 *   &lt;prefix&gt;<I>a string</I>&lt;/prefix&gt;
 *   &lt;passThrough&gt;<I>true or false</I>&lt;/passThrough&gt;
 *   &lt;fakeDomainCheck&gt;<I>true or false</I>&lt;/fakeDomainCheck&gt;
 *   &lt;to&gt;<I><B>unaltered or postmaster</B></I>&lt;/to&gt;
 *   &lt;recipients&gt;<B>postmaster</B>&lt;/recipients&gt;
 *   &lt;inline&gt;see {@link Resend}&lt;/inline&gt;
 *   &lt;attachment&gt;see {@link Resend}&lt;/attachment&gt;
 *   &lt;isReply&gt;true&lt;/isReply&gt;
 *   &lt;debug&gt;<I>true or false</I>&lt;/debug&gt;
 * &lt;/mailet&gt;
 * </CODE></PRE>
 * <P><I>notice</I>, <I>sendingAddress</I> and <I>attachStackTrace</I> can be used instead of
 * <I>message</I>, <I>sender</I> and <I>attachError</I>; such names are kept for backward compatibility.</P>
 *
 * @version CVS $Revision: 1.9.4.12 $ $Date: 2004/03/15 03:54:19 $
 */
public class NotifyPostmaster extends AbstractNotify {

    /**
     * Return a string describing this mailet.
     *
     * @return a string describing this mailet
     */
    public String getMailetInfo() {
        return "NotifyPostmaster Mailet";
    }

    /** Gets the expected init parameters. */
    protected  String[] getAllowedInitParameters() {
        String[] allowedArray = {
//            "static",
            "debug",
            "passThrough",
            "fakeDomainCheck",
            "inline",
            "attachment",
            "message",
            "notice",
            "sender",
            "sendingAddress",
            "prefix",
            "attachError",
            "attachStackTrace",
            "to"
        };
        return allowedArray;
    }
    
    /* ******************************************************************** */
    /* ****************** Begin of getX and setX methods ****************** */
    /* ******************************************************************** */

    /**
     * @return the postmaster address
     */
    protected Collection getRecipients() {
        Collection newRecipients = new HashSet();
        newRecipients.add(getMailetContext().getPostmaster());
        return newRecipients;
    }

    /**
     * @return <CODE>SpecialAddress.UNALTERED</CODE> if specified or postmaster if missing
     */
    protected InternetAddress[] getTo() throws MessagingException {
        String addressList = getInitParameter("to");
        InternetAddress[] iaarray = new InternetAddress[1];
        iaarray[0] = getMailetContext().getPostmaster().toInternetAddress();
        if (addressList != null) {
            MailAddress specialAddress = getSpecialAddress(addressList,
                                            new String[] {"postmaster", "unaltered"});
            if (specialAddress != null) {
                iaarray[0] = specialAddress.toInternetAddress();
            } else {
                log("\"to\" parameter ignored, set to postmaster");
            }
        }
        return iaarray;
    }

    /**
     * @return the <CODE>attachStackTrace</CODE> init parameter, 
     * or the <CODE>attachError</CODE> init parameter if missing,
     * or false if missing 
     */
    protected boolean attachError() throws MessagingException {
        String parameter = getInitParameter("attachStackTrace");
        if (parameter == null) {
            return super.attachError();
        }        
        return new Boolean(parameter).booleanValue();
    }

    /* ******************************************************************** */
    /* ******************* End of getX and setX methods ******************* */
    /* ******************************************************************** */

}

