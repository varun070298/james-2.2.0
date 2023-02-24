/***********************************************************************
 * Copyright (c) 2003-2004 The Apache Software Foundation.             *
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
package org.apache.james.util.mail.handlers;

import java.io.IOException;
import java.io.OutputStream;

import javax.activation.ActivationDataFlavor;
import javax.activation.DataSource;
import javax.mail.MessagingException;

import org.apache.james.util.mail.MimeMultipartReport;

/**
 * <p>Data Content Handler for...</p>
 * <dl>
 * <dt>MIME type name</dt><dd>multipart</dd>
 * <dt>MIME subtype name</dt><dd>report</dd>
 * </dl>
 */
public class multipart_report extends AbstractDataContentHandler
{
    /**
     * Default constructor.
     */
    public multipart_report()
    {
        super();
    }

    /**
     * @see org.apache.james.util.mail.handlers.AbstractDataContentHandler#computeDataFlavor()
     */
    protected ActivationDataFlavor computeDataFlavor()
    {
        return new ActivationDataFlavor(MimeMultipartReport.class,
                "multipart/report", "Multipart Report");
    }

    /**
     * @see javax.activation.DataContentHandler#writeTo(java.lang.Object,
     *      java.lang.String, java.io.OutputStream)
     */
    public void writeTo(Object aPart, String aMimeType, OutputStream aStream)
            throws IOException
    {
        if (!(aPart instanceof MimeMultipartReport))
            throw new IOException("Type \"" + aPart.getClass().getName()
                    + "\" is not supported.");
        try
        {
            ((MimeMultipartReport) aPart).writeTo(aStream);
        }
        catch (MessagingException e)
        {
            throw new IOException(e.getMessage());
        }
    }

    /**
     * @see org.apache.james.util.mail.handlers.AbstractDataContentHandler#computeContent(javax.activation.DataSource)
     */
    protected Object computeContent(DataSource aDataSource)
            throws MessagingException
    {
        return new MimeMultipartReport(aDataSource);
    }
}
