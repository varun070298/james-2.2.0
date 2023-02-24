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

import org.apache.avalon.framework.component.ComponentManager;
import org.apache.james.Constants;
import org.apache.james.services.UsersRepository;
import org.apache.james.services.UsersStore;
import org.apache.james.util.RFC2822Headers;
import org.apache.james.util.XMLResources;
import org.apache.mailet.GenericMailet;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.MailetException;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.internet.ParseException;
import java.io.IOException;
import java.util.*;


/**
 * CommandListservProcessor processes messages intended for the list serv mailing list.
 * For command handling, see {@link CommandListservManager} <br />
 *
 * This class is based on the existing list serv processor shipped with James.
 * <br />
 * <br />
 *
 * To configure the CommandListservProcessor place this configuratin in the root processor:
 * <pre>
 * &lt;mailet match="RecipientIs=announce@localhost" class="CommandListservProcessor"&gt;
 *  &lt;membersonly&gt;false&lt;/membersonly&gt;
 *  &lt;attachmentsallowed&gt;true&lt;/attachmentsallowed&gt;
 *  &lt;replytolist&gt;true&lt;/replytolist&gt;
 *  &lt;repositoryName&gt;list-announce&lt;/repositoryName&gt;
 *  &lt;subjectprefix&gt;Announce&lt;/subjectprefix&gt;
 *  &lt;autobracket&gt;true&lt;/autobracket&gt;
 *  &lt;listOwner&gt;owner@localhost&lt;/listOwner&gt;
 *  &lt;listName&gt;announce&lt;/listName&gt;
 * &lt;/mailet&gt;
 *
 * </pre>
 *
 * @version CVS $Revision: 1.1.2.6 $ $Date: 2004/04/16 20:59:14 $
 * @since 2.2.0
 */
public class CommandListservProcessor extends GenericMailet {

    /**
     * Whether only members can post to the list specified by the config param: 'membersonly'.
     * <br />
     * eg: <pre>&lt;membersonly&gt;false&lt;/membersonly&gt;</pre>
     *
     * Defaults to false
     */
    protected boolean membersOnly;

    /**
     * Whether attachments can be sent to the list specified by the config param: 'attachmentsallowed'.
     * <br />
     * eg: <pre>&lt;attachmentsallowed&gt;true&lt;/attachmentsallowed&gt;</pre>
     *
     * Defaults to true
     */
    protected boolean attachmentsAllowed;

    /**
     * Whether the reply-to header should be set to the list address
     * specified by the config param: 'replytolist'.
     * <br />
     * eg: <pre>&lt;replytolist&gt;true&lt;/replytolist&gt;</pre>
     *
     * Defaults to true
     */
    protected boolean replyToList;

    /**
     * A String to prepend to the subject of the message when it is sent to the list
     * specified by the config param: 'subjectPrefix'.
     * <br />
     * eg: <pre>&lt;subjectPrefix&gt;MyList&lt;/subjectPrefix&gt;</pre>
     *
     * For example: MyList
     */
    protected String subjectPrefix;

    /**
     * Whether the subject prefix should be bracketed with '[' and ']'
     * specified by the config param: 'autoBracket'.
     * <br />
     * eg: <pre>&lt;autoBracket&gt;true&lt;/autoBracket&gt;</pre>
     *
     * Defaults to true
     */
    protected boolean autoBracket;

    /**
     * The repository containing the users on this list
     * specified by the config param: 'repositoryName'.
     * <br />
     * eg: <pre>&lt;repositoryName&gt;list-announce&lt;/repositoryName&gt;</pre>
     */
    protected UsersRepository usersRepository;

    /**
     * The list owner
     * specified by the config param: 'listOwner'.
     * <br />
     * eg: <pre>&lt;listOwner&gt;owner@localhost&lt;/listOwner&gt;</pre>
     */
    protected MailAddress listOwner;

    /**
     * Name of the mailing list
     * specified by the config param: 'listName'.
     * <br />
     * eg: <pre>&lt;listName&gt;announce&lt;/listName&gt;</pre>
     *
     */
    protected String listName;

    /**
     * The list serv manager
     */
    protected ICommandListservManager commandListservManager;

    /**
     * Mailet that will add the footer to the message
     */
    protected CommandListservFooter commandListservFooter;

    /**
     * @see XMLResources
     */
    protected XMLResources xmlResources;

    /**
     * Initialize the mailet
     */
    public void init() throws MessagingException {
        membersOnly = getBoolean("membersonly", false);
        attachmentsAllowed = getBoolean("attachmentsallowed", true);
        replyToList = getBoolean("replytolist", true);
        subjectPrefix = getString("subjectprefix", null);
        listName = getString("listName", null);
        autoBracket = getBoolean("autobracket", true);
        try {
            listOwner = new MailAddress(getString("listOwner", null));
            //initialize resources
            initializeResources();
            //init user repos
            initUsersRepository();
        } catch (Exception e) {
            throw new MessagingException(e.getMessage(), e);
        }
    }

    /**
     * A message was sent to the list serv.  Broadcast if appropriate...
     * @param mail
     * @throws MessagingException
     */
    public void service(Mail mail) throws MessagingException {
        try {
            Collection members = new ArrayList();
            members.addAll(getMembers());
            MailAddress listservAddr = (MailAddress) mail.getRecipients().iterator().next();

            //Check for members only flag....
            if (!checkMembers(members, mail)) {
                return;
            }

            //Check for no attachments
            if (!checkAnnouncements(mail)) {
                return;
            }

            //check been there
            if (!checkBeenThere(listservAddr, mail)) {
                return;
            }

            //addfooter
            addFooter(mail);

            //prepare the new message
            MimeMessage message = prepareListMessage(mail, listservAddr);

            //Set the subject if set
            setSubject(message);

            //Send the message to the list members
            //We set the list owner as the sender for now so bounces go to him/her
            getMailetContext().sendMail(listOwner, members, message);
        } catch (IOException ioe) {
            throw new MailetException("Error creating listserv message", ioe);
        } finally {
            //Kill the old message
            mail.setState(Mail.GHOST);
        }
    }

    /**
     * Add the footer using {@link CommandListservFooter}
     * @param mail
     * @throws MessagingException
     */
    protected void addFooter(Mail mail) throws MessagingException {
        getCommandListservFooter().service(mail);
    }

    protected void setSubject(MimeMessage message) throws MessagingException {
        String prefix = subjectPrefix;
        if (prefix != null) {
            if (autoBracket) {
                StringBuffer prefixBuffer =
                        new StringBuffer(64)
                        .append("[")
                        .append(prefix)
                        .append("]");
                prefix = prefixBuffer.toString();
            }
            String subj = message.getSubject();
            if (subj == null) {
                subj = "";
            }
            subj = normalizeSubject(subj, prefix);
            AbstractRedirect.changeSubject(message, subj);
        }
    }

    /**
     * Create a new message with some set headers
     * @param mail
     * @param listservAddr
     * @return a prepared List Message
     * @throws MessagingException
     */
    protected MimeMessage prepareListMessage(Mail mail, MailAddress listservAddr) throws MessagingException {
        //Create a copy of this message to send out
        MimeMessage message = new MimeMessage(mail.getMessage());

        //We need tao remove this header from the copy we're sending around
        message.removeHeader(RFC2822Headers.RETURN_PATH);

        //We're going to set this special header to avoid bounces
        //  getting sent back out to the list
        message.setHeader("X-been-there", listservAddr.toString());

        //If replies should go to this list, we need to set the header
        if (replyToList) {
            message.setHeader(RFC2822Headers.REPLY_TO, listservAddr.toString());
        }

        return message;
    }

    /**
     * return true if this is ok, false otherwise
     * Check if the X-been-there header is set to the listserv's name
     * (the address).  If it has, this means it's a message from this
     * listserv that's getting bounced back, so we need to swallow it
     *
     * @param listservAddr
     * @param mail
     * @return true if this message has already bounced, false otherwse
     * @throws MessagingException
     */
    protected boolean checkBeenThere(MailAddress listservAddr, Mail mail) throws MessagingException {
        if (listservAddr.equals(mail.getMessage().getHeader("X-been-there"))) {
            return false;
        }
        return true;
    }

    /**
     * Returns true if this is ok to send to the list
     * @param mail
     * @return true if this message is ok, false otherwise
     * @throws IOException
     * @throws MessagingException
     */
    protected boolean checkAnnouncements(Mail mail) throws IOException, MessagingException {
        if (!attachmentsAllowed && mail.getMessage().getContent() instanceof MimeMultipart) {
            Properties standardProperties = getCommandListservManager().getStandardProperties();

            getCommandListservManager().onError(mail,
                    xmlResources.getString("invalid.mail.subject", standardProperties),
                    xmlResources.getString("error.attachments", standardProperties));
            return false;
        }
        return true;
    }

    /**
     * Returns true if this user is ok to send to the list
     *
     * @param members
     * @param mail
     * @return true if this message is ok, false otherwise
     * @throws MessagingException
     */
    protected boolean checkMembers(Collection members, Mail mail) throws MessagingException {
        if (membersOnly && !members.contains(mail.getSender())) {
            Properties standardProperties = getCommandListservManager().getStandardProperties();
            getCommandListservManager().onError(mail,
                    xmlResources.getString("invalid.mail.subject", standardProperties),
                    xmlResources.getString("error.membersonly", standardProperties));

            return false;
        }
        return true;
    }

    public Collection getMembers() throws ParseException {
        Collection reply = new Vector();
        for (Iterator it = usersRepository.list(); it.hasNext();) {
            String member = it.next().toString();
            try {
                reply.add(new MailAddress(member));
            } catch (Exception e) {
                // Handle an invalid subscriber address by logging it and
                // proceeding to the next member.
                StringBuffer logBuffer =
                        new StringBuffer(1024)
                        .append("Invalid subscriber address: ")
                        .append(member)
                        .append(" caused: ")
                        .append(e.getMessage());
                log(logBuffer.toString());
            }
        }
        return reply;
    }

    /**
     * Get a configuration value
     * @param attrName
     * @param defValue
     * @return the value if found, defValue otherwise
     */
    protected boolean getBoolean(String attrName, boolean defValue) {
        boolean value = defValue;
        try {
            value = new Boolean(getInitParameter(attrName)).booleanValue();
        } catch (Exception e) {
            // Ignore any exceptions, default to false
        }
        return value;
    }

    /**
     * Get a configuration value
     * @param attrName
     * @param defValue
     * @return the attrValue if found, defValue otherwise
     */
    protected String getString(String attrName, String defValue) {
        String value = defValue;
        try {
            value = getInitParameter(attrName);
        } catch (Exception e) {
            // Ignore any exceptions, default to false
        }
        return value;
    }

    /**
     * initialize the resources
     * @throws Exception
     */
    protected void initializeResources() throws Exception {
        xmlResources = getCommandListservManager().initXMLResources(new String[]{"List Manager"})[0];
    }

    /**
     * Fetch the repository of users
     */
    protected void initUsersRepository() throws Exception {
        ComponentManager compMgr = (ComponentManager) getMailetContext().getAttribute(Constants.AVALON_COMPONENT_MANAGER);
        UsersStore usersStore = (UsersStore) compMgr.lookup("org.apache.james.services.UsersStore");
        String repName = getInitParameter("repositoryName");

        usersRepository = usersStore.getRepository(repName);
        if (usersRepository == null) throw new Exception("Invalid user repository: " + repName);
    }

    /**
     * <p>This takes the subject string and reduces (normailzes) it.
     * Multiple "Re:" entries are reduced to one, and capitalized.  The
     * prefix is always moved/placed at the beginning of the line, and
     * extra blanks are reduced, so that the output is always of the
     * form:</p>
     * <code>
     * &lt;prefix&gt; + &lt;one-optional-"Re:"*gt; + &lt;remaining subject&gt;
     * </code>
     * <p>I have done extensive testing of this routine with a standalone
     * driver, and am leaving the commented out debug messages so that
     * when someone decides to enhance this method, it can be yanked it
     * from this file, embedded it with a test driver, and the comments
     * enabled.</p>
     */
    static private String normalizeSubject(final String subj, final String prefix) {
        // JDK IMPLEMENTATION NOTE!  When we require JDK 1.4+, all
        // occurrences of subject.toString.().indexOf(...) can be
        // replaced by subject.indexOf(...).

        StringBuffer subject = new StringBuffer(subj);
        int prefixLength = prefix.length();

        // System.err.println("In:  " + subject);

        // If the "prefix" is not at the beginning the subject line, remove it
        int index = subject.toString().indexOf(prefix);
        if (index != 0) {
            // System.err.println("(p) index: " + index + ", subject: " + subject);
            if (index > 0) {
                subject.delete(index, index + prefixLength);
            }
            subject.insert(0, prefix); // insert prefix at the front
        }

        // Replace Re: with RE:
        String match = "Re:";
        index = subject.toString().indexOf(match, prefixLength);

        while(index > -1) {
            // System.err.println("(a) index: " + index + ", subject: " + subject);
            subject.replace(index, index + match.length(), "RE:");
            index = subject.toString().indexOf(match, prefixLength);
            // System.err.println("(b) index: " + index + ", subject: " + subject);
        }

        // Reduce them to one at the beginning
        match ="RE:";
        int indexRE = subject.toString().indexOf(match, prefixLength) + match.length();
        index = subject.toString().indexOf(match, indexRE);
        while(index > 0) {
            // System.err.println("(c) index: " + index + ", subject: " + subject);
            subject.delete(index, index + match.length());
            index = subject.toString().indexOf(match, indexRE);
            // System.err.println("(d) index: " + index + ", subject: " + subject);
        }

        // Reduce blanks
        match = "  ";
        index = subject.toString().indexOf(match, prefixLength);
        while(index > -1) {
            // System.err.println("(e) index: " + index + ", subject: " + subject);
            subject.replace(index, index + match.length(), " ");
            index = subject.toString().indexOf(match, prefixLength);
            // System.err.println("(f) index: " + index + ", subject: " + subject);
        }


        // System.err.println("Out: " + subject);

        return subject.toString();
    }

    /**
     * lazy retrieval
     * @return ICommandListservManager
     */
    protected ICommandListservManager getCommandListservManager() {
        if (commandListservManager == null) {
            commandListservManager = (ICommandListservManager) getMailetContext().getAttribute(ICommandListservManager.ID + listName);
            if (commandListservManager == null) {
                throw new IllegalStateException("Unable to find command list manager named: " + listName);
            }
        }

        return commandListservManager;
    }

    /**
     * Lazy init
     * @throws MessagingException
     */
    protected CommandListservFooter getCommandListservFooter() throws MessagingException {
        if (commandListservFooter == null) {
            commandListservFooter = new CommandListservFooter(getCommandListservManager());
            commandListservFooter.init(getMailetConfig());
        }
        return commandListservFooter;
    }
}
