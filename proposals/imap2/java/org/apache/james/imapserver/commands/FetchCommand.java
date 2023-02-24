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

import org.apache.excalibur.util.StringUtil;
import org.apache.james.core.MimeMessageWrapper;
import org.apache.james.imapserver.ImapRequestLineReader;
import org.apache.james.imapserver.ImapResponse;
import org.apache.james.imapserver.ImapSession;
import org.apache.james.imapserver.ProtocolException;
import org.apache.james.imapserver.store.MailboxException;
import org.apache.james.imapserver.store.MessageFlags;
import org.apache.james.imapserver.store.SimpleImapMessage;
import org.apache.james.imapserver.store.ImapMailbox;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Collection;

/**
 * Handles processeing for the FETCH imap command.
 *
 *
 * @version $Revision: 1.1.2.3 $
 */
class FetchCommand extends SelectedStateCommand implements UidEnabledCommand
{
    public static final String NAME = "FETCH";
    public static final String ARGS = "<message-set> <fetch-profile>";

    private FetchCommandParser parser = new FetchCommandParser();

    /** @see CommandTemplate#doProcess */
    protected void doProcess( ImapRequestLineReader request,
                              ImapResponse response,
                              ImapSession session )
            throws ProtocolException, MailboxException
    {
        doProcess( request, response, session, false );
    }

    public void doProcess( ImapRequestLineReader request,
                              ImapResponse response,
                              ImapSession session,
                              boolean useUids )
            throws ProtocolException, MailboxException
    {
        IdSet idSet = parser.set( request );
        FetchRequest fetch = parser.fetchRequest( request );
        parser.endLine( request );

        ImapMailbox mailbox = session.getSelected();
        long[] uids = mailbox.getMessageUids();
        for ( int i = 0; i < uids.length; i++ ) {
            long uid = uids[i];
            int msn = mailbox.getMsn( uid );

            if ( ( useUids && idSet.includes( uid ) ) ||
                 ( !useUids && idSet.includes( msn ) ) )
            {
                SimpleImapMessage imapMessage = mailbox.getMessage( uid );
                String msgData = outputMessage( fetch, imapMessage );
                response.fetchResponse( msn, msgData );
            }
        }

        session.unsolicitedResponses( response );
        response.commandComplete( this );
    }

    private String outputMessage( FetchRequest fetch, SimpleImapMessage message )
            throws MailboxException, ProtocolException
    {

        StringBuffer response = new StringBuffer();

        List elements = fetch.getElements();
        for ( int i = 0; i < elements.size(); i++ ) {
            FetchElement fetchElement = ( FetchElement ) elements.get( i );
            if ( i > 0 ) {
                response.append( SP );
            }
            response.append( fetchElement.getResponseName() );
            response.append( SP );

            if ( fetchElement == FetchElement.FLAGS ) {
                MessageFlags flags = message.getFlags();
                response.append( flags.format() );
            }
            else if ( fetchElement == FetchElement.INTERNALDATE ) {
                // TODO format properly
                String internalDate = message.getAttributes().getInternalDateAsString();
                response.append( "\"" );
                response.append( internalDate );
                response.append ( "\"" );
            }
            else if ( fetchElement == FetchElement.RFC822_SIZE ) {
                response.append( message.getAttributes().getSize() );
            }
            else if ( fetchElement == FetchElement.ENVELOPE ) {
                response.append( message.getAttributes().getEnvelope() );
            }
            else if ( fetchElement == FetchElement.BODY ) {
                response.append( message.getAttributes().getBodyStructure( false ) );
            }
            else if ( fetchElement == FetchElement.BODYSTRUCTURE ) {
                response.append( message.getAttributes().getBodyStructure( true ) );
            }
            else if ( fetchElement == FetchElement.UID ) {
                response.append( message.getUid() );
            }
            // Various mechanisms for returning message body.
            else if ( fetchElement instanceof BodyFetchElement ) {
                BodyFetchElement bodyFetchElement = (BodyFetchElement)fetchElement;
                String sectionSpecifier = bodyFetchElement.getParameters();
                boolean peek = bodyFetchElement.isPeek();

                MimeMessage mimeMessage = message.getMimeMessage();
                try {
                    handleBodyFetch( mimeMessage, sectionSpecifier, response );
                }
                catch ( MessagingException e ) {
                    // TODO  chain exceptions
                    throw new MailboxException( e.getMessage() );
                }

                if ( ! peek ) {
                    message.getFlags().setSeen( true );
                    // TODO need to store this change.
                }
            }
            else {
                throw new ProtocolException( "Invalid fetch attribute" );
            }
        }

        return response.toString();
    }


    private void handleBodyFetch( MimeMessage mimeMessage,
                                  String sectionSpecifier,
                                  StringBuffer response )
            throws ProtocolException, MessagingException
    {
        if ( sectionSpecifier.length() == 0 ) {
            // TODO - need to use an InputStream from the response here.
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            try {
                mimeMessage.writeTo(bout);
            }
            catch ( IOException e ) {
                throw new ProtocolException( "Error reading message source" );
            }
            byte[] bytes = bout.toByteArray();
            addLiteral( bytes, response );
        }
        else if ( sectionSpecifier.equalsIgnoreCase( "HEADER" ) ) {
            Enumeration enum = mimeMessage.getAllHeaderLines();
            addHeaders( enum, response );
        }
        else if ( sectionSpecifier.startsWith( "HEADER.FIELDS.NOT " ) ) {
            String[] excludeNames = extractHeaderList( sectionSpecifier, "HEADER.FIELDS.NOT ".length() );
            Enumeration enum = mimeMessage.getNonMatchingHeaderLines( excludeNames );
            addHeaders( enum, response );
        }
        else if ( sectionSpecifier.startsWith( "HEADER.FIELDS " ) ) {
            String[] includeNames = extractHeaderList( sectionSpecifier, "HEADER.FIELDS ".length() );
            Enumeration enum = mimeMessage.getMatchingHeaderLines( includeNames );
            addHeaders( enum, response );
        }
        else if ( sectionSpecifier.equalsIgnoreCase( "MIME" ) ) {
            // TODO implement
            throw new ProtocolException( "MIME not yet implemented." );
        }
        else if ( sectionSpecifier.equalsIgnoreCase( "TEXT" ) ) {
            // TODO - need to use an InputStream from the response here.
            // TODO - this is a hack. To get just the body content, I'm using a null
            // input stream to take the headers. Need to have a way of ignoring headers.
            ByteArrayOutputStream headerOut = new ByteArrayOutputStream();
            ByteArrayOutputStream bodyOut = new ByteArrayOutputStream();
            try {
                MimeMessageWrapper.writeTo( mimeMessage, headerOut, bodyOut );
                byte[] bytes = bodyOut.toByteArray();

                addLiteral( bytes, response );

            }
            catch ( IOException e ) {
                throw new ProtocolException( "Error reading message source" );
            }
        }
        else {
            // Should be a part specifier followed by a section specifier.
            // See if there's a leading part specifier.
            // If so, get the number, get the part, and call this recursively.
            int dotPos = sectionSpecifier.indexOf( '.' );
            if ( dotPos == -1 ) {
                throw new ProtocolException( "Malformed fetch attribute: " + sectionSpecifier );
            }
            int partNumber = Integer.parseInt( sectionSpecifier.substring( 0, dotPos ) );
            String partSectionSpecifier = sectionSpecifier.substring( dotPos + 1 );

            // TODO - get the MimePart of the mimeMessage, and call this method
            // with the new partSectionSpecifier.
//        MimeMessage part;
//        handleBodyFetch( part, partSectionSpecifier, response );
            throw new ProtocolException( "Mime parts not yet implemented for fetch." );
        }

    }

    private void addLiteral( byte[] bytes, StringBuffer response )
    {
        response.append('{' );
        response.append( bytes.length + 1 );
        response.append( '}' );
        response.append( "\r\n" );

        for ( int i = 0; i < bytes.length; i++ ) {
            byte b = bytes[i];
            response.append((char)b);
        }
    }

    // TODO should do this at parse time.
    private String[] extractHeaderList( String headerList, int prefixLen )
    {
        // Remove the trailing and leading ')('
        String tmp = headerList.substring( prefixLen + 1, headerList.length() - 1 );
        String[] headerNames = StringUtil.split( tmp, " " );
        return headerNames;
    }

    private void addHeaders( Enumeration enum, StringBuffer response )
    {
        List lines = new ArrayList();
        int count = 0;
        while (enum.hasMoreElements()) {
            String line = (String)enum.nextElement();
            count += line.length() + 2;
            lines.add(line);
        }
        response.append( '{' );
        response.append( count + 2 );
        response.append( '}' );
        response.append("\r\n");

        Iterator lit = lines.iterator();
        while (lit.hasNext()) {
            String line = (String)lit.next();
            response.append( line );
            response.append( "\r\n" );
        }
        response.append("\r\n");
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

    private class FetchCommandParser extends CommandParser
    {


        public FetchRequest fetchRequest( ImapRequestLineReader request )
                throws ProtocolException
        {
            FetchRequest fetch = new FetchRequest();
            CharacterValidator validator = new NoopCharValidator();

            char next = nextNonSpaceChar( request );
            consumeChar( request, '(' );

            next = nextNonSpaceChar( request );
            while ( next != ')' ) {
                fetch.addElement( getNextElement( request ) );
                next = nextNonSpaceChar( request );
            }

            consumeChar(request, ')');

            return fetch;
        }

        private FetchElement getNextElement( ImapRequestLineReader request)
                throws ProtocolException
        {
            char next = nextCharInLine( request );
                StringBuffer element = new StringBuffer();
                while ( next != ' ' && next != '[' && next != ')' ) {
                    element.append(next);
                    request.consume();
                    next = nextCharInLine( request );
                }
                String name = element.toString();
                // Simple elements with no '[]' parameters.
                if ( next == ' ' || next == ')' ) {
                    if ( "FAST".equalsIgnoreCase( name ) ) {
                        return FetchElement.FAST;
                    }
                    if ( "FULL".equalsIgnoreCase( name ) ) {
                        return FetchElement.FULL;
                    }
                    if ( "ALL".equalsIgnoreCase( name ) ) {
                        return FetchElement.ALL;
                    }
                    if ( "FLAGS".equalsIgnoreCase( name ) ) {
                        return FetchElement.FLAGS;
                    }
                    if ( "RFC822.SIZE".equalsIgnoreCase( name ) ) {
                        return FetchElement.RFC822_SIZE;
                    }
                    if ( "ENVELOPE".equalsIgnoreCase( name ) ) {
                        return FetchElement.ENVELOPE;
                    }
                    if ( "INTERNALDATE".equalsIgnoreCase( name ) ) {
                        return FetchElement.INTERNALDATE;
                    }
                    if ( "BODY".equalsIgnoreCase( name ) ) {
                        return FetchElement.BODY;
                    }
                    if ( "BODYSTRUCTURE".equalsIgnoreCase( name ) ) {
                        return FetchElement.BODYSTRUCTURE;
                    }
                    if ( "UID".equalsIgnoreCase( name ) ) {
                        return FetchElement.UID;
                    }
                    if ( "RFC822".equalsIgnoreCase( name ) ) {
                        return new BodyFetchElement( "RFC822", "", false );
                    }
                    if ( "RFC822.HEADER".equalsIgnoreCase( name ) ) {
                        return new BodyFetchElement( "RFC822.HEADER", "HEADER", true );
                    }
                    if ( "RFC822.TEXT".equalsIgnoreCase( name ) ) {
                        return new BodyFetchElement( "RFC822.TEXT", "TEXT", false );
                    }
                    else {
                        throw new ProtocolException( "Invalid fetch attribute: " + name );
                    }
                }
                else {
                    consumeChar( request, '[' );

                    StringBuffer sectionIdentifier = new StringBuffer();
                    next = nextCharInLine( request );
                    while ( next != ']' ) {
                        sectionIdentifier.append( next );
                        request.consume();
                        next = nextCharInLine(request);
                    }
                    consumeChar( request, ']' );

                    String parameter = sectionIdentifier.toString();

                    if ( "BODY".equalsIgnoreCase( name ) ) {
                        return new BodyFetchElement( "BODY[" + parameter + "]", parameter, false );
                    }
                    if ( "BODY.PEEK".equalsIgnoreCase( name ) ) {
                        return new BodyFetchElement( "BODY[" + parameter + "]", parameter, true );
                    }
                    else {
                        throw new ProtocolException( "Invalid fetch attibute: " + name + "[]" );
                    }
                }
            }

        private char nextCharInLine( ImapRequestLineReader request )
                throws ProtocolException
        {
            char next = request.nextChar();
            if ( next == '\r' || next == '\n' ) {
                throw new ProtocolException( "Unexpected end of line." );
            }
            return next;
        }

        private char nextNonSpaceChar( ImapRequestLineReader request )
                throws ProtocolException
        {
            char next = request.nextChar();
            while ( next == ' ' ) {
                request.consume();
                next = request.nextChar();
            }
            return next;
        }

    }


    private static class FetchRequest
    {
        private ArrayList elements = new ArrayList();
        List getElements() {
            return Collections.unmodifiableList( elements );
        }
        void addElement( FetchElement element )
        {
            if ( element == FetchElement.ALL ) {
                elements.add( FetchElement.FLAGS );
                elements.add( FetchElement.INTERNALDATE );
                elements.add( FetchElement.RFC822_SIZE );
                elements.add( FetchElement.ENVELOPE );
            }
            else if ( element == FetchElement.FAST ) {
                elements.add( FetchElement.FLAGS );
                elements.add( FetchElement.INTERNALDATE );
                elements.add( FetchElement.RFC822_SIZE );
           }
            else if ( element == FetchElement.FULL ) {
                elements.add( FetchElement.FLAGS );
                elements.add( FetchElement.INTERNALDATE );
                elements.add( FetchElement.RFC822_SIZE );
                elements.add( FetchElement.ENVELOPE );
                elements.add( FetchElement.BODY );
            }
            else {
                elements.add( element );
            }
        }
    }

    private static class FetchElement
    {
        public static final FetchElement FLAGS = new FetchElement( "FLAGS" );
        public static final FetchElement INTERNALDATE = new FetchElement( "INTERNALDATE" );
        public static final FetchElement RFC822_SIZE= new FetchElement( "RFC822.SIZE" );
        public static final FetchElement ENVELOPE = new FetchElement( "ENVELOPE" );
        public static final FetchElement BODY = new FetchElement( "BODY" );
        public static final FetchElement BODYSTRUCTURE = new FetchElement( "BODYSTRUCTURE" );
        public static final FetchElement UID = new FetchElement( "UID" );

        public static final FetchElement FAST = new FetchElement( "FAST" );
        public static final FetchElement FULL = new FetchElement( "FULL" );
        public static final FetchElement ALL = new FetchElement( "ALL" );

        String name;

        protected FetchElement( String name )
        {
            this.name = name;
        }

        public String getResponseName() {
            return this.name;
        }
    }

    private class BodyFetchElement extends FetchElement
    {
        private boolean peek;
        private String sectionIdentifier;

        public BodyFetchElement( String name, String sectionIdentifier, boolean peek )
        {
            super( name );
            this.sectionIdentifier = sectionIdentifier;
            this.peek = peek;
        }

        public String getParameters()
        {
            return this.sectionIdentifier;
        }

        public boolean isPeek()
        {
            return this.peek;
        }
    }

}
/*
6.4.5.  FETCH Command

   Arguments:  message set
               message data item names

   Responses:  untagged responses: FETCH

   Result:     OK - fetch completed
               NO - fetch error: can't fetch that data
               BAD - command unknown or arguments invalid

      The FETCH command retrieves data associated with a message in the
      mailbox.  The data items to be fetched can be either a single atom
      or a parenthesized list.

      The currently defined data items that can be fetched are:

      ALL            Macro equivalent to: (FLAGS INTERNALDATE
                     RFC822.SIZE ENVELOPE)

      BODY           Non-extensible form of BODYSTRUCTURE.

      BODY[<section>]<<partial>>
                     The text of a particular body section.  The section
                     specification is a set of zero or more part
                     specifiers delimited by periods.  A part specifier
                     is either a part number or one of the following:
                     HEADER, HEADER.FIELDS, HEADER.FIELDS.NOT, MIME, and
                     TEXT.  An empty section specification refers to the
                     entire message, including the header.

                     Every message has at least one part number.
                     Non-[MIME-IMB] messages, and non-multipart
                     [MIME-IMB] messages with no encapsulated message,
                     only have a part 1.

                     Multipart messages are assigned consecutive part
                     numbers, as they occur in the message.  If a
                     particular part is of type message or multipart,
                     its parts MUST be indicated by a period followed by
                     the part number within that nested multipart part.

                     A part of type MESSAGE/RFC822 also has nested part
                     numbers, referring to parts of the MESSAGE part's
                     body.

                     The HEADER, HEADER.FIELDS, HEADER.FIELDS.NOT, and
                     TEXT part specifiers can be the sole part specifier
                     or can be prefixed by one or more numeric part
                     specifiers, provided that the numeric part
                     specifier refers to a part of type MESSAGE/RFC822.
                     The MIME part specifier MUST be prefixed by one or
                     more numeric part specifiers.

                     The HEADER, HEADER.FIELDS, and HEADER.FIELDS.NOT
                     part specifiers refer to the [RFC-822] header of
                     the message or of an encapsulated [MIME-IMT]
                     MESSAGE/RFC822 message.  HEADER.FIELDS and
                     HEADER.FIELDS.NOT are followed by a list of
                     field-name (as defined in [RFC-822]) names, and
                     return a subset of the header.  The subset returned
                     by HEADER.FIELDS contains only those header fields
                     with a field-name that matches one of the names in
                     the list; similarly, the subset returned by
                     HEADER.FIELDS.NOT contains only the header fields
                     with a non-matching field-name.  The field-matching
                     is case-insensitive but otherwise exact.  In all
                     cases, the delimiting blank line between the header
                     and the body is always included.

                     The MIME part specifier refers to the [MIME-IMB]
                     header for this part.

                     The TEXT part specifier refers to the text body of
                     the message, omitting the [RFC-822] header.


                       Here is an example of a complex message
                       with some of its part specifiers:

                        HEADER     ([RFC-822] header of the message)
                        TEXT       MULTIPART/MIXED
                        1          TEXT/PLAIN
                        2          APPLICATION/OCTET-STREAM
                        3          MESSAGE/RFC822
                        3.HEADER   ([RFC-822] header of the message)
                        3.TEXT     ([RFC-822] text body of the message)
                        3.1        TEXT/PLAIN
                        3.2        APPLICATION/OCTET-STREAM
                        4          MULTIPART/MIXED
                        4.1        IMAGE/GIF
                        4.1.MIME   ([MIME-IMB] header for the IMAGE/GIF)
                        4.2        MESSAGE/RFC822
                        4.2.HEADER ([RFC-822] header of the message)
                        4.2.TEXT   ([RFC-822] text body of the message)
                        4.2.1      TEXT/PLAIN
                        4.2.2      MULTIPART/ALTERNATIVE
                        4.2.2.1    TEXT/PLAIN
                        4.2.2.2    TEXT/RICHTEXT


                     It is possible to fetch a substring of the
                     designated text.  This is done by appending an open
                     angle bracket ("<"), the octet position of the
                     first desired octet, a period, the maximum number
                     of octets desired, and a close angle bracket (">")
                     to the part specifier.  If the starting octet is
                     beyond the end of the text, an empty string is
                     returned.

                     Any partial fetch that attempts to read beyond the
                     end of the text is truncated as appropriate.  A
                     partial fetch that starts at octet 0 is returned as
                     a partial fetch, even if this truncation happened.

                          Note: this means that BODY[]<0.2048> of a
                          1500-octet message will return BODY[]<0>
                          with a literal of size 1500, not BODY[].

                          Note: a substring fetch of a
                          HEADER.FIELDS or HEADER.FIELDS.NOT part
                          specifier is calculated after subsetting
                          the header.


                     The \Seen flag is implicitly set; if this causes
                     the flags to change they SHOULD be included as part
                     of the FETCH responses.

      BODY.PEEK[<section>]<<partial>>
                     An alternate form of BODY[<section>] that does not
                     implicitly set the \Seen flag.

      BODYSTRUCTURE  The [MIME-IMB] body structure of the message.  This
                     is computed by the server by parsing the [MIME-IMB]
                     header fields in the [RFC-822] header and
                     [MIME-IMB] headers.

      ENVELOPE       The envelope structure of the message.  This is
                     computed by the server by parsing the [RFC-822]
                     header into the component parts, defaulting various
                     fields as necessary.

      FAST           Macro equivalent to: (FLAGS INTERNALDATE
                     RFC822.SIZE)

      FLAGS          The flags that are set for this message.

      FULL           Macro equivalent to: (FLAGS INTERNALDATE
                     RFC822.SIZE ENVELOPE BODY)

      INTERNALDATE   The internal date of the message.

      RFC822         Functionally equivalent to BODY[], differing in the
                     syntax of the resulting untagged FETCH data (RFC822
                     is returned).

      RFC822.HEADER  Functionally equivalent to BODY.PEEK[HEADER],
                     differing in the syntax of the resulting untagged
                     FETCH data (RFC822.HEADER is returned).

      RFC822.SIZE    The [RFC-822] size of the message.

      RFC822.TEXT    Functionally equivalent to BODY[TEXT], differing in
                     the syntax of the resulting untagged FETCH data
                     (RFC822.TEXT is returned).

      UID            The unique identifier for the message.

   Example:    C: A654 FETCH 2:4 (FLAGS BODY[HEADER.FIELDS (DATE FROM)])
               S: * 2 FETCH ....
               S: * 3 FETCH ....
               S: * 4 FETCH ....
               S: A654 OK FETCH completed


7.4.2.  FETCH Response

   Contents:   message data

      The FETCH response returns data about a message to the client.
      The data are pairs of data item names and their values in
      parentheses.  This response occurs as the result of a FETCH or
      STORE command, as well as by unilateral server decision (e.g. flag
      updates).

      The current data items are:

      BODY           A form of BODYSTRUCTURE without extension data.

      BODY[<section>]<<origin_octet>>
                     A string expressing the body contents of the
                     specified section.  The string SHOULD be
                     interpreted by the client according to the content
                     transfer encoding, body type, and subtype.

                     If the origin octet is specified, this string is a
                     substring of the entire body contents, starting at
                     that origin octet.  This means that BODY[]<0> MAY
                     be truncated, but BODY[] is NEVER truncated.

                     8-bit textual data is permitted if a [CHARSET]
                     identifier is part of the body parameter
                     parenthesized list for this section.  Note that
                     headers (part specifiers HEADER or MIME, or the
                     header portion of a MESSAGE/RFC822 part), MUST be
                     7-bit; 8-bit characters are not permitted in
                     headers.  Note also that the blank line at the end
                     of the header is always included in header data.

                     Non-textual data such as binary data MUST be
                     transfer encoded into a textual form such as BASE64
                     prior to being sent to the client.  To derive the
                     original binary data, the client MUST decode the
                     transfer encoded string.

      BODYSTRUCTURE  A parenthesized list that describes the [MIME-IMB]
                     body structure of a message.  This is computed by
                     the server by parsing the [MIME-IMB] header fields,
                     defaulting various fields as necessary.

                     For example, a simple text message of 48 lines and
                     2279 octets can have a body structure of: ("TEXT"
                     "PLAIN" ("CHARSET" "US-ASCII") NIL NIL "7BIT" 2279
                     48)

                     Multiple parts are indicated by parenthesis
                     nesting.  Instead of a body type as the first
                     element of the parenthesized list there is a nested
                     body.  The second element of the parenthesized list
                     is the multipart subtype (mixed, digest, parallel,
                     alternative, etc.).

                     For example, a two part message consisting of a
                     text and a BASE645-encoded text attachment can have
                     a body structure of: (("TEXT" "PLAIN" ("CHARSET"
                     "US-ASCII") NIL NIL "7BIT" 1152 23)("TEXT" "PLAIN"
                     ("CHARSET" "US-ASCII" "NAME" "cc.diff")
                     "<960723163407.20117h@cac.washington.edu>"
                     "Compiler diff" "BASE64" 4554 73) "MIXED"))

                     Extension data follows the multipart subtype.
                     Extension data is never returned with the BODY
                     fetch, but can be returned with a BODYSTRUCTURE
                     fetch.  Extension data, if present, MUST be in the
                     defined order.

                     The extension data of a multipart body part are in
                     the following order:

                     body parameter parenthesized list
                        A parenthesized list of attribute/value pairs
                        [e.g. ("foo" "bar" "baz" "rag") where "bar" is
                        the value of "foo" and "rag" is the value of
                        "baz"] as defined in [MIME-IMB].

                     body disposition
                        A parenthesized list, consisting of a
                        disposition type string followed by a
                        parenthesized list of disposition
                        attribute/value pairs.  The disposition type and
                        attribute names will be defined in a future
                        standards-track revision to [DISPOSITION].

                     body language
                        A string or parenthesized list giving the body
                        language value as defined in [LANGUAGE-TAGS].

                     Any following extension data are not yet defined in
                     this version of the protocol.  Such extension data
                     can consist of zero or more NILs, strings, numbers,
                     or potentially nested parenthesized lists of such
                     data.  Client implementations that do a
                     BODYSTRUCTURE fetch MUST be prepared to accept such
                     extension data.  Server implementations MUST NOT
                     send such extension data until it has been defined
                     by a revision of this protocol.

                     The basic fields of a non-multipart body part are
                     in the following order:

                     body type
                        A string giving the content media type name as
                        defined in [MIME-IMB].

                     body subtype
                        A string giving the content subtype name as
                        defined in [MIME-IMB].

                     body parameter parenthesized list
                        A parenthesized list of attribute/value pairs
                        [e.g. ("foo" "bar" "baz" "rag") where "bar" is
                        the value of "foo" and "rag" is the value of
                        "baz"] as defined in [MIME-IMB].

                     body id
                        A string giving the content id as defined in
                        [MIME-IMB].

                     body description
                        A string giving the content description as
                        defined in [MIME-IMB].

                     body encoding
                        A string giving the content transfer encoding as
                        defined in [MIME-IMB].

                     body size
                        A number giving the size of the body in octets.
                        Note that this size is the size in its transfer
                        encoding and not the resulting size after any
                        decoding.

                     A body type of type MESSAGE and subtype RFC822
                     contains, immediately after the basic fields, the
                     envelope structure, body structure, and size in
                     text lines of the encapsulated message.

                     A body type of type TEXT contains, immediately
                     after the basic fields, the size of the body in
                     text lines.  Note that this size is the size in its
                     content transfer encoding and not the resulting
                     size after any decoding.

                     Extension data follows the basic fields and the
                     type-specific fields listed above.  Extension data
                     is never returned with the BODY fetch, but can be
                     returned with a BODYSTRUCTURE fetch.  Extension
                     data, if present, MUST be in the defined order.

                     The extension data of a non-multipart body part are
                     in the following order:

                     body MD5
                        A string giving the body MD5 value as defined in
                        [MD5].

                     body disposition
                        A parenthesized list with the same content and
                        function as the body disposition for a multipart
                        body part.

                     body language
                        A string or parenthesized list giving the body
                        language value as defined in [LANGUAGE-TAGS].

                     Any following extension data are not yet defined in
                     this version of the protocol, and would be as
                     described above under multipart extension data.

      ENVELOPE       A parenthesized list that describes the envelope
                     structure of a message.  This is computed by the
                     server by parsing the [RFC-822] header into the
                     component parts, defaulting various fields as
                     necessary.

                     The fields of the envelope structure are in the
                     following order: date, subject, from, sender,
                     reply-to, to, cc, bcc, in-reply-to, and message-id.
                     The date, subject, in-reply-to, and message-id
                     fields are strings.  The from, sender, reply-to,
                     to, cc, and bcc fields are parenthesized lists of
                     address structures.

                     An address structure is a parenthesized list that
                     describes an electronic mail address.  The fields
                     of an address structure are in the following order:
                     personal name, [SMTP] at-domain-list (source
                     route), mailbox name, and host name.

                     [RFC-822] group syntax is indicated by a special
                     form of address structure in which the host name
                     field is NIL.  If the mailbox name field is also
                     NIL, this is an end of group marker (semi-colon in
                     RFC 822 syntax).  If the mailbox name field is
                     non-NIL, this is a start of group marker, and the
                     mailbox name field holds the group name phrase.

                     Any field of an envelope or address structure that
                     is not applicable is presented as NIL.  Note that
                     the server MUST default the reply-to and sender
                     fields from the from field; a client is not
                     expected to know to do this.

      FLAGS          A parenthesized list of flags that are set for this
                     message.

      INTERNALDATE   A string representing the internal date of the
                     message.

      RFC822         Equivalent to BODY[].

      RFC822.HEADER  Equivalent to BODY.PEEK[HEADER].

      RFC822.SIZE    A number expressing the [RFC-822] size of the
                     message.

      RFC822.TEXT    Equivalent to BODY[TEXT].

      UID            A number expressing the unique identifier of the
                     message.


   Example:    S: * 23 FETCH (FLAGS (\Seen) RFC822.SIZE 44827)

*/
