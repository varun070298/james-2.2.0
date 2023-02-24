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

package org.apache.james.mailrepository;

import org.apache.avalon.cornerstone.services.store.ObjectRepository;
import org.apache.avalon.cornerstone.services.store.Store;
import org.apache.avalon.cornerstone.services.store.StreamRepository;
import org.apache.avalon.framework.activity.Initializable;
import org.apache.avalon.framework.component.Component;
import org.apache.avalon.framework.component.ComponentException;
import org.apache.avalon.framework.component.ComponentManager;
import org.apache.avalon.framework.component.Composable;
import org.apache.avalon.framework.configuration.Configurable;
import org.apache.avalon.framework.configuration.Configuration;
import org.apache.avalon.framework.configuration.ConfigurationException;
import org.apache.avalon.framework.configuration.DefaultConfiguration;
import org.apache.avalon.framework.logger.AbstractLogEnabled;
import org.apache.james.core.MailImpl;
import org.apache.james.core.MimeMessageWrapper;
import org.apache.james.services.MailRepository;
import org.apache.james.util.Lock;

import java.io.OutputStream;
import java.util.*;
import javax.mail.MessagingException;

/**
 * Implementation of a MailRepository on a FileSystem.
 *
 * Requires a configuration element in the .conf.xml file of the form:
 *  &lt;repository destinationURL="file://path-to-root-dir-for-repository"
 *              type="MAIL"
 *              model="SYNCHRONOUS"/&gt;
 * Requires a logger called MailRepository.
 *
 * @version 1.0.0, 24/04/1999
 */
public class AvalonMailRepository
    extends AbstractLogEnabled
    implements MailRepository, Component, Configurable, Composable, Initializable {

    /**
     * Whether 'deep debugging' is turned on.
     */
    protected final static boolean DEEP_DEBUG = false;

    private static final String TYPE = "MAIL";

    private Lock lock;
    private Store store;
    private StreamRepository sr;
    private ObjectRepository or;
    private String destination;
    private Set keys;
    private boolean fifo;

    /**
     * @see org.apache.avalon.framework.component.Composable#compose(ComponentManager)
     */
    public void compose( final ComponentManager componentManager )
            throws ComponentException {
        store = (Store)componentManager.
        lookup( "org.apache.avalon.cornerstone.services.store.Store" );
    }

    /**
     * @see org.apache.avalon.framework.configuration.Configurable#configure(Configuration)
     */
    public void configure(Configuration conf) throws ConfigurationException {
        destination = conf.getAttribute("destinationURL");
        if (getLogger().isDebugEnabled()) {
            getLogger().debug("AvalonMailRepository.destinationURL: " + destination);
        }
        String checkType = conf.getAttribute("type");
        if (! (checkType.equals("MAIL") || checkType.equals("SPOOL")) ) {
            String exceptionString = "Attempt to configure AvalonMailRepository as " +
                                     checkType;
            if (getLogger().isWarnEnabled()) {
                getLogger().warn(exceptionString);
            }
            throw new ConfigurationException(exceptionString);
        }
        fifo = conf.getAttributeAsBoolean("FIFO", false);
        // ignore model
    }

    /**
     * @see org.apache.avalon.framework.activity.Initializable#initialize()
     */
    public void initialize()
            throws Exception {
        try {
            //prepare Configurations for object and stream repositories
            DefaultConfiguration objectConfiguration
                = new DefaultConfiguration( "repository",
                                            "generated:AvalonFileRepository.compose()" );

            objectConfiguration.setAttribute("destinationURL", destination);
            objectConfiguration.setAttribute("type", "OBJECT");
            objectConfiguration.setAttribute("model", "SYNCHRONOUS");

            DefaultConfiguration streamConfiguration
                = new DefaultConfiguration( "repository",
                                            "generated:AvalonFileRepository.compose()" );

            streamConfiguration.setAttribute( "destinationURL", destination );
            streamConfiguration.setAttribute( "type", "STREAM" );
            streamConfiguration.setAttribute( "model", "SYNCHRONOUS" );

            sr = (StreamRepository) store.select(streamConfiguration);
            or = (ObjectRepository) store.select(objectConfiguration);
            lock = new Lock();
            keys = Collections.synchronizedSet(new HashSet());


            //Finds non-matching pairs and deletes the extra files
            HashSet streamKeys = new HashSet();
            for (Iterator i = sr.list(); i.hasNext(); ) {
                streamKeys.add(i.next());
            }
            HashSet objectKeys = new HashSet();
            for (Iterator i = or.list(); i.hasNext(); ) {
                objectKeys.add(i.next());
            }

            Collection strandedStreams = (Collection)streamKeys.clone();
            strandedStreams.removeAll(objectKeys);
            for (Iterator i = strandedStreams.iterator(); i.hasNext(); ) {
                String key = (String)i.next();
                remove(key);
            }

            Collection strandedObjects = (Collection)objectKeys.clone();
            strandedObjects.removeAll(streamKeys);
            for (Iterator i = strandedObjects.iterator(); i.hasNext(); ) {
                String key = (String)i.next();
                remove(key);
            }

            //Next get a list from the object repository
            //  and use that for the list of keys
            keys.clear();
            for (Iterator i = or.list(); i.hasNext(); ) {
                keys.add(i.next());
            }
            if (getLogger().isDebugEnabled()) {
                StringBuffer logBuffer =
                    new StringBuffer(128)
                            .append(this.getClass().getName())
                            .append(" created in ")
                            .append(destination);
                getLogger().debug(logBuffer.toString());
            }
        } catch (Exception e) {
            final String message = "Failed to retrieve Store component:" + e.getMessage();
            getLogger().error( message, e );
            throw e;
        }
    }

    /**
     * Releases a lock on a message identified by a key
     *
     * @param key the key of the message to be unlocked
     *
     * @return true if successfully released the lock, false otherwise
     */
    public boolean unlock(String key) {
        if (lock.unlock(key)) {
            if ((DEEP_DEBUG) && (getLogger().isDebugEnabled())) {
                StringBuffer debugBuffer =
                    new StringBuffer(256)
                            .append("Unlocked ")
                            .append(key)
                            .append(" for ")
                            .append(Thread.currentThread().getName())
                            .append(" @ ")
                            .append(new java.util.Date(System.currentTimeMillis()));
                getLogger().debug(debugBuffer.toString());
            }
//            synchronized (this) {
//                notifyAll();
//            }
            return true;
        } else {
            return false;
        }
    }

    /**
     * Obtains a lock on a message identified by a key
     *
     * @param key the key of the message to be locked
     *
     * @return true if successfully obtained the lock, false otherwise
     */
    public boolean lock(String key) {
        if (lock.lock(key)) {
            if ((DEEP_DEBUG) && (getLogger().isDebugEnabled())) {
                StringBuffer debugBuffer =
                    new StringBuffer(256)
                            .append("Locked ")
                            .append(key)
                            .append(" for ")
                            .append(Thread.currentThread().getName())
                            .append(" @ ")
                            .append(new java.util.Date(System.currentTimeMillis()));
                getLogger().debug(debugBuffer.toString());
            }
//            synchronized (this) {
//                notifyAll();
//            }
            return true;
        } else {
            return false;
        }
    }

    /**
     * Stores a message in this repository. Shouldn't this return the key
     * under which it is stored?
     *
     * @param mc the mail message to store
     */
    public void store(MailImpl mc) throws MessagingException {
        try {
            String key = mc.getName();
            //Remember whether this key was locked
            boolean wasLocked = lock.isLocked(key);

            if (!wasLocked) {
                //If it wasn't locked, we want a lock during the store
                lock.lock(key);
            }
            try {
                if (!keys.contains(key)) {
                    keys.add(key);
                }
                boolean saveStream = true;

                if (mc.getMessage() instanceof MimeMessageWrapper) {
                    MimeMessageWrapper wrapper = (MimeMessageWrapper) mc.getMessage();
                    if (DEEP_DEBUG) {
                        System.out.println("Retrieving from: " + wrapper.getSourceId());
                        StringBuffer debugBuffer =
                            new StringBuffer(64)
                                    .append("Saving to:       ")
                                    .append(destination)
                                    .append("/")
                                    .append(mc.getName());
                        System.out.println(debugBuffer.toString());
                        System.out.println("Modified: " + wrapper.isModified());
                    }
                    StringBuffer destinationBuffer =
                        new StringBuffer(128)
                            .append(destination)
                            .append("/")
                            .append(mc.getName());
                    if (wrapper.getSourceId().equals(destinationBuffer.toString()) && !wrapper.isModified()) {
                        //We're trying to save to the same place, and it's not modified... we shouldn't save.
                        //More importantly, if we try to save, we will create a 0-byte file since we're
                        //retrying to retrieve from a file we'll be overwriting.
                        saveStream = false;
                    }
                }
                if (saveStream) {
                    OutputStream out = null;
                    try {
                        out = sr.put(key);
                        mc.writeMessageTo(out);
                    } finally {
                        if (out != null) out.close();
                    }
                }
                //Always save the header information
                or.put(key, mc);
            } finally {
                if (!wasLocked) {
                    //If it wasn't locked, we need to now unlock
                    lock.unlock(key);
                }
            }

            if ((DEEP_DEBUG) && (getLogger().isDebugEnabled())) {
                StringBuffer logBuffer =
                    new StringBuffer(64)
                            .append("Mail ")
                            .append(key)
                            .append(" stored.");
                getLogger().debug(logBuffer.toString());
            }

            synchronized (this) {
//                notifyAll();
                notify();
            }
        } catch (Exception e) {
            getLogger().error("Exception storing mail: " + e);
            throw new MessagingException("Exception caught while storing Message Container: " + e);
        }
    }

    /**
     * Retrieves a message given a key. At the moment, keys can be obtained
     * from list() in superinterface Store.Repository
     *
     * @param key the key of the message to retrieve
     * @return the mail corresponding to this key, null if none exists
     */
    public MailImpl retrieve(String key) throws MessagingException {
        if ((DEEP_DEBUG) && (getLogger().isDebugEnabled())) {
            getLogger().debug("Retrieving mail: " + key);
        }
        try {
            MailImpl mc = null;
            try {
                mc = (MailImpl) or.get(key);
            } catch (RuntimeException re) {
                StringBuffer exceptionBuffer =
                    new StringBuffer(128)
                            .append("Exception retrieving mail: ")
                            .append(re.toString())
                            .append(", so we're deleting it... good riddance!");
                getLogger().debug(exceptionBuffer.toString());
                remove(key);
                return null;
            }
            MimeMessageAvalonSource source = new MimeMessageAvalonSource(sr, destination, key);
            mc.setMessage(new MimeMessageWrapper(source));

            return mc;
        } catch (Exception me) {
            getLogger().error("Exception retrieving mail: " + me);
            throw new MessagingException("Exception while retrieving mail: " + me.getMessage());
        }
    }

    /**
     * Removes a specified message
     *
     * @param mail the message to be removed from the repository
     */
    public void remove(MailImpl mail) throws MessagingException {
        remove(mail.getName());
    }


    /**
     * Removes a Collection of mails from the repository
     * @param mails The Collection of <code>MailImpl</code>'s to delete
     * @throws MessagingException
     * @since 2.2.0
     */
    public void remove(Collection mails) throws MessagingException {
        Iterator delList = mails.iterator();
        while (delList.hasNext()) {
            remove((MailImpl)delList.next());
        }
    }

    /**
     * Removes a message identified by key.
     *
     * @param key the key of the message to be removed from the repository
     */
    public void remove(String key) throws MessagingException {
        if (lock(key)) {
            try {
                keys.remove(key);
                sr.remove(key);
                or.remove(key);
            } finally {
                unlock(key);
            }
        } else {
            StringBuffer exceptionBuffer =
                new StringBuffer(64)
                        .append("Cannot lock ")
                        .append(key)
                        .append(" to remove it");
            throw new MessagingException(exceptionBuffer.toString());
        }
    }

    /**
     * List string keys of messages in repository.
     *
     * @return an <code>Iterator</code> over the list of keys in the repository
     *
     */
    public Iterator list() {
        // Fix ConcurrentModificationException by cloning 
        // the keyset before getting an iterator
        final ArrayList clone;
        synchronized(keys) {
            clone = new ArrayList(keys);
        }
        if (fifo) Collections.sort(clone); // Keys is a HashSet; impose FIFO for apps that need it
        return clone.iterator();
    }
}
