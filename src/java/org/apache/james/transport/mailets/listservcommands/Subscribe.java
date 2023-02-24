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

package org.apache.james.transport.mailets.listservcommands;

import org.apache.avalon.framework.configuration.Configuration;
import org.apache.avalon.framework.configuration.ConfigurationException;
import org.apache.james.services.UsersRepository;
import org.apache.james.transport.mailets.ICommandListservManager;
import org.apache.james.util.XMLResources;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;

import javax.mail.MessagingException;
import java.util.Properties;

/**
 * Subscribe handles the subscribe command.
 * It is configured by:
 * <pre>&lt;command name="subscribe" class="Subscribe"/&gt;</pre>
 *
 * <br />
 * <br />
 *
 * It uses the formatted text-based resources for its return mail body:
 * <ul>
 *  <li>subscribe
 * </ul>
 *
 * <br />
 * <br />
 * After formatting the text, the message is delivered with {@link #sendStandardReply}
 *
 * Note, prior to formatting and sending any text, the user is checked to see if they
 * are already subscribed to this list.  If not, they will be sent a confirmation mail to
 * be processed by {@link SubscribeConfirm}
 *
 * @version CVS $Revision: 1.1.2.3 $ $Date: 2004/03/15 03:54:20 $
 * @since 2.2.0
 * @see SubscribeConfirm
 */
public class Subscribe extends BaseCommand {

    //For resources
    protected XMLResources xmlResources;



    public void init(ICommandListservManager commandListservManager, Configuration configuration) throws ConfigurationException {
        super.init(commandListservManager, configuration);
        xmlResources = initXMLResources(new String[]{"subscribe"})[0];
    }

    /**
     * After ensuring that the user isn't already subscribed, confirmation mail
     * will be sent to be processed by {@link SubscribeConfirm}.
     *
     * @param mail
     * @throws MessagingException
     */
    public void onCommand(Mail mail) throws MessagingException {
        if (checkSubscriptionStatus(mail)) {
            //send confirmation mail
            Properties props = getStandardProperties();
            props.put("SENDER_ADDR", mail.getSender().toString());

            String confirmationMail = xmlResources.getString("text", props);
            String subject = xmlResources.getString("confirm.subscribe.subject", props);
            String replyAddress = xmlResources.getString("confirm.subscribe.address", props);

            sendStandardReply(mail, subject, confirmationMail, replyAddress);
        }
    }

    /**
     * Checks to see if this user is already subscribed, if so return false and send a message
     * @param mail
     * @return false if the user is subscribed, true otherwise
     * @throws MessagingException
     */
    protected boolean checkSubscriptionStatus(Mail mail) throws MessagingException {
        MailAddress mailAddress = mail.getSender();
        UsersRepository usersRepository = getUsersRepository();
        if (usersRepository.contains(mailAddress.toString())) {
            getCommandListservManager().onError(mail,
                    "Invalid request",
                    xmlResources.getString("already.subscribed", getStandardProperties()));
            return false;
        }
        return true;
    }
}
