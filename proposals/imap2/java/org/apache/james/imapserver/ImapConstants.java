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

package org.apache.james.imapserver;

public interface ImapConstants
{
    // Basic response types
    String OK = "OK";
    String NO = "NO";
    String BAD = "BAD";
    String BYE = "BYE";
    String UNTAGGED = "*";

    String SP = " ";
    String VERSION = "IMAP4rev1";
    String CAPABILITIES = "LITERAL+";

    String USER_NAMESPACE = "#mail";

    char HIERARCHY_DELIMITER_CHAR = '.';
    char NAMESPACE_PREFIX_CHAR = '#';
    String HIERARCHY_DELIMITER = String.valueOf( HIERARCHY_DELIMITER_CHAR );
    String NAMESPACE_PREFIX = String.valueOf( NAMESPACE_PREFIX_CHAR );

    String INBOX_NAME = "INBOX";
}
