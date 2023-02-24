/***********************************************************************
 * Copyright (c) 2003-2004 The Apache Software Foundation.             *
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
 
package org.apache.james.fetchmail;

import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.StringTokenizer;

import javax.mail.Address;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.InternetHeaders;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.ParseException;

import org.apache.james.core.MailImpl;
import org.apache.james.util.RFC2822Headers;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;

/**
 * <p>Class <code>MessageProcessor</code> handles the delivery of 
 * <code>MimeMessages</code> to the James input spool.</p>
 * 
 * <p>Messages written to the input spool always have the following Mail
 * Attributes set:</p>
 * <dl>
 * <dt>org.apache.james.fetchmail.taskName (java.lang.String)</dt>
 *      <dd>The name of the fetch task that processed the message</dd>
 * <dt>org.apache.james.fetchmail.folderName (java.lang.String)</dt>
 *      <dd>The name of the folder from which the message was fetched</dd>
 * </dl>
 * 
 * <p>Messages written to the input spool have the following Mail Attributes
 *  set if the corresponding condition is satisfied:
 * <dl>
 * <dt>org.apache.james.fetchmail.isBlacklistedRecipient</dt>
 *      <dd>The recipient is in the configured blacklist</dd>
 * <dt>org.apache.james.fetchmail.isMaxMessageSizeExceeded (java.lang.String)</dt>
 *      <dd>The message size exceeds the configured limit. An empty message is
 *          written to the input spool. The Mail Attribute value is a String
 *          representing the size of the original message in bytes.</dd>
 * <dt>org.apache.james.fetchmail.isRecipientNotFound</dt>
 *      <dd>The recipient could not be found. Delivery is to the configured recipient. 
 *          See the discussion of delivery to a sole intended recipient below.</dd>
 * <dt>org.apache.james.fetchmail.isRemoteRecievedHeaderInvalid</dt>
 *       <dd>The Receieved header at the index specified by parameter
 *       <code>remoteReceivedHeaderIndex</code> is invalid.</dd>
 * <dt>org.apache.james.fetchmail.isRemoteRecipient</dt>
 *      <dd>The recipient is on a remote host</dd>
 * <dt>org.apache.james.fetchmail.isUserUndefined</dt>
 *      <dd>The recipient is on a localhost but not defined to James</dd>
 * </dl>
 * 
 * <p>Configuration settings -
 *  see <code>org.apache.james.fetchmail.ParsedConfiguration</code>
 * - control the messages that are written to the James input spool, those that
 * are rejected and what happens to messages that are rejected.</p>
 * 
 * <p>Rejection processing is based on the following filters:</p>
 * <dl> 
 * <dt>RejectRemoteRecipient</dt> 
 *      <dd>Rejects recipients on remote hosts</dd>
 * <dt>RejectBlacklistedRecipient</dt>
 *      <dd>Rejects recipients configured in a blacklist</dd>
 * <dt>RejectUserUndefined</dt>
 *      <dd>Rejects recipients on local hosts who are not defined as James users</dd>
 * <dt>RejectRecipientNotFound</dt>
 *      <dd>See the discussion of delivery to a sole intended recipient below</dd>
 * <dt>RejectMaxMessageSizeExceeded</dt>
 *      <dd>Rejects messages whose size exceeds the configured limit</dd>
 * <dt>RejectRemoteReceievedHeaderInvalid</dt>
 *      <dd>Rejects messages whose Received header is invalid.</dd>
 * </dl>
 * 
 * <p>Rejection processing is intentionally limited to managing the status of the
 * messages that are rejected on the server from which they were fetched. View
 * it as a simple automation of the manual processing an end-user would perform 
 * through a mail client. Messages may be marked as seen or be deleted.</p> 
 * 
 * <p>Further processing can be achieved by configuring to disable rejection for 
 * one or more filters. This enables Messages that would have been rejected to
 * be written to the James input spool. The conditional Mail Attributes 
 * described above identify the filter states. The Matcher/Mailet chain can 
 * then be used to perform any further processing required, such as notifying
 * the Postmaster and/or sender, marking the message for error processing, etc.</p>
 * 
 * <p>Note that in the case of a message exceeding the message size limit, the
 * message that is written to the input spool has no content. This enables 
 * configuration of a mailet notifying the sender that their mail has not been
 * delivered due to its size while maintaining the purpose of the filter which is
 * to avoid injecting excessively large messages into the input spool.</p>
 * 
 * <p>Delivery is to a sole intended recipient. The recipient is determined in the
 * following manner:</p>
 *
 * <ol> 
 * <li>If isIgnoreIntendedRecipient(), use the configured recipient</li>
 * <li>If the Envelope contains a for: stanza, use the recipient in the stanza</li>
 * <li>If the Message has a sole intended recipient, use this recipient</li>
 * <li>If not rejectRecipientNotFound(), use the configured recipient</li>
 * </ol>
 * 
 * <p>If a recipient cannot be determined after these steps, the message is 
 * rejected.</p>
 * 
 * <p>Every delivered message CURRENTLY has an "X-fetched-from" header added 
 * containing the name of the fetch task. Its primary uses are to detect bouncing
 * mail and provide backwards compatibility with the fetchPop task that inserted
 * this header to enable injected messages to be detected in the Matcher/Mailet 
 * chain. This header is DEPRECATED and WILL BE REMOVED in a future version of 
 * fetchmail. Use the Mail Attribute <code>org.apache.james.fetchmail.taskName</code>
 * instead.
 * 
 * <p><code>MessageProcessor</code> is as agnostic as it can be about the format
 * and contents of the messages it delivers. There are no RFCs that govern its
 * behavior. The most releveant RFCs relate to the exchange of messages between
 * MTA servers, but not POP3 or IMAP servers which are normally end-point
 * servers and not expected to re-inject mail into MTAs. None the less, the
 * intent is to conform to the 'spirit' of the RFCs.
 * <code>MessageProcessor</code> relies on the MTA (James in this
 * implementation) to manage and validate the injected mail just as it would
 * when receiving mail from an upstream MTA.</p> 
 * 
 * <p>The only correction applied by <code>MessageProcessor</code> is to correct a
 * partial originator address. If the originator address has a valid user part
 * but no domain part, a domain part is added. The added domain is either the 
 * default domain specified in the configuration, or if not specified, the 
 * fully qualified name of the machine on which the fetch task is running.</p>
 * 
 * <p>The status of messages on the server from which they were fetched that 
 * cannot be injected into the input spool due to non-correctable errors is 
 * determined by the undeliverable configuration options.</p>
 * 
 * <p>Creation Date: 27-May-03</p>
 *
 */
public class MessageProcessor extends ProcessorAbstract
{
    private MimeMessage fieldMessageIn;

    /**
     * Recipient cannot be found
     */ 
    private boolean fieldRecipientNotFound = false;

    /**
     * Recipient is a local user on a local host
     */ 
    private boolean fieldRemoteRecipient = true;

    /**
     * The mail's Received header at index remoteReceivedHeaderIndex is invalid.
     */     
    private Boolean fieldRemoteReceivedHeaderInvalid;

    /**
     * Recipient is not a local user
     */ 
    private boolean fieldUserUndefined = false;
    
    /**
     * The Maximum Message has been exceeded
     */ 
    private Boolean fieldMaxMessageSizeExceeded;    
    
    
    /**
     * Field names for an RFC2822 compliant RECEIVED Header
     */
    static final private String fieldRFC2822RECEIVEDHeaderFields =
        "from by via with id for ;";
    
    /**
     * Recipient is blacklisted
     */ 
    private boolean fieldBlacklistedRecipient = false;
    
    /**
     * The RFC2822 compliant "Received : from" domain
     */
    private String fieldRemoteDomain;
    
    /**
     * The remote address derived from the remote domain
     */
    private String fieldRemoteAddress;
    
    /**
     * The remote host name derived from the remote domain
     */
    private String fieldRemoteHostName;           
    
    /**
     * Constructor for MessageProcessor.
     * 
     * @param account
     */
    private MessageProcessor(Account account)
    {
        super(account);
    }
    
    /**
     * Constructor for MessageProcessor.
     * 
     * @param messageIn
     * @param account
     */

    MessageProcessor(
        MimeMessage messageIn,
         Account account)
    {
        this(account);
        setMessageIn(messageIn);
    }   

    
    /**
     * Method process attempts to deliver a fetched message.
     * 
     * @see org.apache.james.fetchmail.ProcessorAbstract#process()
     */
    public void process() throws MessagingException
    {
        // Log delivery attempt
        if (getLogger().isDebugEnabled())
        {
            StringBuffer logMessageBuffer =
                new StringBuffer("Attempting delivery of message with id. ");
            logMessageBuffer.append(getMessageIn().getMessageID());
            getLogger().debug(logMessageBuffer.toString());
        }

        // Determine the intended recipient
        MailAddress intendedRecipient = getIntendedRecipient();
        setRecipientNotFound(null == intendedRecipient);

        if (isRecipientNotFound())
        {
            if (isDeferRecipientNotFound())
            {

                String messageID = getMessageIn().getMessageID();
                if (!getDeferredRecipientNotFoundMessageIDs()
                    .contains(messageID))
                {
                    getDeferredRecipientNotFoundMessageIDs().add(messageID);
                    if (getLogger().isDebugEnabled())
                    {
                        StringBuffer messageBuffer =
                            new StringBuffer("Deferred processing of message for which the intended recipient could not be found. Message ID: ");
                        messageBuffer.append(messageID);
                        getLogger().debug(messageBuffer.toString());
                    }
                    return;
                }
                else
                {
                    getDeferredRecipientNotFoundMessageIDs().remove(messageID);
                    if (getLogger().isDebugEnabled())
                    {
                        StringBuffer messageBuffer =
                            new StringBuffer("Processing deferred message for which the intended recipient could not be found. Message ID: ");
                        messageBuffer.append(messageID);
                        getLogger().debug(messageBuffer.toString());
                    }
                }
            }

            if (isRejectRecipientNotFound())
            {
                rejectRecipientNotFound();
                return;
            }
            intendedRecipient = getRecipient();
            StringBuffer messageBuffer =
                new StringBuffer("Intended recipient not found. Using configured recipient as new envelope recipient - ");
            messageBuffer.append(intendedRecipient);
            messageBuffer.append('.');
            logStatusInfo(messageBuffer.toString());
        }

        // Set the filter states
        setBlacklistedRecipient(isBlacklistedRecipient(intendedRecipient));
        setRemoteRecipient(!isLocalServer(intendedRecipient));
        setUserUndefined(!isLocalRecipient(intendedRecipient));

        // Apply the filters. Return if rejected
        if (isRejectBlacklisted() && isBlacklistedRecipient())
        {
            rejectBlacklistedRecipient(intendedRecipient);
            return;
        }

        if (isRejectRemoteRecipient() && isRemoteRecipient())
        {
            rejectRemoteRecipient(intendedRecipient);
            return;
        }

        if (isRejectUserUndefined() && isUserUndefined())
        {
            rejectUserUndefined(intendedRecipient);
            return;
        }

        if (isRejectMaxMessageSizeExceeded()
            && isMaxMessageSizeExceeded().booleanValue())
        {
            rejectMaxMessageSizeExceeded(getMessageIn().getSize());
            return;
        }
        
        if (isRejectRemoteReceivedHeaderInvalid()
            && isRemoteReceivedHeaderInvalid().booleanValue())
        {
            rejectRemoteReceivedHeaderInvalid();
            return;
        }        

        // Create the mail
        // If any of the mail addresses are malformed, we will get a
        // ParseException. 
        // If the IP address and host name for the remote domain cannot
        // be found, we will get an UnknownHostException.
        // In both cases, we log the problem and
        // return. The message disposition is defined by the
        // <undeliverable> attributes.
        Mail mail = null;
        try
        {
            mail = createMail(createMessage(), intendedRecipient);
        }
        catch (ParseException ex)
        {
            handleParseException(ex);
            return;
        }
        catch (UnknownHostException ex)
        {
            handleUnknownHostException(ex);
            return;
        }

        addMailAttributes(mail);
        addErrorMessages(mail);        

        // If this mail is bouncing move it to the ERROR repository
        if (isBouncing())
        {
            handleBouncing(mail);
            return;
        }

        // OK, lets send that mail!
        sendMail(mail);
    }

    /**
     * Method rejectRemoteRecipient.
     * @param recipient
     * @throws MessagingException
     */
    protected void rejectRemoteRecipient(MailAddress recipient)
        throws MessagingException
    {
        // Update the flags of the received message
        if (!isLeaveRemoteRecipient())
            setMessageDeleted();

        if (isMarkRemoteRecipientSeen())
            setMessageSeen();

        StringBuffer messageBuffer =
            new StringBuffer("Rejected mail intended for remote recipient: ");
        messageBuffer.append(recipient);
        messageBuffer.append('.');          
        logStatusInfo(messageBuffer.toString());

        return;
    }
    
    /**
     * Method rejectBlacklistedRecipient.
     * @param recipient
     * @throws MessagingException
     */
    protected void rejectBlacklistedRecipient(MailAddress recipient)
        throws MessagingException
    {
        // Update the flags of the received message
        if (!isLeaveBlacklisted())
            setMessageDeleted();
        if (isMarkBlacklistedSeen())
            setMessageSeen();

        StringBuffer messageBuffer =
            new StringBuffer("Rejected mail intended for blacklisted recipient: ");
        messageBuffer.append(recipient);
        messageBuffer.append('.');        
        logStatusInfo(messageBuffer.toString());

        return;
    }

    /**
     * Method rejectRecipientNotFound.
     * @throws MessagingException
     */
    protected void rejectRecipientNotFound() throws MessagingException
    {
        // Update the flags of the received message
        if (!isLeaveRecipientNotFound())
            setMessageDeleted();

        if (isMarkRecipientNotFoundSeen())
            setMessageSeen();

        StringBuffer messageBuffer =
            new StringBuffer("Rejected mail for which a sole intended recipient could not be found.");
        messageBuffer.append(" Recipients: ");
        Address[] allRecipients = getMessageIn().getAllRecipients();
        for (int i = 0; i < allRecipients.length; i++)
        {
            messageBuffer.append(allRecipients[i]);
            messageBuffer.append(' ');
        }
        messageBuffer.append('.');          
        logStatusInfo(messageBuffer.toString());
        return;
    }
    
    /**
     * Method rejectUserUndefined.
     * @param recipient
     * @throws MessagingException
     */
    protected void rejectUserUndefined(MailAddress recipient)
        throws MessagingException
    {
        // Update the flags of the received message
        if (!isLeaveUserUndefined())
            setMessageDeleted();

        if (isMarkUserUndefinedSeen())
            setMessageSeen();

        StringBuffer messageBuffer =
            new StringBuffer("Rejected mail intended for undefined user: ");
        messageBuffer.append(recipient);
        messageBuffer.append('.');          
        logStatusInfo(messageBuffer.toString());

        return;
    }
    
    /**
     * Method rejectMaxMessageSizeExceeded.
     * @param message size
     * @throws MessagingException
     */
    protected void rejectMaxMessageSizeExceeded(int messageSize)
        throws MessagingException
    {
        // Update the flags of the received message
        if (!isLeaveMaxMessageSizeExceeded())
            setMessageDeleted();

        if (isMarkMaxMessageSizeExceededSeen())
            setMessageSeen();

        StringBuffer messageBuffer =
            new StringBuffer("Rejected mail exceeding message size limit. Message size: ");
        messageBuffer.append(messageSize/1024);
        messageBuffer.append("KB.");          
        logStatusInfo(messageBuffer.toString());

        return;
    }
    
    /**
     * Method rejectRemoteReceivedHeaderInvalid.
     * @throws MessagingException
     */
    protected void rejectRemoteReceivedHeaderInvalid()
        throws MessagingException
    {
        // Update the flags of the received message
        if (!isLeaveRemoteReceivedHeaderInvalid())
            setMessageDeleted();

        if (isMarkRemoteReceivedHeaderInvalidSeen())
            setMessageSeen();

        StringBuffer messageBuffer =
            new StringBuffer("Rejected mail with an invalid Received: header at index ");
        messageBuffer.append(getRemoteReceivedHeaderIndex());
        messageBuffer.append(".");          
        logStatusInfo(messageBuffer.toString());       
        return;
    }           
    
    /**
     * <p>Method createMessage answers a new <code>MimeMessage</code> from the
     * fetched message.</p>
     * 
     * <p>If the maximum message size is exceeded, an empty message is created,
     * else the new message is a copy of the received message.</p>
     * 
     * @return MimeMessage
     * @throws MessagingException
     */
    protected MimeMessage createMessage() throws MessagingException
    {
        // Create a new messsage from the received message
        MimeMessage messageOut = null;
        if (isMaxMessageSizeExceeded().booleanValue())
            messageOut = createEmptyMessage();
        else
            messageOut = new MimeMessage(getMessageIn());

        // set the X-fetched headers
        // Note this is still required to detect bouncing mail and
        // for backwards compatibility with fetchPop 
        messageOut.addHeader("X-fetched-from", getFetchTaskName());

        return messageOut;
    }
    
    /**
     * Method createEmptyMessage answers a new 
     * <code>MimeMessage</code> from the fetched message with the message 
     * contents removed. 
     * 
     * @return MimeMessage
     * @throws MessagingException
     */
    protected MimeMessage createEmptyMessage()
        throws MessagingException
    {
        // Create an empty messsage
        MimeMessage messageOut = new MimeMessage(getSession());

        // Propogate the headers and subject
        Enumeration headersInEnum = getMessageIn().getAllHeaderLines();
        while (headersInEnum.hasMoreElements())
            messageOut.addHeaderLine((String) headersInEnum.nextElement());
        messageOut.setSubject(getMessageIn().getSubject());

        // Add empty text
        messageOut.setText("");

        // Save
        messageOut.saveChanges();

        return messageOut;
    }       

    /**
     * Method createMail creates a new <code>Mail</code>.
     * 
     * @param message
     * @param recipient
     * @return Mail
     * @throws MessagingException
     */
    protected Mail createMail(MimeMessage message, MailAddress recipient)
        throws MessagingException, UnknownHostException
    {
        Collection recipients = new ArrayList(1);
        recipients.add(recipient);
        MailImpl mail =
            new MailImpl(getServer().getId(), getSender(), recipients, message);
        // Ensure the mail is created with non-null remote host name and address,
        // otherwise the Mailet chain may go splat!
        if (getRemoteAddress() == null || getRemoteHostName() == null)
        {
            mail.setRemoteAddr("127.0.0.1");
            mail.setRemoteHost("localhost");
        }
        else
        {
            mail.setRemoteAddr(getRemoteAddress());
            mail.setRemoteHost(getRemoteHostName());
        }

        if (getLogger().isDebugEnabled())
        {
            StringBuffer messageBuffer =
                new StringBuffer("Created mail with name: ");
            messageBuffer.append(mail.getName());
            messageBuffer.append(", sender: ");
            messageBuffer.append(mail.getSender());
            messageBuffer.append(", recipients: ");
            Iterator recipientIterator = mail.getRecipients().iterator();
            while (recipientIterator.hasNext())
            {
                messageBuffer.append(recipientIterator.next());
                messageBuffer.append(' ');
            }
            messageBuffer.append(", remote address: ");
            messageBuffer.append(mail.getRemoteAddr());
            messageBuffer.append(", remote host name: ");
            messageBuffer.append(mail.getRemoteHost());
            messageBuffer.append('.');
            getLogger().debug(messageBuffer.toString());
        }
        return mail;
    }
     

    /**
     * Method getSender answers a <code>MailAddress</code> for the sender.
     * 
     * @return MailAddress
     * @throws MessagingException
     */
    protected MailAddress getSender() throws MessagingException
    {
        String from = "FETCHMAIL-SERVICE";
        try {
            from = ((InternetAddress) getMessageIn().getFrom()[0]).getAddress().trim();
        }
        catch (Exception _) {
            getLogger().info("Could not identify sender -- using default value");
        }

        InternetAddress internetAddress = null;

        // Check for domain part, add default if missing
        if (from.indexOf('@') < 0)
        {
            StringBuffer fromBuffer = new StringBuffer(from);
            fromBuffer.append('@');
            fromBuffer.append(getDefaultDomainName());
            internetAddress = new InternetAddress(fromBuffer.toString());
        }
        else
            internetAddress = new InternetAddress(from);

        return new MailAddress(internetAddress);
    }
    
    /**
     * <p>Method computeRemoteDomain answers a <code>String</code> that is the
     * RFC2822 compliant "Received : from" domain extracted from the message
     * being processed.</p>
     * 
     * <p>Normally this is the domain that sent the message to the host for the
     * message store as reported by the second "received" header. The index of
     * the header to use is specified by the configuration parameter
     * <code>RemoteReceivedHeaderIndex</code>. If a header at this index does
     * not exist, the domain of the successively closer "received" headers
     * is tried until they are exhausted, then "localhost" is used.</p>
     * 
     * @return String
     */
    protected String computeRemoteDomain() throws MessagingException
    {
        StringBuffer domainBuffer = new StringBuffer();        
        String[] headers = null; 
        if (getRemoteReceivedHeaderIndex() > -1)
              getMessageIn().getHeader(RFC2822Headers.RECEIVED);
              
        if (null != headers)
        {
            // If there are RECEIVED headers and the index to begin at is greater
            // than -1, try and extract the domain
            if (headers.length > 0)
            {
                final String headerTokens = " \n\r";

                // Search the headers for a domain
                for (int headerIndex =
                    headers.length > getRemoteReceivedHeaderIndex()
                        ? getRemoteReceivedHeaderIndex()
                        : headers.length - 1;
                    headerIndex >= 0 && domainBuffer.length() == 0;
                    headerIndex--)
                {
                    // Find the "from" token
                    StringTokenizer tokenizer =
                        new StringTokenizer(headers[headerIndex], headerTokens);
                    boolean inFrom = false;
                    while (!inFrom && tokenizer.hasMoreTokens())
                        inFrom = tokenizer.nextToken().equals("from");

                    // Add subsequent tokens to the domain buffer until another 
                    // field is encountered or there are no more tokens
                    while (inFrom && tokenizer.hasMoreTokens())
                    {
                        String token = tokenizer.nextToken();
                        if (inFrom =
                            getRFC2822RECEIVEDHeaderFields().indexOf(token)
                                == -1)
                        {
                            domainBuffer.append(token);
                            domainBuffer.append(' ');
                        }
                    }
                }
            }
        }

        // Default is "localhost"
        if (domainBuffer.length() == 0)
            domainBuffer.append("localhost");

        return domainBuffer.toString().trim();
    }    
    
    /**
     * Method handleBouncing sets the Mail state to ERROR and delete from
     * the message store.
     * 
     * @param mail
     */
    protected void handleBouncing(Mail mail) throws MessagingException
    {
        mail.setState(Mail.ERROR);
        setMessageDeleted();

        mail.setErrorMessage(
            "This mail from FetchMail task "
                + getFetchTaskName()
                + " seems to be bouncing!");
        logStatusError("Message is bouncing! Deleted from message store and moved to the Error repository.");
    }
    
    /**
     * Method handleParseException.
     * @param ex
     * @throws MessagingException
     */
    protected void handleParseException(ParseException ex)
        throws MessagingException
    {
        // Update the flags of the received message
        if (!isLeaveUndeliverable())
            setMessageDeleted();
        if (isMarkUndeliverableSeen())
            setMessageSeen();
        logStatusWarn("Message could not be delivered due to an error parsing a mail address.");
        if (getLogger().isDebugEnabled())
        {
            StringBuffer messageBuffer =
                new StringBuffer("UNDELIVERABLE Message ID: ");
            messageBuffer.append(getMessageIn().getMessageID());
            getLogger().debug(messageBuffer.toString(), ex);
        }
    }
    
    /**
     * Method handleUnknownHostException.
     * @param ex
     * @throws MessagingException
     */
    protected void handleUnknownHostException(UnknownHostException ex)
        throws MessagingException
    {
        // Update the flags of the received message
        if (!isLeaveUndeliverable())
            setMessageDeleted();
    
        if (isMarkUndeliverableSeen())
            setMessageSeen();
    
        logStatusWarn("Message could not be delivered due to an error determining the remote domain.");
        if (getLogger().isDebugEnabled())
        {
            StringBuffer messageBuffer =
                new StringBuffer("UNDELIVERABLE Message ID: ");
            messageBuffer.append(getMessageIn().getMessageID());
            getLogger().debug(messageBuffer.toString(), ex);
        }
    }    
    
    /**
     * Method isLocalRecipient.
     * @param recipient
     * @return boolean
     */
    protected boolean isLocalRecipient(MailAddress recipient)
    {
        return isLocalUser(recipient) && isLocalServer(recipient);
    }
    
    /**
     * Method isLocalServer.
     * @param recipient
     * @return boolean
     */
    protected boolean isLocalServer(MailAddress recipient)
    {
        return getServer().isLocalServer(recipient.getHost());
    }
    
    /**
     * Method isLocalUser.
     * @param recipient
     * @return boolean
     */
    protected boolean isLocalUser(MailAddress recipient)
    {
        return getLocalUsers().containsCaseInsensitive(recipient.getUser());
    }       
    
    /**
     * Method isBlacklistedRecipient.
     * @param recipient
     * @return boolean
     */
    protected boolean isBlacklistedRecipient(MailAddress recipient)
    {
        return getBlacklist().contains(recipient);
    }       

    /**
     * Check if this mail has been bouncing by counting the X-fetched-from 
     * headers for this task
     * 
     * @return boolean
     */
    protected boolean isBouncing() throws MessagingException
    {
        Enumeration enum =
            getMessageIn().getMatchingHeaderLines(
                new String[] { "X-fetched-from" });
        int count = 0;
        while (enum.hasMoreElements())
        {
            String header = (String) enum.nextElement();
            if (header.equals(getFetchTaskName()))
                count++;
        }
        return count >= 3;
    }
    
    /**
     * Method sendMail.
     * @param mail
     * @throws MessagingException
     */
    protected void sendMail(Mail mail) throws MessagingException
    {
        // send the mail
        getServer().sendMail(mail);

        // Update the flags of the received message
        if (!isLeave())
            setMessageDeleted();

        if (isMarkSeen())
            setMessageSeen();

        // Log the status
        StringBuffer messageBuffer =
            new StringBuffer("Spooled message to recipients: ");
        Iterator recipientIterator = mail.getRecipients().iterator();
        while (recipientIterator.hasNext())
        {
            messageBuffer.append(recipientIterator.next());
            messageBuffer.append(' ');
        }
        messageBuffer.append('.');
        logStatusInfo(messageBuffer.toString());
    }   


    /**
     * Method getEnvelopeRecipient answers the recipient if found else null.
     * 
     * Try and parse the "for" parameter from a Received header
     * Maybe not the most accurate parsing in the world but it should do
     * I opted not to use ORO (maybe I should have)
     * 
     * @param msg
     * @return String
     */

    protected String getEnvelopeRecipient(MimeMessage msg) throws MessagingException
    {
        try
        {
            Enumeration enum =
                msg.getMatchingHeaderLines(new String[] { "Received" });
            while (enum.hasMoreElements())
            {
                String received = (String) enum.nextElement();

                int nextSearchAt = 0;
                int i = 0;
                int start = 0;
                int end = 0;
                boolean hasBracket = false;
                boolean usableAddress = false;
                while (!usableAddress && (i != -1))
                {
                    hasBracket = false;
                    i = received.indexOf("for ", nextSearchAt);
                    if (i > 0)
                    {
                        start = i + 4;
                        end = 0;
                        nextSearchAt = start;
                        for (int c = start; c < received.length(); c++)
                        {
                            char ch = received.charAt(c);
                            switch (ch)
                            {
                                case '<' :
                                    hasBracket = true;
                                    continue;
                                case '@' :
                                    usableAddress = true;
                                    continue;
                                case ' ' :
                                    end = c;
                                    break;
                                case ';' :
                                    end = c;
                                    break;
                            }
                            if (end > 0)
                                break;
                        }
                    }
                }
                if (usableAddress)
                {
                    // lets try and grab the email address
                    String mailFor = received.substring(start, end);

                    // strip the <> around the address if there are any
                    if (mailFor.startsWith("<") && mailFor.endsWith(">"))
                        mailFor = mailFor.substring(1, (mailFor.length() - 1));

                    return mailFor;
                }
            }
        }
        catch (MessagingException me)
        {
            logStatusWarn("No Received headers found.");
        }
        return null;
    }
    
    /**
     * Method getIntendedRecipient answers the sole intended recipient else null.
     * 
     * @return MailAddress
     * @throws MessagingException
     */
    protected MailAddress getIntendedRecipient() throws MessagingException
    {
        // If the original recipient should be ignored, answer the 
        // hard-coded recipient
        if (isIgnoreRecipientHeader())
        {
            StringBuffer messageBuffer =
                new StringBuffer("Ignoring recipient header. Using configured recipient as new envelope recipient: ");
            messageBuffer.append(getRecipient());
            messageBuffer.append('.');            
            logStatusInfo(messageBuffer.toString());
            return getRecipient();
        }

        // If we can determine who the message was received for, answer
        // the target recipient
        String targetRecipient = getEnvelopeRecipient(getMessageIn());
        if (targetRecipient != null)
        {
            MailAddress recipient = new MailAddress(targetRecipient);
            StringBuffer messageBuffer =
                new StringBuffer("Using original envelope recipient as new envelope recipient: ");
            messageBuffer.append(recipient);
            messageBuffer.append('.');              
            logStatusInfo(messageBuffer.toString());
            return recipient;
        }

        // If we can determine the intended recipient from all of the recipients,
        // answer the intended recipient. This requires that there is exactly one
        // recipient answered by getAllRecipients(), which examines the TO: CC: and
        // BCC: headers
        Address[] allRecipients = getMessageIn().getAllRecipients();
        if (allRecipients.length == 1)
        {
            MailAddress recipient =
                new MailAddress((InternetAddress) allRecipients[0]);
            StringBuffer messageBuffer =
                new StringBuffer("Using sole recipient header address as new envelope recipient: ");
            messageBuffer.append(recipient);
            messageBuffer.append('.');              
            logStatusInfo(messageBuffer.toString());
            return recipient;
        }

        return null;
    }           

    /**
     * Returns the messageIn.
     * @return MimeMessage
     */
    protected MimeMessage getMessageIn()
    {
        return fieldMessageIn;
    }

    /**
     * Sets the messageIn.
     * @param messageIn The messageIn to set
     */
    protected void setMessageIn(MimeMessage messageIn)
    {
        fieldMessageIn = messageIn;
    }

    /**
     * Returns the localRecipient.
     * @return boolean
     */
    protected boolean isRemoteRecipient()
    {
        return fieldRemoteRecipient;
    }
    
    /**
     * Returns <code>boolean</code> indicating if the message to be delivered
     * was unprocessed in a previous delivery attempt.
     * @return boolean
     */
    protected boolean isPreviouslyUnprocessed()
    {
        return true;
    }
    
    /**
     * Log the status of the current message as INFO.
     * @param detailMsg
     */
    protected void logStatusInfo(String detailMsg) throws MessagingException
    {
        getLogger().info(getStatusReport(detailMsg).toString());
    }

    /**
     * Log the status the current message as WARN.
     * @param detailMsg
     */
    protected void logStatusWarn(String detailMsg) throws MessagingException
    {
        getLogger().warn(getStatusReport(detailMsg).toString());
    }
    
    /**
     * Log the status the current message as ERROR.
     * @param detailMsg
     */
    protected void logStatusError(String detailMsg) throws MessagingException
    {
        getLogger().error(getStatusReport(detailMsg).toString());
    }    

    /**
     * Answer a <code>StringBuffer</code> containing a message reflecting
     * the current status of the message being processed.
     * 
     * @param detailMsg
     * @return StringBuffer
     */
    protected StringBuffer getStatusReport(String detailMsg) throws MessagingException
    {
        StringBuffer messageBuffer = new StringBuffer(detailMsg);
        if (detailMsg.length() > 0)
            messageBuffer.append(' ');
        messageBuffer.append("Message ID: ");
        messageBuffer.append(getMessageIn().getMessageID());
        messageBuffer.append(". Flags: Seen = ");
        messageBuffer.append(new Boolean(isMessageSeen()));
        messageBuffer.append(", Delete = ");
        messageBuffer.append(new Boolean(isMessageDeleted()));
        messageBuffer.append('.');
        return messageBuffer;
    }    
    
    /**
     * Returns the userUndefined.
     * @return boolean
     */
    protected boolean isUserUndefined()
    {
        return fieldUserUndefined;
    }
    
    /**
     * Is the DELETED flag set?
     * @throws MessagingException
     */
    protected boolean isMessageDeleted() throws MessagingException
    {
       return getMessageIn().isSet(Flags.Flag.DELETED);
    }
    
    /**
     * Is the SEEN flag set?
     * @throws MessagingException
     */
    protected boolean isMessageSeen() throws MessagingException
    {
       return getMessageIn().isSet(Flags.Flag.SEEN);
    }    
    
    /**
     * Set the DELETED flag.
     * @throws MessagingException
     */
    protected void setMessageDeleted() throws MessagingException
    {
            getMessageIn().setFlag(Flags.Flag.DELETED, true);
    }    
    
    /*    /**
     * Set the SEEN flag.
     * @throws MessagingException
     */
    protected void setMessageSeen() throws MessagingException
    {
        // If the Seen flag is not handled by the folder
        // allow a handler to do whatever it deems necessary
        if (!getMessageIn()
            .getFolder()
            .getPermanentFlags()
            .contains(Flags.Flag.SEEN))
            handleMarkSeenNotPermanent();
        else
            getMessageIn().setFlag(Flags.Flag.SEEN, true);
    }
    
    /**
     * <p>Handler for when the folder does not support the SEEN flag.
     * The default behaviour implemented here is to log a warning and set the
     * flag anyway.</p>
     * 
     * <p> Subclasses may choose to override this and implement their own
     * solutions.</p>
     *  
     * @throws MessagingException
     */
    protected void handleMarkSeenNotPermanent() throws MessagingException
    {
        getMessageIn().setFlag(Flags.Flag.SEEN, true);
        logStatusWarn("Message marked as SEEN, but the folder does not support a permanent SEEN flag.");
    }            

    /**
     * Returns the Blacklisted.
     * @return boolean
     */
    protected boolean isBlacklistedRecipient()
    {
        return fieldBlacklistedRecipient;
    }

    /**
     * Sets the localRecipient.
     * @param localRecipient The localRecipient to set
     */
    protected void setRemoteRecipient(boolean localRecipient)
    {
        fieldRemoteRecipient = localRecipient;
    }

    /**
     * Sets the userUndefined.
     * @param userUndefined The userUndefined to set
     */
    protected void setUserUndefined(boolean userUndefined)
    {
        fieldUserUndefined = userUndefined;
    }
    
    /**
     * Adds the mail attributes to a <code>Mail</code>. 
     * @param aMail a Mail instance
     */
    protected void addMailAttributes(Mail aMail) throws MessagingException
    {
        aMail.setAttribute(
            getAttributePrefix() + "taskName",
            getFetchTaskName());

        aMail.setAttribute(
            getAttributePrefix() + "folderName",
            getMessageIn().getFolder().getFullName());

        if (isRemoteRecipient())
            aMail.setAttribute(
                getAttributePrefix() + "isRemoteRecipient",
                null);

        if (isUserUndefined())
            aMail.setAttribute(getAttributePrefix() + "isUserUndefined", null);

        if (isBlacklistedRecipient())
            aMail.setAttribute(
                getAttributePrefix() + "isBlacklistedRecipient",
                null);

        if (isRecipientNotFound())
            aMail.setAttribute(
                getAttributePrefix() + "isRecipientNotFound",
                null);

        if (isMaxMessageSizeExceeded().booleanValue())
            aMail.setAttribute(
                getAttributePrefix() + "isMaxMessageSizeExceeded",
                new Integer(getMessageIn().getSize()).toString());
                
        if (isRemoteReceivedHeaderInvalid().booleanValue())
            aMail.setAttribute(
                getAttributePrefix() + "isRemoteReceivedHeaderInvalid",
                null);                
    }

    /**
     * Adds any  required error messages to a <code>Mail</code>. 
     * @param aMail a Mail instance
     */
    protected void addErrorMessages(Mail mail) throws MessagingException
    {
        if (isMaxMessageSizeExceeded().booleanValue())
        {
            StringBuffer msgBuffer =
                new StringBuffer("550 - Rejected - This message has been rejected as the message size of ");
            msgBuffer.append(getMessageIn().getSize() * 1000 / 1024 / 1000f);
            msgBuffer.append("KB exceeds the maximum permitted size of ");
            msgBuffer.append(getMaxMessageSizeLimit() / 1024);
            msgBuffer.append("KB.");
            mail.setErrorMessage(msgBuffer.toString());
        }
    }         

    /**
     * Sets the Blacklisted.
     * @param blacklisted The blacklisted to set
     */
    protected void setBlacklistedRecipient(boolean blacklisted)
    {
        fieldBlacklistedRecipient = blacklisted;
    }

    /**
     * Returns the recipientNotFound.
     * @return boolean
     */
    protected boolean isRecipientNotFound()
    {
        return fieldRecipientNotFound;
    }

    /**
     * Sets the recipientNotFound.
     * @param recipientNotFound The recipientNotFound to set
     */
    protected void setRecipientNotFound(boolean recipientNotFound)
    {
        fieldRecipientNotFound = recipientNotFound;
    }

    /**
     * Returns the remoteDomain, lazily initialised as required.
     * @return String
     */
    protected String getRemoteDomain() throws MessagingException
    {
        String remoteDomain;
        if (null == (remoteDomain = getRemoteDomainBasic()))
        {
            updateRemoteDomain();
            return getRemoteDomain();
        }    
        return remoteDomain;
    }
    
    /**
     * Returns the remoteDomain.
     * @return String
     */
    private String getRemoteDomainBasic()
    {
        return fieldRemoteDomain;
    }    

    /**
     * Sets the remoteDomain.
     * @param remoteDomain The remoteDomain to set
     */
    protected void setRemoteDomain(String remoteDomain)
    {
        fieldRemoteDomain = remoteDomain;
    }
    
    /**
     * Updates the remoteDomain.
     */
    protected void updateRemoteDomain() throws MessagingException
    {
        setRemoteDomain(computeRemoteDomain());
    }
    
    /**
     * Answer the IP Address of the remote server for the message being 
     * processed.
     * @return String
     * @throws MessagingException
     * @throws UnknownHostException
     */
    protected String computeRemoteAddress()
        throws MessagingException, UnknownHostException
    {
        String domain = getRemoteDomain();
        String address = null;
        String validatedAddress = null;
        int ipAddressStart = domain.indexOf('[');
        int ipAddressEnd = -1;
        if (ipAddressStart > -1)
            ipAddressEnd = domain.indexOf(']', ipAddressStart);
        if (ipAddressEnd > -1)
            address = domain.substring(ipAddressStart + 1, ipAddressEnd);
        else
        {
            int hostNameEnd = domain.indexOf(' ');
            if (hostNameEnd == -1)
                hostNameEnd = domain.length();
            address = domain.substring(0, hostNameEnd);
        }
        validatedAddress = org.apache.james.dnsserver.DNSServer.getByName(address).getHostAddress();

        return validatedAddress;
    }

    /**
     * Answer the Canonical host name of the remote server for the message 
     * being processed.
     * @return String
     * @throws MessagingException
     * @throws UnknownHostException
     */
    protected String computeRemoteHostName()
        throws MessagingException, UnknownHostException
    {
        // These shenanigans are required to get the fully qualified
        // hostname prior to JDK 1.4 in which get getCanonicalHostName()
        // does the job for us
        InetAddress addr1 = org.apache.james.dnsserver.DNSServer.getByName(getRemoteAddress());
        InetAddress addr2 = org.apache.james.dnsserver.DNSServer.getByName(addr1.getHostAddress());
        return addr2.getHostName();
    }        

    /**
     * Returns the remoteAddress, lazily initialised as required.
     * @return String
     */
    protected String getRemoteAddress()
        throws MessagingException, UnknownHostException
    {
        String remoteAddress;
        if (null == (remoteAddress = getRemoteAddressBasic()))
        {
            updateRemoteAddress();
            return getRemoteAddress();
        }
        return remoteAddress;
    }
    
    /**
     * Returns the remoteAddress.
     * @return String
     */
    private String getRemoteAddressBasic()
    {
        return fieldRemoteAddress;
    }    

    /**
     * Returns the remoteHostName, lazily initialised as required.
     * @return String
     */
    protected String getRemoteHostName()
        throws MessagingException, UnknownHostException
    {
        String remoteHostName;
        if (null == (remoteHostName = getRemoteHostNameBasic()))
        {
            updateRemoteHostName();
            return getRemoteHostName();
        }
        return remoteHostName;
    }
    
    /**
     * Returns the remoteHostName.
     * @return String
     */
    private String getRemoteHostNameBasic()
    {
        return fieldRemoteHostName;
    }    

    /**
     * Sets the remoteAddress.
     * @param remoteAddress The remoteAddress to set
     */
    protected void setRemoteAddress(String remoteAddress)
    {
        fieldRemoteAddress = remoteAddress;
    }
    
    /**
     * Updates the remoteAddress.
     */
    protected void updateRemoteAddress()
        throws MessagingException, UnknownHostException
    {
        setRemoteAddress(computeRemoteAddress());
    }    

    /**
     * Sets the remoteHostName.
     * @param remoteHostName The remoteHostName to set
     */
    protected void setRemoteHostName(String remoteHostName)
    {
        fieldRemoteHostName = remoteHostName;
    }
    
    /**
     * Updates the remoteHostName.
     */
    protected void updateRemoteHostName()
        throws MessagingException, UnknownHostException
    {
        setRemoteHostName(computeRemoteHostName());
    }    

    /**
     * Returns the rFC2822RECEIVEDHeaderFields.
     * @return String
     */
    public static String getRFC2822RECEIVEDHeaderFields()
    {
        return fieldRFC2822RECEIVEDHeaderFields;
    }

    /**
     * Returns the maxMessageSizeExceeded, lazily initialised as required.
     * @return Boolean
     */
    protected Boolean isMaxMessageSizeExceeded() throws MessagingException
    {
        Boolean isMaxMessageSizeExceeded = null;
        if (null
            == (isMaxMessageSizeExceeded = isMaxMessageSizeExceededBasic()))
        {
            updateMaxMessageSizeExceeded();
            return isMaxMessageSizeExceeded();
        }
        return isMaxMessageSizeExceeded;
    }    

    /**
     * Refreshes the maxMessageSizeExceeded.
     */
    protected void updateMaxMessageSizeExceeded() throws MessagingException
    {
        setMaxMessageSizeExceeded(computeMaxMessageSizeExceeded());
    }

    /**
     * Compute the maxMessageSizeExceeded.
     * @return Boolean
     */
    protected Boolean computeMaxMessageSizeExceeded() throws MessagingException
    {
        if (0 == getMaxMessageSizeLimit())
            return Boolean.FALSE;
        return new Boolean(getMessageIn().getSize() > getMaxMessageSizeLimit());
    }
    
    /**
     * Returns the maxMessageSizeExceeded.
     * @return Boolean
     */
    private Boolean isMaxMessageSizeExceededBasic()
    {
        return fieldMaxMessageSizeExceeded;
    }    

    /**
     * Sets the maxMessageSizeExceeded.
     * @param maxMessageSizeExceeded The maxMessageSizeExceeded to set
     */
    protected void setMaxMessageSizeExceeded(Boolean maxMessageSizeExceeded)
    {
        fieldMaxMessageSizeExceeded = maxMessageSizeExceeded;
    }

    /**
     * Returns the remoteReceivedHeaderInvalid, lazily initialised.
     * @return Boolean
     */
    protected Boolean isRemoteReceivedHeaderInvalid() throws MessagingException
    {
        Boolean isInvalid = null;
        if (null == (isInvalid = isRemoteReceivedHeaderInvalidBasic()))
        {
            updateRemoteReceivedHeaderInvalid();
            return isRemoteReceivedHeaderInvalid();
        }    
        return isInvalid;
    }
    
    /**
     * Computes the remoteReceivedHeaderInvalid.
     * @return Boolean
     */
    protected Boolean computeRemoteReceivedHeaderInvalid()
        throws MessagingException
    {
        Boolean isInvalid = Boolean.FALSE;
        try
        {
            getRemoteAddress();
        }
        catch (UnknownHostException e)
        {
            isInvalid = Boolean.TRUE;
        }
        return isInvalid;
    }    
    
    /**
     * Returns the remoteReceivedHeaderInvalid.
     * @return Boolean
     */
    private Boolean isRemoteReceivedHeaderInvalidBasic()
    {
        return fieldRemoteReceivedHeaderInvalid;
    }    

    /**
     * Sets the remoteReceivedHeaderInvalid.
     * @param remoteReceivedHeaderInvalid The remoteReceivedHeaderInvalid to set
     */
    protected void setRemoteReceivedHeaderInvalid(Boolean remoteReceivedHeaderInvalid)
    {
        fieldRemoteReceivedHeaderInvalid = remoteReceivedHeaderInvalid;
    }
    
    /**
     * Updates the remoteReceivedHeaderInvalid.
     */
    protected void updateRemoteReceivedHeaderInvalid() throws MessagingException
    {
        setRemoteReceivedHeaderInvalid(computeRemoteReceivedHeaderInvalid());
    }    

}
