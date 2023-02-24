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

package org.apache.james.transport;

import org.apache.avalon.framework.activity.Initializable;
import org.apache.avalon.framework.activity.Disposable;
import org.apache.avalon.framework.logger.AbstractLogEnabled;
import org.apache.avalon.framework.logger.Logger;
import org.apache.james.core.MailImpl;
import org.apache.james.core.MailetConfigImpl;
import org.apache.james.services.SpoolRepository;
import org.apache.mailet.*;

import javax.mail.MessagingException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.Iterator;
import java.util.Locale;

/**
 * Implements a processor for mails, directing the mail down
 * the chain of matchers/mailets.
 *
 *  SAMPLE CONFIGURATION
 *  &lt;processor name="try" onerror="return,log"&gt;
 *      &lt;mailet match="RecipientIsLocal" class="LocalDelivery"&gt;
 *      &lt;/mailet&gt;
 *      &lt;mailet match="All" class="RemoteDelivery"&gt;
 *          &lt;delayTime&gt;21600000&lt;/delayTime&gt;
 *          &lt;maxRetries&gt;5&lt;/maxRetries&gt;
 *      &lt;/mailet&gt;
 *  &lt;/processor&gt;
 *
 * Note that the 'onerror' attribute is not yet supported.
 *
 * As of James v2.2.0a5, 'onerror' functionality is implemented, but
 * it is implemented on the &lt;mailet&gt; tag.  The specification is:
 *
 *   &lt;mailet match="..." class="..."
 *       [onMatchException="{noMatch|matchAll|error|&lt;aProcessorName&gt;}"] 
 *       [onMailetException="{ignore|error|&lt;aProcessorName&gt;}"]&gt;
 *
 * noMatch:   no addresses are considered to match
 * matchAll:  all addresses are considered to match
 * error:     as before, send the message to the ERROR processor
 *
 * Otherwise, a processor name can be specified, and the message will
 * be sent there.
 *
 * <P>CVS $Id: LinearProcessor.java,v 1.10.4.7 2004/04/14 06:41:56 noel Exp $</P>
 * @version 2.2.0
 */
public class LinearProcessor 
    extends AbstractLogEnabled
    implements Initializable, Disposable {

    private static final Random random = new Random();  // Used to generate new mail names

    /**
     *  The name of the matcher used to terminate the matcher chain.  The
     *  end of the matcher/mailet chain must be a matcher that matches
     *  all mails and a mailet that sets every mail to GHOST status.
     *  This is necessary to ensure that mails are removed from the spool
     *  in an orderly fashion.
     */
    private static final String TERMINATING_MATCHER_NAME = "Terminating%Matcher%Name";

    /**
     *  The name of the mailet used to terminate the mailet chain.  The
     *  end of the matcher/mailet chain must be a matcher that matches
     *  all mails and a mailet that sets every mail to GHOST status.
     *  This is necessary to ensure that mails are removed from the spool
     *  in an orderly fashion.
     */
    private static final String TERMINATING_MAILET_NAME = "Terminating%Mailet%Name";

    private List mailets;  // The list of mailets for this processor
    private List matchers; // The list of matchers for this processor
    private volatile boolean listsClosed;  // Whether the matcher/mailet lists have been closed.
    private SpoolRepository spool;  // The spool on which this processor is acting

    /**
     * Set the spool to be used by this LinearProcessor.
     *
     * @param spool the spool to be used by this processor
     *
     * @throws IllegalArgumentException when the spool passed in is null
     */
    public void setSpool(SpoolRepository spool) {
        if (spool == null) {
            throw new IllegalArgumentException("The spool cannot be null");
        }
        this.spool = spool;
    }

    /**
     * @see org.apache.avalon.framework.activity.Initializable#initialize()
     */
    public void initialize() {
        matchers = new ArrayList();
        mailets = new ArrayList();
    }

    /**
     * <p>The dispose operation is called at the end of a components lifecycle.
     * Instances of this class use this method to release and destroy any
     * resources that they own.</p>
     *
     * <p>This implementation disposes of all the mailet instances added to the
     * processor</p>
     *
     * @throws Exception if an error is encountered during shutdown
     */
    public void dispose() {
        Iterator it = mailets.iterator();
        boolean debugEnabled = getLogger().isDebugEnabled();
        while (it.hasNext()) {
            Mailet mailet = (Mailet)it.next();
            if (debugEnabled) {
                getLogger().debug("Shutdown mailet " + mailet.getMailetInfo());
            }
            mailet.destroy();
        }
    }

    /**
     * <p>Adds a new <code>Matcher</code> / <code>Mailet</code> pair
     * to the processor.  Checks to ensure that the matcher and
     * mailet passed in are not null.  Synchronized to ensure that
     * the matchers and mailets are kept in sync.</p>
     *
     * <p>It is an essential part of the contract of the LinearProcessor
     * that a particular matcher/mailet combination be used to
     * terminate the processor chain.  This is done by calling the  
     * closeProcessorList method.</p>
     *
     * <p>Once the closeProcessorList has been called any subsequent
     * call to the add method will result in an IllegalStateException.</p>
     *
     * <p>This method is synchronized to protect against corruption of
     * matcher/mailets lists</p>
     *
     * @param matcher the new matcher being added
     * @param mailet the new mailet being added
     *
     * @throws IllegalArgumentException when the matcher or mailet passed in is null
     * @throws IllegalStateException when this method is called after the processor lists have been closed
     */
    public synchronized void add(Matcher matcher, Mailet mailet) {
        if (matcher == null) {
            throw new IllegalArgumentException("Null valued matcher passed to LinearProcessor.");
        }
        if (mailet == null) {
            throw new IllegalArgumentException("Null valued mailet passed to LinearProcessor.");
        }
        if (listsClosed) {
            throw new IllegalStateException("Attempt to add matcher/mailet after lists have been closed");
        }
        matchers.add(matcher);
        mailets.add(mailet);
    }

    /**
     * <p>Closes the processor matcher/mailet list.</p>
     *
     * <p>This method is synchronized to protect against corruption of
     * matcher/mailets lists</p>
     *
     * @throws IllegalStateException when this method is called after the processor lists have been closed
     */
    public synchronized void closeProcessorLists() {
        if (listsClosed) {
            throw new IllegalStateException("Processor's matcher/mailet lists have already been closed.");
        }
        Matcher terminatingMatcher =
            new GenericMatcher() {
                public Collection match(Mail mail) {
                    return mail.getRecipients();
                }
            
                public String getMatcherInfo() {
                    return TERMINATING_MATCHER_NAME;
                }
            };
        Mailet terminatingMailet = 
            new GenericMailet() {
                public void service(Mail mail) {
                    if (!(Mail.ERROR.equals(mail.getState()))) {
                        // Don't complain if we fall off the end of the
                        // error processor.  That is currently the
                        // normal situation for James, and the message
                        // will show up in the error store.
                        StringBuffer warnBuffer = new StringBuffer(256)
                                              .append("Message ")
                                              .append(((MailImpl)mail).getName())
                                              .append(" reached the end of this processor, and is automatically deleted.  This may indicate a configuration error.");
                        LinearProcessor.this.getLogger().warn(warnBuffer.toString());
                    }
                    mail.setState(Mail.GHOST);
                }
            
                public String getMailetInfo() {
                    return getMailetName();
                }
            
                public String getMailetName() {
                    return TERMINATING_MAILET_NAME;
                }
            };
        add(terminatingMatcher, terminatingMailet);
        listsClosed = true;
    }

    /**
     * <p>Processes a single mail message through the chain of matchers and mailets.</p>
     *
     * <p>Calls to this method before setSpool has been called with a non-null argument
     * will result in an <code>IllegalStateException</code>.</p>
     *
     * <p>If the matcher/mailet lists have not been closed by a call to the closeProcessorLists
     * method then a call to this method will result in an <code>IllegalStateException</code>.
     * The end of the matcher/mailet chain must be a matcher that matches all mails and 
     * a mailet that sets every mail to GHOST status.  This is necessary to ensure that 
     * mails are removed from the spool in an orderly fashion.  The closeProcessorLists method
     * ensures this.</p>
     * 
     * @param mail the new mail to be processed
     *
     * @throws IllegalStateException when this method is called before the processor lists have been closed
     *                                  or the spool has been initialized
     */
    public void service(MailImpl mail) throws MessagingException {
        if (spool == null) {
            throw new IllegalStateException("Attempt to service mail before the spool has been set to a non-null value");
        }

        if (!listsClosed) {
            throw new IllegalStateException("Attempt to service mail before matcher/mailet lists have been closed");
        }

        if (getLogger().isDebugEnabled()) {
            getLogger().debug("Servicing mail: " + mail.getName());
        }
        //  unprocessed is an array of Lists of Mail objects
        //  the array indicates which matcher/mailet (stage in the linear
        //  processor) that this Mail needs to be processed.
        //  e.g., a Mail in unprocessed[0] needs to be
        //  processed by the first matcher/mailet.
        //
        //  It is a List of Mail objects at each array spot as multiple Mail
        //  objects could be at the same stage.
        //
        //  Note that every Mail object in this array will either be the 
        //  original Mail object passed in, or a result of this method's
        //  (and hence this thread's) processing.

        List[] unprocessed = new List[matchers.size() + 1];

        for (int i = 0; i < unprocessed.length; i++) {
            // No need to use synchronization, as this is totally
            // local to the method
            unprocessed[i] = new ArrayList();
        }

        //Add the object to the bottom of the list
        unprocessed[0].add(mail);

        //This is the original state of the message
        String originalState = mail.getState();

        //We'll use these as temporary variables in the loop
        mail = null;  // the message we're currently processing
        int i = 0;    // where in the stage we're looking
        while (true) {
            //  The last element in the unprocessed array has mail messages
            //  that have completed all stages.  We want them to just die,
            //  so we clear that spot to allow garbage collection of the
            //  objects.
            //
            //  Please note that the presence of the terminating mailet at the end
            //  of the chain is critical to the proper operation
            //  of the LinearProcessor code.  If this mailet is not placed
            //  at the end of the chain with a terminating matcher, there is a 
            //  potential for configuration or implementation errors to 
            //  lead to mails trapped in the spool.  This matcher/mailet
            //  combination is added when the closeProcessorList method
            //  is called.
            unprocessed[unprocessed.length - 1].clear();

            //initialize the mail reference we will be searching on
            mail = null;

            //Scan through all stages, trying to find a message to process
            for (i = 0; i < unprocessed.length; i++) {
                if (unprocessed[i].size() > 0) {
                    //Get the first element from the queue, and remove it from there
                    mail = (MailImpl)unprocessed[i].remove(0);
                    break;
                }
            }

            //Check it we found anything
            if (mail == null) {
                //We found no messages to process... we're done servicing the mail object
                return;
            }


            //Call the matcher and find what recipients match
            Collection recipients = null;
            Matcher matcher = (Matcher) matchers.get(i);
            StringBuffer logMessageBuffer = null;
            if (getLogger().isDebugEnabled()) {
                logMessageBuffer =
                    new StringBuffer(128)
                            .append("Checking ")
                            .append(mail.getName())
                            .append(" with ")
                            .append(matcher);
                getLogger().debug(logMessageBuffer.toString());
            }
            try {
                recipients = matcher.match(mail);
                if (recipients == null) {
                    //In case the matcher returned null, create an empty Collection
                    recipients = new ArrayList(0);
                } else if (recipients != mail.getRecipients()) {
                    //Make sure all the objects are MailAddress objects
                    verifyMailAddresses(recipients);
                }
            } catch (MessagingException me) {
                // look in the matcher's mailet's init attributes
                MailetConfig mailetConfig = ((Mailet) mailets.get(i)).getMailetConfig();
                String onMatchException = ((MailetConfigImpl) mailetConfig).getInitAttribute("onMatchException");
                if (onMatchException == null) {
                    onMatchException = Mail.ERROR;
                } else {
                    onMatchException = onMatchException.trim().toLowerCase(Locale.US);
                }
                if (onMatchException.compareTo("nomatch") == 0) {
                    //In case the matcher returned null, create an empty Collection
                    recipients = new ArrayList(0);
                } else if (onMatchException.compareTo("matchall") == 0) {
                    recipients = mail.getRecipients();
                    // no need to verify addresses
                } else {
                    handleException(me, mail, matcher.getMatcherConfig().getMatcherName(), onMatchException);
                }
            }

            // Split the recipients into two pools.  notRecipients will contain the
            // recipients on the message that the matcher did not return.
            Collection notRecipients;
            if (recipients == mail.getRecipients() || recipients.size() == 0) {
                notRecipients = new ArrayList(0);
            } else {
                notRecipients = new ArrayList(mail.getRecipients());
                notRecipients.removeAll(recipients);
            }

            if (recipients.size() == 0) {
                //Everything was not a match... store it in the next spot in the array
                unprocessed[i + 1].add(mail);
                continue;
            }
            if (notRecipients.size() != 0) {
                // There are a mix of recipients and not recipients.
                // We need to clone this message, put the notRecipients on the clone
                // and store it in the next spot
                MailImpl notMail = (MailImpl)mail.duplicate(newName(mail));
                notMail.setRecipients(notRecipients);
                unprocessed[i + 1].add(notMail);
                //We have to set the reduce possible recipients on the old message
                mail.setRecipients(recipients);
            }
            // We have messages that need to process... time to run the mailet.
            Mailet mailet = (Mailet) mailets.get(i);
            if (getLogger().isDebugEnabled()) {
                logMessageBuffer =
                    new StringBuffer(128)
                            .append("Servicing ")
                            .append(mail.getName())
                            .append(" by ")
                            .append(mailet.getMailetInfo());
                getLogger().debug(logMessageBuffer.toString());
            }
            try {
                mailet.service(mail);
                // Make sure all the recipients are still MailAddress objects
                verifyMailAddresses(mail.getRecipients());
            } catch (MessagingException me) {
                MailetConfig mailetConfig = mailet.getMailetConfig();
                String onMailetException = ((MailetConfigImpl) mailetConfig).getInitAttribute("onMailetException");
                if (onMailetException == null) {
                    onMailetException = Mail.ERROR;
                } else {
                    onMailetException = onMailetException.trim().toLowerCase(Locale.US);
                }
                if (onMailetException.compareTo("ignore") == 0) {
                    // ignore the exception and continue
                    // this option should not be used if the mail object can be changed by the mailet
                    verifyMailAddresses(mail.getRecipients());
                } else {
                    handleException(me, mail, mailet.getMailetConfig().getMailetName(), onMailetException);
                }
            }

            // See if the state was changed by the mailet
            if (!mail.getState().equals(originalState)) {
                //If this message was ghosted, we just want to let it die
                if (mail.getState().equals(Mail.GHOST)) {
                    // let this instance die...
                    mail = null;
                    continue;
                }
                // This was just set to another state requiring further processing... 
                // Store this back in the spool and it will get picked up and 
                // run in that processor
                spool.store(mail);
                mail = null;
                continue;
            } else {
                // Ok, we made it through with the same state... move it to the next
                //  spot in the array
                unprocessed[i + 1].add(mail);
            }

        }
    }

    /**
     * Create a unique new primary key name.
     *
     * @param mail the mail to use as the basis for the new mail name
     * 
     * @return a new name
     */
    private String newName(MailImpl mail) {
        StringBuffer nameBuffer =
            new StringBuffer(64)
                    .append(mail.getName())
                    .append("-!")
                    .append(random.nextInt(1048576));
        return nameBuffer.toString();
    }



    /**
     * Checks that all objects in this class are of the form MailAddress.
     *
     * @throws MessagingException when the <code>Collection</code> contains objects that are not <code>MailAddress</code> objects
     */
    private void verifyMailAddresses(Collection col) throws MessagingException {
        try {
            MailAddress addresses[] = (MailAddress[])col.toArray(new MailAddress[0]);

            // Why is this here?  According to the javadoc for
            // java.util.Collection.toArray(Object[]), this should
            // never happen.  The exception will be thrown.
            if (addresses.length != col.size()) {
                throw new MailetException("The recipient list contains objects other than MailAddress objects");
            }
        } catch (ArrayStoreException ase) {
            throw new MailetException("The recipient list contains objects other than MailAddress objects");
        }
    }

    /**
     * This is a helper method that updates the state of the mail object to
     * Mail.ERROR as well as recording the exception to the log
     *
     * @param me the exception to be handled
     * @param mail the mail being processed when the exception was generated
     * @param offendersName the matcher or mailet than generated the exception
     * @param nextState the next state to set
     *
     * @throws MessagingException thrown always, rethrowing the passed in exception
     */
    private void handleException(MessagingException me, Mail mail, String offendersName, String nextState) throws MessagingException {
        System.err.println("exception! " + me);
        mail.setState(nextState);
        StringWriter sout = new StringWriter();
        PrintWriter out = new PrintWriter(sout, true);
        StringBuffer exceptionBuffer =
            new StringBuffer(128)
                    .append("Exception calling ")
                    .append(offendersName)
                    .append(": ")
                    .append(me.getMessage());
        out.println(exceptionBuffer.toString());
        Exception e = me;
        while (e != null) {
            e.printStackTrace(out);
            if (e instanceof MessagingException) {
                e = ((MessagingException)e).getNextException();
            } else {
                e = null;
            }
        }
        String errorString = sout.toString();
        mail.setErrorMessage(errorString);
        getLogger().error(errorString);
        throw me;
    }
}
