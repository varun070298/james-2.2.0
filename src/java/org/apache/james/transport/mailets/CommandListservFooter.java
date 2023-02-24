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

import org.apache.avalon.framework.configuration.ConfigurationException;
import org.apache.james.util.XMLResources;
import org.apache.mailet.GenericMailet;
import org.apache.mailet.Mail;
import org.apache.oro.text.regex.*;

import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.internet.MimePart;
import java.io.IOException;


/**
 * CommandListservFooter is based on the AddFooter mailet.
 *
 * It is used by the {@link CommandListservProcessor} to inject a footer into mailing list.
 * <br />
 * <br />
 *
 * @version CVS $Revision: 1.1.2.3 $ $Date: 2004/03/15 03:54:19 $
 * @since 2.2.0
 * @see XMLResources
 */
public class CommandListservFooter extends GenericMailet {

    protected String footerText;
    protected String footerHtml;

    /**
     * The list serv manager
     */
    protected ICommandListservManager commandListservManager;

    /**
     * For matching
     */
    protected Perl5Compiler perl5Compiler = new Perl5Compiler();
    protected Pattern insertPattern;
    protected Pattern newlinePattern;

    //For resources
    protected XMLResources[] xmlResources = new XMLResources[2];

    protected static final int TEXT_PLAIN = 0;
    protected static final int TEXT_HTML = 1;

    public CommandListservFooter(ICommandListservManager commandListservManager) {
        this.commandListservManager = commandListservManager;
        try {
            insertPattern = perl5Compiler.compile("</body>\\s*</html>", Perl5Compiler.CASE_INSENSITIVE_MASK);
            newlinePattern = perl5Compiler.compile("\r\n|\n", Perl5Compiler.CASE_INSENSITIVE_MASK);
        } catch (MalformedPatternException e) {
            throw new IllegalStateException("Unable to parse regexps: " + e.getMessage());
        }
    }

    /**
     * Initialize the mailet
     */
    public void init() throws MessagingException {
        try {
            xmlResources = commandListservManager.initXMLResources(new String[]{"footer", "footer_html"});
        } catch (ConfigurationException e) {
            throw new MessagingException(e.getMessage(), e);
        }
    }

    /**
     * Return a string describing this mailet.
     *
     * @return a string describing this mailet
     */
    public String getMailetInfo() {
        return "CommandListservFooter Mailet";
    }


    /**
     * Identify what type of mimeMessage it is, and attach the footer
     * @param mail
     * @throws MessagingException
     */
    public void service(Mail mail) throws MessagingException {
        try {
            MimeMessage message = mail.getMessage();

            //I want to modify the right message body
            if (message.isMimeType("text/plain")) {
                //This is a straight text message... just append the single part normally
                addToText(message);
            } else if (message.isMimeType("multipart/mixed")) {
                //Find the first body part, and determine what to do then.
                MimeMultipart multipart = (MimeMultipart) message.getContent();
                MimeBodyPart part = (MimeBodyPart) multipart.getBodyPart(0);
                attachFooter(part);
                //We have to do this because of a bug in JavaMail (ref id 4404733)
                message.setContent(multipart);
            } else {
                //Find the HTML and text message types and add to each
                MimeMultipart multipart = (MimeMultipart) message.getContent();
                int count = multipart.getCount();
                for (int index = 0; index < count; index++) {
                    MimeBodyPart part = (MimeBodyPart) multipart.getBodyPart(index);
                    attachFooter(part);
                }
                //We have to do this because of a bug in JavaMail (ref id 4404733)
                message.setContent(multipart);
            }
        } catch (IOException ioe) {
            throw new MessagingException("Could not read message", ioe);
        }
    }

    /**
     * Get and cache the footer text
     *
     * @return the footer text
     * @see XMLResources
     */
    protected String getFooterText() {
        if (footerText == null) {
            footerText = getFormattedText(TEXT_PLAIN);
        }
        return footerText;
    }

    /**
     * Get and cache the footer html text
     *
     * @return the footer text
     * @see XMLResources
     */
    protected String getFooterHTML() {
        if (footerHtml == null) {
            String footerText = getFormattedText(TEXT_HTML);
            footerHtml = Util.substitute(new Perl5Matcher(),
                    newlinePattern,
                    new StringSubstitution(" <br />"),
                    footerText,
                    Util.SUBSTITUTE_ALL);
        }
        return footerHtml;
    }

    /**
     * Prepends the content of the MimePart as HTML to the existing footer.
     * We use the regular expression to inject the footer inside of the body tag appropriately.
     *
     * @param part the MimePart to attach
     *
     * @throws MessagingException
     * @throws java.io.IOException
     */
    protected void addToHTML(MimePart part) throws MessagingException, IOException {
        String content = part.getContent().toString();
        StringSubstitution stringSubstitution = new StringSubstitution("<br />" + getFooterHTML() + "</body</html>");
        String result = Util.substitute(new Perl5Matcher(), insertPattern, stringSubstitution, content, 1);
        part.setContent(result, part.getContentType());
    }

    /**
     * Prepends the content of the MimePart as text to the existing footer
     *
     * @param part the MimePart to attach
     *
     * @throws MessagingException
     * @throws IOException
     */
    protected void addToText(MimePart part) throws MessagingException, IOException {
        String content = part.getContent().toString();
        if (!content.endsWith("\n")) {
            content += "\r\n";
        }
        content += getFooterText();
        part.setText(content);
    }

    /**
     * Attaches a MimePart as an appropriate footer
     *
     * @param part the MimePart to attach
     *
     * @throws MessagingException
     * @throws IOException
     */
    protected void attachFooter(MimePart part) throws MessagingException, IOException {
        if (part.isMimeType("text/plain")) {
            addToText(part);
        } else if (part.isMimeType("text/html")) {
            addToHTML(part);
        } else if (part.getContent() instanceof MimeMultipart) {
            MimeMultipart multipart = (MimeMultipart) part.getContent();
            int count = multipart.getCount();
            for (int index = 0; index < count; index++) {
                MimeBodyPart mimeBodyPart = (MimeBodyPart) multipart.getBodyPart(index);
                attachFooter(mimeBodyPart);
            }
            part.setContent(multipart);
        } else {
            //System.err.println(part.getContentType());
        }
    }

    /**
     * @see XMLResources#getString
     * @param index either {@link #TEXT_PLAIN} or {@link #TEXT_HTML}
     * @return a formatted text with the proper list and domain
     */
    protected String getFormattedText(int index) {
        return xmlResources[index].getString("text");
    }
}
