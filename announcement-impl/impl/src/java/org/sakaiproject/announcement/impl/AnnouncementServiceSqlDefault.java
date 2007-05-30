/**********************************************************************************
 * $URL$
 * $Id$
 ***********************************************************************************
 *
 * Copyright (c) 2004, 2005, 2006 The Sakai Foundation.
 *
 * Licensed under the Educational Community License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/ecl1.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 **********************************************************************************/
package org.sakaiproject.announcement.impl;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.sakaiproject.announcement.api.AnnouncementServiceSql;



/**
 * methods for accessing announcement data in a database.
 */
public class AnnouncementServiceSqlDefault implements AnnouncementServiceSql {

   // logger
   protected final transient Log logger = LogFactory.getLog(getClass());

   /**
    * returns the sql statement which retrieves all the message records from the specified table.
    */
   public String getListChannelMessagesSql1(String table) {
      return "select CHANNEL_ID, MESSAGE_ID, XML from " + table /* + " where OWNER is null" */;
   }

   /**
    * returns the sql statement which retrieves all the message records from the specified table.
    */
   public String getListChannelMessagesSql2(String table) {
      return "select CHANNEL_ID, MESSAGE_ID, XML, PUBVIEW from " + table;
   }

   /**
    * returns the sql statement which updates specified message in the specified table.
    */
   public String getUpdateChannelMessageSql1(String table) {
      return "update " + table + " set OWNER = ?, DRAFT = ? where CHANNEL_ID = ? and MESSAGE_ID = ?";
   }

   /**
    * returns the sql statement which updates specified message in the specified table.
    */
   public String getUpdateChannelMessageSql2(String table) {
      return "update " + table + " set PUBVIEW = ? where CHANNEL_ID = ? and MESSAGE_ID = ?";
   }

   /**
    * returns the sql statement which updates specified message in the specified table.
    */
   public String getUpdateChannelMessageSql3(String table) {
      return "update " + table + " set PUBVIEW = ?, XML = ? where CHANNEL_ID = ? and MESSAGE_ID = ?";
   }
}
