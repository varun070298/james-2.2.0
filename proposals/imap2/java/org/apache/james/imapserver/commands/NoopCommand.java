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

/**
 * Handles processeing for the NOOP imap command.
 *
 *
 * @version $Revision: 1.2.2.3 $
 */
class NoopCommand extends CommandTemplate
{
    public static final String NAME = "NOOP";
    public static final String ARGS = null;

    /** @see org.apache.james.imapserver.commands.CommandTemplate#doProcess */
    protected void doProcess( ImapRequestLineReader request,
                              ImapResponse response,
                              ImapSession session ) throws ProtocolException
    {
        parser.endLine( request );
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
6.1.2.  NOOP Command

   Arguments:  none

   Responses:  no specific responses for this command (but see below)

   Result:     OK - noop completed
               BAD - command unknown or arguments invalid

      The NOOP command always succeeds.  It does nothing.

      Since any command can return a status update as untagged data, the
      NOOP command can be used as a periodic poll for new messages or
      message status updates during a period of inactivity.  The NOOP
      command can also be used to reset any inactivity autologout timer
      on the server.

   Example:    C: a002 NOOP
               S: a002 OK NOOP completed
                  . . .
               C: a047 NOOP
               S: * 22 EXPUNGE
               S: * 23 EXISTS
               S: * 3 RECENT
               S: * 14 FETCH (FLAGS (\Seen \Deleted))
               S: a047 OK NOOP completed
*/
