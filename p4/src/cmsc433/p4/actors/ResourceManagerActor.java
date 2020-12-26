package cmsc433.p4.actors;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import cmsc433.p4.enums.AccessRequestDenialReason;
import cmsc433.p4.enums.AccessRequestType;
import cmsc433.p4.enums.AccessType;
import cmsc433.p4.enums.ManagementRequestDenialReason;
import cmsc433.p4.enums.ManagementRequestType;
import cmsc433.p4.enums.ResourceStatus;
import cmsc433.p4.messages.AccessReleaseMsg;
import cmsc433.p4.messages.AccessRequestDeniedMsg;
import cmsc433.p4.messages.AccessRequestGrantedMsg;
import cmsc433.p4.messages.AccessRequestMsg;
import cmsc433.p4.messages.AddInitialLocalResourcesRequestMsg;
import cmsc433.p4.messages.AddInitialLocalResourcesResponseMsg;
import cmsc433.p4.messages.AddLocalUsersRequestMsg;
import cmsc433.p4.messages.AddLocalUsersResponseMsg;
import cmsc433.p4.messages.AddRemoteManagersRequestMsg;
import cmsc433.p4.messages.AddRemoteManagersResponseMsg;
import cmsc433.p4.messages.LogMsg;
import cmsc433.p4.messages.ManagementRequestDeniedMsg;
import cmsc433.p4.messages.ManagementRequestGrantedMsg;
import cmsc433.p4.messages.ManagementRequestMsg;
import cmsc433.p4.messages.WhoHasResourceRequestMsg;
import cmsc433.p4.messages.WhoHasResourceResponseMsg;
import cmsc433.p4.util.AccessRelease;
import cmsc433.p4.util.AccessRequest;
import cmsc433.p4.util.ManagementRequest;
import cmsc433.p4.util.Resource;

public class ResourceManagerActor extends AbstractActor {
	/** Actor to send logging messages to. */
	private ActorRef logger;
	/** (non-Javadoc)
	 * 
	 * You must provide an implementation of the onReceive() method below.
	 * 
	 * @see akka.actor.AbstractActor#createReceive
	 */

	private HashMap<String, Resource> localResources = new HashMap<>();
	private HashSet<ActorRef> remoteManagers = new HashSet<>();
	private HashSet<ActorRef> localUsers = new HashSet<>();
	private HashMap<String, Set<ManagementRequestMsg>> disable = new HashMap<>();
	private HashMap<String, Queue<AccessRequestMsg>> accessReqMsgQueue = new HashMap<>();
	private HashMap<String, Set<ActorRef>> currRead = new HashMap<>();
	private HashMap<String, ActorRef> currWrite = new HashMap<>();
	private HashMap<String, ActorRef> sourceList = new HashMap<>();
	private HashMap<Object, Integer> potentialList = new HashMap<>();
	private HashMap<ActorRef, Map<String,Object>> resourceMessage = new HashMap<>();

	/**
	 * Constructor.
	 * 
	 * @param logger			Actor to send logging messages to
	 */
	private ResourceManagerActor(ActorRef logger) {
		this.logger = logger;
	}

	/**
	 * Props structure-generator for this class.
	 * @return  Props structure
	 */
	static Props props (ActorRef logger) {
		return Props.create(ResourceManagerActor.class, logger);
	}

	/**
	 * Factory method for creating resource managers.
	 * @param logger			Actor to send logging messages to
	 * @param system			Actor system in which manager will execute
	 * @return					Reference to new manager
	 */
	public static ActorRef makeResourceManager (ActorRef logger, ActorSystem system) {
		return system.actorOf(props(logger));
	}

	/**
	 * Sends a message to the Logger Actor.
	 * @param msg The message to be sent to the logger
	 */
	public void log (LogMsg msg) {
		logger.tell(msg, getSelf());
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(Object.class, this::onReceive)
				.build();
	}

	public void onReceive(Object msg) throws Exception {
		if(msg instanceof AccessReleaseMsg){
			AccessReleaseMsg message = (AccessReleaseMsg) msg;
			AccessRelease release = message.getAccessRelease();
			log(LogMsg.makeAccessReleaseReceivedLogMsg(message.getSender(), getSelf(), release));
			String resourceName=release.getResourceName();
			if(localResources.containsKey(resourceName) == true){
				if(release.getType() == AccessType.CONCURRENT_READ){
					if(currRead.containsKey(resourceName) == true && currRead.get(resourceName).contains(message.getSender())){
						log(LogMsg.makeAccessReleasedLogMsg(message.getSender(), getSelf(), release));
						currRead.get(resourceName).remove(message.getSender());
						if(disable.containsKey(resourceName) == true && currWrite.containsKey(resourceName) == false && currRead.get(resourceName).size() == 0){
							localResources.get(resourceName).disable();
							log(LogMsg.makeResourceStatusChangedLogMsg(getSelf(), resourceName, ResourceStatus.DISABLED));
							disable.get(resourceName).forEach(m -> {
								log(LogMsg.makeManagementRequestGrantedLogMsg(m.getReplyTo(), getSelf(), m.getRequest()));
								m.getReplyTo().tell(new ManagementRequestGrantedMsg(m), getSelf());
							});
							disable.remove(resourceName);
						}
						if(localResources.get(resourceName).getStatus()!=ResourceStatus.DISABLED && !disable.containsKey(resourceName) == true) {
							Queue<AccessRequestMsg> currQueue = accessReqMsgQueue.get(resourceName);
							boolean check = true;
							while (check && accessReqMsgQueue.containsKey(resourceName) == true
									&& currQueue != null
									&& currQueue.size() != 0
									) {
								AccessRequestMsg tRequest = currQueue.peek();
								AccessRequest accessInfo = tRequest.getAccessRequest();
								ActorRef user = tRequest.getReplyTo();
								String tresourceName = accessInfo.getResourceName();
								if (accessInfo.getType() == AccessRequestType.EXCLUSIVE_WRITE_BLOCKING) {
									if (currWrite.containsKey(tresourceName) == false || readHelp(tresourceName, user)) {
										currQueue.poll();
										currWrite.put(tresourceName, user);
										log(LogMsg.makeAccessRequestGrantedLogMsg(user, getSelf(), accessInfo));
										user.tell(new AccessRequestGrantedMsg(tRequest), getSelf());
									} else {
										check = false;
									}
								} else if (currWrite.containsKey(tresourceName) == true
										|| currWrite.get(tresourceName).equals(user)) {
									if (!currRead.containsKey(tresourceName) == true) {
										currRead.put(tresourceName, new HashSet<ActorRef>());
									}
									currRead.get(tresourceName).add(user);
									log(LogMsg.makeAccessRequestGrantedLogMsg(user, getSelf(), accessInfo));
									currQueue.poll();
									user.tell(new AccessRequestGrantedMsg(accessInfo), getSelf());
								} else {
									check = false;
								}
							}
						}
					}else{
						log(LogMsg.makeAccessReleaseIgnoredLogMsg(message.getSender(), getSelf(), release));
					}
				} else if(currWrite.containsKey(resourceName) == true && currWrite.get(resourceName).equals(message.getSender())){
					log(LogMsg.makeAccessReleasedLogMsg(message.getSender(), getSelf(), release));
					currWrite.remove(release.getResourceName());
					if(disable.containsKey(resourceName) == true && currWrite.containsKey(resourceName) == false && currRead.get(resourceName).size() == 0){
						localResources.get(resourceName).disable();
						log(LogMsg.makeResourceStatusChangedLogMsg(getSelf(), resourceName, ResourceStatus.DISABLED));
						disable.get(resourceName).forEach(m -> {
							log(LogMsg.makeManagementRequestGrantedLogMsg(m.getReplyTo(), getSelf(), m.getRequest()));
							m.getReplyTo().tell(new ManagementRequestGrantedMsg(m), getSelf());
						});
						disable.remove(resourceName);
					}

					if(localResources.get(resourceName).getStatus()!=ResourceStatus.DISABLED&&!disable.containsKey(resourceName) == true){
						Queue<AccessRequestMsg> currQueue=accessReqMsgQueue.get(resourceName);
						boolean check =true;
						while(check&& accessReqMsgQueue.containsKey(resourceName) == true
								&& currQueue!=null
								&& currQueue.size() != 0
								){
							AccessRequestMsg tRequest=currQueue.peek();
							AccessRequest accessInfo=tRequest.getAccessRequest();
							ActorRef user=tRequest.getReplyTo();
							String tresourceName=accessInfo.getResourceName();
							if(accessInfo.getType()==AccessRequestType.EXCLUSIVE_WRITE_BLOCKING){
								if(currWrite.containsKey(tresourceName) == false||readHelp(tresourceName,user)){
									currQueue.poll();
									currWrite.put(tresourceName, user);
									log(LogMsg.makeAccessRequestGrantedLogMsg(user,getSelf(),accessInfo));
									user.tell(new AccessRequestGrantedMsg(tRequest), getSelf());
								}else{
									check=false;
								}
							} else if(currWrite.containsKey(tresourceName) == false
									||currWrite.get(tresourceName).equals(user))
							{
								if (!currRead.containsKey(tresourceName) == true) {
									currRead.put(tresourceName,new HashSet<ActorRef>());
								}
								currRead.get(tresourceName).add(user);
								log(LogMsg.makeAccessRequestGrantedLogMsg(user,getSelf(),accessInfo));
								currQueue.poll();
								user.tell(new AccessRequestGrantedMsg(accessInfo),getSelf());
							}else{
								check=false;
							}
						}
					}
				}else{
					log(LogMsg.makeAccessReleaseIgnoredLogMsg(message.getSender(), getSelf(), release));
				}
			} else if(!sourceList.containsKey(resourceName) == true){
				log(LogMsg.makeAccessReleaseIgnoredLogMsg(message.getSender(), getSelf(), release));
			}else{
				log(LogMsg.makeAccessReleaseForwardedLogMsg(getSelf(), sourceList.get(resourceName), release));
				sourceList.get(resourceName).tell(msg, getSelf());
			}
		}
		else if(msg instanceof ManagementRequestMsg){
			ManagementRequestMsg message = (ManagementRequestMsg) msg;
			ManagementRequest request = message.getRequest();
			log(LogMsg.makeManagementRequestReceivedLogMsg(message.getReplyTo(), getSelf(), request));
			String resourceName=request.getResourceName();
			ActorRef From = message.getReplyTo();

			if(localResources.containsKey(resourceName) == true){
				if(request.getType() == ManagementRequestType.ENABLE){
					if(localResources.get(resourceName).getStatus() == ResourceStatus.DISABLED){
						log(LogMsg.makeResourceStatusChangedLogMsg(getSelf(), resourceName, ResourceStatus.ENABLED));
						localResources.get(resourceName).enable();

						log(LogMsg.makeManagementRequestGrantedLogMsg(From, getSelf(), request));
						From.tell(new ManagementRequestGrantedMsg(request), getSelf());
					}
					if(disable.containsKey(resourceName) == true) {
						log(LogMsg.makeManagementRequestGrantedLogMsg(From, getSelf(), request));
						From.tell(new ManagementRequestGrantedMsg(request), getSelf());
					}
				} else if(localResources.get(resourceName).getStatus() != ResourceStatus.DISABLED){
					if(currWrite.containsKey(resourceName) == false &&
							(currRead.containsKey(resourceName) == false || currRead.get(resourceName).size() == 0) &&
							(accessReqMsgQueue.containsKey(resourceName) == true == false || accessReqMsgQueue.get(resourceName).size() == 0))
					{
						log(LogMsg.makeResourceStatusChangedLogMsg(getSelf(), resourceName, ResourceStatus.DISABLED));
						localResources.get(resourceName).disable();

						log(LogMsg.makeManagementRequestGrantedLogMsg(From, getSelf(), request));
						getSender().tell(new ManagementRequestGrantedMsg(request), getSelf());
					} else if (currWrite.containsKey(resourceName) == true ||
							currRead.get(resourceName).contains(From) ||
							(accessReqMsgQueue.containsKey(resourceName) == true && accessReqMsgQueue.get(resourceName).size() != 0)
							){
						log(LogMsg.makeManagementRequestDeniedLogMsg(From, getSelf(), request, ManagementRequestDenialReason.ACCESS_HELD_BY_USER));
						From.tell(new ManagementRequestDeniedMsg(request, ManagementRequestDenialReason.ACCESS_HELD_BY_USER), getSelf());
					} else {
						if(!disable.containsKey(resourceName) == true) {
							disable.put(resourceName, new HashSet<ManagementRequestMsg>());
						}
						disable.get(resourceName).add((ManagementRequestMsg) msg);
					}
				}
			} else if(sourceList.containsKey(resourceName) == true){
				log(LogMsg.makeManagementRequestForwardedLogMsg(getSelf(), sourceList.get(resourceName), request));
				sourceList.get(resourceName).tell(msg, getSelf());
			}else{
				potentialList.put(msg,remoteManagers.size());
				WhoHasResourceRequestMsg future = new WhoHasResourceRequestMsg(resourceName);
				remoteManagers.forEach(RM -> {
					if(!resourceMessage.containsKey(RM)) {
						resourceMessage.put(RM, new HashMap<String, Object>());
					}
					resourceMessage.get(RM).put(resourceName,  msg);

					RM.tell(future, getSelf());
				});
			}
		}  else if (msg instanceof AddRemoteManagersRequestMsg){
			((AddRemoteManagersRequestMsg) msg).getManagerList().forEach(actor -> {
				if(!getSelf().equals(actor)){
					remoteManagers.add(actor);
				}
			});
			getSender().tell(
					new AddRemoteManagersResponseMsg((AddRemoteManagersRequestMsg)msg), getSelf());
		}else if(msg instanceof AddLocalUsersRequestMsg){
			localUsers.addAll(((AddLocalUsersRequestMsg) msg).getLocalUsers());
			getSender().tell(
					new AddLocalUsersResponseMsg((AddLocalUsersRequestMsg)msg), getSelf());
		}else if (msg instanceof AddInitialLocalResourcesRequestMsg){
			((AddInitialLocalResourcesRequestMsg) msg).getLocalResources().forEach(rs -> {
				rs.enable();

				log(LogMsg.makeLocalResourceCreatedLogMsg(getSelf(), rs.name));
				localResources.put(rs.getName(),rs);
				log(LogMsg.makeResourceStatusChangedLogMsg(getSelf(), rs.name, ResourceStatus.ENABLED));
			});
			getSender().tell(
					new AddInitialLocalResourcesResponseMsg((AddInitialLocalResourcesRequestMsg)msg), getSelf());
		} else if (msg instanceof AccessRequestMsg){
			AccessRequestMsg message=(AccessRequestMsg) msg;
			AccessRequest request = message.getAccessRequest();
			ActorRef replyTo = message.getReplyTo();
			log(LogMsg.makeAccessRequestReceivedLogMsg(replyTo, getSelf(), request));

			String resourceName = request.getResourceName();

			if(localResources.containsKey(resourceName) == true){
				if(ResourceStatus.DISABLED==localResources.get(resourceName).getStatus()|| disable.containsKey(resourceName) == true){
					log(LogMsg.makeAccessRequestDeniedLogMsg(
							replyTo, getSelf(), request, AccessRequestDenialReason.RESOURCE_DISABLED));

					replyTo.tell(
							new AccessRequestDeniedMsg(request,AccessRequestDenialReason.RESOURCE_DISABLED),
							getSelf());
					return;
				}
				if(request.getType() == AccessRequestType.EXCLUSIVE_WRITE_BLOCKING
						||request.getType() == AccessRequestType.CONCURRENT_READ_BLOCKING)
				{
					if(accessReqMsgQueue.containsKey(resourceName) == true == false){
						accessReqMsgQueue.put(resourceName, new LinkedList<>());
					}
					accessReqMsgQueue.get(resourceName).add(message);

					if(localResources.get(resourceName).getStatus() != ResourceStatus.DISABLED&&disable.containsKey(resourceName) == false){
						Queue<AccessRequestMsg> currQueue = accessReqMsgQueue.get(resourceName);
						boolean check = true;
						while (check && accessReqMsgQueue.containsKey(resourceName) == true
								&& currQueue != null
								&& currQueue.size() != 0
								) {
							AccessRequestMsg tRequest = currQueue.peek();
							AccessRequest accessInfo = tRequest.getAccessRequest();
							ActorRef user = tRequest.getReplyTo();
							String tresourceName = accessInfo.getResourceName();
							if (accessInfo.getType() == AccessRequestType.EXCLUSIVE_WRITE_BLOCKING) {
								if (currWrite.containsKey(tresourceName) == false || readHelp(tresourceName, user)) {
									currQueue.poll();
									currWrite.put(tresourceName, user);
									log(LogMsg.makeAccessRequestGrantedLogMsg(user, getSelf(), accessInfo));
									user.tell(new AccessRequestGrantedMsg(tRequest), getSelf());
								} else {
									check = false;
								}
							} else if (currWrite.containsKey(tresourceName) == false
									|| currWrite.get(tresourceName).equals(user)) {
								if (currRead.containsKey(tresourceName) == false) {
									currRead.put(tresourceName, new HashSet<ActorRef>());
								}
								currRead.get(tresourceName).add(user);
								log(LogMsg.makeAccessRequestGrantedLogMsg(user, getSelf(), accessInfo));
								currQueue.poll();
								user.tell(new AccessRequestGrantedMsg(accessInfo), getSelf());
							} else {
								check = false;
							}
						}
					}

					return;
				}
				if(accessReqMsgQueue.containsKey(resourceName) == true == false
						|| accessReqMsgQueue.get(resourceName) == null
						|| accessReqMsgQueue.get(resourceName).size() == 0) {
					if(request.getType()==AccessRequestType.EXCLUSIVE_WRITE_NONBLOCKING){
						if(currWrite.containsKey(resourceName) == true || !readHelp(resourceName,replyTo)){
							log(LogMsg.makeAccessRequestDeniedLogMsg(replyTo,
									getSelf(), request, AccessRequestDenialReason.RESOURCE_BUSY));
							replyTo.tell(new AccessRequestDeniedMsg(request,AccessRequestDenialReason.RESOURCE_BUSY), getSelf());
						}else{
							currWrite.put(resourceName, replyTo);
							log(LogMsg.makeAccessRequestGrantedLogMsg(replyTo,getSelf(),request));
							replyTo.tell(new AccessRequestGrantedMsg((AccessRequestMsg)msg), getSelf());
						}
					} else if (request.getType()==AccessRequestType.CONCURRENT_READ_NONBLOCKING){
						if(currWrite.containsKey(resourceName) == true && !currWrite.get(resourceName).equals(replyTo)){
							log(LogMsg.makeAccessRequestDeniedLogMsg(replyTo,
									getSelf(), request, AccessRequestDenialReason.RESOURCE_BUSY));
							replyTo.tell(new AccessRequestDeniedMsg(request,AccessRequestDenialReason.RESOURCE_BUSY), getSelf());
						}else{
							if (currRead.containsKey(resourceName) == false) {
								currRead.put(resourceName, new HashSet<ActorRef>());
							}
							currRead.get(resourceName).add(replyTo);

							log(LogMsg.makeAccessRequestGrantedLogMsg(replyTo,getSelf(),request));
							replyTo.tell(new AccessRequestGrantedMsg((AccessRequestMsg)msg), getSelf());
						}
					}else{
						System.out.println("************************NOPE**************************");
					}
				} else {
					log(LogMsg.makeAccessRequestDeniedLogMsg(
							replyTo,
							getSelf(),
							request,
							AccessRequestDenialReason.RESOURCE_BUSY));
					replyTo.tell(new AccessRequestDeniedMsg(request,AccessRequestDenialReason.RESOURCE_BUSY),
							getSelf());
				}
			} else if(!sourceList.containsKey(resourceName) == true){
				potentialList.put((AccessRequestMsg)msg,remoteManagers.size());
				WhoHasResourceRequestMsg ms = new WhoHasResourceRequestMsg(resourceName);
				remoteManagers.forEach(RM -> {
					if(!resourceMessage.containsKey(RM)) {
						resourceMessage.put(RM, new HashMap<String, Object>());
					}
					resourceMessage.get(RM).put(resourceName, msg);
					RM.tell(ms, getSelf());
				});
			}else{
				log(LogMsg.makeAccessRequestForwardedLogMsg(getSelf(), sourceList.get(resourceName), request));
				sourceList.get(resourceName).tell(msg, getSelf());
			}
		} else if(msg instanceof WhoHasResourceRequestMsg){
			String resourceName=((WhoHasResourceRequestMsg)msg).getResourceName();
			if(localResources.containsKey(resourceName) == false){
				getSender().tell( new WhoHasResourceResponseMsg(resourceName, false, getSelf()), getSelf());
			}else{
				getSender().tell(new WhoHasResourceResponseMsg(resourceName, true, getSelf()), getSelf());
			}
		} else if (msg instanceof WhoHasResourceResponseMsg){
			WhoHasResourceResponseMsg message = (WhoHasResourceResponseMsg) msg;
			Object oldMessage = resourceMessage.get(message.getSender()).get(message.getResourceName());

			if(message.getResult()){
				log(LogMsg.makeRemoteResourceDiscoveredLogMsg(getSelf(), message.getSender(), message.getResourceName()));
				sourceList.put(message.getResourceName(), message.getSender());

				if(oldMessage instanceof ManagementRequestMsg) {
					log(LogMsg.makeManagementRequestForwardedLogMsg(getSelf(), message.getSender(), ((ManagementRequestMsg) oldMessage).getRequest()));
				}
				else if(oldMessage instanceof AccessRequestMsg){
					log(LogMsg.makeAccessRequestForwardedLogMsg(getSelf(), message.getSender(), ((AccessRequestMsg)oldMessage).getAccessRequest()));
				}

				message.getSender().tell(oldMessage, getSelf());
				potentialList.remove(oldMessage);
				resourceMessage.get(message.getSender()).remove(oldMessage);
			} else {
				if(potentialList.get(oldMessage)!= 0){
					potentialList.put(oldMessage, potentialList.get(oldMessage)-1);
				}

				if(potentialList.get(oldMessage) == 0){
					if(oldMessage instanceof ManagementRequestMsg){
						log(LogMsg.makeManagementRequestDeniedLogMsg(((ManagementRequestMsg) oldMessage).getReplyTo(),
								getSelf(), ((ManagementRequestMsg) oldMessage).getRequest(), ManagementRequestDenialReason.RESOURCE_NOT_FOUND));

						ActorRef replyTo = ((ManagementRequestMsg) oldMessage).getReplyTo();

						replyTo.tell(new ManagementRequestDeniedMsg(((ManagementRequestMsg) oldMessage).getRequest()
								,ManagementRequestDenialReason.RESOURCE_NOT_FOUND), getSelf());
					}else{
						log(LogMsg.makeAccessRequestDeniedLogMsg(((AccessRequestMsg) oldMessage).getReplyTo(),
								getSelf(), ((AccessRequestMsg) oldMessage).getAccessRequest(), AccessRequestDenialReason.RESOURCE_NOT_FOUND));

						ActorRef replyTo = ((AccessRequestMsg) oldMessage).getReplyTo();

						replyTo.tell(new AccessRequestDeniedMsg(((AccessRequestMsg) oldMessage).getAccessRequest()
								,AccessRequestDenialReason.RESOURCE_NOT_FOUND), getSelf());
					}
				}
			}
		}
	}
	
	
	
	
	

	public Boolean writeHelp(String resourceName,ActorRef user){
		if(currWrite.containsKey(resourceName) == true){
			for(ActorRef a:currRead.get(resourceName)){
				if(!a.equals(user)){
					return false;
				}
			}
		}
		return true;
	}

	public Boolean readHelp(String resourceName,ActorRef user){
		if(currRead.containsKey(resourceName) == true){
			for(ActorRef a:currRead.get(resourceName)){
				if(!a.equals(user)){
					return false;
				}
			}
		}
		return true;
	}
}
