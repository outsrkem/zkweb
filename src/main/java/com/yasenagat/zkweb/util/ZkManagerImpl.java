package com.yasenagat.zkweb.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.ZooDefs.Perms;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Stat;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.yasenagat.zkweb.util.ZkManagerImpl.ZkConnectInfo.ZkHostPort;

public class ZkManagerImpl implements Watcher,ZkManager {

	private ZooKeeper zk=null;
	private ServerStatusByCMD serverStatusByCMD;
	private ZkConnectInfo zkConnectInfo=new ZkConnectInfo();
	private final String ROOT = "/";
	private static final org.slf4j.Logger log = LoggerFactory.getLogger(ZkManagerImpl.class);
//	private static final ZkManagerImpl _instance = new ZkManagerImpl();
	public ZkManagerImpl(){
		new ZkJMXInfo(zkConnectInfo);
		serverStatusByCMD=new ServerStatusByCMD(zkConnectInfo);
	}
	
	public static ZkManagerImpl createZk(){
		
		return new ZkManagerImpl();
	}
	public static class ZkConnectInfo{
		private String connectStr;
		private int timeout;
		public static class ZkHostPort{
			private String host;
			private int port;
			public String getHost() {
				return host;
			}
			public void setHost(String host) {
				this.host = host;
			}
			public int getPort() {
				return port;
			}
			public void setPort(int port) {
				this.port = port;
			}
		}
		public String getConnectStr() {
			return connectStr;
		}
		public void setConnectStr(String connectStr) {
			this.connectStr = connectStr;
		}
		public List<ZkHostPort> getConnectInfo(){
			List<ZkHostPort> retList=new ArrayList<>();
			for(String hostIp:connectStr.split(",")) {
				ZkHostPort zkHostPort=new ZkHostPort();
				String[] hostIpArray=hostIp.split(":");
				zkHostPort.setHost(hostIpArray[0]);
				if(hostIpArray.length==1) {
					zkHostPort.setPort(2181);
				}else {
					zkHostPort.setPort(Integer.parseInt(hostIpArray[1]));
				}
				retList.add(zkHostPort);
			}
			return retList;
		}
		public int getTimeout() {
			return timeout;
		}
		public void setTimeout(int timeout) {
			this.timeout = timeout;
		}
	}
	private interface ZkState{
		List<PropertyPanel> state() throws IOException, MalformedObjectNameException,  
        InstanceNotFoundException, IntrospectionException, ReflectionException;
		List<PropertyPanel> simpleState() throws IOException, MalformedObjectNameException,  
        InstanceNotFoundException, IntrospectionException, ReflectionException;
	};
	
	public static class ServerStatusByCMD implements ZkState{  
		private ZkConnectInfo zkConnectInfo;
		
		private static final ImmutableMap<String, ImmutableList<String>> cmdKeys=new ImmutableMap.Builder<String, ImmutableList<String>>()
				.put(
				"srvr",ImmutableList.of(
						"Zookeeper version","Latency min/avg/max","Received","Sent",
						"Connections","Outstanding","Zxid","Mode","Node"))
				.put("conf",ImmutableList.of()).put("cons",ImmutableList.of())
				.put("envi",ImmutableList.of()).put("ruok",ImmutableList.of())
				.put("wchs",ImmutableList.of()).put("wchc",ImmutableList.of())
				.put("wchp",ImmutableList.of()).put("mntr",ImmutableList.of()).build();
		private static final ImmutableMap<String, String> cmdFindStr=new ImmutableMap.Builder<String, String>()
				.put("srvr",": ")
				.put("conf","=").put("cons","(")
				.put("envi","=").put("ruok","")
				.put("wchs","").put("wchc","")
				.put("wchp","").put("mntr"," ").build();
	    public ServerStatusByCMD(ZkConnectInfo zkConnectInfo) {
	    	this.zkConnectInfo=zkConnectInfo;
		}
	    private List<PropertyPanel> executeOneCmdByWch(Socket sock,String cmd,String group) throws IOException{
	    	BufferedReader reader = null;  
	    	List<PropertyPanel> retList=new ArrayList<>();
	    	try {
            reader = new BufferedReader(new InputStreamReader(sock.getInputStream()));  
            
            String line;  
            String lines="";
            PropertyPanel propertyPanel=new PropertyPanel();
            while ((line = reader.readLine()) != null) {  
            	List<String> keys=cmdKeys.get(cmd);
            	if(keys==null) {
            		continue;
            	}
            	lines=lines+line;
            }  
            propertyPanel=new PropertyPanel();
			propertyPanel.setInfo(cmd, lines.trim(),group);
        	retList.add(propertyPanel);
            return retList;
	    	}finally {
	    		if (reader != null) {  
	                reader.close();  
	            } 
			}
            
	    }
	    private List<PropertyPanel> executeOneCmd(Socket sock,String cmd,String group) throws IOException{
	    	BufferedReader reader = null;  
	    	List<PropertyPanel> retList=new ArrayList<>();
	    	try {
            reader = new BufferedReader(new InputStreamReader(sock.getInputStream()));  
            
            String line;  
            PropertyPanel propertyPanel=new PropertyPanel();
            while ((line = reader.readLine()) != null) {  
            	List<String> keys=cmdKeys.get(cmd);
            	if(keys==null) {
            		continue;
            	}
            	for(int i=0;i<keys.size();i++) {
            		if(cmd.equals("ruok")) {
            			propertyPanel=new PropertyPanel();
        				propertyPanel.setInfo(keys.get(i), line.trim(),group);
                    	retList.add(propertyPanel);
                    	continue;
            		}
            		if(cmd.equals("conf")||cmd.equals("cons")||cmd.equals("envi")||cmd.equals("mntr")) {
        				propertyPanel=new PropertyPanel();
        				String[] strArray=line.split(cmdFindStr.get(cmd));
        				if(cmd.equals("cons")) {
        					String vString=line.replaceFirst(strArray[0]+cmdFindStr.get(cmd), "").trim();
        					vString=vString.substring(0,vString.length()-1);
        					if(vString.isEmpty()) {
        						continue;
        					}
        					propertyPanel.setInfo(strArray[0], vString ,group);
        				}else {
        					String vString=line.replaceFirst(strArray[0]+cmdFindStr.get(cmd), "").trim();
        					if(vString.isEmpty()) {
        						continue;
        					}
        					propertyPanel.setInfo(strArray[0],vString,group);
        				}
                    	retList.add(propertyPanel);
            			continue;
            		}
            		if (line.indexOf(keys.get(i)+cmdFindStr.get(cmd)) != -1) { 
        				propertyPanel=new PropertyPanel();
        				String vString=line.replaceFirst(keys.get(i)+cmdFindStr.get(cmd), "").trim();
    					if(vString.isEmpty()) {
    						continue;
    					}
                    	propertyPanel.setInfo(keys.get(i), vString,group);
                    	retList.add(propertyPanel);
            		}
            	}
            }  
            return retList;
	    	}finally {
	    		if (reader != null) {  
	                reader.close();  
	            } 
			}
            
	    }
	    private List<PropertyPanel> executeOneCmdSimple(Socket sock,String cmd,String group) throws IOException{
	    	BufferedReader reader = null;  
	    	List<PropertyPanel> retList=new ArrayList<>();
	    	try {
	    	reader = new BufferedReader(new InputStreamReader(sock.getInputStream()));  
            
            String line;  
            PropertyPanel propertyPanel=new PropertyPanel();
            while ((line = reader.readLine()) != null) {  
            	List<String> keys=cmdKeys.get(cmd);
            	if(keys==null) {
            		continue;
            	}
            	for(int i=0;i<keys.size();i++) {
            		if (line.indexOf(keys.get(i)+cmdFindStr.get(cmd)) != -1) { 
            			if(keys.get(i).equals("Mode")) {
        					propertyPanel=new PropertyPanel();
        					String vString=line.replaceFirst(keys.get(i)+cmdFindStr.get(cmd), "").trim();
        					if(vString.isEmpty()) {
        						continue;
        					}
                        	propertyPanel.setInfo(keys.get(i), vString,group);
                        	retList.add(propertyPanel);
                        	return retList;
        				}                        	
            			
                    }
            	}
            }  
            return retList;
	    	}finally {
	    		if (reader != null) {  
	                reader.close();  
	            } 
			}
	    }
	    public List<PropertyPanel> state()  throws IOException, MalformedObjectNameException,  
    	InstanceNotFoundException, IntrospectionException, ReflectionException{  
	    	return innerState(false);
	    }
	    public List<PropertyPanel> simpleState() throws MalformedObjectNameException, InstanceNotFoundException, IntrospectionException, ReflectionException, IOException {
			return innerState(true);
		}  
		public List<PropertyPanel> innerState(boolean simpleFlag)  throws IOException, MalformedObjectNameException,  
        	InstanceNotFoundException, IntrospectionException, ReflectionException{  
	        String host;  
	        int port;
	        List<PropertyPanel> retList=new ArrayList<>();
	        String group;
			for (ZkHostPort zkHostPort : zkConnectInfo.getConnectInfo()) {
				host = zkHostPort.getHost();
				port = zkHostPort.getPort();
				Socket sock = null;

				// cmd="srvr";
				for (String cmd : cmdKeys.keySet()) {
					try {
						sock = new Socket(host, port);
						OutputStream outstream = sock.getOutputStream();
						// ??????Zookeeper???????????????????????????????????????
						outstream.write(cmd.getBytes());
						outstream.flush();
						group = host + "." + port + "." + cmd;
						log.info("group=" + group);
						if (simpleFlag) {
							retList.addAll(executeOneCmdSimple(sock, cmd, group));
							break;
						} else {
							if (cmd.equals("wchs") || cmd.equals("wchc") || cmd.equals("wchp")) {
								retList.addAll(executeOneCmdByWch(sock, cmd, group));
							} else {
								retList.addAll(executeOneCmd(sock, cmd, group));
							}
						}
					} catch (Exception e) {
						sock = null;
						//e.printStackTrace();
						log.info("",e);
						log.error("zk open error for state(four cmd): echo {} |nc {} {}",cmd,host, port,e);
						break;
					} finally {
						if (sock != null) {
							// sock.shutdownOutput();
							sock.close();
						}
					}
				}
			}
			return retList;  
	    }
		
	}  
	public static class ZkJMXInfo {  
	    private JMXConnector connectorJMX;  
	    public ZkJMXInfo(ZkConnectInfo zkConnectInfo) {
		}

		/** 
	     * @param args 
	     * @throws IOException 
	     * @throws MalformedObjectNameException 
	     * @throws InstanceNotFoundException 
	     * @throws ReflectionException 
	     * @throws IntrospectionException 
	     */  
	    public List<Object> state()  throws IOException, MalformedObjectNameException,  
    	InstanceNotFoundException, IntrospectionException, ReflectionException{  
	    	return innerState(false);
	    }
	    public List<Object> simpleState() throws MalformedObjectNameException, InstanceNotFoundException, IntrospectionException, ReflectionException, IOException {
			return innerState(true);
		}  
	    public List<Object> innerState(boolean simpleFlag) throws IOException, MalformedObjectNameException,  
	        InstanceNotFoundException, IntrospectionException, ReflectionException {  
	    	List<Object> retList=new ArrayList<>();
	        PropertyPanel propertyPanel=new PropertyPanel();
	        propertyPanel.setInfo("jmx","unsupported" ,"jmx");
	        retList.add(propertyPanel);
	        return retList;
	        /*
	        OperatingSystemMXBean osbean = ManagementFactory.getOperatingSystemMXBean(); 
	        ///TODO
	        System.out.println("????????????:" + osbean.getArch());//????????????????????????  
	        System.out.println("???????????????:" + osbean.getAvailableProcessors());///??????  
	        System.out.println("??????:" + osbean.getName());//??????  
	  
	        System.out.println(osbean.getVersion());//??????????????????  
	        ThreadMXBean threadBean=ManagementFactory.getThreadMXBean();  
	        System.out.println("????????????:" + threadBean.getThreadCount());//????????????  
	  
	        ClassLoadingMXBean classLoadingMXBean = ManagementFactory.getClassLoadingMXBean();  
	        CompilationMXBean compilationMXBean = ManagementFactory.getCompilationMXBean();  
	        System.out.println("===========");  
	  
	        // ?????? MBeanServer??????????????? MXBean ??????  
	        MBeanServerConnection mbsc = createMBeanServer("192.168.1.100", "9991", "controlRole", "123456");  
	  
	        // ????????????  
	        ObjectName os = new ObjectName("java.lang:type=OperatingSystem");  
	        System.out.println("????????????:" + getAttribute(mbsc, os, "Arch"));//????????????  
	        System.out.println("???????????????:" + getAttribute(mbsc, os, "AvailableProcessors"));//??????  
	        System.out.println("???????????????:" + getAttribute(mbsc, os, "TotalPhysicalMemorySize"));//???????????????  
	        System.out.println("??????????????????:" + getAttribute(mbsc, os, "FreePhysicalMemorySize"));//??????????????????  
	        System.out.println("???????????????:" + getAttribute(mbsc, os, "TotalSwapSpaceSize"));//???????????????  
	        System.out.println("??????????????????:" + getAttribute(mbsc, os, "FreeSwapSpaceSize"));//??????????????????  
	  
	        System.out.println("????????????:" + getAttribute(mbsc, os, "Name")+ getAttribute(mbsc, os, "Version"));//????????????  
	        System.out.println("?????????????????????:" + getAttribute(mbsc, os, "CommittedVirtualMemorySize"));//?????????????????????  
	        System.out.println("??????cpu?????????:" + getAttribute(mbsc, os, "SystemCpuLoad"));//??????cpu?????????  
	        System.out.println("??????cpu?????????:" + getAttribute(mbsc, os, "ProcessCpuLoad"));//??????cpu?????????  
	  
	        System.out.println("============");//  
	        // ??????  
	        ObjectName Threading = new ObjectName("java.lang:type=Threading");  
	        System.out.println("????????????:" + getAttribute(mbsc, Threading, "ThreadCount"));// ????????????  
	        System.out.println("??????????????????:" + getAttribute(mbsc, Threading, "DaemonThreadCount"));// ??????????????????  
	        System.out.println("??????:" + getAttribute(mbsc, Threading, "PeakThreadCount"));// ??????  
	        System.out.println("?????????????????????:" + getAttribute(mbsc, Threading, "TotalStartedThreadCount"));// ?????????????????????  
	        ThreadMXBean threadBean2 = ManagementFactory.newPlatformMXBeanProxy  
	                (mbsc, ManagementFactory.THREAD_MXBEAN_NAME, ThreadMXBean.class);  
	        System.out.println("????????????:" + threadBean2.getThreadCount());// ????????????  
	        ThreadMXBean threadBean3 = ManagementFactory.getThreadMXBean();  
	        System.out.println("??????????????????:" + threadBean3.getThreadCount());// ??????????????????  
	  
	        System.out.println("============");//  
	        ObjectName Compilation = new ObjectName("java.lang:type=Compilation");  
	        System.out.println("??????????????? ??????:" + getAttribute(mbsc, Compilation, "TotalCompilationTime"));// ??????????????? ??????  
	  
	        System.out.println("============");//  
	        ObjectName ClassLoading = new ObjectName("java.lang:type=ClassLoading");  
	        System.out.println("??????????????????:" + getAttribute(mbsc, ClassLoading, "TotalLoadedClassCount"));// ??????????????????  
	        System.out.println("??????????????????:" + getAttribute(mbsc, ClassLoading, "LoadedClassCount"));// ??????????????????  
	        System.out.println("??????????????????:" + getAttribute(mbsc, ClassLoading, "UnloadedClassCount"));// ??????????????????  
	  
	  
	        System.out.println("==========================================================");//  
	        // http://zookeeper.apache.org/doc/r3.4.6/zookeeperJMX.html  
	        // org.apache.ZooKeeperService:name0=ReplicatedServer_id1,name1=replica.1,name2=Follower  
	        ObjectName replica = new ObjectName("org.apache.ZooKeeperService:name0=ReplicatedServer_id1,name1=replica.1");  
	        System.out.println("replica.1????????????:" + getAttribute(mbsc, replica, "State"));// ????????????  
	  
	        mbsc = createMBeanServer("192.168.1.100", "9992", "controlRole", "123456");  
	        System.out.println("==============???????????????===========");  
	        ObjectName dataTreePattern = new ObjectName("org.apache.ZooKeeperService:name0=ReplicatedServer_id?,name1=replica.?,name2=*,name3=InMemoryDataTree");  
	        Set<ObjectName> dataTreeSets = mbsc.queryNames(dataTreePattern, null);  
	        Iterator<ObjectName> dataTreeIterator = dataTreeSets.iterator();  
	        // ????????????  
	        while (dataTreeIterator.hasNext()) {  
	            ObjectName dataTreeObjectName = dataTreeIterator.next();  
	            DataTreeMXBean dataTree = JMX.newMBeanProxy(mbsc, dataTreeObjectName, DataTreeMXBean.class);  
	            System.out.println("????????????:" + dataTree.getNodeCount());// ????????????  
	            System.out.println("Watch??????:" + dataTree.getWatchCount());// Watch??????  
	            System.out.println("??????????????????:" + dataTree.countEphemerals());// Watch??????  
	            System.out.println("????????????????????????:" + dataTree.approximateDataSize());// ????????????????????????????????????  
	  
	            Map<String, String> dataTreeMap = dataTreeObjectName.getKeyPropertyList();  
	            String replicaId = dataTreeMap.get("name1").replace("replica.", "");  
	            String role = dataTreeMap.get("name2");// Follower,Leader,Observer,Standalone  
	            String canonicalName = dataTreeObjectName.getCanonicalName();  
	            int roleEndIndex = canonicalName.indexOf(",name3");  
	  
	            ObjectName roleObjectName = new ObjectName(canonicalName.substring(0, roleEndIndex));  
	            System.out.println("==============zk????????????===========");  
	            ZooKeeperServerMXBean ZooKeeperServer = JMX.newMBeanProxy(mbsc, roleObjectName, ZooKeeperServerMXBean.class);  
	            System.out.println(role + " ???IP?????????:" + ZooKeeperServer.getClientPort());// IP?????????  
	            System.out.println(role + " ??????????????????:" + ZooKeeperServer.getNumAliveConnections());// ?????????  
	            System.out.println(role + " ??????????????????:" + ZooKeeperServer.getOutstandingRequests());// ?????????????????????  
	            System.out.println(role + " ????????????:" + ZooKeeperServer.getPacketsReceived());// ????????????  
	            System.out.println(role + " ????????????:" + ZooKeeperServer.getPacketsSent());// ????????????  
	            System.out.println(role + " ????????????????????????:" + ZooKeeperServer.getAvgRequestLatency());  
	            System.out.println(role + " ????????????????????????:" + ZooKeeperServer.getMaxRequestLatency());  
	  
	            System.out.println(role + " ???????????????IP????????????????????????:" + ZooKeeperServer.getMaxClientCnxnsPerHost());  
	            System.out.println(role + " ??????Session??????????????????:" + ZooKeeperServer.getMaxSessionTimeout());  
	            System.out.println(role + " ????????????????????????:" + ZooKeeperServer.getTickTime());  
	            System.out.println(role + " ??????:" + ZooKeeperServer.getVersion());// ??????  
	            // ??????????????????  
//	            ZooKeeperServer.resetLatency(); //??????min/avg/max latency statistics  
//	            ZooKeeperServer.resetMaxLatency(); //????????????????????????  
//	            ZooKeeperServer.resetStatistics(); // ??????????????????????????????  
	  
	  
	            System.out.println("==============??????????????????????????????===========");  
	            ObjectName connectionPattern = new ObjectName("org.apache.ZooKeeperService:name0=ReplicatedServer_id?,name1=replica.?,name2=*,name3=Connections,*");  
	            Set<ObjectName> connectionSets = mbsc.queryNames(connectionPattern, null);  
	            List<ObjectName> connectionList = new ArrayList<ObjectName>(connectionSets.size());  
	            connectionList.addAll(connectionSets);  
	            Collections.sort(connectionList);  
	            for (ObjectName connectionON : connectionList) {  
	                System.out.println("=========================");  
	                ConnectionMXBean connectionBean = JMX.newMBeanProxy(mbsc, connectionON, ConnectionMXBean.class);  
	                System.out.println(" IP+Port:" + connectionBean.getSourceIP());//  
	                System.out.println(" SessionId:" + connectionBean.getSessionId());//  
	                System.out.println(" PacketsReceived:" + connectionBean.getPacketsReceived());// ????????????  
	                System.out.println(" PacketsSent:" + connectionBean.getPacketsSent());// ????????????  
	                System.out.println(" MinLatency:" + connectionBean.getMinLatency());//  
	                System.out.println(" AvgLatency:" + connectionBean.getAvgLatency());//  
	                System.out.println(" MaxLatency:" + connectionBean.getMaxLatency());//  
	                System.out.println(" StartedTime:" + connectionBean.getStartedTime());//  
	                System.out.println(" EphemeralNodes:" + connectionBean.getEphemeralNodes().length);//  
	                System.out.println(" EphemeralNodes:" + Arrays.asList(connectionBean.getEphemeralNodes()));//  
	                System.out.println(" OutstandingRequests:" + connectionBean.getOutstandingRequests());//  
	                  
	                //connectionBean.resetCounters();  
	                //connectionBean.terminateConnection();  
	                //connectionBean.terminateSession();  
	            }  
	        }  
	        // close connection  
	        if (connectorJMX != null) {  
	            connectorJMX.close();  
	        }
			return retList;  */
	    }  
	  
	    /** 
	     * ???????????? 
	     * 
	     * @param ip 
	     * @param jmxport 
	     * @return 
	     */  
	    public MBeanServerConnection createMBeanServer(String ip, String jmxport, String userName, String password) {  
	        try {  
	            String jmxURL = "service:jmx:rmi:///jndi/rmi://" + ip + ":"  
	                    + jmxport + "/jmxrmi";  
	            // jmxurl  
	            JMXServiceURL serviceURL = new JMXServiceURL(jmxURL);  
	  
	            Map<String, String[]> map = new HashMap<String, String[]>();  
	            String[] credentials = new String[] { userName, password };  
	            map.put("jmx.remote.credentials", credentials);  
	            connectorJMX = JMXConnectorFactory.connect(serviceURL, map);  
	            MBeanServerConnection mbsc = connectorJMX.getMBeanServerConnection();  
	            return mbsc;  
	  
	        } catch (IOException ioe) {  
	        	log.info("",ioe);
	            log.error(ip + ":" + jmxport + " ??????????????????");  
	        }  
	        return null;  
	    }  
	  
	    /** 
	     * ??????MBeanServer??????????????????[objName]???MBean???[objAttr]????????? 
	     * <p> 
	     * ????????????: return MBeanServer.getAttribute(ObjectName name, String attribute) 
	     * 
	     * @param mbeanServer 
	     *            - MBeanServer?????? 
	     * @param objName 
	     *            - MBean???????????? 
	     * @param objAttr 
	     *            - MBean?????????????????? 
	     * @return ????????? 
	     */  
	    @SuppressWarnings("unused")
		private String getAttribute(MBeanServerConnection mbeanServer,  
	            ObjectName objName, String objAttr) {  
	        if (mbeanServer == null || objName == null || objAttr == null)  
	            throw new IllegalArgumentException();  
	        try {  
	            return String.valueOf(mbeanServer.getAttribute(objName, objAttr));  
	        } catch (Exception e) {  
	            return null;  
	        }  
	    }  
	}  
//	public boolean connect(Properties p) {
//
//		try {
//			return this.connect(p.getProperty(P.host.toString()), (Integer
//					.valueOf(p.getProperty(P.sessionTimeOut.toString()))));
//		} catch (Exception e) {
//			log.info("",e);
//			return false;
//		}
//	};
//
//	private boolean connect(String host, int timeout) {
//		try {
//			if (null == zk) {
//				zk = new ZooKeeper(host, timeout, this);
//			}
//		} catch (Exception e) {
//			log.info("",e);
//			return false;
//		}
//		return true;
//	}
	
//	public ZkManagerImpl connect() {
//
//		try {
//			Properties p = ConfigUtil.getP();
//			return this.connect(p.getProperty(P.host.toString()), (Integer
//					.valueOf(p.getProperty(P.sessionTimeOut.toString()))));
//		} catch (Exception e) {
//			log.info("",e);
//			return this;
//		}
//	};
//	
//	public ZkManagerImpl connect(Properties p) {
//
//		try {
//			return this.connect(p.getProperty(P.host.toString()), (Integer
//					.valueOf(p.getProperty(P.sessionTimeOut.toString()))));
//		} catch (Exception e) {
//			log.info("",e);
//			return this;
//		}
//	};
	@Override
	public List<PropertyPanel> getJMXInfo(boolean simpleFlag) {
		try {
			if(simpleFlag)
				return serverStatusByCMD.simpleState();
			//return jmxInfo.state();
			return serverStatusByCMD.state();
		} catch (MalformedObjectNameException | InstanceNotFoundException | IntrospectionException | ReflectionException
				| IOException e) {
			log.info("",e);
		}
		return Collections.emptyList();
	}

	public ZkManagerImpl connect(String host, int timeout) {
		try {
			zkConnectInfo.setConnectStr(host);
			zkConnectInfo.setTimeout(timeout);
			if (null == zk) {
				zk = new ZooKeeper(host, timeout, this);
			}
		} catch (Exception e) {
			log.info("",e);
		}
		return this;
	}

	public boolean disconnect() {
		if (zk != null) {
			try {
				zk.close();
				zk = null;
				return true;
			} catch (InterruptedException e) {
				log.info("",e);
				return false;
			}
		} else {
			log.error("zk is not init");
		}
		return false;
	};

	public List<String> getChildren(String path){

		try {
			return zk.getChildren(path == null ? ROOT : path, false);
		} catch (Exception e) {
			log.info("",e);
			reconnect();
		}
		return new ArrayList<String>();
	}

	public String getData(String path) {
		return getData(path,true);
	}
	public String getData(String path,boolean isPrintLog) {
		try {
			Stat s = zk.exists(path, false);
			if (s != null) {
				byte b[] = zk.getData(path, false, s);
				if(null == b){
					return "";
				}
				String pathContent=new String(zk.getData(path, false, s));
				if(isPrintLog)log.info("data[{}] : {}",path,pathContent);
				return pathContent;
			}
		} catch (Exception e) {
			log.info("",e);
			reconnect();
		}
		return null;
	}

	public Map<String, String> getNodeMeta(String nodePath) {
		Map<String, String> nodeMeta = new LinkedHashMap<String, String>();
		try {
			if (nodePath.length() == 0) {
				nodePath = ROOT;
			}
			Stat s = zk.exists(nodePath, false);
			if (s != null) {
				SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
				String timeStr;
				nodeMeta.put(Meta.aversion.toString(),
						String.valueOf(s.getAversion()));
				timeStr = sdf.format(new Date(s.getCtime()));
				nodeMeta.put(Meta.ctime.toString(),
						timeStr+" ["+String.valueOf(s.getCtime())+"]");
				nodeMeta.put(Meta.cversion.toString(),
						String.valueOf(s.getCversion()));
				nodeMeta.put(Meta.czxid.toString(),
						String.valueOf(s.getCzxid()));
				nodeMeta.put(Meta.dataLength.toString(),
						String.valueOf(s.getDataLength()));
				nodeMeta.put(Meta.ephemeralOwner.toString(),
						String.valueOf(s.getEphemeralOwner()));
				timeStr = sdf.format(new Date(s.getMtime()));
				nodeMeta.put(Meta.mtime.toString(),
						timeStr+" ["+String.valueOf(s.getMtime())+"]");
				nodeMeta.put(Meta.mzxid.toString(),
						String.valueOf(s.getMzxid()));
				nodeMeta.put(Meta.numChildren.toString(),
						String.valueOf(s.getNumChildren()));
				nodeMeta.put(Meta.pzxid.toString(),
						String.valueOf(s.getPzxid()));
				nodeMeta.put(Meta.version.toString(),
						String.valueOf(s.getVersion()));
			}
		} catch (Exception e) {
			e.printStackTrace();
			log.error("",e);
			reconnect();
		}
		return nodeMeta;
	}

	public List<Map<String, String>> getACLs(String nodePath) {
		List<Map<String, String>> returnACLs = new ArrayList<Map<String, String>>();
		try {
			if (nodePath.length() == 0) {
				nodePath = ROOT;
			}
			Stat s = zk.exists(nodePath, false);
			if (s != null) {
				List<ACL> acls = zk.getACL(nodePath, s);
				for (ACL acl : acls) {
					Map<String, String> aclMap = new LinkedHashMap<String, String>();
					aclMap.put(Acl.scheme.toString(), acl.getId().getScheme());
					aclMap.put(Acl.id.toString(), acl.getId().getId());
					StringBuilder sb = new StringBuilder();
					int perms = acl.getPerms();
					boolean addedPerm = false;
					if ((perms & Perms.READ) == Perms.READ) {
						sb.append("Read");
						addedPerm = true;
					}
					if (addedPerm) {
						sb.append(", ");
					}
					if ((perms & Perms.WRITE) == Perms.WRITE) {
						sb.append("Write");
						addedPerm = true;
					}
					if (addedPerm) {
						sb.append(", ");
					}
					if ((perms & Perms.CREATE) == Perms.CREATE) {
						sb.append("Create");
						addedPerm = true;
					}
					if (addedPerm) {
						sb.append(", ");
					}
					if ((perms & Perms.DELETE) == Perms.DELETE) {
						sb.append("Delete");
						addedPerm = true;
					}
					if (addedPerm) {
						sb.append(", ");
					}
					if ((perms & Perms.ADMIN) == Perms.ADMIN) {
						sb.append("Admin");
						addedPerm = true;
					}
					aclMap.put(Acl.perms.toString(), sb.toString());
					returnACLs.add(aclMap);
				}
			}
		} catch (Exception e) {
			log.info("",e);
			//log.error("",e);
			reconnect();
		}
		return returnACLs;
	}

	public boolean createNode(String path, String nodeName,String data) {
		try {
			String p;
			if(ROOT.equals(path)){
				p = path + nodeName;
			}else {
				p = path + "/" + nodeName;
			}
			Stat s = zk.exists(p, false);
			if (s == null)
			{
				zk.create(p, data.getBytes(),
						Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
			}
			return true;
		} catch (Exception e) {
			log.info("",e);
			reconnect();
		}
		return false;
	}

	public boolean deleteNode(String nodePath) {
		try {
			Stat s = zk.exists(nodePath, false);
			if (s != null) {
				List<String> children = zk.getChildren(nodePath, false);
				for (String child : children) {
					String node = nodePath + "/" + child;
					deleteNode(node);
				}
				zk.delete(nodePath, -1);
			}
			return true;
		} catch (Exception e) {
			log.info("",e);
			reconnect();
		}
		return false;
	}

	public boolean setData(String nodePath, String data) {
		try {
			zk.setData(nodePath, data.getBytes("utf-8"), -1);
			return true;
		} catch (Exception e) {
			log.info("",e);
			reconnect();
		}
		return false;
	}

	public void process(WatchedEvent arg0) {
		// do nothing
	}

	public long getNodeId(String nodePath) {
		
		try {
			Stat s = zk.exists(nodePath, false);
			if(s != null){
				return s.getPzxid();
			}
		} catch (Exception e) {
			log.info("",e);
			reconnect();
		} 

		return 0l;
	}

	@Override
	public void reconnect(){
		if(zk != null) {
			try {
			zk.close();
			}catch (Exception e) {
			}
			try {
			zk=new ZooKeeper(this.zkConnectInfo.getConnectStr(), this.zkConnectInfo.getTimeout(),this);
			}catch (Exception e) {
				log.info("",e);
				zk=null;
			}
		}
		
	}


}
