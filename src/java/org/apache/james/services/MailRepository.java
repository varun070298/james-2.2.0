/***********************************************************************
 * Copyright (c) 1999-2004 The Apache Software Foundation.             *
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

package org.apache.james.services;

import org.apache.james.core.MailImpl;

import javax.mail.MessagingException;

import java.util.Collection;
import java.util.Iterator;

/**
 * Interface for a Repository to store Mails.
 * @version 1.0.0, 24/04/1999
 */
public interface MailRepository {

    /**
     * Define a MAIL repository. MAILS are stored in the specified
     * destination.
     */
    String MAIL = "MAIL";


    /**
     * Stores a message in this repository. Shouldn't this return the key
     * under which it is stored?
     *
     * @param mc the mail message to store
     */
    void store(MailImpl mc) throws MessagingException;

    /**
     * List string keys of messages in repository.
     *
     * @return an <code>Iterator</code> over the list of keys in the repository
     *
     */
    Iterator list() throws MessagingException;

    /**
     * Retrieves a message given a key. At the moment, keys can be obtained
     * from list() in superinterface Store.Repository
     *
     * @param key the key of the message to retrieve
     * @return the mail corresponding to this key, null if none exists
     */
    MailImpl retrieve(String key) throws MessagingException;

    /**
     * Removes a specified message
     *
     * @param mail the message to be removed from the repository
     */
    void remove(MailImpl mail) throws MessagingException;

    /**
     * Remove an Collection of mails from the repository
     *
     * @param mails The Collection of <code>MailImpl</code>'s to delete
     * @since 2.2.0
     */
     void remove(Collection mails) throws MessagingException;

    /**
     * Removes a message identified by key.
     *
     * @param key the key of the message to be removed from the repository
     */
    void remove(String key) throws MessagingException;

    /**
     * Obtains a lock on a message identified by key
     *
     * @param key the key of the message to be locked
     *
     * @return true if successfully obtained the lock, false otherwise
     */
    boolean lock(String key) throws MessagingException;

    /**
     * Releases a lock on a message identified the key
     *
     * @param key the key of the message to be unlocked
     *
     * @return true if successfully released the lock, false otherwise
     */
    boolean unlock(String key) throws MessagingException;
}
