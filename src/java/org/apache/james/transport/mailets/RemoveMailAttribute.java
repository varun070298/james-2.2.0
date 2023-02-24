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

import org.apache.mailet.GenericMailet;
import org.apache.mailet.Mail;
import org.apache.mailet.MailetException;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.StringTokenizer;
import javax.mail.MessagingException;

/**
 * This mailet sets attributes on the Mail.
 * 
 * Sample configuration:
 * &lt;mailet match="All" class="RemoveMailAttribute"&gt;
 *   &lt;name&gt;attribute_name1&lt;/name&gt;
 *   &lt;name&gt;attribute_name2&lt;/name&gt;
 * &lt;/mailet&gt;
 *
 * @version CVS $Revision: 1.1.2.2 $ $Date: 2004/03/15 03:54:19 $
 * @since 2.2.0
 */
public class RemoveMailAttribute extends GenericMailet {
    
    private ArrayList attributesToRemove = new ArrayList();
    
    /**
     * Return a string describing this mailet.
     *
     * @return a string describing this mailet
     */
    public String getMailetInfo() {
        return "Remove Mail Attribute Mailet";
    }

    /**
     * Initialize the mailet
     *
     * @throws MailetException if the processor parameter is missing
     */
    public void init() throws MailetException
    {
        String name = getInitParameter("name");

        if (name != null) {
            StringTokenizer st = new StringTokenizer(name, ",") ;
            while (st.hasMoreTokens()) {
                String attribute_name = st.nextToken().trim() ;
                attributesToRemove.add(attribute_name);
            }
        }
    }

    /**
     * Remove the configured attributes
     *
     * @param mail the mail to process
     *
     * @throws MessagingException in all cases
     */
    public void service(Mail mail) throws MessagingException {
        Iterator iter = attributesToRemove.iterator();
        while (iter.hasNext()) {
            String attribute_name = iter.next().toString();
            mail.removeAttribute (attribute_name);
        }
    }
    

}
