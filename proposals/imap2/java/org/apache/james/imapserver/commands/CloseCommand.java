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

package org.apache.james.imapserver.commands;

import org.apache.james.imapserver.ImapRequestLineReader;
import org.apache.james.imapserver.ImapResponse;
import org.apache.james.imapserver.ImapSession;
import org.apache.james.imapserver.ProtocolException;
import org.apache.james.imapserver.ImapHost;
import org.apache.james.imapserver.store.ImapMailbox;
import org.apache.james.imapserver.store.MailboxException;

/**
 * Handles processeing for the CHECK imap command.
 *
 *
 * @version $Revision: 1.1.2.3 $
 */
class CloseCommand extends SelectedStateCommand
{
    public static final String NAME = "CLOSE";
    public static final String ARGS = null;

    /** @see CommandTemplate#doProcess */
    protected void doProcess( ImapRequestLineReader request,
                              ImapResponse response,
                              ImapSession session )
            throws ProtocolException, MailboxException
    {
        parser.endLine( request );

        if ( ! session.selectedIsReadOnly() ) {
            ImapMailbox mailbox = session.getSelected();
            ImapHost host = session.getHost();
            host.expunge( mailbox );
        }
        session.deselect();

        session.unsolicitedResponses( response );
        response.commandComplete( this );
    }


    /** @see ImapCommand#getName */
    public String getName()
    {
        return NAME;
    }

    /** @see CommandTemplate#getArgSyntax */
    public String getArgSyntax()
    {
        return ARGS;
    }
}
/*
6.4.2.  CLOSE Command

   Arguments:  none

   Responses:  no specific responses for this command

   Result:     OK - close completed, now in authenticated state
               NO - close failure: no mailbox selected
               BAD - command unknown or arguments invalid

      The CLOSE command permanently removes from the currently selected
      mailbox all messages that have the \Deleted flag set, and returns
      to authenticated state from selected state.  No untagged EXPUNGE
      responses are sent.

      No messages are removed, and no error is given, if the mailbox is
      selected by an EXAMINE command or is otherwise selected read-only.

      Even if a mailbox is selected, a SELECT, EXAMINE, or LOGOUT
      command MAY be issued without previously issuing a CLOSE command.
      The SELECT, EXAMINE, and LOGOUT commands implicitly close the
      currently selected mailbox without doing an expunge.  However,
      when many messages are deleted, a CLOSE-LOGOUT or CLOSE-SELECT

      sequence is considerably faster than an EXPUNGE-LOGOUT or
      EXPUNGE-SELECT because no untagged EXPUNGE responses (which the
      client would probably ignore) are sent.

   Example:    C: A341 CLOSE
               S: A341 OK CLOSE completed
*/
