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
import org.apache.james.imapserver.store.ImapMailbox;
import org.apache.james.imapserver.store.MailboxException;

/**
 * Handles processeing for the STATUS imap command.
 *
 *
 * @version $Revision: 1.4.2.3 $
 */
class StatusCommand extends AuthenticatedStateCommand
{
    public static final String NAME = "STATUS";
    public static final String ARGS = "<mailbox> ( <status-data-item>+ )";

    private static final String MESSAGES = "MESSAGES";
    private static final String RECENT = "RECENT";
    private static final String UIDNEXT = "UIDNEXT";
    private static final String UIDVALIDITY = "UIDVALIDITY";
    private static final String UNSEEN = "UNSEEN";

    private StatusCommandParser parser = new StatusCommandParser();

    /** @see CommandTemplate#doProcess */
    protected void doProcess( ImapRequestLineReader request,
                              ImapResponse response,
                              ImapSession session )
            throws ProtocolException, MailboxException
    {
        String mailboxName = parser.mailbox( request );
        StatusDataItems statusDataItems = parser.statusDataItems( request );
        parser.endLine( request );

        ImapMailbox mailbox = getMailbox( mailboxName, session, true );

        StringBuffer buffer = new StringBuffer( mailboxName );
        buffer.append( SP );
        buffer.append( "(" );

        if ( statusDataItems.messages ) {
            buffer.append( MESSAGES );
            buffer.append( SP );
            buffer.append( mailbox.getMessageCount() );
            buffer.append( SP );
        }

        if ( statusDataItems.recent ) {
            buffer.append( RECENT );
            buffer.append( SP );
            buffer.append( mailbox.getRecentCount() );
            buffer.append( SP );
        }

        if ( statusDataItems.uidNext ) {
            buffer.append( UIDNEXT );
            buffer.append( SP );
            buffer.append( mailbox.getUidNext() );
            buffer.append( SP );
        }

        if ( statusDataItems.uidValidity ) {
            buffer.append( UIDVALIDITY );
            buffer.append( SP );
            buffer.append( mailbox.getUidValidity() );
            buffer.append( SP );
        }

        if ( statusDataItems.unseen ) {
            buffer.append( UNSEEN );
            buffer.append( SP );
            buffer.append( mailbox.getUnseenCount() );
            buffer.append( SP );
        }
        if ( buffer.charAt( buffer.length() - 1 ) == ' ' ) {
            buffer.setLength( buffer.length() - 1 );
        }
        buffer.append(')');
        response.commandResponse( this, buffer.toString());

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

    private class StatusCommandParser extends CommandParser
    {
        StatusDataItems statusDataItems( ImapRequestLineReader request )
                throws ProtocolException
        {
            StatusDataItems items = new StatusDataItems();

            request.nextWordChar();
            consumeChar( request, '(' );
            CharacterValidator validator = new NoopCharValidator();
            String nextWord = consumeWord( request, validator );
            while ( ! nextWord.endsWith(")" ) ) {
                addItem( nextWord, items );
                nextWord = consumeWord( request, validator );
            }
            // Got the closing ")", may be attached to a word.
            if ( nextWord.length() > 1 ) {
                addItem( nextWord.substring(0, nextWord.length() - 1 ), items );
            }

            return items;
        }

        private void addItem( String nextWord, StatusDataItems items )
                throws ProtocolException
        {
            if ( nextWord.equals( MESSAGES ) ) {
                items.messages = true;
            }
            else if ( nextWord.equals( RECENT ) ) {
                items.recent = true;
            }
            else if ( nextWord.equals( UIDNEXT ) ) {
                items.uidNext = true;
            }
            else if ( nextWord.equals( UIDVALIDITY ) ) {
                items.uidValidity = true;
            }
            else if ( nextWord.equals( UNSEEN ) ) {
                items.unseen = true;
            }
            else {
                throw new ProtocolException( "Unknown status item: '" + nextWord + "'" );
            }
        }
    }

    private class StatusDataItems
    {
        boolean messages;
        boolean recent;
        boolean uidNext;
        boolean uidValidity;
        boolean unseen;
    }
}
/*
6.3.10. STATUS Command

   Arguments:  mailbox name
               status data item names

   Responses:  untagged responses: STATUS

   Result:     OK - status completed
               NO - status failure: no status for that name
               BAD - command unknown or arguments invalid

      The STATUS command requests the status of the indicated mailbox.
      It does not change the currently selected mailbox, nor does it
      affect the state of any messages in the queried mailbox (in
      particular, STATUS MUST NOT cause messages to lose the \Recent
      flag).

      The STATUS command provides an alternative to opening a second
      IMAP4rev1 connection and doing an EXAMINE command on a mailbox to
      query that mailbox's status without deselecting the current
      mailbox in the first IMAP4rev1 connection.

      Unlike the LIST command, the STATUS command is not guaranteed to
      be fast in its response.  In some implementations, the server is
      obliged to open the mailbox read-only internally to obtain certain
      status information.  Also unlike the LIST command, the STATUS
      command does not accept wildcards.

      The currently defined status data items that can be requested are:

      MESSAGES       The number of messages in the mailbox.

      RECENT         The number of messages with the \Recent flag set.

      UIDNEXT        The next UID value that will be assigned to a new
                     message in the mailbox.  It is guaranteed that this
                     value will not change unless new messages are added
                     to the mailbox; and that it will change when new
                     messages are added even if those new messages are
                     subsequently expunged.



Crispin                     Standards Track                    [Page 33]

RFC 2060                       IMAP4rev1                   December 1996


      UIDVALIDITY    The unique identifier validity value of the
                     mailbox.

      UNSEEN         The number of messages which do not have the \Seen
                     flag set.


      Example:    C: A042 STATUS blurdybloop (UIDNEXT MESSAGES)
                  S: * STATUS blurdybloop (MESSAGES 231 UIDNEXT 44292)
                  S: A042 OK STATUS completed

*/
