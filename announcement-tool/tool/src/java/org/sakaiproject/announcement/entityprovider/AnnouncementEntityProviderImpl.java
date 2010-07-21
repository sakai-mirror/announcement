package org.sakaiproject.announcement.entityprovider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Stack;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.announcement.api.AnnouncementChannel;
import org.sakaiproject.announcement.api.AnnouncementMessage;
import org.sakaiproject.announcement.api.AnnouncementMessageHeader;
import org.sakaiproject.announcement.api.AnnouncementService;
import org.sakaiproject.authz.api.SecurityService;
import org.sakaiproject.entity.api.Reference;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.entitybroker.EntityReference;
import org.sakaiproject.entitybroker.EntityView;
import org.sakaiproject.entitybroker.entityprovider.CoreEntityProvider;
import org.sakaiproject.entitybroker.entityprovider.annotations.EntityCustomAction;
import org.sakaiproject.entitybroker.entityprovider.capabilities.ActionsExecutable;
import org.sakaiproject.entitybroker.entityprovider.capabilities.AutoRegisterEntityProvider;
import org.sakaiproject.entitybroker.entityprovider.capabilities.RESTful;
import org.sakaiproject.entitybroker.entityprovider.extension.Formats;
import org.sakaiproject.entitybroker.entityprovider.search.Restriction;
import org.sakaiproject.entitybroker.entityprovider.search.Search;
import org.sakaiproject.entitybroker.util.AbstractEntityProvider;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.message.api.MessageHeader;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.site.api.ToolConfiguration;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.user.api.UserDirectoryService;
import org.sakaiproject.util.MergedList;
import org.sakaiproject.util.MergedListEntryProviderBase;
import org.sakaiproject.util.MergedListEntryProviderFixedListWrapper;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class AnnouncementEntityProviderImpl extends AbstractEntityProvider implements CoreEntityProvider, AutoRegisterEntityProvider, RESTful, ActionsExecutable{

	public final static String ENTITY_PREFIX = "announcement";
	
	private static final String PORTLET_CONFIG_PARAM_MERGED_CHANNELS = "mergedAnnouncementChannels";
	private static final String MOTD_SITEID = "!site";
	private static final String MOTD_CHANNEL_SUFFIX = "motd";
	public static int DEFAULT_DISPLAY_NUMBER_OPTION = 3;
	private static final String UPDATE_PERMISSIONS = "site.upd";
	private static final String varNameNumberOfDaysInPast = "days";
	private static final String varNameNumberOfAnnouncements = "items";
	int numberOfDaysInThePast = 10;
	// hours * minutes * seconds * milliseconds
	private static final long MILLISECONDS_IN_DAY = (24 * 60 * 60 * 1000);
	private static final Log log = LogFactory.getLog(AnnouncementEntityProviderImpl.class);
	
	
	public String getEntityPrefix() {
		return ENTITY_PREFIX;
	}

	
	/**
	 * Get the list of announcements for a site (or user site, or !site for motd)
	 */
	public List<DecoratedAnnouncement> getAnnouncements(String siteId) {
		
		List<DecoratedAnnouncement> dAnnouncements = new ArrayList<DecoratedAnnouncement>();
		
		//check if we are loading the MOTD
		boolean motdView = false;
		if(StringUtils.equals(siteId, MOTD_SITEID)) {
			motdView = true;
		}
		
		//get currentUserId for permissions checks, not needed for motdView
		String currentUserId = sessionManager.getCurrentSessionUserId();
		if(StringUtils.isBlank(currentUserId) && !motdView){
			return dAnnouncements;
		}
		
		try {

				//check if we are loading data for a user
				//note that it is not possible to SPECIFY a userId.
				boolean workspaceView = false;
				if(siteService.isUserSite(siteId)) {
					workspaceView = true;
				}
				
				log.debug("workspaceView: " + workspaceView);
				log.debug("motdView: " + motdView);
				log.debug("siteId: " + siteId);
				log.debug("currentUserId: " + currentUserId);

				boolean isSynoptic = true;

				List<AnnouncementMessage> messageList = new ArrayList<AnnouncementMessage>();
				MergedList mergedAnnouncementList = new MergedList();

				String[] channelArrayFromConfigParameterValue = null;	

				//create the channelId
				String channelId = null;
				if(motdView) {
					channelId = announcementService.channelReference(siteId, MOTD_CHANNEL_SUFFIX);
				} else {
					channelId = announcementService.channelReference(siteId, SiteService.MAIN_CONTAINER);
				}
				
				log.debug("channel: " + channelId);

				//get the channels
				AnnouncementChannel defaultChannel;
				try {
					defaultChannel = announcementService.getAnnouncementChannel(channelId);

					Site site = null;
					String initMergeList = null;
					ToolConfiguration tc = null;
					ToolConfiguration synopticTc = null;
					
					if(!motdView) {
						
						//get site
						site = siteService.getSite(siteId);

						//get tool properties for sakai.announcements in this site
						tc = site.getToolForCommonId("sakai.announcements");
					}
						
					if (tc != null){
						//Properties ps= tc.getPlacementConfig();
						initMergeList = tc.getPlacementConfig().getProperty(PORTLET_CONFIG_PARAM_MERGED_CHANNELS);	
					}

					if (!securityService.isSuperUser() && workspaceView) {
						channelArrayFromConfigParameterValue = mergedAnnouncementList.getAllPermittedChannels(new AnnouncementChannelReferenceMaker());
					} else {
						channelArrayFromConfigParameterValue = mergedAnnouncementList.getChannelReferenceArrayFromDelimitedString(channelId, initMergeList);
					}
					

					mergedAnnouncementList.loadChannelsFromDelimitedString(
							workspaceView, 
							new MergedListEntryProviderFixedListWrapper(
									new EntryProvider(), 
									channelId, 
									channelArrayFromConfigParameterValue,
									new AnnouncementReferenceToChannelConverter()
							),
							StringUtils.trimToEmpty(currentUserId),
							channelArrayFromConfigParameterValue,
							securityService.isSuperUser(),
							siteId);

					//synoptic announcement settings
					boolean isEnforceNumberOfAnnouncements = true;
					int numberOfAnnouncements = DEFAULT_DISPLAY_NUMBER_OPTION;
					boolean enforceDays = true;
					int maxNumberOfDaysInThePastProp = numberOfDaysInThePast;

					if(!motdView) {
						//get properties for synoptic tool in this site
						synopticTc = site.getToolForCommonId("sakai.synoptic.announcement");
					}
					
					if(synopticTc != null){
						Properties props = synopticTc.getPlacementConfig();
						if(props.isEmpty()) {
							props = synopticTc.getConfig();
						}
						
						if(props != null){
							if (props.get(varNameNumberOfAnnouncements) != null) {
								numberOfAnnouncements = getIntegerParameter(props, varNameNumberOfAnnouncements, numberOfAnnouncements);
								isEnforceNumberOfAnnouncements = true;
							}
							if (props.get(varNameNumberOfDaysInPast) != null) {
								maxNumberOfDaysInThePastProp = getIntegerParameter(props, varNameNumberOfDaysInPast, numberOfDaysInThePast);
								enforceDays = true;
							}
						}
					}


					//get messages in each channel
					for (Iterator channelsIt = mergedAnnouncementList.iterator(); channelsIt.hasNext();) {
						MergedList.MergedEntry curEntry = (MergedList.MergedEntry) channelsIt.next();

						// If this entry should not be merged, skip to the next one.
						if (!curEntry.isMerged()) {
							continue;
						}

						AnnouncementChannel curChannel = null;
						try {
							curChannel = (AnnouncementChannel) announcementService.getChannel(curEntry.getReference());
						}
						catch (IdUnusedException e) {
							e.printStackTrace();
						}
						catch (PermissionException e) {
							e.printStackTrace();
						}

						if (curChannel != null) {
							if (announcementService.allowGetChannel(curChannel.getReference())) {
								try {
									messageList.addAll(wrapList(curChannel.getMessages(null, true), curChannel, defaultChannel, maxNumberOfDaysInThePastProp, enforceDays, motdView));
								} catch (PermissionException e) {
									e.printStackTrace();
								}
							}
						}
					}

					// Do an overall sort. We couldn't do this earlier since each merged channel
					Collections.sort(messageList);

					// Apply any necessary list truncation.
					messageList = getViewableMessages(messageList, siteId, workspaceView, isSynoptic);
					messageList = trimListToMaxNumberOfAnnouncements(messageList, isEnforceNumberOfAnnouncements, numberOfAnnouncements);

				} catch (PermissionException e2) {
					log.warn("PermissionException: AnnouncementEntityProviderImpl: getEntities: userEid: " + currentUserId, e2);
				}

				for (AnnouncementMessage announcement : (List<AnnouncementMessage>) messageList) {
					List<String> attachmentsList = new ArrayList<String>();
					for (Reference attachment : (List<Reference>) announcement.getHeader().getAttachments()) {
						attachmentsList.add(attachment.getProperties().getPropertyFormatted(attachment.getProperties().getNamePropDisplayName()));
					}
					dAnnouncements.add(new DecoratedAnnouncement(announcement.getId(), announcement
							.getAnnouncementHeader().getSubject(), announcement
							.getBody(), announcement.getHeader().getFrom()
							.getDisplayName(), "" + announcement.getHeader().getDate().getTime(),							
							attachmentsList, ((WrappedAnnouncement) announcement).getSiteId(), ((WrappedAnnouncement) announcement).getSiteTitle()));
				}

			} catch (Exception e) {
				log.warn("Exception: AnnouncementEntityProviderImpl: getEntities: userEid: " + currentUserId, e);
			}
		
    	return dAnnouncements;
	}
	
	/**
	 * site/siteId
	 */
	@EntityCustomAction(action="site",viewKey=EntityView.VIEW_LIST)
    public List<DecoratedAnnouncement> getAnnouncementsForSite(EntityView view, Map<String, Object> params) {
		String siteId = view.getPathSegment(2);
        
        if (StringUtils.isBlank(siteId)) {
            throw new IllegalArgumentException("siteId must be set in order to get the announcements for a site, via /announcement/site/siteId");
        }
        List<DecoratedAnnouncement> l = getAnnouncements(siteId);
        return l;
    }
	
	/**
	 * user
	 */
	@EntityCustomAction(action="user",viewKey=EntityView.VIEW_LIST)
    public List<DecoratedAnnouncement> getAnnouncementsForUser(EntityView view, EntityReference ref) {
		
        String userId = sessionManager.getCurrentSessionUserId();
    	if (StringUtils.isBlank(userId)) {
    		throw new SecurityException("You must be logged in to get your announcements.");
    	}
       
        //we still need a siteId since Announcements keys it's data on a channel reference created from a siteId.
        //in the case of a user, this is the My Workspace siteId for that user (as an internal user id)
        String siteId = siteService.getUserSiteId(userId);
        if(StringUtils.isBlank(siteId)) {
    		throw new IllegalArgumentException("No valid siteId was found for userId: " + userId);
        }
        
        List<DecoratedAnnouncement> l = getAnnouncements(siteId);
        return l;
    }
	
	/**
	 * motd
	 */
	@EntityCustomAction(action="motd",viewKey=EntityView.VIEW_LIST)
    public List<DecoratedAnnouncement> getMessagesOfTheDay(EntityView view, EntityReference ref) {
        
        List<DecoratedAnnouncement> l = getAnnouncements(MOTD_SITEID);
        return l;
    }

	
	/**
	 * This will limit the maximum number of announcements that is shown.
	 */
	private List<AnnouncementMessage> trimListToMaxNumberOfAnnouncements(List<AnnouncementMessage> messageList, boolean isEnforceNumberOfAnnouncementsLimit, int numberOfAnnouncements) {
		
		if (isEnforceNumberOfAnnouncementsLimit) {
			ArrayList<AnnouncementMessage> destList = new ArrayList<AnnouncementMessage>();

			// We need to go backwards through the list, limiting it to the number
			// of announcements that we're allowed to display.
			for (int i = messageList.size() - 1, curAnnouncementCount = 0; i >= 0 && curAnnouncementCount < numberOfAnnouncements; i--) {
				AnnouncementMessage message = (AnnouncementMessage) messageList.get(i);
				destList.add(message);
				curAnnouncementCount++;
			}
			return destList;
		}
		else {
			return messageList;
		}
	}
	
	
	/**
	 * Utility routine used to get an integer named value from a map or supply a default value if none is found.
	 */
	private int getIntegerParameter(Map params, String paramName, int defaultValue) {
		String intValString = (String) params.get(paramName);

		if (StringUtils.trimToNull(intValString) != null) {
			return Integer.parseInt(intValString);
		}
		else {
			return defaultValue;
		}
	}
	
	
	/**
	 * Filters out messages based on hidden property and release/retract dates.
	 * Only use hidden if in synoptic tool.
	 * 
	 * @param messageList
	 * 			The unfiltered message list
	 * 
	 * @return
	 * 			List of messsage this user is able to view
	 */
	private List<AnnouncementMessage> getViewableMessages(List<AnnouncementMessage> messageList, String siteId, boolean isMyWorkspace, boolean isSynoptic) {
		final List<AnnouncementMessage> filteredMessages = new ArrayList<AnnouncementMessage>();
		
		for (Iterator<AnnouncementMessage> messIter = messageList.iterator(); messIter.hasNext();) {
			final AnnouncementMessage message = (AnnouncementMessage) messIter.next();
			
			// for synoptic tool or if in MyWorkspace, 
			// only display if not hidden AND
			// between release and retract dates (if set)
			if (isMyWorkspace || isSynoptic) {
				if (!isHidden(message) && announcementService.isMessageViewable(message)) {
					filteredMessages.add(message);
				}
			}
			else {
				// on main page, if hidden but user has hidden permission
				// then display. Otherwise, if between release/retract dates
				// or they are not set
				if (isHidden(message)) {
					if (canViewHidden(message, siteId)) {
						filteredMessages.add(message);
					}
				}
				else if (announcementService.isMessageViewable(message)) {
					filteredMessages.add(message);
				}
				else if (canViewHidden(message, siteId)) {
					filteredMessages.add(message);
				}
			}
		}
		
		return filteredMessages;
	}
	
	/**
	 * Determine if message is hidden (draft property set)
	 */
	private boolean isHidden(AnnouncementMessage message) {
		return 	message.getHeader().getDraft();
	}
	
	/**
	 * Determines if use has draft (UI: hidden) permission or site.upd
	 * If so, they will be able to view messages that are hidden
	 */
	private boolean canViewHidden(AnnouncementMessage msg, String siteId) {
		final boolean b = securityService.unlock(AnnouncementService.SECURE_READ_DRAFT, msg.getReference()) || securityService.unlock(UPDATE_PERMISSIONS, "/site/"+ siteId); 
		return b;
	}
	
	
	
	/*
	 * Callback class so that we can form references in a generic way.
	 */
	private final class AnnouncementChannelReferenceMaker implements MergedList.ChannelReferenceMaker {
		public String makeReference(String siteId) {
			return announcementService.channelReference(siteId, SiteService.MAIN_CONTAINER);
		}
	}

	public final class AnnouncementReferenceToChannelConverter implements MergedListEntryProviderFixedListWrapper.ReferenceToChannelConverter {
		public Object getChannel(String channelReference) {
			try {
				return announcementService.getAnnouncementChannel(channelReference);
			}
			catch (IdUnusedException e) {
				return null;
			}
			catch (PermissionException e) {
				return null;
			}
		}
	}

	
	/**
	 * Used to provide a interface to the MergedList class that is shared with the calendar action.
	 */
	public class EntryProvider extends MergedListEntryProviderBase {
		/*
		 * (non-Javadoc)
		 * 
		 * @see org.sakaiproject.util.MergedListEntryProviderBase#makeReference(java.lang.String)
		 */
		public Object makeObjectFromSiteId(String id) {
			String channelReference = announcementService.channelReference(id, SiteService.MAIN_CONTAINER);
			Object channel = null;

			if (channelReference != null) {
				try {
					channel = announcementService.getChannel(channelReference);
				}
				catch (IdUnusedException e) {
					// The channel isn't there.
				}
				catch (PermissionException e) {
					// We can't see the channel
				}
			}

			return channel;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see org.chefproject.actions.MergedEntryList.EntryProvider#allowGet(java.lang.Object)
		 */
		public boolean allowGet(String ref) {
			return announcementService.allowGetChannel(ref);
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see org.chefproject.actions.MergedEntryList.EntryProvider#getContext(java.lang.Object)
		 */
		public String getContext(Object obj) {
			if (obj == null) {
				return "";
			}

			AnnouncementChannel channel = (AnnouncementChannel) obj;
			return channel.getContext();
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see org.chefproject.actions.MergedEntryList.EntryProvider#getReference(java.lang.Object)
		 */
		public String getReference(Object obj) {
			if (obj == null) {
				return "";
			}

			AnnouncementChannel channel = (AnnouncementChannel) obj;
			return channel.getReference();
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see org.chefproject.actions.MergedEntryList.EntryProvider#getProperties(java.lang.Object)
		 */
		public ResourceProperties getProperties(Object obj) {
			if (obj == null) {
				return null;
			}

			AnnouncementChannel channel = (AnnouncementChannel) obj;
			return channel.getProperties();
		}

	}

	private List<AnnouncementMessage> wrapList(List<AnnouncementMessage> messages, AnnouncementChannel currentChannel, AnnouncementChannel hostingChannel, int maxNumberOfDaysInThePast, boolean isEnforceNumberOfDaysInThePastLimit, boolean motdView) {
	//	int maxNumberOfDaysInThePast = options.getNumberOfDaysInThePast();

		List<AnnouncementMessage> messageList = new ArrayList<AnnouncementMessage>();

		Iterator<AnnouncementMessage> it = messages.iterator();

		while (it.hasNext()) {
			AnnouncementMessage message = (AnnouncementMessage) it.next();

			// See if the message falls within the filter window.
			if (isEnforceNumberOfDaysInThePastLimit && !isMessageWithinLastNDays(message, maxNumberOfDaysInThePast)) {
				continue;
			}
		
				Site site;
				try {
					if(motdView) {
						messageList.add(new WrappedAnnouncement(message, currentChannel.getContext(), "MOTD"));
					} else {
						site = siteService.getSite(currentChannel.getContext());
						messageList.add(new WrappedAnnouncement(message, currentChannel.getContext(), site.getTitle()));
					}
				} catch (IdUnusedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}					
		}

		return messageList;
	}

	
	/**
	 * See if the given message was posted in the last N days, where N is the value of the maxDaysInPast parameter.
	 */
	private static boolean isMessageWithinLastNDays(AnnouncementMessage message, int maxDaysInPast) {
		
		long currentTime = new Date().getTime();
		long timeDeltaMSeconds = currentTime - message.getHeader().getDate().getTime();
		long numDays = timeDeltaMSeconds / MILLISECONDS_IN_DAY;

		return (numDays <= maxDaysInPast);
	}
	
	
	/**
	 * Unimplemented EntityBroker methods
	 */
	public List<String> findEntityRefs(String[] arg0, String[] arg1, String[] arg2, boolean arg3) {
		// TODO Auto-generated method stub
		return null;
	}

	public String createEntity(EntityReference ref, Object entity, Map<String, Object> params) {
		// TODO Auto-generated method stub
		return null;
	}

	public Object getSampleEntity() {
		// TODO Auto-generated method stub
		return null;
	}

	public void updateEntity(EntityReference ref, Object entity, Map<String, Object> params) {
		// TODO Auto-generated method stub
		
	}

	public Object getEntity(EntityReference arg0) {
		// TODO Auto-generated method stub
		return null;
	}

	public void deleteEntity(EntityReference ref, Map<String, Object> params) {
		// TODO Auto-generated method stub
		
	}
	
	public String[] getHandledOutputFormats() {
		return new String[] { Formats.HTML, Formats.XML, Formats.JSON };
	}

	public String[] getHandledInputFormats() {
		// TODO Auto-generated method stub
		return null;
	}


	public boolean entityExists(String arg0) {
		// TODO Auto-generated method stub
		return false;
	}
	
	public List<?> getEntities(EntityReference ref, Search search) {
		return null;
	}
		
	public class DecoratedAnnouncement {
		private String id;
		private String title;
		private String body;
		private String createdByDisplayName;
		private String createdOn;
		private List<String> attachments;
		private String siteId;
		private String siteTitle;
		
		public DecoratedAnnouncement(String id, String title, String body, String createdByDisplayName, String createdOn, List<String> attachments, String siteId, String siteTitle){
			this.title = title;
			this.body = body;
			this.createdByDisplayName = createdByDisplayName;
			this.createdOn = createdOn;
			this.attachments = attachments;
			this.id = id;
			this.siteId = siteId;
			this.siteTitle = siteTitle;
		}

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public String getTitle() {
			return title;
		}

		public void setTitle(String title) {
			this.title = title;
		}

		public String getBody() {
			return body;
		}

		public void setBody(String body) {
			this.body = body;
		}

		public String getCreatedByDisplayName() {
			return createdByDisplayName;
		}

		public void setCreatedByDisplayName(String createdByDisplayName) {
			this.createdByDisplayName = createdByDisplayName;
		}

		public String getCreatedOn() {
			return createdOn;
		}

		public void setCreatedOn(String createdOn) {
			this.createdOn = createdOn;
		}

		public List<String> getAttachments() {
			return attachments;
		}

		public void setAttachments(List<String> attachments) {
			this.attachments = attachments;
		}

		public String getSiteId() {
			return siteId;
		}

		public void setSiteId(String siteId) {
			this.siteId = siteId;
		}

		public String getSiteTitle() {
			return siteTitle;
		}

		public void setSiteTitle(String siteTitle) {
			this.siteTitle = siteTitle;
		}	
		
	}
	
	
	public class WrappedAnnouncement implements AnnouncementMessage {
		
		private AnnouncementMessage message;
		private String siteId;
		private String siteTitle;
		
		public WrappedAnnouncement(AnnouncementMessage message, String siteId, String siteTitle){
			this.message = message;
			this.siteId = siteId; 
			this.siteTitle = siteTitle;
		}

		public AnnouncementMessage getMessage() {
			return message;
		}

		public void setMessage(AnnouncementMessage message) {
			this.message = message;
		}

		public String getSiteId() {
			return siteId;
		}

		public void setSiteId(String siteId) {
			this.siteId = siteId;
		}

		public String getSiteTitle() {
			return siteTitle;
		}

		public void setSiteTitle(String siteTitle) {
			this.siteTitle = siteTitle;
		}

		public AnnouncementMessageHeader getAnnouncementHeader() {
			return message.getAnnouncementHeader();
		}

		public String getBody() {
			return message.getBody();
		}

		public MessageHeader getHeader() {
			return message.getHeader();
		}

		public String getId() {
			return message.getId();
		}

		public ResourceProperties getProperties() {
			return message.getProperties();
		}

		public String getReference() {
			return message.getReference();
		}

		public String getReference(String rootProperty) {
			return message.getReference(rootProperty);
		}

		public String getUrl() {
			return message.getUrl();
		}

		public String getUrl(String rootProperty) {
			return message.getUrl(rootProperty);
		}

		public int compareTo(Object o) {
			return message.compareTo(o);
		}

		public Element toXml(Document doc, Stack stack) {
			// TODO Auto-generated method stub
			return null;
		}
	}
	

	private SecurityService securityService;
	public void setSecurityService(SecurityService securityService) {
		this.securityService = securityService;
	}
	
	private SessionManager sessionManager;
	public void setSessionManager(SessionManager sessionManager) {
		this.sessionManager = sessionManager;
	}
	
	private SiteService siteService;
	public void setSiteService(SiteService siteService) {
		this.siteService = siteService;
	}
	
	private AnnouncementService announcementService;
	public void setAnnouncementService(AnnouncementService announcementService) {
		this.announcementService = announcementService;
	}


}
