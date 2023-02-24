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

package org.apache.james.fetchpop;

/**
 * An interface to expose James management functionality through JMX.
 * 
 * @phoenix:mx-topic name="FetchScheduler,type=POP"
 */
public interface FetchSchedulerMBean {
    /**
    * @phoenix:mx-attribute
    * @phoenix:mx-description Returns flag indicating it this service is enabled 
    * @phoenix:mx-isWriteable no
    * 
    * @return boolean The enabled flag     
    */  
    public boolean isEnabled();

}
